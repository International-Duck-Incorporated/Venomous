package net.venomous.meteoraddon.modules.world;

import java.util.List;
import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.AimEase;
import net.venomous.meteoraddon.utils.MovementMath;
import net.venomous.meteoraddon.utils.PreRotate;
import net.venomous.meteoraddon.utils.ScaffoldCadence;
import net.venomous.meteoraddon.utils.RotationGcd;
import net.venomous.meteoraddon.utils.ScaffoldAim;
import net.venomous.meteoraddon.utils.ScaffoldGates;
import net.venomous.meteoraddon.utils.SneakTiming;

public class Scaffold extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgAnticheat = this.settings.createGroup("Anticheat");
    private final SettingGroup sgGrim = this.settings.createGroup("Grim");
    private final SettingGroup sgBridge = this.settings.createGroup("Bridge Assist");
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

    private final Setting<Integer> sneakDelay = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("sneak-delay")
            .description("Milliseconds to stay crouched after clearing a bridge edge. Holding the "
                + "crouch for a real duration instead of snapping it off on the first clear tick "
                + "is what keeps the transition from reading as a safe-walk script; see SneakTiming.")
            .defaultValue(SneakTiming.DEFAULT_SNEAK_DELAY_MS)
            .range(0, 1000)
            .sliderMax(500)
            .build()
    );

    private final Setting<List<Block>> blockWhitelist = this.sgGeneral.add(
        new BlockListSetting.Builder()
            .name("block-whitelist")
            .description("Only ever place these blocks. An empty list means unrestricted.")
            .build()
    );

    private final Setting<List<Block>> blockBlacklist = this.sgGeneral.add(
        new BlockListSetting.Builder()
            .name("block-blacklist")
            .description("Never place these blocks. Useful where glass, ladders or glowstone end "
                + "up in the hotbar - bridging with those looks nothing like building with one "
                + "material. Always wins over the whitelist.")
            .build()
    );

    private final Setting<Boolean> pitchCheck = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("pitch-check")
            .description("Only place while looking down towards the bridge. Someone about to "
                + "bridge is watching their feet; placing while staring at the horizon is not "
                + "something a person does. Complements pitch-assist.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Integer> pitchThreshold = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("pitch-threshold")
            .description("How far down the head must be pointed before placing, in degrees. "
                + "0 disables the gate.")
            .defaultValue(0)
            .range(0, 90)
            .sliderMax(90)
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

    private final Setting<Double> cameraSpeed = this.sgAnticheat.add(
        new DoubleSetting.Builder()
            .name("camera-speed")
            .description("Degrees per tick the camera may travel toward the placement face. The "
                + "move eases in and decelerates into the target, so this is the peak, not the rate "
                + "it starts or ends at. This drives what you see only; the rotation the server is "
                + "told is snapped to the face, because AntiCheat-Addition's ScaffoldRotation "
                + "scores rotation variance and a multi-tick approach reads as random aiming.")
            .defaultValue(22.0)
            .min(1.0)
            .sliderMax(60.0)
            .build()
    );

    private final Setting<Boolean> cameraFollow = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("camera-follow")
            .description("Ease the camera onto the placement face. Your view tracks what you are "
                + "building, as it does when bridging by hand, but it is eased and GCD-quantised "
                + "rather than snapping. The server rotation is still spoofed independently "
                + "through PreRotate, so the two are no longer forced to be identical.")
            .defaultValue(true)
            .build()
    );

    private static final float CAPTURE_DEGREES = 0.4F;

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

    /** Extra ticks a placement may be held back by, on top of the configured delay. */
    private static final int CADENCE_JITTER_TICKS = ScaffoldCadence.CADENCE_JITTER_TICKS;

    private int prevSlot = -1;
    private int towerTicks = 0;
    private int scaffoldCount = 0;
    private int placeCooldown = 0;
    private BlockPos lastAttemptPos = null;
    private int repeatedAttempts = 0;
    private boolean managedSneak = false;
    private long sneakHeldSinceMs = 0L;
    private boolean previousSneakDown = false;
    private int targetY = -1;
    private float camYawVelocity = 0.0F;
    private float camPitchVelocity = 0.0F;
    private Scaffold.Technique lastTechnique = null;
    private Vec3 bridgeHeading = Vec3.ZERO;
    private final Random random = new Random();

    public Scaffold() {
        super(VenomousAddon.CATEGORY, "v-scaffold", "Scaffold & Bridge bypasses for Grim, NCP, and fast bridging.");
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
        this.targetY = -1;
        this.camYawVelocity = 0.0F;
        this.camPitchVelocity = 0.0F;
    }

    private boolean isBridgeMode() {
        return this.technique.get() == Scaffold.Technique.AutoSneak
            || this.technique.get() == Scaffold.Technique.BridgeMacro
            || this.technique.get() == Scaffold.Technique.VapeBridge
           ;
    }

    /** Per-technique placement cadence multiplier applied on top of {@code place-delay}. */
    private int techniqueDelay() {
        int base = Math.max(1, this.placeDelay.get());
        return switch (this.technique.get()) {
            case VapeBridge -> Math.max(1, base - 2);
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
            this.lastAttemptPos = null;
            this.repeatedAttempts = 0;
            this.lastTechnique = selected;
        }

        if (this.mc.options.keyJump.isDown() || (int)Math.floor(this.mc.player.getY() - 1.0) > this.targetY) {
            this.targetY = (int)Math.floor(this.mc.player.getY() - 1.0);
        }

        switch (selected) {
            case GrimScaffold -> this.tickGrim();
            case AutoSneak, BridgeMacro, VapeBridge -> this.tickBridgeMode();
            case NCPNormal, Normal -> this.tickStandardMode();
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
            this.updateManagedSneak(this.isOverEdge()
                || this.edgeDistance(this.movementDirection()) < this.jittered(0.32));
            if (!this.rotate.get()) {
                this.place(placePos);
            } else {
                this.aimAndPlace(placePos);
            }
            return;
        }

        this.updateManagedSneak(this.autoSneak.get() && this.isOverEdge());

        // Expand/VulcanExpand were removed: they placed extra blocks per tick, which no cadence
        // survives and which the anticheat's straight-line minimum punishes hardest.
        if (this.rotate.get()) {
            this.aimAndPlace(placePos);
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
        boolean nearEdge = this.isOverEdge()
            || (direction.lengthSqr() > 1.0E-4 && this.edgeDistance(direction) < this.jittered(0.38));
        this.updateManagedSneak(nearEdge);

        // A low acceleration curve is intentional: no one-frame 180/90 degree snap.
        if (!this.rotate.get()) {
            this.place(target);
        } else {
            this.aimAndPlace(target);
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

        if (!edge) {
            this.releaseManagedSneak();
        } else {
            if (!this.mc.player.onGround()) {
                this.pressManagedSneak();
            }

            boolean shouldPlace = this.bridgePlaceAssist.get();
            if (shouldPlace) {
                BlockPos target = BlockPos.containing(this.mc.player.getX() + dir.x * 0.65, this.blockY(), this.mc.player.getZ() + dir.z * 0.65);
                float oldYaw = this.mc.player.getYRot();
                float oldPitch = this.mc.player.getXRot();

                if (this.bridgePitchAssist.get() && (tech == Scaffold.Technique.VapeBridge)) {
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
    /**
     * Eases the server rotation onto the placement face, then places once it has landed.
     *
     * <p>Grim's {@code RotationPlace} ray-traces the <em>server</em> rotation and asks whether it
     * reaches the block being placed; {@code AirLiquidPlace} fires when the resolved position has no
     * valid support. Both firing together means the aim is landing somewhere the placement cannot
     * come from. Two changes address that:</p>
     *
     * <ul>
     *   <li>The target is the centre of the placement face rather than the centre of the block.
     *       While bridging, the eye is above and behind the cell being filled, so a ray at the block
     *       centre enters through a face the player is not addressing and the click resolves to
     *       air.</li>
     *   <li>The target does not slide toward the eye. Sliding was tried and reverted: as the player
     *       crossed the block the point flipped to the opposite edge of the face, swinging the
     *       target by most of 180 degrees. The debug log caught it - the face pitch alternated
     *       between 62.5 and 56.9 on alternating ticks, and every one of those flips lined up with
     *       a {@code RotationPlace} flag.</li>
     *   <li>The approach is eased rather than stepped, so the rotation fades in off the old target
     *       and decelerates onto the face. {@link AimEase} already carries a velocity ramp with
     *       ease-out near the target, so it is reused instead of adding a second curve.</li>
     * </ul>
     */
    private void aimAndPlace(BlockPos pos) {
        // A cell that is free but has no supporting face is not a placement target. canPlace
        // answers "is the cell free"; it is still true when nothing around it can hold a block,
        // and clicking anyway is exactly what Grim's AirLiquidPlace reports. The per-tick debug
        // log showed those two facts side by side - "side=NULL canPlace=true" on the very ticks
        // the flag arrived - and also showed the module retrying the same dead cell forever.
        if (!ScaffoldAim.hasSupport(BlockUtils.canPlace(pos), BlockUtils.getPlaceSide(pos) != null)) {
            return;
        }

        // The camera follows the same face aim, eased and slower than the server rotation so it
        // reads as a look rather than a lock. GCD-quantised, because every delta a real mouse
        // emits is a whole multiple of the sensitivity grid.
        float[] face = this.faceAimAt(pos);
        this.easeCameraOnto(face);

        float[] aim = this.serverAimAt(pos);

        // Only place once the rotation has actually arrived; PreRotate enforces the tolerance and
        // defers the interaction to SendMovementPacketsEvent.Pre so it leaves before the packet.
        PreRotate.rotateThen(this, aim[0], aim[1], () -> this.place(pos));
    }

    /**
     * Eases the camera onto the placement face.
     *
     * <p>Runs well below the server rotation's rate so the view visibly leads into a placement
     * rather than locking onto it, and every delta is quantised to the sensitivity GCD - an
     * arbitrary float written straight to {@code setYRot} is an angle no mouse could produce,
     * which is precisely what rotation checks fingerprint.</p>
     */
    private void easeCameraOnto(float[] face) {
        if (!this.cameraFollow.get() || this.mc.player == null) return;

        float yaw = this.mc.player.getYRot();
        float pitch = this.mc.player.getXRot();
        float yawDelta = wrapDegrees(face[0] - yaw);
        float pitchDelta = face[1] - pitch;

        float yawStep = AimEase.step(yawDelta, this.maxAimStep() * 0.6F, this.camYawVelocity,
            CAPTURE_DEGREES, this.aimNoise());
        float pitchStep = AimEase.step(pitchDelta, this.maxAimStep() * 0.35F, this.camPitchVelocity,
            CAPTURE_DEGREES, this.aimNoise());

        this.camYawVelocity = AimEase.nextVelocity(yawDelta, this.maxAimStep() * 0.6F,
            this.camYawVelocity, CAPTURE_DEGREES, 0.0F);
        this.camPitchVelocity = AimEase.nextVelocity(pitchDelta, this.maxAimStep() * 0.35F,
            this.camPitchVelocity, CAPTURE_DEGREES, 0.0F);

        double sensitivity = this.mc.options.sensitivity().get();

        // The yaw is written UNWRAPPED, deliberately. Grim's AimModulo360 is not a range check: it flags
        // |yaw| < 360 together with |deltaYaw| > 320 in a single tick, i.e. a client that reports a
        // wrapped value where vanilla would report an unwrapped one. Entity.turn never wraps yaw;
        // only pitch is clamped. Wrapping here would manufacture exactly the +-360 jump that check
        // exists to catch, so the eased value is stored as-is and wrapDegrees is applied to the
        // remaining error instead - which is what makes the approach take the short way round.
        this.mc.player.setYRot(yaw + RotationGcd.quantize(sensitivity, yawStep));
        this.mc.player.setXRot(clamp(pitch + pitchStep, -90.0F, 90.0F));
    }

    /**
     /**
     * The exact yaw and pitch of the placement face, for the server.
     *
     * <p>Snapped, not eased, and deliberately so. Easing the <em>server</em> rotation was tried and
     * reverted against a live run. Two independent pieces of evidence say it costs more than it
     * buys:</p>
     *
     * <ul>
     *   <li>AntiCheat-Addition's {@code ScaffoldRotation} scores rotation <em>variance</em> - a change
     *       of 35 degrees or more inside two ticks, more than 360 degrees of summed change, or a
     *       variance above 70, which its own source comments describe as "sending random rotations
     *       <em>or is perfectly aiming at blocks</em>". An eased approach spends several ticks inside
     *       that window on every single block. The debug log from that run showed the face pitch
     *       alternating 62.5 then 56.9 on consecutive ticks with yaw moving 22 to 24 degrees per
     *       tick, which is precisely the pattern the check scores.</li>
     *   <li>Grim's {@code RotationPlace} retries on the tick <em>before</em> the place while the
     *       rotation is still moving. A slow approach keeps the ray off the block for longer, which
     *       is how it went from firing once to firing on every placement.</li>
     * </ul>
     *
     * <p>Both reference implementations agree: Meteor's scaffold sends
     * {@code Mth.wrapDegrees(target - current)} directly, and LiquidBounce's
     * {@code FaceTargetPositionFactory} returns a point on the face with no smoothing at all. Grim
     * explicitly tolerates rotation and place in the same tick - {@code CheckManagerListener} adopts
     * a rotation packet that arrives within 15 ms of the place - which is what {@link PreRotate}
     * already arranges.</p>
     *
     * <p>The visible easing the player cares about is not lost; it moved to the camera, which is
     * what they actually see. See {@link #easeCameraOnto}.</p>
     */
    private float[] serverAimAt(BlockPos pos) {
        return this.faceAimAt(pos);
    }

    /**
     * Yaw and pitch onto the centre of the placement face, without touching the camera.
     *
     * <p>Uses Meteor's own side resolution so the face is the one the placement would actually use.
     * See {@link ScaffoldAim}.</p>
     */
    private float[] faceAimAt(BlockPos pos) {
        Vec3 eyes = this.mc.player.getEyePosition();

        // getPlaceSide returns null when no face of pos has support, which is routine mid-jump and
        // when a bridge is first extended. ScaffoldAim.aimPoint handles that by falling back to the
        // block centre, so this never dereferences a null Direction.
        Direction side = BlockUtils.getPlaceSide(pos);
        double[] normal = side == null ? null : new double[] {
            side.getStepX(), side.getStepY(), side.getStepZ()
        };

        double[] point = ScaffoldAim.aimPoint(pos.getX(), pos.getY(), pos.getZ(), normal, 0.4);

        double dx = point[0] - eyes.x;
        double dy = point[1] - eyes.y;
        double dz = point[2] - eyes.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        return new float[] {
            (float)Math.toDegrees(Math.atan2(-dx, dz)),
            (float)-Math.toDegrees(Math.atan2(dy, horizontal))
        };
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

    private float maxAimStep() {
        return (float) (double) this.cameraSpeed.get();
    }

    /** Signed jitter for the easing, so a sweep never runs at an exactly constant rate. */
    private float aimNoise() {
        return (float) (this.random.nextGaussian() * 0.25);
    }

    /** Signed jitter, used to break up constant-rate sweeps. */

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
        if (this.isSupported(below)) {
            return below;
        }

        for (Direction dir : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos check = below.relative(dir);
            if (!this.isSupported(check)) {
                continue;
            }

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

        return null;
    }

    /**
     * Whether {@code pos} is a cell this module may actually place into.
     *
     * <p>Two conditions, not one. {@code canPlace} asks whether the cell is free, which is not the
     * same question as whether there is anything to place against: a free cell with no supporting
     * neighbour on any of its six faces still reports {@code canPlace = true}. Targeting one of
     * those produced two failures at once - Grim flagged {@code AirLiquidPlace}, and because the
     * placement could never succeed the module retried the same dead cell on every tick for as long
     * as the player stood there, with the debug log printing an identical line each time.</p>
     *
     * <p>Requiring a side here rather than only in {@link #aimAndPlace} is deliberate: it means the
     * caller gets {@code null} and moves on to the next candidate instead of stalling on a cell that
     * can never be filled.</p>
     */
    private boolean isSupported(BlockPos pos) {
        return ScaffoldAim.hasSupport(
            BlockUtils.canPlace(pos), BlockUtils.getPlaceSide(pos) != null);
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
 * Places a block at the given position, without touching rotation.
 *
 * <p>Deliberately has no "rotate" variant any more. {@link BlockUtils#place} with
 * {@code rotate = true} calls {@code Rotations.rotate(yaw, pitch, runnable)}, and Meteor runs that
 * callback from {@code SendMovementPacketsEvent.Post} - the interaction therefore leaves
 * <em>after</em> the movement packet. AntiCheat-Addition names it on sight:</p>
 *
 * <pre>{@code
 *   [Anticheat-Test] >Looks like you are currently attacking/placing on POST,
 *                     this is a very common check on most anti-cheats
 * }</pre>
 *
 * <p>That was happening on every path except GrimScaffold and NCP, because those were the only two
 * routed through {@link #aimAndPlace}. Rotation-and-place is now always {@code aimAndPlace}, which
 * defers to {@code Pre} via {@link PreRotate}. Removing the overload means the POST ordering cannot
 * be reintroduced by a call site that forgets.</p>
 */
    private boolean place(BlockPos pos) {
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

        if (this.isSupported(pos)) {
            if (BlockUtils.place(pos, block, false, 10, this.swing.get(), true)) {
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

    /**
     * Whether {@code item} is a block this configuration is willing to place.
     *
     * <p>Applies the blacklist, then the whitelist. A whitelist only restricts once it has entries,
     * so turning the feature on without picking anything does not silently stop the module.</p>
     */
    private boolean isPlaceableBlock(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) return false;
        return ScaffoldGates.blockAllowed(blockItem.getBlock(),
            this.blockWhitelist.get(), this.blockBlacklist.get());
    }

    /** Head-pitch gate; see {@link ScaffoldGates#pitchAllows}. */
    private boolean pitchAllowsPlacing() {
        if (!this.pitchCheck.get() || this.mc.player == null) return true;
        return ScaffoldGates.pitchAllows(this.mc.player.getXRot(), this.pitchThreshold.get());
    }

    /**
     * Picks the item to place with, preferring to keep the slot we already hold.
     *
     * <p>Grim's {@code PacketOrderE} - "Changed held item slot during another conflicting action" -
     * fires on {@code HELD_ITEM_CHANGE} whenever any action is outstanding, and a block placement
     * counts as one. Scanning the hotbar every tick meant swapping the slot immediately before
     * every place, so each swap arrived while the previous placement was still in flight:</p>
     *
     * <pre>{@code
     *   Grim > PacketOrderE x0.0 attacking=false, rightClicking=true,
     *            openingInventory=false, releasing=false, sneaking=false, ...
     * }</pre>
     *
     * <p>Nothing here is gained by swapping when the current slot already holds a usable block, so
     * the held item is checked first and only a genuine change of material triggers a swap. That is
     * also what a person does: you pick a stack and keep bridging on it.</p>
     */
    private FindItemResult findBlock() {
        if (!this.pitchAllowsPlacing()) {
            return new FindItemResult(-1, 0);
        }

        ItemStack held = this.mc.player.getMainHandItem();

        // Already holding something we are allowed to place: no slot change, no HELD_ITEM_CHANGE.
        if (this.isPlaceableBlock(held)) {
            return new FindItemResult(this.mc.player.getInventory().getSelectedSlot(),
                held.getCount());
        }

        if (!this.autoBlockSwitch.get()) {
            return InvUtils.findInHotbar(item -> false);
        }

        if (this.technique.get() == Scaffold.Technique.GrimScaffold && this.grimUseScaffolding.get()
            && ScaffoldGates.blockAllowed(Blocks.SCAFFOLDING, this.blockWhitelist.get(), this.blockBlacklist.get())) {
            FindItemResult scaffold = InvUtils.findInHotbar(Items.SCAFFOLDING);
            if (scaffold.found()) {
                return scaffold;
            }
        }

        return InvUtils.findInHotbar(this::isPlaceableBlock);
    }

    private void pressManagedSneak() {
        if (!this.managedSneak) {
            this.previousSneakDown = this.mc.options.keyShift.isDown();
            this.sneakHeldSinceMs = this.nowMs();
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
        this.sneakHeldSinceMs = 0L;
    }

    /**
     * Crouch at the edge, but keep holding for {@code sneak-delay} after the edge clears.
     *
     * <p>All four call sites used to be {@code if (edge) press(); else release();}, which releases on
     * the very first tick the edge is gone. That instant transition is the shape AntiCheat-Addition's
     * {@code Scaffold.parts.Safewalk.Timing} looks for: movement that stops without a sustained
     * crouch. The decision itself lives in {@link SneakTiming} so it can be tested without a game.</p>
     */
    private void updateManagedSneak(boolean atEdge) {
        boolean onGround = this.mc.player != null && this.mc.player.onGround();
        long heldFor = this.managedSneak && this.sneakHeldSinceMs > 0L
            ? Math.max(0L, nowMs() - this.sneakHeldSinceMs)
            : 0L;

        if (SneakTiming.shouldHold(atEdge, onGround, this.managedSneak, heldFor, this.sneakDelay.get())) {
            this.pressManagedSneak();
        } else {
            this.releaseManagedSneak();
        }
    }

    /** Monotonic wall clock; {@code getGameTime()} counts ticks and is not milliseconds. */
    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
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
        NCPNormal,
        Normal;
    }
}