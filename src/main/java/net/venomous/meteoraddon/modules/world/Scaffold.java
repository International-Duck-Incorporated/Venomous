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
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
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
import net.venomous.meteoraddon.utils.GrimPacketModel;
import net.venomous.meteoraddon.utils.GrimRay;
import net.venomous.meteoraddon.utils.ScaffoldCamera;
import net.venomous.meteoraddon.utils.MovementMath;
import net.venomous.meteoraddon.utils.PreRotate;
import net.venomous.meteoraddon.utils.ScaffoldCadence;
import net.venomous.meteoraddon.utils.ScaffoldEdge;
import net.venomous.meteoraddon.utils.RotationGcd;
import net.venomous.meteoraddon.utils.ScaffoldAim;
import net.venomous.meteoraddon.utils.ScaffoldGates;
import net.venomous.meteoraddon.utils.SneakTiming;

public class Scaffold extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgAnticheat = this.settings.createGroup("Anticheat");

    /**
     * Verbose logging of what the module is doing and why.
     *
     * <p>Every decision the module makes is a guess about what the anticheat is checking, and the
     * server only ever says "flagged". The log lines here are what makes that guessable: the
     * crouch's decision and its inputs, the aim that is being sent, whether the ray reached the
     * block, and what the client was doing at the moment of the placement.</p>
     *
     * <p>Tagged {@code DEBUG-scf} so it can be stripped with one grep when the diagnosis is done.</p>
     */
    /**
     * The per-tick movement log, off by default.
     *
     * <p>Six lines per placement is enough to see the movement, and it is enough to make the client
     * miss its own packet deadlines: the live log filled with Grim's Timer check, which is the
     * server saying the client's packets were late. The place and state lines are cheap and are
     * the ones that say anything; this one is for when the movement itself is the question.</p>
     */
    private final Setting<Boolean> debugMovement = this.sgAnticheat.add(
        new BoolSetting.Builder()
            .name("debug-movement")
            .description("Log the movement of every tick for six ticks after a placement. This is "
                + "what the server's Simulation check measures, but it prints enough to make the "
                + "client's packets late, so it is off by default.")
            .defaultValue(false)
            .build()
    );

    private final Setting<Boolean> debugLogging = this.sgAnticheat.add(
        new BoolSetting.Builder()
            .name("debug-log")
            .description("Log every decision the module makes: the crouch, the aim, the ray, and "
                + "the placement. Tagged [DEBUG-scf] so it can be removed in one grep.")
            .defaultValue(true)
            .build()
    );

    /**
     * The packet-rate watch, off by default.
     *
     * <p>Grim's timer and aim checks are decided by the order of the packets the client sends, and
     * nothing in the game records that - so this prints the numbers Grim works from, as a model of
     * its own accounting running in the client. The counted rate is the one that matters: Grim
     * writes twenty a second against, and anything above it makes the balance run ahead until the
     * Timer check fires and then keeps firing.</p>
     */
    private final Setting<Boolean> debugPackets = this.sgAnticheat.add(
        new BoolSetting.Builder()
            .name("debug-packets")
            .description("Print Grim's own packet accounting once a second: the movement packets, "
                + "the end-of-tick packets, the rate Grim's Timer check counts against twenty, and "
                + "the duplicate rotations its AimDuplicateLook reports.")
            .defaultValue(false)
            .build()
    );
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

    /**
     * When the crouch is on.
     *
     * <p>This was a yes/no and it could not be one thing, because the crouch does two different
     * jobs that pull in opposite directions.</p>
     *
     * <p>It is a <em>speed control</em>: a crouch is thirty per cent of the walking speed, and a
     * bridge is walked backwards, which is thirty per cent again, so a permanent crouch makes a
     * bridge a third of the speed it could be - one block a second in the live log.</p>
     *
     * <p>And it is a <em>fallback</em>: the module places the block under the player's feet, which
     * means the player is always falling for a tick or two until the block lands. If the server
     * refuses the placement - and the anticheat-test server does, on a timer - there is a hole, and
     * the crouch is what gives the module the time to fill it: a crouched player is in a cell
     * three times longer.</p>
     *
     * <p>And it is a <em>flag</em>: every time it goes on or off, the client's speed changes by
     * seventy per cent and the server only learns about it a tick later, which Grim's Simulation
     * measures as the difference between the movement it predicted and the movement the client
     * made. The live log had .010444, .028401 and .071601 for one bridge, and past a tenth of a
     * block Grim teleports the player back.</p>
     *
     * <p>So the three modes are:</p>
     */
    private final Setting<Scaffold.SneakMode> autoSneak = this.sgGeneral.add(
        new EnumSetting.Builder<Scaffold.SneakMode>()
            .name("auto-sneak")
            .description("When the crouch is on. Never: never, and the bridge is at full speed. "
                + "At-edges: at a block edge, for the whole bridge - the crouch changes only when "
                + "the bridge starts or ends, so it costs nothing in the movement prediction. "
                + "While-bridging: for the whole walk while the module is on, a third of the speed "
                + "but the bridge holds together on a server that refuses placements.")
            .defaultValue(Scaffold.SneakMode.AtEdges)
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
     * The crouch state the module wants, when it could not be applied this tick.
     *
     * <p>Deferred rather than dropped: the crouch may not change on a tick where a placement
     * leaves, or Grim's PacketOrderF denies the placement, but it must not be lost either - a
     * crouch that is never applied is not a crouch.</p>
     */
    private boolean pendingSneak = false;

    /** Whether the crouch is currently being deferred, so the deferral is logged once. */
    private boolean crouchDeferred = false;

    /**
     * Whether the cooldown allows a placement right now.
     *
     * <p>Wall clock, not ticks: a tick-counted cooldown assumes 20 TPS, and this client does not
     * run at 20.</p>
     */
    private boolean placementDue() {
        return this.nowMs() >= this.nextPlaceAtMs;
    }

    /**
     * How many ticks of detail to log after a placement.
     *
     * <p>Grim's Simulation check compares the movement the client actually performed against the
     * movement it predicted, and reports the distance between the two vectors. The offset in the
     * log arrives on the tick of a placement, so the tick-by-tick movement around a placement is
     * what has to be logged - not the position, and not a per-tick log for the whole session.</p>
     */
    private int debugTicksAfterPlacement = 0;

    /** The position on the previous tick, so the movement can be logged the way Grim measures it. */
    private double debugLastX = Double.NaN;
    private double debugLastY = Double.NaN;
    private double debugLastZ = Double.NaN;

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
        this.managedSneak = false;
        this.previousSneakDown = false;
        this.lastTechnique = null;
        this.bridgeHeading = Vec3.ZERO;
        this.targetY = this.mc.player != null ? (int)Math.floor(this.mc.player.getY() - 1.0) : -1;

        // The packet watch counts whether the module is on or not, so the window it reports has to
        // start here rather than with whatever the client sent while the module was off.
        GrimPacketModel.get().snapshot();
    }

    @Override
    public void onDeactivate() {
        if (this.prevSlot != -1 && this.mc.player != null) {
            this.mc.player.getInventory().setSelectedSlot(this.prevSlot);
        }

        this.releaseManagedSneak();
        this.nextPlaceAtMs = 0L;
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

        // The camera is the player's until the module has something to place, and the module cannot
        // have the camera at all while the player is fighting.
        ScaffoldCamera.release();

        if (this.scaffoldCount % 20 == 0) this.debugHeartbeat();

        this.debugTick();

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
        if (!this.canBridge()) {
            this.releaseManagedSneak();
            return;
        }

        if (!this.findBlock().found()) return;

        if (this.tower.get() && this.mc.player.getXRot() < -75.0F) {
            this.doTower();
            return;
        }

        BlockPos placePos = this.getPlacePos();
        if (placePos == null) {
            // No target, but the crouch is not dropped here. The module fills the cell the
            // player is standing on, so for the tick after a placement there is no target left -
            // and that is the normal state of a bridge, not an exception. Releasing the crouch
            // there is what made it come and go on every placement, which the live log shows:
            //
            //   crouch: key down (was false)
            //   place: cell=... PLACED
            //   crouch: edge=false ... -> release
            //   crouch: key up (restored to false)
            //
            // A crouch that changes twice a placement changes the client's speed by seventy per
            // cent twice a placement, and the server only learns about it a tick later. Grim's
            // Simulation measures the difference between the movement it predicted and the
            // movement the client made, and that difference is the speed change: the live log has
            // .010444, .028401 and .071601 for the same bridge. Past a tenth of a block Grim
            // teleports the player back, and being teleported back mid-bridge is how you fall
            // off it.
            //
            // So the edge decides, on this path exactly as on every other: the player is at the
            // edge of a bridge for the whole of the walk, and the crouch is held for it.
            this.updateManagedSneak(this.shouldSneak());
            return;
        }

        // Sneaking at the edge is now strictly opt-in. It used to be forced on by the NCP profile
        // and by the bridge modes, and it put "sneaking=true" on every Grim packet-order flag:
        // PacketOrderE reports any held-item change while a flag is set, and PacketOrderF reports
        // placing while it is set. A flag the player did not ask for, on every placement.
        this.updateManagedSneak(this.shouldSneak());

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
        if (!this.canBridge()) {
            this.releaseManagedSneak();
            return;
        }

        BlockPos target = this.getPlacePos();
        if (target == null || !this.findBlock().found()) {
            // The same as in tickStandardMode: no target is the normal state of a bridge for the
            // tick after a placement, and the crouch is decided by the edge, not by there being
            // something to place.
            this.updateManagedSneak(this.shouldSneak());
            return;
        }

        // Also opt-in now, for the same reason as tickStandardMode.
        this.updateManagedSneak(this.shouldSneak());

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

        // The module takes the camera while it is bridging. The angle is applied at the head of the
        // player's tick, after the mouse and before the movement, so the walk the client performs
        // and the walk the server predicts are the same walk; the mixin is ScaffoldCameraMixin.
        ScaffoldCamera.take(face[0], face[1]);

        this.lastServerYaw = Rotations.serverYaw;
        this.lastServerPitch = Rotations.serverPitch;

        // The pitch is only spoofed when a placement is going out. On every other tick the server
        // is told the player's own pitch, so their head does not move: a spectator watching sees a
        // player who is looking where they are looking, rather than one whose head is pinned to
        // the ground and jerks on every placement.
        boolean pending = PreRotate.hasPending(this);
        boolean due = this.placementDue();

        // The server is told the aim, and the placement goes out one tick later, once the server
        // has actually received that rotation. Grim judges the placement against the rotation the
        // server holds at that moment, so that is the angle the ray has to be tested against -
        // and it is tested at that moment rather than guessed at here.
        //
        // The wait is on the pitch alone. The yaw is the player's own and moves with the mouse, so
        // waiting for the yaw would hold the placement back every time they looked around - and
        // the action is discarded after five ticks, so the bridge would only build while they
        // happened to be holding the mouse still.
        //
        // ONE rotation request per tick, and never two. This used to ask for the rotation with
        // rotateOnly and then again with rotateThenOnPitch, and Meteor's rotation queue hands out
        // one rotation per tick - so the two requests were applied a tick apart and the server was
        // told the same angle twice. Three things came out of that:
        //
        //   - Grim's AimDuplicateLook reports a rotation packet with the same yaw and pitch as
        //     the previous one, and the live log has eleven of them;
        //   - the rotation the server was holding when the placement went out was the one from
        //     the tick before, not the aim the placement was made with, so the ray was traced
        //     against an angle the server had never been told - which is what "the ray missed the
        //     block" is;
        //   - and the queue ran a tick ahead of the aim for the rest of the bridge.
        if (due) {
        PreRotate.rotateThenOnPitch(this, face[0], face[1], () -> {
            boolean reaches = this.rayReachesSupport(support, face[0], face[1]);

            if (!reaches) {
                // The ray missed the block, so wait the normal cadence before trying again. Left
                // alone the module re-tests the ray every tick, because the cooldown only moves
                // when a block actually goes down - and a line of log per tick is enough to make
                // the client miss its own packet deadlines, which Grim's Timer check reports.
                this.nextPlaceAtMs = this.nowMs()
                    + (long)ScaffoldCadence.delayMillis(this.techniqueDelay(), 0);

                this.debugLog("place: cell=%s support=%s side=%s aim=%.2f,%.2f camYaw=%.2f "
                        + "serverHolds=%.2f,%.2f - the ray missed the block",
                    pos, support, side, face[0], face[1], this.mc.player.getYRot(),
                    Rotations.serverYaw, Rotations.serverPitch);

                return;
            }

            // One line per placement: the aim, the angle the server is holding, and what the
            // placement did. It used to be two lines here and three more from the crouch, and a
            // log that heavy is enough to make the client miss its own packet deadlines - which
            // Grim's Timer check reports as the game running too fast.
            boolean placed = this.place(pos);

            Vec3 at = this.mc.player.position();
            Vec3 vel = this.mc.player.getDeltaMovement();

            this.debugLog("place: cell=%s support=%s side=%s aim=%.2f,%.2f camYaw=%.2f "
                    + "serverHolds=%.2f,%.2f -> %s at %.2f,%.2f,%.2f vel=%.3f,%.3f,%.3f onGround=%s",
                pos, support, side, face[0], face[1], this.mc.player.getYRot(),
                Rotations.serverYaw, Rotations.serverPitch, placed ? "PLACED" : "FAILED",
                at.x, at.y, at.z, vel.x, vel.y, vel.z, this.mc.player.onGround());
        });
        } else {
            // No placement this tick, so the rotation still has to be sent: the camera is the
            // aim, and the server has to be told it, or the walk it predicts and the walk the
            // client performs are along two different angles.
            PreRotate.rotateOnly(this, face[0], face[1]);
        }
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

        // The aim point is the top of the block the placement is made against, not the centre of
        // the face the two blocks share.
        //
        // The shared face's centre is a point on the ground that is almost directly below the
        // player, so the elevation to it swings by fifteen degrees as the player walks across their
        // own cell - the live log shows the pitch going 81.3, 82.4, 84.4, 86.4 and back within a
        // second. A pitch that swings is a rotation that moves faster than a hand can, and a chat
        // player watching the bridge called it out. The top of the support is a whole block wide,
        // so the same ray still reaches it from a wide range of pitches and the aim can hold
        // still.
        BlockPos support = side == null ? pos : pos.relative(side);
        double[] point = new double[] {
            support.getX() + 0.5D, support.getY() + 1.0D, support.getZ() + 0.5D
        };

        float pitch = ScaffoldAim.pitchToPoint(eyes.x, eyes.y, eyes.z, point[0], point[1], point[2]);

        // And it is held inside a band. The ray has to reach the block, and the block is a block
        // wide and about two blocks away, so anything from about fifty to eighty degrees reaches
        // it; outside that band the ray misses and the placement is refused. Holding the pitch
        // inside the band means the rotation does not swing, and a rotation that does not swing is
        // a rotation that does not look like a mouse.
        pitch = clamp(pitch, 50.0F, 82.0F);

        // The yaw is the player's own, exactly as the camera has it.
        float yaw = this.mc.player.getYRot();

        // The first-call test has to be Float.isNaN and not an equality comparison. NaN is not
        // equal to itself, so `smoothedPitch == Float.NaN` is never true, the initialisation never
        // ran, and the pitch was NaN from the first tick onwards.
        if (Float.isNaN(this.smoothedPitch)) {
            this.smoothedPitch = pitch;
        } else {
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
    private boolean rayReachesSupport(BlockPos support, float yaw, float pitch) {
        if (this.mc.player == null) return false;

        Vec3 eye = this.mc.player.getEyePosition();

        // The rotation being tested is the one the placement goes out with: Grim judges a
        // placement after the flying packet, and the placement leaves in the PRE phase of that
        // very packet, so the rotation in it is the one just asked for.
        return GrimRay.rotationPlaceWouldPass(eye.x, eye.z, this.mc.player.getY(),
            yaw, pitch, this.lastServerYaw, this.lastServerPitch,
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
        if (!this.isSupported(below)) return null;

        return below;
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
            if (this.place(below)) {
                this.debugLog("tower: cell=%s PLACED at %.2f,%.2f,%.2f",
                    below, this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ());
            }
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
        if (!this.placementDue()) {
            this.debugLog("place: cell=%s skipped, %dms of cooldown left", pos, this.nextPlaceAtMs - now);
            return false;
        }

        FindItemResult block = this.findBlock();
        if (!block.found()) {
            this.debugLog("place: cell=%s nothing placeable in hand", pos);
            return false;
        }

        int delay = this.techniqueDelay();
        double gap = ScaffoldCadence.delayMillis(delay, this.random.nextInt(ScaffoldCadence.CADENCE_JITTER_MS + 1));

        if (this.isSupported(pos)) {
            if (BlockUtils.place(pos, block, false, 10, this.swing.get(), true)) {
                // Jitter only ever lengthens the gap, never shortens it, so bridging stays at
                // least as reliable as the fixed cadence while no longer being metronomic.
                this.nextPlaceAtMs = now + (long) gap;

                this.debugTicksAfterPlacement = 6;

                return true;
            }

            this.debugLog("place: cell=%s the client refused the placement", pos);
        } else {
            this.debugLog("place: cell=%s has no support face", pos);
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
            this.debugLog("crouch: key down (was %s)", this.previousSneakDown);
        }

        this.managedSneak = true;
        this.mc.options.keyShift.setDown(true);
    }

    private void releaseManagedSneak() {
        if (this.managedSneak) {
            this.mc.options.keyShift.setDown(this.previousSneakDown);
            this.debugLog("crouch: key up (restored to %s)", this.previousSneakDown);
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
     * <p>What used to be done about it here was to hold the crouch still while a placement was
     * waiting, and apply the change on a later tick. That is no longer needed, and it was costing
     * a tick of delay and a line of log on every placement: the placement now leaves from the hook
     * in the player's tick, which is <em>before</em> the input packet that carries the crouch. So
     * the crouch and the placement are on the wire in the same order a vanilla client's are - the
     * placement first, the crouch after it - and the server never sees an action inside a
     * crouch it has not been told about yet.</p>
     */
    private void updateManagedSneak(boolean atEdge) {
        boolean onGround = this.mc.player != null && this.mc.player.onGround();
        long heldFor = this.managedSneak && this.sneakHeldSinceMs > 0L
            ? Math.max(0L, nowMs() - this.sneakHeldSinceMs)
            : 0L;

        boolean hold = SneakTiming.shouldHold(atEdge, onGround,
            this.managedSneak || this.pendingSneak, heldFor, this.sneakDelay.get());

        // The state the crouch wants, whether or not it can be applied this tick.
        this.pendingSneak = hold;

        if (hold != this.managedSneak) {
            this.debugLog("crouch: edge=%s onGround=%s held=%dms -> %s",
                atEdge, onGround, heldFor, hold ? "press" : "release");
        }

        if (hold) {
            this.pressManagedSneak();
        } else {
            this.releaseManagedSneak();
        }
    }

    private static final Logger LOG = LogUtils.getLogger();

    /**
     * The whole state of the module, once a second.
     *
     * <p>The per-tick lines are what the client pays for, and a per-tick log was measured slowing
     * the client down, so the tick-by-tick detail is left out and this carries the state instead:
     * where the player is, how fast they are moving, whether they are on the ground, whether the
     * crouch is down, whether there is a drop in front of them, and what the server is being
     * told.</p>
     */
    /**
     * The movement of this tick, in the form Grim measures it.
     *
     * <p>Grim's Simulation offset is the distance between the vector the client moved by and the
     * vector it predicted the client would move by - the position delta, tick by tick. So the
     * number to log is the delta, and the state around it: the ground, the crouch, and what is
     * underfoot.</p>
     *
     * <p>Only for a few ticks after a placement, because that is when the offset appears, and
     * because a log that prints every tick is a log that costs the client its frame rate.</p>
     */
    private void debugTick() {
        if (!this.debugLogging.get() || !this.debugMovement.get() || this.mc.player == null) return;

        double x = this.mc.player.getX();
        double y = this.mc.player.getY();
        double z = this.mc.player.getZ();

        if (this.debugTicksAfterPlacement > 0) {
            this.debugTicksAfterPlacement--;

            if (!Double.isNaN(this.debugLastX)) {
                double dx = x - this.debugLastX;
                double dy = y - this.debugLastY;
                double dz = z - this.debugLastZ;

                this.debugLog("tick: moved %.4f,%.4f,%.4f (|%.4f|) vel=%.3f,%.3f,%.3f onGround=%s crouch=%s below=%s",
                    dx, dy, dz, Math.sqrt(dx * dx + dy * dy + dz * dz),
                    this.mc.player.getDeltaMovement().x,
                    this.mc.player.getDeltaMovement().y,
                    this.mc.player.getDeltaMovement().z,
                    this.mc.player.onGround(), this.managedSneak,
                    BlockPos.containing(x, y - 1.0D, z));
            }
        }

        this.debugLastX = x;
        this.debugLastY = y;
        this.debugLastZ = z;
    }

    private void debugHeartbeat() {
        if (!this.debugLogging.get() || this.mc.player == null) return;

        Vec3 at = this.mc.player.position();
        Vec3 vel = this.mc.player.getDeltaMovement();
        BlockPos below = BlockPos.containing(at.x, at.y - 1.0D, at.z);

        this.debugLog("state: pos=%.2f,%.2f,%.2f vel=%.3f,%.3f,%.3f onGround=%s crouch=%s edge=%s below=%s camYaw=%.1f serverHolds=%.1f,%.1f",
            at.x, at.y, at.z, vel.x, vel.y, vel.z, this.mc.player.onGround(),
            this.managedSneak, this.isOverEdge(), below, this.mc.player.getYRot(),
            Rotations.serverYaw, Rotations.serverPitch);

        this.debugPackets();
    }

    /**
     * Grim's own packet accounting, once a second.
     *
     * <p>Grim's timer check is a balance written against twenty packets a second, and the aim
     * checks are decided by the order of the packets rather than by anything in the game. Neither
     * can be reproduced from the game's state, so the client keeps a model of Grim's accounting
     * and prints it here.</p>
     *
     * <p>The number to watch is {@code counted}: Grim adds fifty milliseconds to its balance for
     * every packet it counts, so anything above twenty a second makes the balance run ahead until
     * the check fires - and it keeps firing, because the packet it cancels is the one that would
     * have told Grim the tick had a movement packet in it.</p>
     */
    private void debugPackets() {
        if (!this.debugPackets.get()) return;

        GrimPacketModel.Window window = GrimPacketModel.get().snapshot();

        if (window.duplicateLooks > 0) {
            this.debugLog("packets: %s lastDuplicate=%.2f,%.2f", window, window.duplicateYaw,
                window.duplicatePitch);
        } else {
            this.debugLog("packets: %s", window);
        }
    }

    /**
     * One line of diagnosis, prefixed so it can be found or removed in one grep.
     *
     * <p>Everything the module decides is a guess about what the anticheat is checking, and the
     * server only says "flagged" and how big the difference was. These lines are what turn that
     * into something answerable: what the crouch decided and why, what angle was sent, whether the
     * ray reached the block, and where the client was when it placed.</p>
     */
    private void debugLog(String message, Object... args) {
        if (!this.debugLogging.get()) return;
        LOG.info("[DEBUG-scf] " + (args.length == 0 ? message : String.format(message, args)));
    }

    /**
     * Whether the player is in a state to bridge.
     *
     * <p>Fighting counts. A player who is swinging at something is not bridging, and the camera is
     * the only thing they can aim with, so the module must not take it off them.</p>
     *
     * <p>It must not place either. The rotation the server is told has to be the angle the client
     * walks along, and while the camera is the player's own those are two different angles - the
     * client would walk the camera's line and the server would predict the module's, and Grim
     * would report the difference in millimetres of movement every tick.</p>
     */
    private boolean canBridge() {
        if (this.mc.player == null) return false;
        if (this.mc.options.keyAttack.isDown()) return false;
        return true;
    }

    /** Monotonic wall clock; {@code getGameTime()} counts ticks and is not milliseconds. */
    private static long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    /**
     * Whether the player is at the edge of something.
     *
     * <p>The edge is the cell in the direction the player is walking into. On a bridge that cell
     * is empty for the whole of the walk - the module fills the cell under the player - so this is
     * true for the whole bridge, and the crouch is held for the whole bridge.</p>
     *
     * <p>It was the cell under the player once, and that is not the same question: the cell under
     * the player is the moment after they have already stepped off it, and it flickers on every
     * placement. The live log showed the crouch going down and coming straight back up on every
     * placement, which is a crouch that changes the client's speed by seventy per cent twice a
     * placement, and the server only learns about it a tick later.</p>
     *
     * <p>The geometry is in {@link ScaffoldEdge} so it can be tested without a world.</p>
     */
    private boolean isOverEdge() {
        if (this.mc.level == null || this.mc.player == null) return false;

        Vec3 dir = this.movementDirection();

        // The cell underfoot at the level below the player's feet: this is the edge for safe
        // walking - the drop is below the block the player is standing on.
        return ScaffoldEdge.overEdge(
            this.mc.player.getX(), this.mc.player.getY() - 1.0D, this.mc.player.getZ(),
            dir.x, dir.z,
            (x, y, z) -> this.mc.level.getBlockState(new BlockPos(x, y, z)).isAir());
    }

    /**
     * Whether there is a drop in front of the player.
     *
     * <p>The direction the player is <em>looking</em>, which is the classic safe walk: a player
     * standing at the edge of a platform is looking at the drop, and the crouch is what keeps them
     * on the block. A player bridging backwards is looking at the bridge they have already built,
     * and there is nothing to be careful of.</p>
     */
    private boolean isOverTheDrop() {
        if (this.mc.level == null || this.mc.player == null) return false;

        double yaw = Math.toRadians(this.mc.player.getYRot());
        double dx = -Math.sin(yaw);
        double dz = Math.cos(yaw);

        return ScaffoldEdge.overEdge(
            this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(),
            dx, dz,
            (x, y, z) -> this.mc.level.getBlockState(new BlockPos(x, y, z)).isAir());
    }

    /**
     * Whether the crouch belongs on right now.
     *
     * <p>The crouch does two different jobs, and they pull in opposite directions.</p>
     *
     * <p>It is a <em>fallback</em>: the module places the block under the player's feet, so the
     * player is always falling for a tick or two until the block lands. If the server refuses the
     * placement - and the anticheat-test server does, on a timer - there is a hole, and the crouch
     * is what gives the module the time to fill it: a crouched player is in a cell three times
     * longer.</p>
     *
     * <p>And it is a <em>speed control</em>: a crouch is thirty per cent of the walking speed, and
     * a bridge is walked backwards, which is thirty per cent again, so a permanent crouch makes a
     * bridge a third of the speed it could be - one block a second in the live log.</p>
     *
     * <p>And it is a <em>flag</em>: every time it goes on or off, the client's speed changes by
     * seventy per cent and the server only learns about it a tick later, which Grim's Simulation
     * measures. The crouch must change as seldom as possible - once when the bridge starts, once
     * when it ends - not on every placement. That is why the decision is the edge state, which is
     * steady for the whole of a bridge, and not "is there a target right now", which flickers on
     * the tick after every placement.</p>
     *
     * <p>Which of those three it is depends on what the player is doing, and that is what the
     * setting decides.</p>
     */
    private boolean shouldSneak() {
        switch (this.autoSneak.get()) {
            case Never -> {
                return false;
            }
            case AtEdges -> {
                // The crouch is on when the player is at an edge - whether they are walking into
                // the drop (safe walk) or looking at it (bridging backwards). The edge is steady
                // for the whole of a bridge - the module fills the cell under the player's feet,
                // so the cell ahead stays empty - so the crouch is held for the whole bridge and
                // changed only when the bridge starts or ends.
                return this.isOverEdge() || this.isOverTheDrop();
            }
            default -> {
                // The whole walk: the crouch is on before the bridge and stays on until it ends,
                // so the client's speed never changes and Grim's Simulation has nothing to
                // measure. A third of the speed, but the bridge holds together even on a server
                // that refuses placements, because the crouched player is in a cell three times
                // longer and the module has three times as long to fill each one.
                return true;
            }
        }
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

    /** When the crouch is on. See the {@code auto-sneak} setting for what each one is for. */
    public enum SneakMode {
        /** Never. The bridge is at full speed and the module is on its own. */
        Never,
        /** At a block edge: the crouch is on for the whole bridge and off on flat ground. */
        AtEdges,
        /** For the whole walk while the module is on: a third of the speed, and the bridge holds together. */
        WhileBridging;
    }
}