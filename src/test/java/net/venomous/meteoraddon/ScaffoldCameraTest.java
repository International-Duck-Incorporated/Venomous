package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the camera behaviour of {@code v-scaffold}.
 *
 * <p>There are two separate rotations in play and conflating them is what caused every report in
 * this module's history:</p>
 *
 * <ul>
 *   <li>the <b>server</b> rotation, spoofed independently and deferred through {@code PreRotate}, so
 *       the interaction leaves before the movement packet; and</li>
 *   <li>the <b>camera</b>, which is what the player actually sees.</li>
 * </ul>
 *
 * <p>The rule this class enforces is that the camera belongs to the player and the module never
 * writes it, and that the yaw the server is told is the player's own yaw. Both halves matter, and
 * they are two halves of one thing.</p>
 *
 * <p>Grim's movement prediction brute-forces the nine possible key combinations, rotates each of
 * them by the yaw in the movement packet, and asks which result is closest to where the client
 * actually ended up - {@code PredictionEngine.loopVectors} and
 * {@code InputTransformer.getMovementResultFromInput}. The client walks along its camera's yaw, so
 * the camera's yaw and the server's yaw have to be the same angle or the client walks a line the
 * server did not predict. That difference is Grim's {@code Simulation}: it flags at one millimetre
 * and past ten it teleports the player back.</p>
 *
 * <p>Asserts on source because "the module does not write the camera" is a property of the wiring
 * and has no behavioural seam. See {@link SourceGuard} for why that is done defensively.</p>
 */
public class ScaffoldCameraTest extends SourceGuard {
    private static final String SCAFFOLD =
        "src/main/java/net/venomous/meteoraddon/modules/world/Scaffold.java";

    private String scaffold() {
        return code(source(SCAFFOLD));
    }

    /**
     * The module must not write the camera, ever.
     *
     * <p>This is the freelook, and it is the whole of the "the camera snaps back to the normal
     * camera of the user" complaint. The module used to write the camera to the rotation it wanted
     * the server to hold, once per tick, which is the module fighting the player for the view: the
     * mouse's input was overwritten before the next tick's movement was computed, so the player
     * could not look anywhere and could not turn around without the view being dragged with them.</p>
     *
     * <p>It has to be "never", not "not usually": a single write puts the camera and the packet a
     * different angle apart, and that is a millimetre of movement difference every tick.</p>
     */
    @Test
    void theModuleNeverWritesTheCamera() {
        String code = scaffold();

        assertFalse(code.contains("setYRot("),
            "the module must not write the camera's yaw; the camera is the player's, and the yaw "
                + "the server is told has to be the player's own so the server's movement "
                + "prediction matches the walk the client performs");

        assertFalse(code.contains("setXRot("),
            "same, for the pitch: the camera's pitch is the player's, and the server is told a "
                + "spoofed pitch because the pitch does not enter the movement prediction");
    }

    /**
     * The yaw the server is told is the player's own yaw, read straight off the camera.
     *
     * <p>Not a smoothed version of it, not a heading derived from the block being placed. The
     * server's prediction rotates its candidate movement directions by this yaw, so it has to be
     * the angle the client is actually walking along.</p>
     */
    @Test
    void theServerIsToldThePlayersOwnYaw() {
        String body = methodBody(source(SCAFFOLD), "private float[] aimRotation(BlockPos pos)");

        requireAnchor(body, "aimRotation", "getYRot");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("floatyaw=this.mc.player.getYRot();"),
            "the yaw sent to the server has to be the camera's own yaw, unchanged");

        assertFalse(squeezed.contains("smoothedYaw"),
            "the yaw must not be smoothed: the client walks along the camera's yaw, so a smoothed "
                + "yaw and the camera's yaw are two different directions and the client walks a "
                + "line the server did not predict");
    }

    /**
     * The movement input must not be compensated.
     *
     * <p>This was the "the player cannot move and gets frozen in place" session, and the reason is
     * in Grim's own source. The prediction rotates the nine candidate key combinations by the
     * server's yaw; the client's movement is the camera's yaw applied to its input. Rotating the
     * input by the difference between the two puts the client's movement at an arbitrary angle
     * between the eight directions Grim is willing to accept - it comes off the grid - and the
     * offset is then the full length of the movement rather than a millimetre. The live log showed
     * 0.156 blocks against a threshold of 0.001, and Grim's {@code OffsetHandler} teleports the
     * player back once the offset passes 0.1, which is the freezing.</p>
     */
    @Test
    void theMovementInputIsNotCompensated() {
        String code = scaffold();

        assertFalse(code.contains("InputCompensation"),
            "rotating the movement input takes the walk off the grid Grim's prediction allows, and "
                + "the offset becomes the length of the movement rather than a millimetre");

        assertFalse(code.contains("compensateInput"),
            "same, for the helper that did the rotating");
    }

    /**
     * The aim is the only thing that is smoothed, and it is the pitch.
     *
     * <p>One smoothed value, handed to Meteor to be sent. It is not written to the camera, because
     * the camera is the player's.</p>
     */
    @Test
    void thePitchIsTheOnlySmoothedValue() {
        String code = squeeze(code(source(SCAFFOLD)));

        assertTrue(code.contains("(pitch-this.smoothedPitch)*AIM_SMOOTH_PITCH"),
            "the pitch closes a fixed share of the remaining gap");

        assertFalse(code.contains("AIM_SMOOTH="),
            "there is no yaw share any more: the yaw is not the module's to smooth");
    }

    /**
     * The pitch has to track its target closely, because it moves faster than anything else.
     *
     * <p>The pitch's target is the elevation of the face being clicked, which is nearly underfoot,
     * and that elevation moves several degrees every tick as the player walks - and the ray is
     * steep enough that the lag turns into a missed placement.</p>
     */
    @Test
    void thePitchIsSmoothedMoreCloselyThanAnythingElse() {
        String code = squeeze(code(source(SCAFFOLD)));

        int pitch = code.indexOf("AIM_SMOOTH_PITCH=");

        assertTrue(pitch >= 0, "the pitch share has to be there");

        String pitchShare = code.substring(pitch + "AIM_SMOOTH_PITCH=".length());
        pitchShare = pitchShare.substring(0, pitchShare.indexOf("F"));

        assertTrue(Float.parseFloat(pitchShare) >= 0.5F,
            "the pitch share is " + pitchShare + "; the pitch target moves several degrees a tick "
                + "and a slow aim lags far enough for the ray to miss the face");
    }

    /**
     * The smoothing has to actually initialise.
     *
     * <p>This is a real bug that shipped and it is worth a guard of its own. The first-call test
     * was written {@code smoothedYaw == Float.NaN}, and NaN is not equal to itself in Java, so the
     * branch never ran. The aim was NaN from the first tick, the server discarded every rotation
     * with "Invalid entity rotation: NaN, discarding.", and the module stopped placing blocks at
     * all - the symptom was "it doesn't auto place the blocks anymore" with no error anywhere that
     * pointed at the cause.</p>
     *
     * <p>Asserted on the shape of the test rather than on the arithmetic, because the arithmetic is
     * the easy part: the initialisation has to be reachable, and the only way it can be is a
     * NaN-aware comparison.</p>
     */
    @Test
    void theSmoothingIsInitialisedWithANaNSafeTest() {
        String body = methodBody(source(SCAFFOLD), "private float[] aimRotation(BlockPos pos)");

        requireAnchor(body, "aimRotation", "smoothedPitch");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("Float.isNaN(this.smoothedPitch)"),
            "the first-call test has to be Float.isNaN. NaN is not equal to itself, so an equality "
                + "comparison against Float.NaN is never true and the initialisation never runs - "
                + "the aim stays NaN, the server discards every rotation, and nothing is placed");

        assertFalse(squeezed.contains("this.smoothedPitch==Float.NaN"),
            "an equality comparison against Float.NaN is the exact bug that shipped");

        assertTrue(squeezed.contains("this.smoothedPitch=pitch;"),
            "and the initialisation has to seed the smoothed pitch from the target");
    }

    /**
     * The rotation must never repeat exactly, or Grim flags it.
     *
     * <p>{@code AimDuplicateLook} flags a rotation packet whose yaw and pitch are both identical to
     * the previous packet's. A player standing still on a settled bridge would otherwise send the
     * same rotation every tick. It fired fourteen times in one session, at the join, before there
     * was a wobble.</p>
     */
    @Test
    void theRotationNeverRepeatsExactly() {
        String code = squeeze(code(source(SCAFFOLD)));

        assertTrue(code.contains("PITCH_WOBBLE"),
            "the pitch carries a small wobble so the rotation is never byte-for-byte the same as "
                + "the previous one, which is what AimDuplicateLook reports");

        assertTrue(code.contains("Math.sin(this.scaffoldCount"),
            "and the wobble is a sine rather than a random number, so the deltas stay smooth and "
                + "the rotation still looks like something a hand produced");
    }

    /**
     * The one invariant this class was silently not covering: the aim must hand
     * {@code ScaffoldAim.aimPoint} a real normal.
     *
     * <p>Passing {@code null} there is legal - the helper treats it as "no support, use the block
     * centre" - which made {@code aimPoint(null, ...)} compile and pass every test while silently
     * reverting the aim to the block centre. Found by mutation: nothing was checking the
     * argument.</p>
     */
    @Test
    void theAimForwardsARealPlacementSideToTheHelper() {
        String body = methodBody(source(SCAFFOLD), "private float[] aimRotation(BlockPos pos)");

        requireAnchor(body, "aimRotation", "getPlaceSide");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("side==null?pos:pos.relative(side)"),
            "the aim is worked out against the block the placement is made against, so the side has "
                + "to be resolved rather than assumed");

        assertTrue(squeezed.contains("support.getX()+0.5D"),
            "and the aim point is the top of that block, not a point on the ground below the "
                + "player, whose elevation swings as the player walks across their cell");
    }

    /**
     * The module must never yank the player's view around behind their back.
     *
     * <p>Every remaining flag in the live log traced back to this. The bridge techniques snapped
     * yaw to the nearest 45 degrees, forced pitch to 75.5, then restored the player's own angles in
     * the same tick - so the camera was written four times per tick and the player was steering
     * against it the whole time. Holding S did not walk in a straight line, correcting that by hand
     * produced wild rotations, and the correction itself is what got flagged.</p>
     */
    @Test
    void theModuleDoesNotYankTheViewAround() {
        String code = scaffold();

        assertFalse(code.contains("Math.round(yaw / 45.0F)"),
            "snapping the player's yaw to the nearest 45 degrees is the strafe lock; it fights the "
                + "player every time they turn and it is why holding S would not walk straight");
        assertFalse(code.contains("setXRot(75.5F)"),
            "forcing pitch to 75.5 hijacks the view without the player asking");
        assertFalse(code.contains("yRotO = oldYaw") || code.contains("xRotO = oldPitch"),
            "restoring the previous angles afterwards is what made the camera visibly snap back and "
                + "forth within a single tick");
    }

    /**
     * The crouch must not change on a tick where a placement is going out.
     *
     * <p>Grim's {@code PacketOrderF} cancels an action packet that arrives while the server still
     * believes the player is sneaking, and the server believes it between the "start sneaking"
     * packet and the next movement packet. The placement leaves in the movement packet's PRE
     * phase, which is after the player's tick has already sent the crouch input - so a crouch that
     * changes on that tick gets the placement cancelled.</p>
     *
     * <p>A cancelled placement leaves the client standing on a block the server never received,
     * and that desync is worth tenths of a block: the live log showed the offsets jump to 0.15 and
     * the server teleporting the player back.</p>
     */
    @Test
    void theCrouchDoesNotChangeOnATickWithAPlacement() {
        String body = methodBody(source(SCAFFOLD), "private void updateManagedSneak(boolean atEdge)");

        requireAnchor(body, "updateManagedSneak", "PreRotate.hasPending");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("PreRotate.hasPending(this)") && squeezed.contains("return;"),
            "a crouch that changes on a tick where a placement leaves is what gets that placement "
                + "cancelled by PacketOrderF, and the cancelled placement is what desyncs the "
                + "client from the server");
    }

    /**
     * Sneaking is opt-in.
     *
     * <p>It used to be forced by two of the technique profiles, and every single Grim
     * packet-order flag in the last run carried {@code sneaking=true}:
     * {@code PacketOrderE} reports any held-item change while a flag is set, {@code PacketOrderF}
     * reports placing while it is set. A flag the player did not ask for, on every placement.</p>
     */
    @Test
    void sneakingIsOptIn() {
        String code = scaffold();

        for (String method : new String[] {"private void tickStandardMode()",
                                           "private void tickBridgeMode()",
                                           "private void tickGrim()"}) {
            String body = squeeze(methodBody(source(SCAFFOLD), method));

            if (body.isEmpty()) continue;

            assertFalse(body.contains("this.updateManagedSneak(this.isOverEdge())"),
                method + " forces a sneak with no setting behind it");
        }

        assertTrue(squeeze(scaffold()).contains("this.autoSneak.get()&&this.isOverEdge()"),
            "edge sneaking should be reachable, but only when the player asked for it");
    }

    /**
     * The placement is gated on the ray reaching the block, not on an angle.
     *
     * <p>The face is nearly underfoot - the eye about 1.6 blocks above it and a third of a block
     * away - so the ray is very steep and a couple of degrees of pitch error moves the landing
     * point half a block, from the middle of the face to its top edge. The camera was lagging the
     * target by more than that, so the ray grazed the corner of the block and the placement was
     * missed, leaving a hole in the bridge for the player to walk into. An angle tolerance cannot
     * express that, so the check itself is used: {@code GrimRay} is a transcription of Grim's own
     * RotationPlace test.</p>
     */
    @Test
    void thePlacementIsGatedOnTheRayReachingTheBlock() {
        String body = methodBody(source(SCAFFOLD),
            "private boolean rayReachesSupport(BlockPos support, float yaw, float pitch)");

        requireAnchor(body, "rayReachesSupport", "GrimRay.rotationPlaceWouldPass");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("yaw,pitch,this.lastServerYaw,this.lastServerPitch"),
            "the ray has to be tested against the rotation the placement goes out with, with the last one sent as the second candidate");

        assertTrue(squeezed.contains("support.getX()"),
            "and against the block that was clicked, which is the support");

        String caller = squeeze(methodBody(source(SCAFFOLD), "private void aimAndPlace(BlockPos pos)"));

        assertTrue(caller.contains("this.rayReachesSupport(support,face[0],face[1])") && caller.contains("if(reaches)"),
            "and the placement is only made when that test passes");

        assertFalse(caller.contains("PLACE_TOLERANCE"),
            "the angle tolerance is what this replaces; it was seven degrees of slack on a geometry "
                + "where two degrees already misses the face");
    }
}
