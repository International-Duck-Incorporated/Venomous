package net.venomous.meteoraddon.modules.world;

import java.util.List;
import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
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
import net.venomous.meteoraddon.utils.MovementMath;
import net.venomous.meteoraddon.utils.PreRotate;
import net.venomous.meteoraddon.utils.ScaffoldCadence;
import net.venomous.meteoraddon.utils.RotationGcd;
import net.venomous.meteoraddon.utils.ScaffoldAim;
import net.venomous.meteoraddon.utils.ScaffoldGates;
import net.venomous.meteoraddon.utils.SneakTiming;
import net.venomous.meteoraddon.utils.AimHold;
import net.venomous.meteoraddon.utils.SneakRepress;

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
            .description("Spoof the pitch so the ray the server traces reaches the block the "
                + "placement is made against. The yaw is always yours either way - that is what "
                + "keeps the server's movement prediction matching the walk you actually perform, "
                + "and it is why the camera is never taken off you. With this off the placement is "
                + "judged against your own look, which the server will flag.")
            .defaultValue(true)
            .build()
    );

    /**
     * Share of the remaining gap the pitch closes each tick.
     *
     * <p>The pitch is the only axis the module spends: the yaw belongs to the player, because
     * Grim's movement prediction rotates its candidate movement directions by the yaw in the
     * movement packet, and the client walks along its camera's yaw. The two have to be the same
     * angle or the client walks a line the server did not predict - Grim's {@code Simulation},
     * which flags at one millimetre and teleports the player back past ten.</p>
     */
    private static final float AIM_SMOOTH_PITCH = 0.5F;

    /**
     * How far the pitch wobbles, in degrees.
     *
     * <p>Grim's {@code AimDuplicateLook} flags a rotation packet that repeats the previous one
     * exactly. A player standing still on a settled bridge would otherwise send the same yaw and
     * the same pitch every tick, and be flagged for it - fourteen flags in one session, the last
     * time this happened, before there was a wobble.</p>
     *
     * <p>It moves the landing point on the face by a fraction of a millimetre, and it is a sine
     * rather than a random number so the deltas stay smooth and the rotation still looks like
     * something a hand produced.</p>
     */
    private static final float PITCH_WOBBLE = 0.08F;

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
     * Wall-clock time of the last placement that actually left the client.
     *
     * <p>The sneaking and the aiming decisions below are measured <em>relative to it</em> rather
     * than to the tick, because the freshest real event the server saw from this module is a
     * placement, and "how long since then" is the quantity a hand answers in both cases. Zero
     * means nothing has been placed yet, which every consumer below reads as "a very long time".</p>
     */
    private long lastPlaceAtMs = 0L;

    /**
     * The pitch the server is told, smoothed.
     *
     * <p>The yaw is not smoothed because it is not the module's: it is the player's own camera yaw,
     * sent unchanged, because Grim's movement prediction compares the walk the client performed
     * against the walk the server predicted from the yaw in the packet, and the two have to be the
     * same angle. The pitch does not enter that prediction, so it is free to be eased, and it has
     * to be: the face being clicked is nearly underfoot, and the elevation to it moves as the
     * player walks.</p>
     */
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
        this.lastPlaceAtMs = 0L;
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
        this.nextPlaceAtMs = 0L;
        this.lastPlaceAtMs = 0L;
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
     *
     * <p>Two Grim checks shape what the aim can be. {@code RotationPlace} ray-traces the
     * <em>server</em> rotation and asks whether it reaches the block being placed, so the aim has
     * to point at that block - and {@code AirLiquidPlace} fires when the resolved position has no
     * valid support, so a cell with no support face must not be clicked at all. The target is
     * therefore the centre of the face being placed against, and the placement waits for the ray
     * to actually reach the block rather than for an angle to come close enough.</p>
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

        // The aim is handed to Meteor, and it is the player's own yaw with one thing changed: the
        // pitch is spent on aiming at the face the placement clicks.
        //
        // The yaw is the player's because Grim's movement prediction insists on it. Grim
        // brute-forces the nine possible key combinations, rotates each of them by the yaw in the
        // movement packet, and asks which of the results is closest to where the client actually
        // ended up. So the client's movement has to run along one of the eight directions around
        // the server's yaw - and the client walks along its camera's yaw. If the camera's yaw and
        // the server's yaw differ, the client walks a line the server did not predict, and the
        // difference is Grim's Simulation: it flags at one millimetre, and past ten it teleports
        // the player back, which reads as being frozen in place.
        //
        // The pitch is free, because the pitch does not enter the movement prediction at all. So
        // the pitch is spent on the one thing that needs it: making the ray the server traces reach
        // the block the placement is made against.
        float[] face = this.aimRotation(pos);

        this.lastServerYaw = Rotations.serverYaw;
        this.lastServerPitch = Rotations.serverPitch;

        // The server is told the aim, and the placement goes out one tick later, once the server
        // has actually received that rotation. Grim judges the placement against the rotation the
        // server holds at that moment, so that is the angle the ray has to be tested against -
        // and it is tested at that moment rather than guessed at here.
        //
        // The wait is on the pitch alone. The yaw is the player's own and moves with the mouse, so
        // waiting for the yaw would hold the placement back every time they looked around - and
        // the action is discarded after five ticks, so the bridge would only build while they
        // happened to be holding the mouse still.
        PreRotate.rotateThenOnPitch(this, face[0], face[1], () -> {
            if (this.rayReachesSupport(support)) {
                this.place(pos);
            }
        });
    }

    /**
     * The rotation the server is told: the player's own yaw, and a pitch aimed at the face.
     *
     * <p>The yaw is read straight off the camera and sent unchanged. Nothing here writes the
     * camera, so the player keeps their view, their mouse and their direction - which is the
     * whole point of the module, and what the "the camera snaps back to the normal camera of the
     * user" complaint was about.</p>
     *
     * <p>There is a price, and it is worth stating plainly rather than discovering it later: the
     * block the placement is made against is the one behind the player, so the ray only reaches it
     * while the player is looking roughly that way. Holding S and looking forward is exactly that
     * case - the camera faces the block the player is walking away from - so the bridge works and
     * the view is the player's. Turn to face the other way and the placement waits for the look to
     * come back, rather than the camera being dragged round to follow.</p>
     *
     * <p>The pitch is smoothed, because the face is nearly underfoot and the elevation to it moves
     * as the player walks, and a pitch that jumps is a pitch no mouse produced. It also carries a
     * very small wobble, because Grim's {@code AimDuplicateLook} flags a rotation that repeats
     * exactly, and a player standing still with a settled aim would otherwise send the same
     * rotation every tick.</p>
     *
     * <p>And the smoothing does not run continuously: after a placement the pitch rests on the
     * angle that performed it, for a duration that fits what the player is doing - see
     * {@link AimHold}. Between rests the chase runs at the gain above; while a placement is staged
     * the hold gives way entirely, because that is when the ray the server traces has to land.</p>
     */
    private float[] aimRotation(BlockPos pos) {
        Vec3 eyes = this.mc.player.getEyePosition();

        // getPlaceSide returns null when no face of pos has support, which is routine mid-jump and
        // when a bridge is first extended. ScaffoldAim.aimPoint handles that by falling back to the
        // block centre, so this never dereferences a null Direction.
        Direction side = BlockUtils.getPlaceSide(pos);
        double[] normal = side == null ? null : new double[] {
            side.getStepX(), side.getStepY(), side.getStepZ()
        };

        double[] point = ScaffoldAim.aimPoint(pos.getX(), pos.getY(), pos.getZ(), normal, 0.5);

        float pitch = ScaffoldAim.pitchToPoint(eyes.x, eyes.y, eyes.z, point[0], point[1], point[2]);

        // The yaw is the player's own, exactly as the camera has it.
        float yaw = this.mc.player.getYRot();

        // The first-call test has to be Float.isNaN and not an equality comparison. NaN is not
        // equal to itself, so `smoothedPitch == Float.NaN` is never true, the initialisation never
        // ran, and the pitch was NaN from the first tick onwards.
        //
        // While a hold is active - see AimHold - the chase is suspended and the pitch stays where
        // the last placement left it, so the aim rests between actions instead of drifting along
        // with the walk. A staged placement turns the hold off, because then the pitch has to work.
        if (Float.isNaN(this.smoothedPitch)) {
            this.smoothedPitch = pitch;
        } else if (!this.holdsPitch()) {
            this.smoothedPitch = this.smoothedPitch
                + (pitch - this.smoothedPitch) * AIM_SMOOTH_PITCH;
        }

        // The wobble. Small enough that it moves the landing point by a fraction of a millimetre,
        // and it keeps the rotation from ever repeating exactly.
        this.smoothedPitch = clamp(this.smoothedPitch
            + PITCH_WOBBLE * (float) Math.sin(this.scaffoldCount * 0.7D), -90.0F, 90.0F);

        return new float[] {yaw, this.smoothedPitch};
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

                // The crouch and the aim are measured from here, not from the attempt: a failed
                // placement never left the client, so nothing the server can see happened.
                this.lastPlaceAtMs = now;
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
     *
     * <p>And the crouch must not change on a tick where a placement is going out, which is what
     * Grim's {@code PacketOrderF} is about. The server believes the player is sneaking between the
     * "start sneaking" packet and the next movement packet, and it cancels any action packet that
     * arrives in that window - the placement included. A cancelled placement leaves the client
     * standing on a block the server never received, and that desync is worth tenths of a block:
     * the live log showed the offsets jump to 0.15 and the server teleporting the player back.</p>
     *
     * <p>The placement leaves in the movement packet's PRE phase, which is after the player's tick
     * has already sent the crouch input. So on a tick where a placement is waiting to go out, the
     * crouch is left exactly as it is - the key stays down and no input packet is sent, and the
     * server's view of the crouch is unchanged.</p>
     *
     * <p>There is a second, quieter shape to the same problem: even with that guard, a fresh
     * <em>press</em> that follows a placement within milliseconds produces a place-squeeze
     * metronome at the cadence of the bridge. So a crouch that is not already down waits out a
     * short delay since the last placement before it starts - see {@link SneakRepress} - while a
     * crouch that is already down is holding rather than pressing and is left alone, and a release
     * is never delayed at all, because a crouch held past its point walks the player into the air.</p>
     */
    private void updateManagedSneak(boolean atEdge) {
        if (PreRotate.hasPending(this)) return;

        boolean onGround = this.mc.player != null && this.mc.player.onGround();
        long heldFor = this.managedSneak && this.sneakHeldSinceMs > 0L
            ? Math.max(0L, nowMs() - this.sneakHeldSinceMs)
            : 0L;

        if (SneakTiming.shouldHold(atEdge, onGround, this.managedSneak, heldFor, this.sneakDelay.get())) {
            // Holding is not pressing: an already-down key changes nothing, so the placement-gap
            // rule has nothing to say to it. Only the fresh press waits for the delay.
            if (this.managedSneak || SneakRepress.allowsPress(this.msSinceLastPlacement(),
                SneakRepress.DEFAULT_DELAY_MS)) {
                this.pressManagedSneak();
            }
        } else {
            this.releaseManagedSneak();
        }
    }

    /**
     * Milliseconds since the last placement actually left the client.
     *
     * <p>Reads as a very large number before one ever does, so nothing downstream needs a
     * special case for "no placements yet".</p>
     */
    private long msSinceLastPlacement() {
        if (this.lastPlaceAtMs == 0L) return Long.MAX_VALUE / 2L;
        return Math.max(0L, nowMs() - this.lastPlaceAtMs);
    }

    /**
     * Whether the smoothed pitch rests this tick instead of chasing the face; see {@link AimHold}.
     */
    private boolean holdsPitch() {
        return AimHold.shouldHold(
            PreRotate.hasPending(this),
            this.msSinceLastPlacement(),
            this.mc.player.isSprinting(),
            !this.movementDirection().equals(Vec3.ZERO));
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