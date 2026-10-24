package net.venomous.meteoraddon.modules.network;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.McCompat;
import net.venomous.meteoraddon.utils.PacketGates;

/**
 * Lag, but only while it pays.
 *
 * <p>While you are closing on a target inside the reach band, your outbound packets are held,
 * so you swing at where the server still thinks you are - a hitbox or two deeper than your
 * legs say you are. The moment the exchange changes shape you flush everything: a hit of your
 * own, taking damage, a sprint restart, raising your sword, drinking a splash, opening a
 * screen, the target leaving the band. The hold is a device for one purpose, and every
 * condition that ends that purpose ends the hold.</p>
 *
 * <p>Ported from Raven B4's {@code LagRange} on the same terms as the other network ports:
 * its shared lag engine is replaced by an owned queue, the closest-target scan replaces its
 * targeting helper, and the first-person indicator is not carried over. The engage shape is
 * the original's: closing on a target beyond the three-block minimum, or already inside it
 * right after a hit landed.</p>
 */
public class LagRange extends Module {
    /** Inside this distance the hold earns nothing - the hit lands from either position. */
    private static final double MINIMUM_DISTANCE_SQ = 3.0 * 3.0;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> range = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("How far away a target may be for the hold to engage.")
            .defaultValue(6.0)
            .range(3.0, 10.0)
            .sliderMax(10.0)
            .build()
    );

    private final Setting<Integer> maximumDelay = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("maximum-delay-ms")
            .description("How long a held packet waits before it is released regardless of anything else.")
            .defaultValue(200)
            .range(50, 1000)
            .sliderMax(500)
            .build()
    );

    private final Setting<Boolean> ignoreTeammates = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("ignore-teammates")
            .description("Never hold against players on your own team, judged by vanilla's ally check.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> holdingWeapon = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("holding-weapon")
            .description("Only hold while a weapon is in hand.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> sprintReset = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("flush-on-sprint-reset")
            .description("Release the hold when a sprint starts, so the sprint reset is seen by the server.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> blockSword = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("flush-on-sword-block")
            .description("Release the hold when you start blocking.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> usedSplashPotion = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("flush-on-splash")
            .description("Release the hold while throwing a splash potion.")
            .defaultValue(true)
            .build()
    );

    private final Queue<LagRange.Held> held = new ConcurrentLinkedQueue<>();
    private Player target;
    private double lastDistSq = -1.0D;
    private int lastTargetHurtTime;
    private int lastSelfHurtTime;
    private boolean lastSprinting;
    private boolean lastBlocking;
    private boolean engaged;
    private boolean flushing = false;

    public LagRange() {
        super(VenomousAddon.CATEGORY, "lag-range", "Delays your packets only while closing on a target, flushing the moment the exchange changes.");
    }

    @Override
    public void onActivate() {
        this.resetState();
    }

    @Override
    public void onDeactivate() {
        this.flushLag();
        this.resetState();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        // A hit is the whole point of the hold; once one lands, the stale position has paid
        // for itself and the server should see the truth again.
        if (this.engaged && event.entity != null) {
            this.flushLag();
        }
    }

    @EventHandler
    private void onSend(Send event) {
        if (!this.engaged || this.flushing || this.mc.player == null || this.mc.getConnection() == null) return;
        if (PacketGates.isRealtimePassthrough(event.packet)) return;

        event.cancel();
        this.held.add(new LagRange.Held(event.packet, System.currentTimeMillis() + this.maximumDelay.get()));
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.getConnection() == null) {
            this.flushLag();
            this.resetState();
            return;
        }

        // A screen open means the player has stopped fighting; Raven flushes here and so does
        // this, because held packets across an inventory are a desync nobody asked for.
        if (this.mc.gui.screen() != null && this.engaged) {
            this.flushLag();
        }

        double rangeSq = this.range.get() * this.range.get();
        Player next = this.findTarget(rangeSq);
        if (!this.sameTarget(next)) {
            if (this.engaged) this.flushLag();
            this.lastDistSq = -1.0D;
            this.lastTargetHurtTime = next != null ? next.hurtTime : 0;
        }
        this.target = next;

        if (this.target == null) {
            if (this.engaged) this.flushLag();
            this.resetState();
            return;
        }

        double distSq = this.target.getBoundingBox().distanceToSqr(this.mc.player.getEyePosition());
        boolean weaponOk = !this.holdingWeapon.get() || this.isHoldingWeapon();

        if (this.engaged) {
            if (distSq > rangeSq) {
                this.flushLag();
            } else {
                // Rising distance ends the hold: the target is leaving, and the stale position
                // stops being an advantage the moment it is out of reach.
                if (this.lastDistSq >= 0.0D && distSq > this.lastDistSq) this.flushLag();

                int selfHurt = this.mc.player.hurtTime;
                if (selfHurt > this.lastSelfHurtTime) this.flushLag();
                this.lastSelfHurtTime = selfHurt;

                if (weaponOk) {
                    this.releaseDue();

                    boolean sprinting = this.mc.player.isSprinting();
                    if (this.sprintReset.get() && sprinting && !this.lastSprinting) this.flushLag();
                    this.lastSprinting = sprinting;

                    boolean blocking = this.mc.player.isBlocking();
                    if (this.blockSword.get() && blocking && !this.lastBlocking) this.flushLag();
                    this.lastBlocking = blocking;

                    if (this.usedSplashPotion.get() && this.mc.player.isUsingItem()) this.flushLag();
                }
            }

            this.lastDistSq = distSq;
            this.lastTargetHurtTime = this.target.hurtTime;
            return;
        }

        // Not engaged: track the exchange and start the hold when it pays.
        int selfHurt = this.mc.player.hurtTime;
        boolean hitLanded = this.lastTargetHurtTime == 0 && this.target.hurtTime > 0;
        boolean closing = this.lastDistSq >= 0.0D && distSq < this.lastDistSq;
        boolean outsideMinimum = distSq > MINIMUM_DISTANCE_SQ;
        boolean moving = this.isMoving();

        this.lastSelfHurtTime = selfHurt;
        this.lastSprinting = this.mc.player.isSprinting();
        this.lastBlocking = this.mc.player.isBlocking();
        this.lastDistSq = distSq;
        this.lastTargetHurtTime = this.target.hurtTime;

        boolean hitStart = hitLanded && distSq <= MINIMUM_DISTANCE_SQ && selfHurt == 0 && moving && weaponOk;

        if (selfHurt == 0 && weaponOk && moving && (closing && outsideMinimum || hitStart)) {
            this.engaged = true;
        }
    }

    private Player findTarget(double rangeSq) {
        Player best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Player candidate : this.mc.level.players()) {
            if (candidate == this.mc.player || candidate.isRemoved() || candidate.isDeadOrDying()) continue;
            if (this.ignoreTeammates.get() && this.mc.player.isAlliedTo(candidate)) continue;

            double distSq = candidate.distanceToSqr(this.mc.player);
            if (distSq > rangeSq) continue;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = candidate;
            }
        }

        return best;
    }

    private void releaseDue() {
        long now = System.currentTimeMillis();
        this.flushing = true;
        try {
            Iterator<LagRange.Held> iterator = this.held.iterator();
            while (iterator.hasNext()) {
                LagRange.Held entry = iterator.next();
                if (entry.releaseAt > now) continue;
                this.mc.getConnection().send(entry.packet);
                iterator.remove();
            }
        } finally {
            this.flushing = false;
        }
    }

    private void flushLag() {
        if (!this.engaged && this.held.isEmpty()) return;

        this.engaged = false;
        this.flushing = true;
        try {
            LagRange.Held entry;
            while ((entry = this.held.poll()) != null) {
                if (this.mc.getConnection() != null) {
                    this.mc.getConnection().send(entry.packet);
                }
            }
        } finally {
            this.flushing = false;
        }
    }

    private void resetState() {
        this.target = null;
        this.lastDistSq = -1.0D;
        this.lastSelfHurtTime = 0;
        this.lastTargetHurtTime = 0;
        this.lastSprinting = false;
        this.lastBlocking = false;
        this.engaged = false;
    }

    private boolean sameTarget(Player next) {
        if (this.target == null || next == null) return this.target == next;
        return this.target.getId() == next.getId();
    }

    private boolean isHoldingWeapon() {
        return McCompat.isWeapon(this.mc.player.getMainHandItem().getItem());
    }

    private boolean isMoving() {
        return this.mc.options.keyUp.isDown() || this.mc.options.keyDown.isDown()
            || this.mc.options.keyLeft.isDown() || this.mc.options.keyRight.isDown();
    }

    @Override
    public String getInfoString() {
        return this.engaged ? this.maximumDelay.get() + "ms" : null;
    }

    private record Held(Packet<?> packet, long releaseAt) {
    }
}
