package net.venomous.meteoraddon.modules.world;

import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.AimEase;
import net.venomous.meteoraddon.utils.MovementMath;
import net.venomous.meteoraddon.utils.PreRotate;
import net.venomous.meteoraddon.utils.ScaffoldCadence;
import net.venomous.meteoraddon.utils.RotationGcd;

public class Scaffold extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgGrim = this.settings.createGroup("Grim");
    private final SettingGroup sgVulcan = this.settings.createGroup("Vulcan");
    private final SettingGroup sgBridge = this.settings.createGroup("Bridge Assist");
    private final SettingGroup sgExpand = this.settings.createGroup("Expand");
    private final SettingGroup sgTower = this.settings.createGroup("Tower");

    private final Setting<Scaffold.Technique> technique = this.sgGeneral.add(
        new EnumSetting.Builder<Scaffold.Technique>()
            .name("technique")
            .description("Scaffold technique — select your anticheat or bridging style.")
            .defaultValue(Scaffold.Technique.GrimScaffold)
            .build()
    );

    private final Setting<Boolean> autoSneak = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-sneak")
            .description("Sneak at block edges (Eagle/BridgeAssist).")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> sameY = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("same-y")
            .description("Keep placing at the same Y level.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> swing = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("swing")
            .description("Swing hand when placing.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> autoBlockSwitch = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("auto-block-switch")
            .description("Auto-switch to blocks.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> rotate = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("rotate")
            .description("Rotate towards the placement block.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> placeDelay = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("place-delay")
            .description("Ticks between placement attempts. Higher values reduce packet spam. "
                + "AntiCheat-Addition expects at least " + (int) ScaffoldCadence.ACADDITION_MIN_STRAIGHT_DELAY_MS
                + "ms between placements on a straight bridge, which is about 5 ticks; going "
                + "below that is flagged regardless of how clean the rotation looks.")
            .defaultValue(ScaffoldCadence.DEFAULT_PLACE_DELAY)
            .range(1, 10)
            .sliderMax(10)
            .build()
    );

    private final Setting<Double> bridgeEdgeThreshold = this.sgBridge.add(
        new DoubleSetting.Builder()
            .name("edge-threshold")
            .description("Distance from a block edge before Bridge Assist sneaks.")
            .defaultValue(0.24)
            .range(0.05, 0.49)
            .sliderMax(0.4)
            .visible(this::isBridgeMode)
            .build()
    );

    private final Setting<Boolean> bridgePlaceAssist = this.sgBridge.add(
        new BoolSetting.Builder()
            .name("place-assist")
            .description("Automatically place blocks below or ahead while bridging.")
            .defaultValue(true)
            .visible(this::isBridgeMode)
            .build()
    );

    private final Setting<Boolean> bridgeCameraStabilisation = this.sgBridge.add(
        new BoolSetting.Builder()
            .name("camera-stabilisation")
            .description("Restore visual camera rotation immediately after bridge placement.")
            .defaultValue(true)
            .visible(() -> this.technique.get() == Scaffold.Technique.BridgeMacro || this.technique.get() == Scaffold.Technique.VapeBridge)
            .build()
    );

    private final Setting<Boolean> bridgePitchAssist = this.sgBridge.add(
        new BoolSetting.Builder()
            .name("pitch-assist")
            .description("Automatically pitch down towards placement face when nearing block edges (bypasses Vulcan/NCP pitch checks).")
            .defaultValue(true)
            .visible(this::isBridgeMode)
            .build()
    );

    private final Setting<Boolean> bridgeStrafeAssist = this.sgBridge.add(
        new BoolSetting.Builder()
            .name("strafe-assist")
            .description("Smooth strafe lock along bridge vector (keeps movement aligned).")
            .defaultValue(false)
            .visible(this::isBridgeMode)
            .build()
    );

    private final Setting<Boolean> grimUseScaffolding = this.sgGrim.add(
        new BoolSetting.Builder()
            .name("grim-use-scaffolding")
            .description("Prefer scaffolding blocks when available. The humanized Grim profile works with normal blocks too.")
            .defaultValue(false)
            .visible(() -> this.technique.get() == Scaffold.Technique.GrimScaffold)
            .build()
    );

    private final Setting<Integer> vulcanExpand = this.sgVulcan.add(
        new IntSetting.Builder()
            .name("vulcan-expand")
            .description("Blocks to expand ahead for Vulcan bridging.")
            .defaultValue(1)
            .range(0, 5)
            .sliderMax(5)
            .visible(() -> this.technique.get() == Scaffold.Technique.VulcanExpand)
            .build()
    );

    private final Setting<Boolean> vulcanSprintDisabler = this.sgVulcan.add(
        new BoolSetting.Builder()
            .name("vulcan-sprint-disabler")
            .description("Send sprint toggle packets for legacy Vulcan versions (disable on modern Vulcan).")
            .defaultValue(false)
            .visible(() -> this.technique.get() == Scaffold.Technique.VulcanExpand || this.technique.get() == Scaffold.Technique.VulcanSprint)
            .build()
    );

    private final Setting<Integer> vulcanSprintInterval = this.sgVulcan.add(
        new IntSetting.Builder()
            .name("vulcan-sprint-interval")
            .description("Ticks between sprint packet sends.")
            .defaultValue(3)
            .range(1, 10)
            .visible(() -> (this.technique.get() == Scaffold.Technique.VulcanExpand || this.technique.get() == Scaffold.Technique.VulcanSprint)
                            && this.vulcanSprintDisabler.get())
                                .build()
    );

    private final Setting<Integer> expand = this.sgExpand.add(
        new IntSetting.Builder()
            .name("expand")
            .description("Blocks to expand forward. NOTE: Expand > 0 flags FarPlace on Grim!")
            .defaultValue(0)
            .range(0, 5)
            .sliderMax(5)
            .build()
    );

    private final Setting<Boolean> tower = this.sgTower.add(
        new BoolSetting.Builder()
            .name("tower")
            .description("Tower up when looking up.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> towerDelay = this.sgTower.add(
        new IntSetting.Builder()
            .name("tower-delay")
            .description("Delay between tower jumps.")
            .defaultValue(3)
            .range(1, 10)
            .visible(this.tower::get)
            .build()
    );

    /** Distance at which a slew is treated as arrived and the last fraction is snapped. */
    private static final float CAPTURE_DEGREES = 6.0F;

    /** Extra ticks a placement may be held back by, on top of the configured delay. */
    private static final int CADENCE_JITTER_TICKS = ScaffoldCadence.CADENCE_JITTER_TICKS;

    private int prevSlot = -1;
    private int towerTicks = 0;
    private int scaffoldCount = 0;
    private int placeCooldown = 0;
    private BlockPos lastAttemptPos = null;
    private int repeatedAttempts = 0;
    private boolean managedSneak = false;
    private boolean previousSneakDown = false;
    private int targetY = -1;
    private Scaffold.Technique lastTechnique = null;
    private Vec3 bridgeHeading = Vec3.ZERO;
    private float restoreYaw;
    private float restorePitch;
    private int godBridgePhase = 0;
    private int godBridgeTicks = 0;
    private int breezilySide = 1;
    private float aimYawVelocity = 0.0F;
    private float aimPitchVelocity = 0.0F;
    private final Random random = new Random();

    public Scaffold() {
        super(VenomousAddon.CATEGORY, "v-scaffold", "Scaffold & Bridge bypasses for Grim, Vulcan, NCP, and fast bridging.");
    }

    @Override
    public void onActivate() {
        this.prevSlot = -1;
        this.towerTicks = 0;
        this.scaffoldCount = 0;
        this.placeCooldown = 0;
        this.lastAttemptPos = null;
        this.repeatedAttempts = 0;
        this.managedSneak = false;
        this.previousSneakDown = false;
        this.lastTechnique = null;
        this.bridgeHeading = Vec3.ZERO;
        this.godBridgePhase = 0;
        this.godBridgeTicks = 0;
        this.breezilySide = 1;
        this.aimYawVelocity = 0.0F;
        this.aimPitchVelocity = 0.0F;
        this.targetY = this.mc.player != null ? (int)Math.floor(this.mc.player.getY() - 1.0) : -1;
    }

    @Override
    public void onDeactivate() {
        if (this.prevSlot != -1 && this.mc.player != null) {
            this.mc.player.getInventory().setSelectedSlot(this.prevSlot);
        }

        this.releaseManagedSneak();
        this.placeCooldown = 0;
        this.lastAttemptPos = null;
        this.repeatedAttempts = 0;
        this.lastTechnique = null;
        this.bridgeHeading = Vec3.ZERO;
        this.godBridgePhase = 0;
        this.godBridgeTicks = 0;
        this.targetY = -1;
        this.aimYawVelocity = 0.0F;
        this.aimPitchVelocity = 0.0F;
    }

    private boolean isBridgeMode() {
        return this.technique.get() == Scaffold.Technique.AutoSneak
            || this.technique.get() == Scaffold.Technique.BridgeMacro
            || this.technique.get() == Scaffold.Technique.VapeBridge
            || this.technique.get() == Scaffold.Technique.GodBridge
            || this.technique.get() == Scaffold.Technique.Breezily
            || this.technique.get() == Scaffold.Technique.VulcanSprint;
    }

    /** Per-technique placement cadence multiplier applied on top of {@code place-delay}. */
    private int techniqueDelay() {
        int base = Math.max(1, this.placeDelay.get());
        return switch (this.technique.get()) {
            case GodBridge, Breezily, VapeBridge -> Math.max(1, base - 2);
            case VulcanExpand, VulcanSprint -> Math.max(1, base);
            default -> base;
        };
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) return;

        this.scaffoldCount++;
        if (this.placeCooldown > 0) this.placeCooldown--;

        Scaffold.Technique selected = this.technique.get();
        if (selected != this.lastTechnique) {
            this.releaseManagedSneak();
            this.bridgeHeading = Vec3.ZERO;
            this.godBridgePhase = 0;
            this.godBridgeTicks = 0;
            this.lastAttemptPos = null;
            this.repeatedAttempts = 0;
            this.lastTechnique = selected;
        }

        if (this.mc.options.keyJump.isDown() || (int)Math.floor(this.mc.player.getY() - 1.0) > this.targetY) {
            this.targetY = (int)Math.floor(this.mc.player.getY() - 1.0);
        }

        if ((selected == Scaffold.Technique.VulcanExpand || selected == Scaffold.Technique.VulcanSprint)
            && this.vulcanSprintDisabler.get()
            && this.scaffoldCount % this.vulcanSprintInterval.get() == 0
            && this.mc.getConnection() != null) {
            this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.START_SPRINTING));
            this.mc.getConnection().send(new ServerboundPlayerCommandPacket(this.mc.player, Action.STOP_SPRINTING));
        }

        switch (selected) {
            case GrimScaffold -> this.tickGrim();
            case GodBridge -> this.tickGodBridge();
            case Breezily -> this.tickBreezily();
            case AutoSneak, BridgeMacro, VapeBridge, VulcanSprint -> this.tickBridgeMode();
            case VulcanExpand, Expand, NCPNormal, Normal -> this.tickStandardMode();
        }
    }

    private void tickStandardMode() {
        FindItemResult block = this.findBlock();
        if (!block.found()) return;

        if (this.tower.get() && this.mc.player.getXRot() < -75.0F) {
            this.doTower();
            return;
        }

        BlockPos placePos = this.getPlacePos();
        if (placePos == null) {
            this.releaseManagedSneak();
            return;
        }

        // NCP is deliberately conservative: crouch before the edge and visibly aim steeply.
        if (this.technique.get() == Scaffold.Technique.NCPNormal) {
            if (this.isOverEdge() || this.edgeDistance(this.movementDirection()) < this.jittered(0.32)) this.pressManagedSneak();
            else this.releaseManagedSneak();
            if (!this.rotate.get()) {
                this.place(placePos, false);
            } else {
                this.aimAndPlace(placePos);
            }
            return;
        }

        if (this.autoSneak.get() && this.isOverEdge()) this.pressManagedSneak();
        else this.releaseManagedSneak();

        if (this.technique.get() == Scaffold.Technique.VulcanExpand && this.vulcanExpand.get() > 0) {
            for (int i = this.vulcanExpand.get(); i >= 0; i--) {
                BlockPos expanded = this.getExpandPos(i);
                if (expanded != null && this.place(expanded)) break;
            }
        } else if (this.technique.get() == Scaffold.Technique.Expand && this.expand.get() > 0) {
            for (int i = this.expand.get(); i >= 0; i--) {
                BlockPos expanded = this.getExpandPos(i);
                if (expanded != null && this.place(expanded)) break;
            }
        } else {
            this.place(placePos);
        }
    }

    /** Human-looking Grim profile: edge crouch, visible eased aim, then an unspoofed interaction. */
    private void tickGrim() {
        BlockPos target = this.getPlacePos();
        if (target == null || !this.findBlock().found()) {
            this.releaseManagedSneak();
            return;
        }

        Vec3 direction = this.movementDirection();
        boolean nearEdge = this.isOverEdge() || (direction.lengthSqr() > 1.0E-4 && this.edgeDistance(direction) < this.jittered(0.38));
        if (nearEdge) this.pressManagedSneak();
        else this.releaseManagedSneak();

        // A low acceleration curve is intentional: no one-frame 180/90 degree snap.
        if (!this.rotate.get()) {
            this.place(target, false);
        } else {
            this.aimAndPlace(target);
        }
    }

    /** Jump forward, look back/down, place beneath the jump, and ease the view forward again. */
    private void tickGodBridge() {
        if (this.godBridgePhase == 0) {
            Vec3 input = this.movementDirection();
            if (input.lengthSqr() < 1.0E-4) return;
            this.bridgeHeading = input.normalize();

            if (this.edgeDistance(this.bridgeHeading) < this.jittered(0.30) && this.mc.player.onGround()) {
                this.restoreYaw = this.mc.player.getYRot();
                this.restorePitch = this.mc.player.getXRot();
                this.mc.player.jumpFromGround();
                this.godBridgePhase = 1;
                this.godBridgeTicks = 0;
            }
            return;
        }

        this.godBridgeTicks++;
        Vec3 velocity = this.mc.player.getDeltaMovement();
        double horizontalSpeed = Math.max(0.18, Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z));
        this.mc.player.setDeltaMovement(
            this.bridgeHeading.x * horizontalSpeed,
            velocity.y,
            this.bridgeHeading.z * horizontalSpeed
        );

        BlockPos target = BlockPos.containing(
            this.mc.player.getX() - this.bridgeHeading.x * 0.35,
            this.blockY(),
            this.mc.player.getZ() - this.bridgeHeading.z * 0.35
        );
        if (!this.mc.level.getBlockState(target).isAir()) {
            target = BlockPos.containing(
                this.mc.player.getX() + this.bridgeHeading.x * 0.55,
                this.blockY(),
                this.mc.player.getZ() + this.bridgeHeading.z * 0.55
            );
        }

        if (this.godBridgePhase == 1) {
            if (this.smoothAimAt(target, 28.0F, 11.0F) && this.place(target, false)) {
                this.godBridgePhase = 2;
                this.godBridgeTicks = 0;
            } else if (this.godBridgeTicks > 8) {
                this.godBridgePhase = 2;
                this.godBridgeTicks = 0;
            }
        } else {
            boolean yawReady = this.smoothViewTo(this.restoreYaw, this.restorePitch, 22.0F, 9.0F);
            if (yawReady || this.godBridgeTicks > 8 || this.mc.player.onGround()) {
                this.godBridgePhase = 0;
                this.godBridgeTicks = 0;
            }
        }
    }

    /** Breezily profile: alternating lateral pulses and rapid placement without edge crouching. */
    private void tickBreezily() {
        Vec3 forward = this.movementDirection();
        if (forward.lengthSqr() < 1.0E-4) return;
        forward = forward.normalize();
        this.releaseManagedSneak();

        if (this.edgeDistance(forward) < this.jittered(0.34)) {
            Vec3 velocity = this.mc.player.getDeltaMovement();
            Vec3 side = new Vec3(-forward.z, 0.0, forward.x).scale(0.055 * this.breezilySide);
            this.mc.player.setDeltaMovement(velocity.x + side.x, velocity.y, velocity.z + side.z);

            BlockPos target = BlockPos.containing(
                this.mc.player.getX() + forward.x * 0.70,
                this.blockY(),
                this.mc.player.getZ() + forward.z * 0.70
            );
            if (this.place(target)) this.breezilySide = -this.breezilySide;
        }
    }

    private void tickBridgeMode() {
        Vec3 dir = this.movementDirection();
        if (dir.lengthSqr() < 1.0E-4) {
            this.releaseManagedSneak();
            return;
        }

        if (this.bridgeStrafeAssist.get()) {
            float yaw = this.mc.player.getYRot();
            float snappedYaw = Math.round(yaw / 45.0F) * 45.0F;
            if (Math.abs(yaw - snappedYaw) < 15.0F) {
                this.mc.player.setYRot(snappedYaw);
            }
        }

        boolean edge = this.edgeDistance(dir) <= this.jittered(this.bridgeEdgeThreshold.get());
        Scaffold.Technique tech = this.technique.get();

        boolean isFastBridge = (tech == Scaffold.Technique.GodBridge || tech == Scaffold.Technique.Breezily);

        if (!edge) {
            this.releaseManagedSneak();
        } else {
            if (!isFastBridge || !this.mc.player.onGround()) {
                this.pressManagedSneak();
            }

            boolean shouldPlace = this.bridgePlaceAssist.get();
            if (shouldPlace) {
                BlockPos target = BlockPos.containing(this.mc.player.getX() + dir.x * 0.65, this.blockY(), this.mc.player.getZ() + dir.z * 0.65);
                float oldYaw = this.mc.player.getYRot();
                float oldPitch = this.mc.player.getXRot();

                if (this.bridgePitchAssist.get() && (tech == Scaffold.Technique.VapeBridge || tech == Scaffold.Technique.GodBridge || tech == Scaffold.Technique.VulcanSprint)) {
                    if (this.mc.player.getXRot() < 65.0F && !this.rotate.get()) {
                        this.mc.player.setXRot(75.5F);
                    }
                }

                boolean placed = this.place(target);
                if (this.bridgeCameraStabilisation.get() && (tech == Scaffold.Technique.BridgeMacro || tech == Scaffold.Technique.VapeBridge)) {
                    this.mc.player.setYRot(oldYaw);
                    this.mc.player.setXRot(oldPitch);
                    this.mc.player.yRotO = oldYaw;
                    this.mc.player.xRotO = oldPitch;
                }

                if (placed && (tech == Scaffold.Technique.BridgeMacro || tech == Scaffold.Technique.VapeBridge)) {
                    this.releaseManagedSneak();
                }
            }
        }
    }

    private Vec3 movementDirection() {
        double forward = (this.mc.options.keyUp.isDown() ? 1 : 0) - (this.mc.options.keyDown.isDown() ? 1 : 0);
        double strafe = (this.mc.options.keyLeft.isDown() ? 1 : 0) - (this.mc.options.keyRight.isDown() ? 1 : 0);
        if (forward == 0.0 && strafe == 0.0) {
            return Vec3.ZERO;
        }

        double[] vel = MovementMath.velocity(this.mc.player.getYRot(), forward, strafe, 1.0);
        return new Vec3(vel[0], 0.0, vel[1]);
    }

    private double edgeDistance(Vec3 dir) {
        double fx = this.mc.player.getX() - Math.floor(this.mc.player.getX());
        double fz = this.mc.player.getZ() - Math.floor(this.mc.player.getZ());
        double dx = dir.x > 0.01 ? 1.0 - fx : (dir.x < -0.01 ? fx : 1.0);
        double dz = dir.z > 0.01 ? 1.0 - fz : (dir.z < -0.01 ? fz : 1.0);
        return Math.min(dx, dz);
    }

    /**
     * Aims at {@code pos} without moving the camera, then places once the rotation has landed.
     *
     * <p>PreRotate hands the aim to Meteor's server-side rotation handling and defers the
     * placement to {@code SendMovementPacketsEvent.Pre}, which runs after that rotation has been
     * sent - so the interaction still leaves from a correct angle.</p>
     *
     * <p>The camera is deliberately left alone. It used to be driven toward the target and the
     * placement sent with that same angle and no server rotation at all, which forced the two to
     * be identical and hijacked the player's view to achieve it. Now they are independent: look
     * wherever you like, the server angle is spoofed underneath.</p>
     */
    private void aimAndPlace(BlockPos pos) {
        float[] aim = this.aimAt(pos);
        PreRotate.rotateThen(this, aim[0], aim[1], () -> this.place(pos, false));
    }

    /** Yaw and pitch that would look at {@code pos}, without touching the camera. */
    private float[] aimAt(BlockPos pos) {
        Vec3 eyes = this.mc.player.getEyePosition();
        double dx = pos.getX() + 0.5 - eyes.x;
        double dy = pos.getY() + 0.5 - eyes.y;
        double dz = pos.getZ() + 0.5 - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        return new float[] {
            (float)Math.toDegrees(Math.atan2(-dx, dz)),
            (float)-Math.toDegrees(Math.atan2(dy, horizontal))
        };
    }

    private boolean smoothAimAt(BlockPos pos, float maxYawStep, float maxPitchStep) {
        Vec3 eyes = this.mc.player.getEyePosition();
        double dx = pos.getX() + 0.5 - eyes.x;
        double dy = pos.getY() + 0.5 - eyes.y;
        double dz = pos.getZ() + 0.5 - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float)Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float)-Math.toDegrees(Math.atan2(dy, horizontal));
        return this.smoothViewTo(yaw, pitch, maxYawStep, maxPitchStep);
    }

    private boolean smoothViewTo(float targetYaw, float targetPitch, float maxYawStep, float maxPitchStep) {
        float yawDelta = wrapDegrees(targetYaw - this.mc.player.getYRot());
        float pitchDelta = targetPitch - this.mc.player.getXRot();

        float yawStep = AimEase.step(yawDelta, maxYawStep, this.aimYawVelocity, CAPTURE_DEGREES, this.aimNoise());
        float pitchStep = AimEase.step(pitchDelta, maxPitchStep, this.aimPitchVelocity, CAPTURE_DEGREES, this.aimNoise());

        this.aimYawVelocity = AimEase.nextVelocity(yawDelta, maxYawStep, this.aimYawVelocity, CAPTURE_DEGREES, 0.0F);
        this.aimPitchVelocity = AimEase.nextVelocity(pitchDelta, maxPitchStep, this.aimPitchVelocity, CAPTURE_DEGREES, 0.0F);

        this.applyLook(this.mc.player.getYRot() + yawStep, this.mc.player.getXRot() + pitchStep);
        return Math.abs(yawDelta) <= 4.0F && Math.abs(pitchDelta) <= 4.0F;
    }

    /**
     * Writes a look change a real mouse could have produced.
     *
     * <p>Every yaw and pitch delta a genuine mouse emits is an exact multiple of the sensitivity
     * GCD. Writing the camera directly with an arbitrary float produces a delta no human could
     * generate, which is precisely what rotation checks fingerprint. Aimbot and KillAura already
     * quantise for this reason; scaffold wrote straight to {@code setYRot}, so its sweep moved by
     * exactly 7 degrees per tick for as long as it lasted.</p>
     */
    private void applyLook(float yaw, float pitch) {
        double sensitivity = this.mc.options.sensitivity().get();
        float yawOut = this.mc.player.getYRot() + RotationGcd.quantize(sensitivity, yaw - this.mc.player.getYRot());
        float pitchOut = this.mc.player.getXRot() + RotationGcd.quantize(sensitivity, pitch - this.mc.player.getXRot());

        this.mc.player.setYRot(yawOut);
        this.mc.player.setXRot(clamp(pitchOut, -90.0F, 90.0F));
    }

    /** Signed jitter, used to break up constant-rate sweeps. */
    private float aimNoise() {
        return (float)(this.random.nextGaussian() * 0.6);
    }

    /** Threshold with per-evaluation variance, so the crouch is not metronomic. */
    private double jittered(double base) {
        return base * (0.82 + this.random.nextDouble() * 0.36);
    }

    private static float wrapDegrees(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int blockY() {
        return (this.sameY.get() && this.targetY != -1) ? this.targetY : (int)Math.floor(this.mc.player.getY() - 1.0);
    }

    private BlockPos getPlacePos() {
        int y = this.blockY();
        BlockPos below = BlockPos.containing(this.mc.player.getX(), y, this.mc.player.getZ());
        if (this.mc.level.getBlockState(below).isAir()) {
            return below;
        }

        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos check = below.relative(dir);
            if (this.mc.level.getBlockState(check).isAir()) {
                double px = this.mc.player.getX() - Math.floor(this.mc.player.getX());
                double pz = this.mc.player.getZ() - Math.floor(this.mc.player.getZ());

                boolean near = switch (dir) {
                    case NORTH -> pz < 0.3;
                    case SOUTH -> pz > 0.7;
                    case EAST -> px > 0.7;
                    case WEST -> px < 0.3;
                    default -> false;
                };
                if (near) {
                    return check;
                }
            }
        }

        return null;
    }

    private BlockPos getExpandPos(int fwd) {
        Vec3 dir = this.movementDirection();
        if (dir.lengthSqr() < 1.0E-4) {
            double yaw = Math.toRadians(this.mc.player.getYRot());
            dir = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
        }
        double d = fwd + 1;
        BlockPos pos = BlockPos.containing(this.mc.player.getX() + dir.x * d, this.blockY(), this.mc.player.getZ() + dir.z * d);
        return this.mc.level.getBlockState(pos).isAir() ? pos : null;
    }

    private void doTower() {
        BlockPos below = BlockPos.containing(this.mc.player.getX(), this.mc.player.getY() - 1.0, this.mc.player.getZ());
        if (this.mc.level.getBlockState(below).isAir()) {
            this.place(below);
        }

        this.towerTicks++;
        if (this.mc.player.onGround() && this.towerTicks % this.towerDelay.get() == 0) {
            this.mc.player.jumpFromGround();
        }
    }

    /**
    * Places a block at the given position using Meteor's normal placement utility.
    *
    * <p>Using Meteor's standard {@link BlockUtils#place} path avoids custom rotation spoofing
    * desyncs between client camera state and server rotation state.
    */
    private boolean place(BlockPos pos) {
        return this.place(pos, this.rotate.get());
    }

    private boolean place(BlockPos pos, boolean shouldRotate) {
        if (this.mc.player == null || this.mc.level == null || this.placeCooldown > 0) {
            return false;
        }

        FindItemResult block = this.findBlock();
        if (!block.found()) {
            return false;
        }

        if (pos.equals(this.lastAttemptPos)) {
            this.repeatedAttempts++;
        } else {
            this.lastAttemptPos = pos;
            this.repeatedAttempts = 0;
        }

        int delay = this.techniqueDelay();
        if (this.repeatedAttempts > 0 && this.repeatedAttempts % delay != 0) {
            return false;
        }

        if (BlockUtils.canPlace(pos)) {
            if (BlockUtils.place(pos, block, shouldRotate, 10, this.swing.get(), true)) {
                // Jitter only ever lengthens the gap, never shortens it, so bridging stays at
                // least as reliable as the fixed cadence while no longer being metronomic.
                this.placeCooldown = ScaffoldCadence.cooldownTicks(
                    delay, this.random.nextInt(CADENCE_JITTER_TICKS + 1), CADENCE_JITTER_TICKS);
                return true;
            }
        }

        this.placeCooldown = Math.max(1, delay / 2);
        return false;
    }

    private FindItemResult findBlock() {
        if (!this.autoBlockSwitch.get()) {
            ItemStack held = this.mc.player.getMainHandItem();
            if (held.getItem() instanceof BlockItem) {
                return InvUtils.findInHotbar(held.getItem());
            }
            return InvUtils.findInHotbar(item -> false);
        }

        if (this.technique.get() == Scaffold.Technique.GrimScaffold && this.grimUseScaffolding.get()) {
            FindItemResult scaffold = InvUtils.findInHotbar(Items.SCAFFOLDING);
            if (scaffold.found()) {
                return scaffold;
            }
        }

        return InvUtils.findInHotbar(item -> item.getItem() instanceof BlockItem);
    }

    private void pressManagedSneak() {
        if (!this.managedSneak) {
            this.previousSneakDown = this.mc.options.keyShift.isDown();
        }

        this.managedSneak = true;
        this.mc.options.keyShift.setDown(true);
    }

    private void releaseManagedSneak() {
        if (this.managedSneak) {
            this.mc.options.keyShift.setDown(this.previousSneakDown);
        }

        this.managedSneak = false;
        this.previousSneakDown = false;
    }

    private boolean isOverEdge() {
        return this.mc.level.getBlockState(BlockPos.containing(this.mc.player.getX(), this.mc.player.getY() - 1.0, this.mc.player.getZ())).isAir();
    }

    @Override
    public String getInfoString() {
        return this.technique.get().name();
    }

    public enum Technique {
        GrimScaffold,
        AutoSneak,
        BridgeMacro,
        VapeBridge,
        VulcanExpand,
        VulcanSprint,
        NCPNormal,
        Normal,
        Expand,
        GodBridge,
        Breezily;
    }
}