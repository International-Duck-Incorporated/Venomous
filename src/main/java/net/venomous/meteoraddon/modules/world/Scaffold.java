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
import net.venomous.meteoraddon.utils.GrimRay;
import net.venomous.meteoraddon.utils.InputCompensation;
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
            .description("Hold the level of the row while you are in the air. On by default: the "
                + "level used to follow the jump key, which made a jump build a column instead of "
                + "the next cell of the row - the row stopped growing and you walked off the end of "
                + "it. Turn this off to follow the player up mid-jump, which is the jump-bridging "
                + "technique and builds a staircase rather than a bridge.")
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
                + "it starts or ends at. The camera and the rotation sent to the server are the "
                + "same angle - that is what keeps the server's movement prediction matching the "
                + "movement the client actually performs.")
            .defaultValue(22.0)
            .min(1.0)
            .sliderMax(60.0)
            .build()
    );

    /**
     * Share of the remaining gap the aim closes each tick.
     *
     * <p>This is where the smoothing lives. It used to be in the camera, which was the mistake:
     * the camera was eased toward the target while the server was told the camera's lagged value,
     * so the two were never the same angle, and Meteor writes the server's angle onto the camera
     * for the movement packet and puts the camera back afterwards. A camera holding a different
     * angle from the one being sent was therefore snapped to it and back on every single tick -
     * the "the camera snaps back to the normal camera of the user" that was reported.</p>
     *
     * <p>One smoothed value now feeds both, so Meteor's snap writes the value that is already
     * there and its restore puts back the same one. The motion is still a continuous function of
     * where the player is, which is what a smooth view is.</p>
     */
    private static final float AIM_SMOOTH = 0.35F;

    /** The pitch is smoothed more gently; it is the axis that moves most as the player walks. */
    private static final float AIM_SMOOTH_PITCH = 0.5F;

    /**
     * How long to wait before retrying a placement that did not go through.
     *
     * <p>Short, because a cell can become placeable the moment the block arrives, and a jump is
     * not long.</p>
     */
    private static final long RETRY_DELAY_MS = 50L;

    /**
     * The pitch tracks much more closely than the yaw, and it has to.
     *
     * <p>The yaw's target is the discrete heading of the support's side, so it is steady and can be
     * eased slowly without consequence. The pitch's target moves by several degrees every tick,
     * because the face is nearly underfoot and the eye's distance to it changes as the player walks
     * - and the ray is steep enough that a couple of degrees of lag moves the landing point half a
     * block, from the middle of the face to its top edge. So the pitch is smoothed more gently,
     * to keep up with a target that moves faster.</p>
     */

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

    private int prevSlot = -1;
    private int towerTicks = 0;
    private int scaffoldCount = 0;

    /**
     * Wall-clock deadline for the next placement, in milliseconds.
     *
     * <p>Not a tick count. A tick-counted cooldown assumes 20 TPS, and this client does not run at
     * 20: five ticks is 250ms at 20 FPS and 1250ms at 4 FPS, and a jump is over long before that.
     * The live log caught what that costs - the player jumped while bridging, the block underneath
     * was still inside the cooldown from the previous placement, and they fell off the bridge.</p>
     */
    private long nextPlaceAtMs = 0L;

    /**
     * The smoothed aim. One value feeds both the camera and the server, so the two can never
     * disagree - which is what made Meteor's snap to the server's angle visible as the camera
     * snapping and snapping back every tick.
     */
    private float smoothedYaw = Float.NaN;
    private float smoothedPitch = Float.NaN;
    private boolean managedSneak = false;
    private long sneakHeldSinceMs = 0L;
    private boolean previousSneakDown = false;
    private int targetY = -1;

    /** The rotation the server held on the previous tick, which Grim also tests against. */
    private float lastServerYaw = 0.0F;
    private float lastServerPitch = 0.0F;
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
        this.nextPlaceAtMs = 0L;
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
        InputCompensation.clear();
        this.nextPlaceAtMs = 0L;
        this.smoothedYaw = Float.NaN;
        this.smoothedPitch = Float.NaN;
        this.lastTechnique = null;
        this.bridgeHeading = Vec3.ZERO;
        this.targetY = -1;
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

        Scaffold.Technique selected = this.technique.get();
        if (selected != this.lastTechnique) {
            this.releaseManagedSneak();
            this.bridgeHeading = Vec3.ZERO;
            this.nextPlaceAtMs = 0L;
            this.lastTechnique = selected;
        }

        // The level the row is built at.
        //
        // It used to be raised the instant the jump key went down, and that is the second half of
        // the fall the live log caught: the module built a column upwards instead of the next cell
        // of the row, so the row stopped growing while the player - who was walking backwards along
        // it - kept moving, and they walked off the end of it. The log shows the target's Y
        // climbing 83, 84, 85, 87 while the player was airborne and nothing being placed in the row.
        //
        // Holding the level while airborne fixes that. Climbing a ledge still works: the player
        // lands on it, and the level follows on the landing tick.
        if (this.targetY == -1 || this.mc.player.onGround() || !this.sameY.get()) {
            this.targetY = (int)Math.floor(this.mc.player.getY() - 1.0);
        }

        switch (selected) {
            case GrimScaffold -> this.tickGrim();
            case AutoSneak, BridgeMacro, VapeBridge -> this.tickBridgeMode();
            case NCPNormal, Normal -> this.tickStandardMode();
        }
    }

    private void tickStandardMode() {
        if (!this.findBlock().found()) return;

        if (this.tower.get() && this.mc.player.getXRot() < -75.0F) {
            this.doTower();
            return;
        }

        BlockPos placePos = this.getPlacePos();
        if (placePos == null) {
            this.releaseManagedSneak();
            return;
        }

        // Sneaking at the edge is now strictly opt-in. It used to be forced on by the NCP profile
        // and by the bridge modes, and it put "sneaking=true" on every Grim packet-order flag:
        // PacketOrderE reports any held-item change while a flag is set, and PacketOrderF reports
        // placing while it is set. A flag the player did not ask for, on every placement.
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

        // Also opt-in now, for the same reason as tickStandardMode.
        this.updateManagedSneak(this.autoSneak.get() && this.isOverEdge());

        if (!this.rotate.get()) {
            this.place(target);
        } else {
            this.aimAndPlace(target);
        }
    }

    private void tickBridgeMode() {
        // Nothing left here, deliberately.
        //
        // This used to snap the player's yaw to the nearest 45 degrees, force pitch to 75.5, place
        // a cell 0.65 blocks AHEAD of the player rather than the one underfoot, and then restore
        // the old yaw and pitch in the same tick. It also called place() directly, skipping the
        // rotation that Grim validates, and force-pressed sneak while doing it.
        //
        // Four separate problems, all reported by the player as one: the camera was being fought
        // over, holding S did not walk in a straight line because the yaw kept snapping, correcting
        // it by hand produced wild rotations, and the forced sneak put "sneaking=true" on every
        // single Grim packet-order flag.
        //
        // Every technique now places the cell underfoot through the same path.
        this.tickStandardMode();
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
     *   <li>Only the camera eases. The server rotation is snapped to the face, because
     *       AntiCheat-Addition's {@code ScaffoldRotation} scores rotation variance and a multi-tick
     *       approach reads as random aiming. See {@link #serverAimAt}.</li>
     * </ul>
     */
    private void aimAndPlace(BlockPos pos) {
        // A cell that is free but has no supporting face is not a placement target. canPlace
        // answers "is the cell free"; it is still true when nothing around it can hold a block,
        // and clicking anyway is exactly what Grim's AirLiquidPlace reports. The per-tick debug
        // log showed those two facts side by side - "side=NULL canPlace=true" on the very ticks
        // the flag arrived - and also showed the module retrying the same dead cell forever.
        Direction side = BlockUtils.getPlaceSide(pos);
        if (side == null || !BlockUtils.canPlace(pos)) {
            return;
        }

        // The block the placement is made against. Grim's RotationPlace ray-traces against this
        // one: Grim's BlockPlace.position is the block that was clicked, and the one being placed
        // is that plus the face normal.
        BlockPos support = pos.relative(side);

        // The aim is computed and smoothed here, and handed to Meteor. The camera is NOT written
        // here: Meteor smooths the rotation it actually sends, so the angle that reaches the server
        // is not the angle that was asked for. The live log showed it plainly - the camera at 81
        // degrees, the server at 87, the raw aim at 89 - and that gap is Grim's Simulation.
        //
        // Grim's OffsetHandler flags when the client's position differs from the server's
        // prediction by more than a millimetre. The client computes the movement from the camera's
        // yaw; the server predicts it from the yaw in the movement packet. If those differ, the
        // client walks a slightly different line and the difference is the flag.
        //
        // So the camera has to hold the rotation the server was just told, and that is only knowable
        // after the packet has gone out. onSendMovementPacketsPost puts the camera back to it.
        float[] face = this.faceAimAt(pos);

        this.lastServerYaw = Rotations.serverYaw;
        this.lastServerPitch = Rotations.serverPitch;

        this.updateInputCompensation();

        // The server is told the aim, and the placement goes out one tick later, once the server
        // has actually received that rotation. Grim judges the placement against the rotation the
        // server holds at that moment, so that is the angle the ray has to be tested against -
        // and it is tested at that moment rather than guessed at here.
        PreRotate.rotateThen(this, face[0], face[1], () -> {
            if (this.rayReachesSupport(support)) {
                this.place(pos);
            }
        });
    }

    /**
     * Keeps the movement input and the rotation the server is told in step.
     *
     * <p>This is what makes freelook work. The camera is the player's and nothing here writes it;
     * the rotation the server is told points at the block being placed. The client computes its
     * movement from the camera and the server predicts it from the packet, so with the two apart the
     * client walks a line the server did not predict - Grim's {@code Simulation}, which flags at
     * one millimetre.</p>
     *
     * <p>Rotating the input by the angle between them closes the gap: the walk the client performs
     * becomes the walk the server expects, and the player is free to look wherever they like while
     * it happens. The mixin that applies it is {@code LocalPlayerMixin}.</p>
     */
    private void updateInputCompensation() {
        if (this.mc.player == null || !this.rotate.get()) {
            InputCompensation.clear();
            return;
        }

        float cameraYaw = this.mc.player.getYRot();
        float serverYaw = Rotations.serverYaw;

        if (!Float.isFinite(cameraYaw) || !Float.isFinite(serverYaw)) {
            InputCompensation.clear();
            return;
        }

        InputCompensation.set(wrapDegrees(serverYaw - cameraYaw));
    }

    /**
     * Whether the ray the server is currently being told actually reaches the block being placed
     * against.
     *
     * <p>Grim's {@code RotationPlace} ray-traces along the server's rotation and asks whether the
     * ray reaches the block that was clicked - and Grim's {@code BlockPlace.position} is the block
     * that was clicked, not the one being placed. So this is the same question Grim asks, asked at
     * the moment Grim would ask it.</p>
     *
     * <p>An angle tolerance cannot express it. The face is nearly underfoot - the eye about 1.6
     * blocks above it and a third of a block away - so the ray is very steep and a couple of
     * degrees of pitch error moves the landing point half a block, from the middle of the face to
     * its top edge. Grim's own escape hatch is no help either: its movement threshold is 0.0002
     * blocks on 1.9+ clients, so the eye-box overlap that lets a near miss through is worth
     * nothing.</p>
     */
    private boolean rayReachesSupport(BlockPos support) {
        if (this.mc.player == null) return false;

        Vec3 eye = this.mc.player.getEyePosition();

        return GrimRay.rotationPlaceWouldPass(eye.x, eye.z, this.mc.player.getY(),
            Rotations.serverYaw, Rotations.serverPitch, this.lastServerYaw, this.lastServerPitch,
            support.getX(), support.getY(), support.getZ(),
            support.getX() + 1.0D, support.getY() + 1.0D, support.getZ() + 1.0D);
    }

    /**
     * Eases the camera onto the placement face.
     *
     * <p>Runs well below the server rotation's rate so the view visibly leads into a placement
     * rather than locking onto it, and every delta is quantised to the sensitivity GCD - an
     * arbitrary float written straight to {@code setYRot} is an angle no mouse could produce,
     * which is precisely what rotation checks fingerprint.</p>
     */
    /**
     /**
             /**
     * Yaw and pitch onto the face the placement clicks, smoothed.
     *
     * <p>Uses Meteor's own side resolution so the face is the one the placement would actually use.
     * See {@link ScaffoldAim}, which documents the direction convention this depends on.</p>
     *
     * <p>The yaw is the heading of the support's side rather than the geometric angle to the click
     * point. The point is half a block from the player's feet, so the angle to it is measured
     * against a horizontal offset of a few tenths of a block and swings ten to seventeen degrees
     * for a fraction of a block of movement. Since the camera is what the movement is computed
     * from, a camera chasing that swings with it, and the two chase each other: the live log
     * caught the result as a pendulum, thirty degrees either side of the target, one swing every
     * ten ticks. The support's side does not move while the player walks, so the camera settles on
     * it instead.</p>
     *
     * <p>The result is smoothed rather than exact, and the smoothing is what makes the view smooth.
     * It has to be here rather than in the camera, because the camera and the rotation the server
     * is told have to be the same angle - Meteor writes the server's angle onto the camera for the
     * movement packet and puts the camera back afterwards, so any difference between the two shows
     * up as the camera snapping and snapping back. One value, smoothed once, feeds both.</p>
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

        double[] point = ScaffoldAim.aimPoint(pos.getX(), pos.getY(), pos.getZ(), normal, 0.5);

        double dx = point[0] - eyes.x;
        double dy = point[1] - eyes.y;
        double dz = point[2] - eyes.z;

        // A vertical face has no heading of its own, so the current one is kept; a horizontal one
        // gets the stable heading of the support's side.
        float heading = ScaffoldAim.headingFor(normal);
        if (Float.isNaN(heading)) {
            heading = this.mc.player.getYRot();
        }

        float pitch = ScaffoldAim.pitchAlong(heading, dx, dy, dz);

        // The smoothing. A share of the remaining gap, so the result can never overshoot, and the
        // motion is a continuous function of where the player is rather than a fresh target every
        // tick - which, at the frame rates this client actually runs, is the difference between a
        // view that glides and one that jumps.
        //
        // The first-call test has to be Float.isNaN and not an equality comparison. NaN is not
        // equal to itself, so `smoothedYaw == Float.NaN` is never true, the initialisation never
        // ran, and the aim was NaN from the first tick onwards. The server discarded every
        // rotation - "Invalid entity rotation: NaN, discarding." - so the module stopped placing
        // blocks entirely and the camera sat wherever the player had left it.
        if (Float.isNaN(this.smoothedYaw)) {
            this.smoothedYaw = heading;
            this.smoothedPitch = pitch;
        } else {
            this.smoothedYaw = this.smoothedYaw
                + wrapDegrees(heading - this.smoothedYaw) * AIM_SMOOTH;
            this.smoothedPitch = this.smoothedPitch
                + (pitch - this.smoothedPitch) * AIM_SMOOTH_PITCH;
        }

        return new float[] {this.smoothedYaw, this.smoothedPitch};
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
        return this.targetY != -1 ? this.targetY : (int)Math.floor(this.mc.player.getY() - 1.0);
    }

    private BlockPos getPlacePos() {
        int y = this.blockY();
        BlockPos below = BlockPos.containing(this.mc.player.getX(), y, this.mc.player.getZ());

        // Only the cell directly underfoot. Sideways candidates used to be considered here, and
        // that was the expand technique - it was asked for removal once and survived anyway.
        //
        // It is also what Grim's PositionPlace reports, "Placed a block against a hidden face".
        // The check asks whether the eye is on the outward side of the face that was clicked:
        //
        //   NORTH -> eye.minZ > box.minZ      SOUTH -> eye.maxZ < box.maxZ
        //   EAST  -> eye.maxX < box.maxX      WEST  -> eye.minX > box.minX
        //
        // Placing the cell north of the bridge means clicking the support's south face while
        // standing north of it, which is exactly that condition, and the live log showed
        // side=north on nearly every tick alongside eleven PositionPlace flags. Filling the cell
        // underfoot always clicks a downward face, which the eye is always above, so it cannot
        // fail this check at all.
        return this.isSupported(below) ? below : null;
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
        if (this.mc.player == null || this.mc.level == null) {
            return false;
        }

        // Wall-clock, not ticks. A tick-counted cooldown assumes 20 TPS, and this client does not
        // run at 20: five ticks is 250ms at 20 FPS and 1250ms at 4 FPS, and a jump is over long
        // before that. The live log caught the consequence - the player jumped while bridging,
        // the block underneath was still inside the cooldown from the previous placement, and they
        // fell off the bridge.
        long now = this.nowMs();
        if (now < this.nextPlaceAtMs) {
            return false;
        }

        FindItemResult block = this.findBlock();
        if (!block.found()) {
            return false;
        }

        int delay = this.techniqueDelay();
        double gap = ScaffoldCadence.delayMillis(delay, this.random.nextInt(ScaffoldCadence.CADENCE_JITTER_MS + 1));

        if (this.isSupported(pos)) {
            if (BlockUtils.place(pos, block, false, 10, this.swing.get(), true)) {
                // Jitter only ever lengthens the gap, never shortens it, so bridging stays at
                // least as reliable as the fixed cadence while no longer being metronomic.
                this.nextPlaceAtMs = now + (long) gap;
                return true;
            }
        }

        // A failed placement retries sooner than a successful one: the cell may become placeable
        // as soon as the block arrives, and a jump is not long.
        this.nextPlaceAtMs = now + RETRY_DELAY_MS;
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