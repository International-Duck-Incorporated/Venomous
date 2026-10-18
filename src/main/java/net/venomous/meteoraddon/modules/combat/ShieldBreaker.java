package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

/**
 * Defeats players holding up a shield.
 *
 * <p>Merges the former {@code shield-bypass} module. Both modules existed to solve exactly one
 * problem — an enemy blocking with a shield — and both keyed off {@code isBlocking()}; they simply
 * disagreed on the method. They are now two modes of one module:
 *
 * <ul>
 *   <li>{@link Mode#Axe} — swap to an axe, which disables shields, then swap back.</li>
 *   <li>{@link Mode#Behind} — briefly reposition behind the target, where the shield does not
 *       protect, land the hit and return.</li>
 * </ul>
 */
public class ShieldBreaker extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgAxe = this.settings.createGroup("Axe");
    private final SettingGroup sgBehind = this.settings.createGroup("Behind");

    private final Setting<Mode> mode = this.sgGeneral.add(
        new EnumSetting.Builder<Mode>()
            .name("mode")
            .description("Axe swaps to an axe to disable the shield; Behind teleports around the target to hit their back.")
            .defaultValue(Mode.Axe)
            .build()
    );

    private final Setting<Double> range = this.sgAxe.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("Range to detect shielded players.")
            .defaultValue(5.0)
            .range(1.0, 10.0)
            .sliderMax(10.0)
            .visible(() -> this.mode.get() == Mode.Axe)
            .build()
    );

    private final Setting<Boolean> swapBack = this.sgAxe.add(
        new BoolSetting.Builder()
            .name("swap-back")
            .description("Swap back to your weapon after breaking the shield.")
            .defaultValue(true)
            .visible(() -> this.mode.get() == Mode.Axe)
            .build()
    );

    private final Setting<Boolean> requireWeapon = this.sgAxe.add(
        new BoolSetting.Builder()
            .name("require-weapon")
            .description("Only activate when holding a sword.")
            .defaultValue(true)
            .visible(() -> this.mode.get() == Mode.Axe)
            .build()
    );

    private final Setting<Boolean> ignoreAxe = this.sgBehind.add(
        new BoolSetting.Builder()
            .name("ignore-axe")
            .description("Do nothing when holding an axe, since axes already disable shields.")
            .defaultValue(true)
            .visible(() -> this.mode.get() == Mode.Behind)
            .build()
    );

    private final Setting<Double> behindDistance = this.sgBehind.add(
        new DoubleSetting.Builder()
            .name("behind-distance")
            .description("Distance behind the target to briefly attack from.")
            .defaultValue(2.0)
            .range(0.5, 5.0)
            .sliderMax(4.0)
            .visible(() -> this.mode.get() == Mode.Behind)
            .build()
    );

    private State state = State.IDLE;
    private Player targetPlayer = null;
    private int originalSlot = -1;

    public ShieldBreaker() {
        super(VenomousAddon.CATEGORY, "shield-breaker", "Breaks shields by swapping to an axe, or by striking from behind.");
    }

    @Override
    public void onActivate() {
        this.resetState();
    }

    @Override
    public void onDeactivate() {
        this.resetState();
    }

    private void resetState() {
        this.state = State.IDLE;
        this.targetPlayer = null;
        this.originalSlot = -1;
    }

    // ---------------------------------------------------------------- Axe mode

    @EventHandler
    private void onTick(Pre event) {
        if (this.mode.get() != Mode.Axe) {
            if (this.state != State.IDLE) {
                this.resetState();
            }
            return;
        }

        if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null) {
            return;
        }

        switch (this.state) {
            case IDLE -> {
                if (!this.mc.options.keyAttack.isDown()) {
                    return;
                }
                if (this.requireWeapon.get() && !this.mc.player.getMainHandItem().getItem().toString().toLowerCase().contains("sword")) {
                    return;
                }

                this.targetPlayer = this.findShieldedPlayer();
                if (this.targetPlayer != null) {
                    this.originalSlot = this.mc.player.getInventory().getSelectedSlot();
                    this.state = State.SWITCHING_AXE;
                }
            }
            case SWITCHING_AXE -> {
                int axeSlot = this.findAxeSlot();
                if (axeSlot == -1) {
                    this.state = State.IDLE;
                    return;
                }
                InvUtils.swap(axeSlot, false);
                this.state = State.BREAKING;
            }
            case BREAKING -> {
                if (this.targetPlayer != null && this.targetPlayer.isBlocking()) {
                    this.mc.gameMode.attack(this.mc.player, this.targetPlayer);
                    this.mc.player.swing(InteractionHand.MAIN_HAND);
                    if (!this.targetPlayer.isBlocking()) {
                        this.state = State.KILLING;
                    }
                    return;
                }
                this.state = this.swapBack.get() ? State.SWITCHING_BACK : State.IDLE;
            }
            case SWITCHING_BACK -> {
                if (this.originalSlot != -1) {
                    InvUtils.swap(this.originalSlot, false);
                }
                this.state = State.IDLE;
            }
            case KILLING -> {
                if (this.swapBack.get() && this.originalSlot != -1) {
                    InvUtils.swap(this.originalSlot, false);
                }
                this.state = State.IDLE;
            }
        }
    }

    // ------------------------------------------------------------- Behind mode

    @EventHandler
    private void onMouse(MouseClickEvent event) {
        if (this.mode.get() != Mode.Behind) {
            return;
        }

        if (this.mc.player == null
            || this.mc.gameMode == null
            || this.mc.level == null
            || this.mc.getConnection() == null
            || this.mc.gui.screen() != null
            || event.action != KeyAction.Press
            || event.button() != 0) {
            return;
        }

        if (this.mc.hitResult instanceof EntityHitResult hit) {
            this.strikeFromBehind(hit.getEntity(), event);
        }
    }

    private void strikeFromBehind(Entity target, MouseClickEvent event) {
        if (!(target instanceof LivingEntity living) || !living.isBlocking()) {
            return;
        }
        if (this.ignoreAxe.get() && InvUtils.testInMainHand(stack -> stack.getItem() instanceof AxeItem)) {
            return;
        }

        Vec3 original = this.mc.player.position();
        Vec3 look = living.getViewVector(1.0F).normalize();
        Vec3 newPos = living.position().subtract(look.scale(this.behindDistance.get()));

        for (double y = 0.0; y <= 3.0; y += 0.25) {
            Vec3 candidate = newPos.add(0.0, y, 0.0);
            if (this.mc.level.noCollision(this.mc.player, this.mc.player.getBoundingBox().move(candidate.subtract(original)))) {
                newPos = candidate;
                break;
            }
        }

        boolean collided = this.mc.player.horizontalCollision;
        event.setCancelled(true);

        this.mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(newPos.x, newPos.y, newPos.z, true, collided));
        this.mc.player.setPos(newPos.x, newPos.y, newPos.z);
        this.mc.gameMode.attack(this.mc.player, target);
        this.mc.player.swing(InteractionHand.MAIN_HAND);
        this.mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(original.x, original.y, original.z, true, collided));
        this.mc.player.setPos(original.x, original.y, original.z);
    }

    // ------------------------------------------------------------------ Shared

    private Player findShieldedPlayer() {
        if (this.mc.level == null || this.mc.player == null) {
            return null;
        }

        double r = this.range.get();
        AABB box = this.mc.player.getBoundingBox().inflate(r);

        for (Player player : this.mc.level.getEntitiesOfClass(Player.class, box)) {
            if (player != this.mc.player && player.isBlocking() && this.mc.player.distanceTo(player) <= r) {
                return player;
            }
        }
        return null;
    }

    private int findAxeSlot() {
        for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getItem(i).getItem() instanceof AxeItem) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String getInfoString() {
        if (this.mode.get() == Mode.Behind) {
            return "Behind";
        }
        return switch (this.state) {
            case IDLE -> "Axe";
            case SWITCHING_AXE -> "Switching to Axe";
            case BREAKING -> "Breaking Shield";
            case SWITCHING_BACK -> "Switching to Weapon";
            case KILLING -> "Executing Kill";
        };
    }

    public enum Mode {
        /** Swap to an axe to disable the shield (original shield-breaker). */
        Axe,
        /** Reposition behind the target and strike (former shield-bypass). */
        Behind
    }

    private enum State {
        IDLE,
        SWITCHING_AXE,
        BREAKING,
        SWITCHING_BACK,
        KILLING
    }
}
