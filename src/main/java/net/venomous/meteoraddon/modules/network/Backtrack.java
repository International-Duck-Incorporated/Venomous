package net.venomous.meteoraddon.modules.network;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.BacktrackMath;
import net.venomous.meteoraddon.utils.RodAimMath;

/**
 * Fights the position the server has already stopped sending.
 *
 * <p>One target is picked - the closest valid player inside the cone - and that target's
 * movement packets are held while the geometry says the withheld position is the better one to
 * swing at: further along your aim ray than the position this client still shows, inside the
 * reach band, on the stale side of the exchange. Everything else flushes. The server's idea of
 * where they are and this client's idea drift apart by exactly the packets on the queue, and
 * the queue is released by age, by damage, by teleport, or by the target changing - whichever
 * comes first.</p>
 *
 * <p>Ported from Raven B4's {@code Backtrack}. Three deviations are deliberate. The hold is
 * scoped to one entity's movement packets rather than everything-but-a-whitelist, so a held
 * session cannot starve the world of chunks or the scoreboard of updates. The 1.8 ghost
 * rendering is not carried over. And the release clock is wall-clock milliseconds rather than
 * tick counts, for the same reason the scaffold's cadence is: this client does not run at
 * 20 FPS.</p>
 *
 * <p>Against a lag-compensating server the effect is bounded by what it already grants you -
 * it replays a fight against a position that is stale by your own latency, not by the hold.
 * Against anything that grades packet timing, the release burst is the signature. Short delays
 * on servers that tolerate lag is the usable range, as with every module in this family.</p>
 */
public class Backtrack extends Module {
    /** The standing player box grows by this on every face before the reach tests. */
    private static final double HITBOX_BORDER = 0.1D;

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> delayMs = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("delay-ms")
            .description("How long the target's movement packets wait before they are released.")
            .defaultValue(150)
            .range(50, 1500)
            .sliderMax(1000)
            .build()
    );

    private final Setting<Double> distanceMin = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("distance-min")
            .description("The inner edge of the reach band. A drifted-away position inside this distance is just a hit - no hold.")
            .defaultValue(2.7)
            .range(0.0, 8.0)
            .sliderMax(8.0)
            .build()
    );

    private final Setting<Double> distanceMax = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("distance-max")
            .description("The outer edge of the reach band. Positions beyond it are not held.")
            .defaultValue(5.0)
            .range(2.0, 8.0)
            .sliderMax(8.0)
            .build()
    );

    private final Setting<Double> fov = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("fov")
            .description("The cone, in degrees, inside which a target is picked. 360 takes anything.")
            .defaultValue(360.0)
            .range(20.0, 360.0)
            .sliderMax(360.0)
            .build()
    );

    private final Setting<Boolean> ignoreTeammates = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("ignore-teammates")
            .description("Never hold against players on your own team, judged by vanilla's ally check.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> flushOnDamage = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("flush-on-damage")
            .description("Release the queue when you take damage.")
            .defaultValue(true)
            .build()
    );

    /** Held movement packets for the target, in arrival order. Netty threads write, the tick reads. */
    private final Queue<Backtrack.Held> held = new ConcurrentLinkedQueue<>();
    /** Latest position described by the target's movement stream, including withheld packets. */
    private volatile double[] heldPos;
    private volatile Entity target;
    private boolean flushing = false;

    public Backtrack() {
        super(VenomousAddon.CATEGORY, "backtrack", "Holds one target's movement packets, so you swing at where they were instead of where they are.");
    }

    @Override
    public void onActivate() {
        this.held.clear();
        this.heldPos = null;
        this.target = null;
    }

    @Override
    public void onDeactivate() {
        this.flushAll();
    }

    @EventHandler
    private void onReceive(Receive event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.getConnection() == null) {
            this.discard();
            return;
        }

        Packet<?> packet = event.packet;

        // Triggers that end a hold regardless of geometry.
        if (packet instanceof ClientboundPlayerPositionPacket
            || packet instanceof ClientboundDisconnectPacket) {
            if (this.isHolding()) this.flushAll();
            return;
        }

        if (this.flushOnDamage.get()
            && packet instanceof ClientboundHurtAnimationPacket hurt
            && hurt.id() == this.mc.player.getId()) {
            if (this.isHolding()) this.flushAll();
            return;
        }

        if (this.flushOnDamage.get()
            && packet instanceof ClientboundSetEntityMotionPacket motion
            && motion.id() == this.mc.player.getId()) {
            if (this.isHolding()) this.flushAll();
            return;
        }

        Entity currentTarget = this.target;
        if (currentTarget == null) return;

        // Only the target's own movement is held; the rest of the world streams normally.
        if (packet instanceof ClientboundMoveEntityPacket move && move.hasPosition()
            && move.getEntity(this.mc.level) == currentTarget) {
            if (this.handleTargetMove(move)) event.cancel();
            return;
        }

        if (packet instanceof ClientboundTeleportEntityPacket teleport && teleport.id() == currentTarget.getId()) {
            // A teleport is the server relocating the target outright - the hold's premise is
            // gone, so everything goes and the teleport passes untouched.
            this.flushAll();
        }
    }

    /**
     * The hold decision for one movement packet: apply its delta to the last position described
     * by the movement stream, and keep it only when the result is the worse target of the two.
     *
     * @return true when the packet was held (and must not reach the world), false when it was
     *         passed through or the queue was flushed to make room for it
     */
    private boolean handleTargetMove(ClientboundMoveEntityPacket move) {
        double[] current = this.heldPos != null ? this.heldPos : new double[] {
            this.target.getX(), this.target.getY(), this.target.getZ()
        };

        // Entity movement deltas are fixed point at 1/4096 of a block.
        double[] next = move.hasPosition()
            ? new double[] {current[0] + move.getXa() / 4096.0D,
                            current[1] + move.getYa() / 4096.0D,
                            current[2] + move.getZa() / 4096.0D}
            : current;

        Vec3 eye = this.mc.player.getEyePosition();
        Vec3 look = this.mc.player.getLookAngle();

        boolean worthHolding = BacktrackMath.shouldHoldPacket(
            current, next,
            new double[] {eye.x, eye.y, eye.z},
            new double[] {look.x, look.y, look.z},
            this.distanceMin.get(), this.distanceMax.get(), HITBOX_BORDER);

        // Track the stream position on every packet, not only while holding. If a passed-through
        // move were skipped here, the next delta would be applied to a stale position and every
        // later hold decision would use geometry for a place the target no longer occupies.
        this.heldPos = next;

        if (worthHolding) {
            this.held.add(new Backtrack.Held(move, System.currentTimeMillis() + this.delayMs.get()));
            return true;
        }

        if (this.isHolding()) {
            this.flushAll();
            // flushAll clears the tracking state; retain the position of the packet passing now.
            this.heldPos = next;
        }
        return false;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.getConnection() == null) {
            this.discard();
            return;
        }

        Entity currentTarget = this.target;
        if (currentTarget != null && !currentTarget.isAlive()) {
            this.flushAll();
            this.target = null;
            this.heldPos = null;
            return;
        }

        if (currentTarget == null) {
            Entity picked = this.findTarget();
            if (picked != null) {
                this.target = picked;
                this.heldPos = new double[] {picked.getX(), picked.getY(), picked.getZ()};
            }
            return;
        }

        this.releaseDue();
    }

    private Entity findTarget() {
        double scanRangeSq = (this.distanceMax.get() + 2.5D) * (this.distanceMax.get() + 2.5D);
        float cameraYaw = this.mc.player.getYRot();
        double eyeX = this.mc.player.getX();
        double eyeZ = this.mc.player.getZ();

        Entity best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (Entity candidate : this.mc.level.players()) {
            if (candidate == this.mc.player || !candidate.isAlive()) continue;
            if (candidate instanceof net.minecraft.world.entity.player.Player player) {
                if (this.ignoreTeammates.get() && this.mc.player.isAlliedTo(player)) continue;
            }

            double distSq = candidate.distanceToSqr(this.mc.player);
            if (distSq > scanRangeSq) continue;

            float targetYaw = RodAimMath.yawTo(candidate.getX() - eyeX, candidate.getZ() - eyeZ);
            if (!RodAimMath.withinFov(cameraYaw, targetYaw, this.fov.get())) continue;

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
            Iterator<Backtrack.Held> iterator = this.held.iterator();
            while (iterator.hasNext()) {
                Backtrack.Held entry = iterator.next();
                if (entry.releaseAt > now) continue;

                this.replay(entry.packet);
                iterator.remove();
            }
        } finally {
            this.flushing = false;
        }
    }

    private void flushAll() {
        this.flushing = true;
        try {
            Backtrack.Held entry;
            while ((entry = this.held.poll()) != null) {
                this.replay(entry.packet);
            }
        } finally {
            this.flushing = false;
            this.heldPos = null;
        }
    }

    private void discard() {
        this.held.clear();
        this.heldPos = null;
        this.target = null;
        this.flushing = false;
    }

    @SuppressWarnings("unchecked")
    private void replay(Packet<?> packet) {
        try {
            ((Packet<ClientGamePacketListener>) packet).handle(this.mc.player.connection);
        } catch (Exception ignored) {
            // A replayed packet whose world moved on under it is dropped rather than fatal.
        }
    }

    private boolean isHolding() {
        return !this.held.isEmpty();
    }

    @Override
    public String getInfoString() {
        return this.isHolding() ? this.held.size() + " held" : null;
    }

    /** One withheld movement packet and its release deadline. */
    private record Held(Packet<?> packet, long releaseAt) {
    }
}
