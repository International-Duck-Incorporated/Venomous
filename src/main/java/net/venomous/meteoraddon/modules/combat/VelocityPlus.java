package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.EnumSetting;

public class VelocityPlus extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgGrim = this.settings.createGroup("Grim Exploits");
    private final SettingGroup sgVulcan = this.settings.createGroup("Vulcan");
    private final Setting<VelocityPlus.Mode> mode = this.sgGeneral
        .add(
            new EnumSetting.Builder<VelocityPlus.Mode>().name("mode").description("Velocity mode — select your anticheat.")
                .defaultValue(VelocityPlus.Mode.GrimBoat)
                .build()
        );
    private final Setting<Double> horizontal = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("horizontal")
                .description("Horizontal velocity % (non-exploit modes).")
                .defaultValue(0.0)
                .range(0.0, 100.0)
                .sliderMax(100.0)
                .visible(() -> !this.isGrimMode() && !this.isVulcanMode())
                .build()
        );
    private final Setting<Double> vertical = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("vertical")
                .description("Vertical velocity % (non-exploit modes).")
                .defaultValue(0.0)
                .range(0.0, 100.0)
                .sliderMax(100.0)
                .visible(() -> !this.isGrimMode() && !this.isVulcanMode())
                .build()
        );
    private final Setting<Boolean> onlyOnGround = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("only-on-ground")
                .description("Only reduce velocity when on ground.")
                .defaultValue(false)
                .build()
        );
    private final Setting<Double> grimBoatKBReduction = this.sgGrim
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("grim-boat-kb-reduction")
                .description("How much to move AGAINST the knockback (up to ~1.0 safe with boat nearby).")
                .defaultValue(0.6)
                .range(0.0, 1.2)
                .sliderMax(1.2)
                .visible(() -> this.mode.get() == VelocityPlus.Mode.GrimBoat)
                .build()
        );
    private final Setting<Boolean> grimAutoBoat = this.sgGrim
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("grim-auto-boat")
                .description("Auto-place boat if none nearby (GrimBoat mode).")
                .defaultValue(true)
                .visible(() -> this.mode.get() == VelocityPlus.Mode.GrimBoat)
                .build()
        );
    private final Setting<Double> grimJumpChance = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("grim-jump-chance")
                .description("Chance to jump when receiving KB (GrimJumpReset).")
                .defaultValue(0.8)
                .range(0.0, 1.0)
                .sliderMax(1.0)
                .visible(() -> this.mode.get() == VelocityPlus.Mode.GrimJumpReset)
                .build()
        );
    private final Setting<Boolean> grimWTap = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("grim-w-tap")
                .description("W-tap after KB (GrimSprintReset).")
                .defaultValue(true)
                .visible(() -> this.mode.get() == VelocityPlus.Mode.GrimSprintReset)
                .build()
        );
    private final Setting<Integer> grimWTapDuration = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("grim-w-tap-duration")
                .description("Ticks to release W (GrimSprintReset).")
                .defaultValue(3)
                .range(1, 10)
                .visible(() -> this.mode.get() == VelocityPlus.Mode.GrimSprintReset && (Boolean)this.grimWTap.get())
                .build()
        );
    private final Setting<Boolean> vulcanCancelTransaction = this.sgVulcan
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("vulcan-cancel-transaction")
                .description("Cancel Vulcan velocity transaction packets (IDs -30767 to -28767 range).")
                .defaultValue(true)
                .visible(() -> this.mode.get() == VelocityPlus.Mode.VulcanTransaction)
                .build()
        );
    private final Setting<Integer> vulcanMinTransactionId = this.sgVulcan
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("vulcan-min-transaction-id")
                .description("Min transaction ID for Vulcan velocity packets (-30767).")
                .defaultValue(-30767)
                .range(-40000, -20000)
                .visible(() -> this.mode.get() == VelocityPlus.Mode.VulcanTransaction && (Boolean)this.vulcanCancelTransaction.get())
                .build()
        );
    private final Setting<Integer> vulcanMaxTransactionId = this.sgVulcan
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("vulcan-max-transaction-id")
                .description("Max transaction ID for Vulcan velocity packets (-28767).")
                .defaultValue(-28767)
                .range(-40000, -20000)
                .visible(() -> this.mode.get() == VelocityPlus.Mode.VulcanTransaction && (Boolean)this.vulcanCancelTransaction.get())
                .build()
        );
    private final Setting<Boolean> vulcanCancelVelocity = this.sgVulcan
        .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                .name("vulcan-cancel-velocity")
                .description("Also cancel the velocity packet itself (VulcanTransaction mode).")
                .defaultValue(true)
                .visible(() -> this.mode.get() == VelocityPlus.Mode.VulcanTransaction)
                .build()
        );
    private final Setting<Double> vulcanHReduce = this.sgVulcan
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("vulcan-h-reduce")
                .description("Horizontal KB % for VulcanReduce mode.")
                .defaultValue(20.0)
                .range(0.0, 100.0)
                .sliderMax(100.0)
                .visible(() -> this.mode.get() == VelocityPlus.Mode.VulcanReduce)
                .build()
        );
    private final Setting<Double> vulcanVReduce = this.sgVulcan
        .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                .name("vulcan-v-reduce")
                .description("Vertical KB % for VulcanReduce mode.")
                .defaultValue(50.0)
                .range(0.0, 100.0)
                .sliderMax(100.0)
                .visible(() -> this.mode.get() == VelocityPlus.Mode.VulcanReduce)
                .build()
        );
    private boolean shouldJump = false;
    private int strafeTicks = 0;
    private int wTapTicks = 0;
    private int ticksSinceBoat = 999;
    private boolean receivedVulcanVelocity = false;

    public VelocityPlus() {
        super(VenomousAddon.CATEGORY, "impact", "Velocity bypasses for Grim/Vulcan/NCP/Oxa/Negativity/LAC.");
    }

    private boolean isGrimMode() {
        return this.mode.get() == VelocityPlus.Mode.GrimBoat
            || this.mode.get() == VelocityPlus.Mode.GrimJumpReset
            || this.mode.get() == VelocityPlus.Mode.GrimSprintReset;
    }

    private boolean isVulcanMode() {
        return this.mode.get() == VelocityPlus.Mode.VulcanTransaction || this.mode.get() == VelocityPlus.Mode.VulcanReduce;
    }

    public void onActivate() {
        this.shouldJump = false;
        this.strafeTicks = 0;
        this.wTapTicks = 0;
        this.ticksSinceBoat = 999;
        this.receivedVulcanVelocity = false;
    }

    @Override
    public void onDeactivate() {
        this.shouldJump = false;
        this.strafeTicks = 0;
        this.wTapTicks = 0;
        this.ticksSinceBoat = 999;
        this.receivedVulcanVelocity = false;
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        if (this.mc.player != null) {
            Packet<?> packet = event.packet;
            if (packet instanceof ClientboundSetEntityMotionPacket velocityPacket) {
                if (velocityPacket.id() != this.mc.player.getId()) {
                    return;
                }

                if ((Boolean)this.onlyOnGround.get() && !this.mc.player.onGround()) {
                    return;
                }

                Vec3 motion = velocityPacket.movement();
                switch ((VelocityPlus.Mode)this.mode.get()) {
                    case GrimBoat:
                        boolean boatNearby = this.isBoatNearby();
                        if (boatNearby) {
                            this.ticksSinceBoat = 0;
                        } else {
                            this.ticksSinceBoat++;
                        }

                        if (this.ticksSinceBoat <= 3) {
                            double reduction = (Double)this.grimBoatKBReduction.get();
                            double counterX = -motion.x * reduction;
                            double counterZ = -motion.z * reduction;
                            this.mc.player.setDeltaMovement(counterX, this.mc.player.getDeltaMovement().y, counterZ);
                        } else if ((Boolean)this.grimAutoBoat.get()) {
                            this.placeBoat();
                        }

                        if (this.mc.player.onGround()) {
                            this.shouldJump = true;
                        }
                        break;
                    case GrimJumpReset:
                        if (this.mc.player.onGround() && Math.random() < (Double)this.grimJumpChance.get()) {
                            this.shouldJump = true;
                        }
                        break;
                    case GrimSprintReset:
                        if ((Boolean)this.grimWTap.get()) {
                            this.wTapTicks = (Integer)this.grimWTapDuration.get();
                        }

                        this.mc.player.setSprinting(false);
                        break;
                    case VulcanTransaction:
                        if ((Boolean)this.vulcanCancelVelocity.get()) {
                            event.cancel();
                            this.mc.player.setDeltaMovement(motion.x * 0.0, motion.y * 0.5, motion.z * 0.0);
                        } else {
                            this.mc.player.setDeltaMovement(motion.x * 0.1, motion.y * 0.5, motion.z * 0.1);
                        }

                        this.receivedVulcanVelocity = true;
                        break;
                    case VulcanReduce: {
                        double hF = (Double)this.vulcanHReduce.get() / 100.0;
                        double vF = (Double)this.vulcanVReduce.get() / 100.0;
                        this.mc.player.setDeltaMovement(motion.x * hF, motion.y * vF, motion.z * hF);
                        event.cancel();
                        break;
                    }
                    case Cancel:
                        event.cancel();
                        break;
                    case Reduce: {
                        double hF = (Double)this.horizontal.get() / 100.0;
                        double vF = (Double)this.vertical.get() / 100.0;
                        this.mc.player.setDeltaMovement(motion.x * hF, motion.y * vF, motion.z * hF);
                        event.cancel();
                        break;
                    }
                    case JumpReset:
                        this.shouldJump = true;
                        this.mc.player.setDeltaMovement(motion.x * 0.1, motion.y * 0.5, motion.z * 0.1);
                        event.cancel();
                        break;
                    case Strafe:
                        this.strafeTicks = 10;
                        this.mc.player.setDeltaMovement(motion.x * 0.3, motion.y * 0.8, motion.z * 0.3);
                        event.cancel();
                        break;
                    case Reverse:
                        this.mc.player.setDeltaMovement(-motion.x, motion.y, -motion.z);
                        event.cancel();
                        break;
                    case Custom: {
                        double hF = (Double)this.horizontal.get() / 100.0;
                        double vF = (Double)this.vertical.get() / 100.0;
                        this.mc.player.setDeltaMovement(motion.x * hF, motion.y * vF, motion.z * hF);
                        event.cancel();
                        break;
                    }
                    case AAC:
                        this.mc.player.setDeltaMovement(motion.x * 0.05, motion.y * 0.42, motion.z * 0.05);
                        event.cancel();
                        break;
                    case BlocksMC:
                        this.mc.player.setDeltaMovement(motion.x * 0.15, motion.y * 0.6, motion.z * 0.15);
                        event.cancel();
                        break;
                    case Hylex:
                        this.shouldJump = true;
                        this.mc.player.setDeltaMovement(0.0, motion.y * 0.5, 0.0);
                        event.cancel();
                        break;
                    case Hypixel:
                        this.mc.player.setDeltaMovement(motion.x * 0.2, motion.y * 0.65, motion.z * 0.2);
                        event.cancel();
                        break;
                    case Intave:
                        this.mc.player.setDeltaMovement(motion.x * 0.75, motion.y * 0.9, motion.z * 0.75);
                        event.cancel();
                        break;
                    case Amplify: {
                        double hF = (Double)this.horizontal.get() / 100.0;
                        double vF = (Double)this.vertical.get() / 100.0;
                        this.mc.player.setDeltaMovement(motion.x * hF * 1.75, motion.y * vF * 1.75, motion.z * hF * 1.75);
                        event.cancel();
                    }
                }
            }
        }
    }

    @EventHandler
    private void onPacketSend(Send event) {
        if (this.mc.player != null
            && this.mode.get() == VelocityPlus.Mode.VulcanTransaction
            && (Boolean)this.vulcanCancelTransaction.get()
            && event.packet instanceof ServerboundPongPacket pongPacket) {
            int id = pongPacket.getId();
            if (id >= (Integer)this.vulcanMinTransactionId.get() && id <= (Integer)this.vulcanMaxTransactionId.get()) {
                event.cancel();
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player != null) {
            if (this.shouldJump && this.mc.player.onGround()) {
                this.mc.player.jumpFromGround();
                this.shouldJump = false;
            }

            if (this.strafeTicks > 0) {
                this.strafeTicks--;
                boolean left = this.mc.player.tickCount % 4 < 2;
                this.mc.player.input.keyPresses = new Input(
                    this.mc.player.input.keyPresses.forward(),
                    this.mc.player.input.keyPresses.backward(),
                    left,
                    !left,
                    this.mc.player.input.keyPresses.jump(),
                    this.mc.player.input.keyPresses.shift(),
                    false
                );
            }

            if (this.wTapTicks > 0) {
                this.wTapTicks--;
                this.mc.player.input.keyPresses = new Input(
                    false,
                    this.mc.player.input.keyPresses.backward(),
                    this.mc.player.input.keyPresses.left(),
                    this.mc.player.input.keyPresses.right(),
                    this.mc.player.input.keyPresses.jump(),
                    this.mc.player.input.keyPresses.shift(),
                    false
                );
            } else if (this.mode.get() == VelocityPlus.Mode.GrimSprintReset) {
                this.mc.player.setSprinting(true);
            }
        }
    }

    private boolean isBoatNearby() {
        if (this.mc.level == null) {
            return false;
        }

        for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof AbstractBoat && entity != this.mc.player.getVehicle() && this.mc.player.distanceTo(entity) < 2.5) {
                return true;
            }
        }

        return false;
    }

    private void placeBoat() {
        int slot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem().toString().toLowerCase().contains("boat")) {
                slot = i;
                break;
            }
        }

        if (slot != -1) {
            int prevSlot = this.mc.player.getInventory().getSelectedSlot();
            this.mc.player.getInventory().setSelectedSlot(slot);
            float prevPitch = this.mc.player.getXRot();
            this.mc.player.setXRot(80.0F);
            this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
            this.mc.player.setXRot(prevPitch);
            this.mc.player.getInventory().setSelectedSlot(prevSlot);
        }
    }

    public String getInfoString() {
        return ((VelocityPlus.Mode)this.mode.get()).name();
    }

    public enum Mode {
        GrimBoat,
        GrimJumpReset,
        GrimSprintReset,
        VulcanTransaction,
        VulcanReduce,
        Cancel,
        Reduce,
        JumpReset,
        Strafe,
        Reverse,
        Custom,
        AAC,
        BlocksMC,
        Hylex,
        Hypixel,
        Intave,
        Amplify;
    }
}
