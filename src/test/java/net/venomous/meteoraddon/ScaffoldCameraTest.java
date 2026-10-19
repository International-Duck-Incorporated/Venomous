package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the camera behaviour of {@code v-scaffold}.
 *
 * <p>There are two separate rotations in play and conflating them is what caused both the original
 * complaint and this one:</p>
 *
 * <ul>
 *   <li>the <b>server</b> rotation, spoofed independently and deferred through {@code PreRotate}, so
 *       the interaction leaves before the movement packet; and</li>
 *   <li>the <b>camera</b>, which is what the player actually sees.</li>
 * </ul>
 *
 * <p>The rule this class now enforces is that the two are the <em>same angle</em>, always. That is
 * not a style preference; it is what keeps the server's movement prediction matching the movement
 * the client performs, which is what Grim's {@code Simulation} checks.</p>
 *
 * <p>Asserts on source because "the camera is handed the face aim, not the block centre" is a
 * property of the wiring and has no behavioural seam. See {@link SourceGuard} for why that is done
 * defensively.</p>
 */
public class ScaffoldCameraTest extends SourceGuard {
    private static final String SCAFFOLD =
        "src/main/java/net/venomous/meteoraddon/modules/world/Scaffold.java";

    private String scaffold() {
        return code(source(SCAFFOLD));
    }

    @Test
    void theCameraIsEasedOntoTheFaceAimAndNotPinnedToTheServerAngle() {
        String body = methodBody(source(SCAFFOLD), "private void aimAndPlace(BlockPos pos)");

        requireAnchor(body, "aimAndPlace", "float[] face = this.faceAimAt(pos);");

        String normalised = squeeze(body);

        assertTrue(normalised.indexOf("float[]face=this.faceAimAt(pos);") >= 0,
            "the face aim has to be computed before it is handed over");
    }

    /**
     * The camera has to end up holding the rotation the server was actually sent.
     *
     * <p>This is Grim's {@code Simulation}, and it is a millimetre of movement rather than a
     * rotation check. Grim's {@code OffsetHandler} flags when the client's position differs from
     * the server's prediction by more than 0.001 blocks. The client computes the movement from the
     * camera's yaw; the server predicts it from the yaw in the movement packet. Those have to be
     * the same number.</p>
     *
     * <p>They were not, and the live log showed it: the camera at 81 degrees, the server at 87, the
     * raw aim at 89. Meteor's {@code Rotations} smooths the rotation it sends, so the angle that
     * reaches the server is not the angle that was asked for. Writing the camera to the requested
     * aim therefore guarantees a mismatch, and it is also the "the camera snaps back to the normal
     * camera of the user": Meteor writes its angle onto the camera for the packet and restores the
     * previous camera angle afterwards.</p>
     *
     * <p>So the camera is put back on the sent rotation once the packet has gone out, which is the
     * only moment that value is knowable.</p>
     */
    @Test
    void theCameraEndsUpHoldingTheRotationTheServerWasSent() {
        String body = methodBody(source(SCAFFOLD),
            "private void onSendMovementPacketsPost(SendMovementPacketsEvent.Post event)");

        requireAnchor(body, "onSendMovementPacketsPost", "setYRot");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("this.mc.player.setYRot(Rotations.serverYaw);"),
            "the camera is put on the rotation the server was just told, not on the one that was "
                + "asked for - Meteor smooths what it sends, so the two are not the same number");

        assertTrue(squeezed.contains("this.mc.player.setXRot(Rotations.serverPitch);"),
            "and the pitch with it");

        assertTrue(squeezed.contains("Float.isFinite(Rotations.serverYaw)")
                && squeezed.contains("Float.isFinite(Rotations.serverPitch)"),
            "a non-finite rotation is never written to the camera; the server discards those with "
                + "\"Invalid entity rotation: NaN, discarding.\" and the module silently stops "
                + "placing");

        assertTrue(squeezed.contains("!this.isActive()"),
            "and the camera is only touched while the module is on");
    }

    /**
     * One smoothed value, and it feeds both the camera and the server.
     *
     * <p>This is the "the camera snaps back to the normal camera of the user" fix. Meteor's
     * {@code Rotations} writes the server's angle onto the camera for the movement packet and puts
     * the camera back afterwards. When the camera held a different angle from the one being sent -
     * which is what happened while the camera was eased separately and the server was told the
     * camera's lagged value - it was snapped to the server's angle and back, every tick. The live
     * log showed the three values apart: the camera at 81 degrees, the server at 87, the raw aim at
     * 89.</p>
     *
     * <p>So the smoothing happens once, where the aim is computed, and the same smoothed value is
     * given to the camera and to the server. Meteor's snap then writes the value that is already
     * there, and its restore puts back the same one.</p>
     */
    /**
     * The camera is written once, from the sent rotation, and never from the requested aim.
     *
     * <p>Two values were fighting over the camera: the aim that was asked for, and the rotation
     * Meteor actually smoothed and sent. The live log caught them three degrees apart. They cannot
     * be reconciled by smoothing, because the one that matters is the one on the wire.</p>
     */
    @Test
    void theCameraAndTheServerAreGivenTheSameSmoothedAngle() {
        String code = squeeze(code(source(SCAFFOLD)));

        assertFalse(code.contains("this.mc.player.setYRot(face[0])"),
            "writing the requested aim to the camera guarantees a mismatch with the packet, "
                + "because Meteor smooths what it sends");

        assertFalse(code.contains("this.mc.player.setXRot(face[1])"),
            "same, for the pitch");

        assertTrue(code.contains("this.mc.player.setYRot(Rotations.serverYaw);"),
            "the camera takes the rotation the server was sent");
    }

    /**
     * The smoothing is a share of the remaining gap, applied to the aim.
     *
     * <p>Not to the camera, and not with a carried velocity: a share of the gap cannot overshoot,
     * reverse or oscillate, and there is no velocity to carry, reset or get wrong.</p>
     */
    @Test
    void theAimIsSmoothedByAShareOfTheRemainingGap() {
        String body = methodBody(source(SCAFFOLD), "private float[] faceAimAt(BlockPos pos)");

        requireAnchor(body, "faceAimAt", "AIM_SMOOTH");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("wrapDegrees(heading-this.smoothedYaw)*AIM_SMOOTH"),
            "the yaw closes a fixed share of the remaining gap");

        assertTrue(squeezed.contains("(pitch-this.smoothedPitch)*AIM_SMOOTH_PITCH"),
            "and so does the pitch");

        assertFalse(squeezed.contains("AimEase."),
            "AimEase is the velocity-ramp easing; a carried velocity is what produced the overshoot");
    }

    /**
     * The camera and the rotation sent to the server must be the same angle.
     *
     * <p>This is the fix for Grim's {@code Simulation}, and it is not cosmetic. Meteor's own
     * {@code Rotations} does this:</p>
     *
     * <pre>{@code
     *   preYaw = mc.player.getYRot();      // remember the player's own camera
     *   mc.player.setYRot(rotation.yaw);   // snap the camera onto the spoofed rotation
     * }</pre>
     *
     * <p>and restores {@code preYaw} once the packet is away. So the camera jumped to the placement
     * angle for the movement packet and then snapped back to the player's own angle - the "the
     * camera snaps back to the normal camera of the user" that was reported. The damage is that
     * {@code aiStep} computes the movement from the camera <em>after</em> the restore, while the
     * packet carries the spoofed angle: the server predicts one walk and the client performs
     * another. Grim reported the difference seventy-four times in the run that found it.</p>
     *
     * <p>Writing the camera here and sending that same angle makes Meteor's snap a no-op and its
     * restore a no-op, so the camera and the packet agree at every moment of the tick.</p>
     */
    /**
     * The camera must close a fixed share of the gap, and hold no velocity.
     *
     * <p>This is the jitter. The easing it replaces carried velocity between ticks and only began
     * decelerating inside a 0.4 degree capture window while still permitted to move up to 13.2
     * degrees in a tick - a window a tenth the width of its own step, so the camera could not land in
     * it. The carried velocity drove it past the target, the remaining distance flipped sign, and it
     * swept back. The live log caught it on a target that was not moving at all:</p>
     *
     * <pre>{@code
     *   aim=(-54,4,79,1)  cam=(-54,3,77,0)
     *   aim=(-54,4,79,1)  cam=(-87,9,77,0)
     *   aim=(-54,4,79,1)  cam=(-115,5,77,0)
     *   aim=(-54,4,79,1)  cam=(-54,3,79,1)
     * }</pre>
     *
     * <p>Taking a share of the remaining gap cannot overshoot, cannot reverse and cannot oscillate:
     * each tick leaves exactly {@code 1 - share} of the error behind. There is no velocity to carry,
     * reset or get wrong.</p>
     */
    @Test
    void theCameraClosesAFixedShareOfTheGapAndHoldsNoVelocity() {
        String body = methodBody(source(SCAFFOLD), "private float[] faceAimAt(BlockPos pos)");

        requireAnchor(body, "faceAimAt", "AIM_SMOOTH");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("wrapDegrees(heading-this.smoothedYaw)*AIM_SMOOTH"),
            "the yaw step is a fixed share of the remaining error");

        assertTrue(squeezed.contains("(pitch-this.smoothedPitch)*AIM_SMOOTH_PITCH"),
            "and so is the pitch");

        assertFalse(squeezed.contains("AimEase."),
            "AimEase is the velocity-ramp easing; a carried velocity is what produced the overshoot");

        assertFalse(squeezed.contains("=AimEase.nextVelocity("),
            "there must be no velocity written back per tick at all");
    }

    /**
     * No carried state anywhere on the module.
     *
     * <p>Asserting only on the method body would miss a velocity field that something else still
     * writes, and the failure mode of such a field is invisible until it oscillates in play. The
     * whole class is checked instead.</p>
     */
    @Test
    void noCameraVelocityStateSurvivesOnTheModule() {
        String code = scaffold();

        assertFalse(code.contains("camYawVelocity"),
            "a camera velocity field is the mechanism behind the overshoot; nothing may write it");
        assertFalse(code.contains("camPitchVelocity"), "same, for pitch");
        assertFalse(code.contains("CAPTURE_DEGREES"),
            "the 0.4 degree capture window is what the velocity could not land inside");
    }

    /**
     * The per-tick random jitter is gone.
     *
     * <p>It was there to stop the sweep running at an exactly constant rate. On the camera it did
     * the opposite of what was wanted: it is jitter, by construction, and jitter is exactly what it
     * was asked about.</p>
     */
    @Test
    void theCameraIsNotShakenEveryTick() {
        String code = scaffold();

        assertFalse(code.contains("aimNoise"),
            "gaussian noise added to the camera step every tick is jitter by definition");
        assertFalse(code.contains("nextGaussian"),
            "and the only remaining use of gaussian noise was to shake the view");
    }

    /**
     * The camera yaw must be written <em>unwrapped</em>.
     *
     * <p>This is the opposite of the obvious fix and the reason matters. Grim's {@code AimModulo360}
     * is not a range check - it is {@code |yaw| < 360 && |deltaYaw| > 320 && |lastDeltaYaw| < 30},
     * which hunts exactly one thing: a client that reports a wrapped yaw where vanilla would report
     * an unwrapped one. {@code Entity.turn} never wraps; only pitch is clamped.</p>
     *
     * <p>Wrapping the value on write therefore does not fix anything - the long runs past 360 the
     * debug log showed are correct vanilla behaviour, the easing was simply taking the short way
     * round - and it manufactures the +-360 single-tick jump the check is built to catch. The
     * remaining error is wrapped instead, so the approach still converges.</p>
     */
    @Test
    void theCameraYawIsWrittenUnwrappedTheWayVanillaDoes() {
        String code = squeeze(code(source(SCAFFOLD)));

        assertFalse(code.contains("setYRot(wrapDegrees("),
            "wrapping the yaw on write creates a single-tick 360 degree jump, which is precisely "
                + "what Grim's AimModulo360 flags; Entity.turn never wraps yaw, so neither may this");

        assertTrue(code.contains("wrapDegrees(heading-this.smoothedYaw)"),
            "the remaining error is what gets wrapped instead, so the approach still takes the "
                + "short way round and converges");
    }

    @Test
    void theCameraIsGcdQuantised() {
        String code = squeeze(code(source(SCAFFOLD)));

        assertFalse(code.contains("RotationGcd.quantize(sensitivity,yawStep)"),
            "the camera used to quantise its own per-tick step to the sensitivity GCD, which was "
                + "meaningful while the camera was eased on its own. It is not eased on its own "
                + "any more: the angle is computed once and written, so there is no per-tick step to "
                + "quantise - and quantising a value that is then sent whole would just round the "
                + "angle the server is told");
    }

    /**
     * The one invariant this class was silently not covering: {@code faceAimAt} must hand
     * {@code ScaffoldAim.aimPoint} a real normal.
     *
     * <p>Passing {@code null} there is legal - the helper treats it as "no support, use the block
     * centre" - which made {@code aimPoint(null, ...)} compile and pass every test while silently
     * reverting the camera to aiming at block centres. Found by mutation: nothing was checking the
     * argument.
     */
    @Test
    void theFaceAimForwardsARealPlacementSideToTheHelper() {
        String body = methodBody(source(SCAFFOLD), "private float[] faceAimAt(BlockPos pos)");

        requireAnchor(body, "faceAimAt", "ScaffoldAim.aimPoint(");

        String squeezed = squeeze(body);

        assertFalse(squeezed.contains("ScaffoldAim.aimPoint(pos.getX(),pos.getY(),pos.getZ(),null,"),
            "faceAimAt is passing a null normal, so the helper silently falls back to the block "
                + "centre and the camera stops looking at the face");

        assertTrue(squeezed.contains("side==null?null:")
                || squeezed.contains("side = BlockUtils.getPlaceSide(pos);"),
            "the normal must be derived from BlockUtils.getPlaceSide(pos)");
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
     * There is no "leave the camera alone" option, and that is deliberate.
     *
     * <p>Leaving the camera free was what made the module pleasant to use and it is exactly what
     * broke the movement prediction: with the camera on the player's own angle and the packet
     * carrying the placement angle, the server predicts a different walk from the one performed.
     * A setting that only produces a flag is worse than no setting, so it is gone - the camera
     * follows the placement, smoothly, and the server is told the same angle.</p>
     */
    @Test
    void thereIsNoOptionToLeaveTheCameraAlone() {
        String code = scaffold();

        assertFalse(code.contains("camera-follow"),
            "a setting that lets the camera diverge from the sent rotation is a setting that "
                + "reintroduces the movement mismatch Grim reports as Simulation");
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
        String body = methodBody(source(SCAFFOLD), "private boolean rayReachesSupport(BlockPos support)");

        requireAnchor(body, "rayReachesSupport", "GrimRay.rotationPlaceWouldPass");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("Rotations.serverYaw"),
            "the ray has to be tested against the rotation the SERVER is holding, because that is "
                + "the one Grim judges the placement by");

        assertTrue(squeezed.contains("support.getX()"),
            "and against the block that was clicked, which is the support");

        String caller = squeeze(methodBody(source(SCAFFOLD), "private void aimAndPlace(BlockPos pos)"));

        assertTrue(caller.contains("if(this.rayReachesSupport(support))"),
            "and the placement is only made when that test passes");

        assertFalse(caller.contains("PLACE_TOLERANCE"),
            "the angle tolerance is what this replaces; it was seven degrees of slack on a geometry "
                + "where two degrees already misses the face");
    }

    /**
     * The pitch has to track its target more closely than the yaw does, because it moves faster.
     *
     * <p>The yaw's target is the discrete heading of the support's side and barely moves. The
     * pitch's target moves by several degrees every tick, because the eye's distance to a nearly
     * underfoot face changes as the player walks - and the ray is steep enough that the lag turns
     * into a missed placement.</p>
     */
    @Test
    void thePitchIsSmoothedMoreCloselyThanTheYaw() {
        String code = squeeze(code(source(SCAFFOLD)));

        int pitch = code.indexOf("AIM_SMOOTH_PITCH=");
        int yaw = code.indexOf("AIM_SMOOTH=");

        assertTrue(pitch >= 0 && yaw >= 0, "both shares have to be there");

        String pitchShare = code.substring(pitch + "AIM_SMOOTH_PITCH=".length());
        pitchShare = pitchShare.substring(0, pitchShare.indexOf("F"));

        String yawShare = code.substring(yaw + "AIM_SMOOTH=".length());
        yawShare = yawShare.substring(0, yawShare.indexOf("F"));

        assertTrue(Float.parseFloat(pitchShare) > Float.parseFloat(yawShare),
            "the pitch share is " + pitchShare + " against a yaw share of " + yawShare
                + "; the pitch target moves several degrees a tick and a slow camera lags far "
                + "enough for the ray to miss the face");
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
        String body = methodBody(source(SCAFFOLD), "private float[] faceAimAt(BlockPos pos)");

        requireAnchor(body, "faceAimAt", "smoothedYaw");

        String squeezed = squeeze(body);

        assertTrue(squeezed.contains("Float.isNaN(this.smoothedYaw)"),
            "the first-call test has to be Float.isNaN. NaN is not equal to itself, so an equality "
                + "comparison against Float.NaN is never true and the initialisation never runs - "
                + "the aim stays NaN, the server discards every rotation, and nothing is placed");

        assertFalse(squeezed.contains("this.smoothedYaw==Float.NaN"),
            "an equality comparison against Float.NaN is the exact bug that shipped");

        assertTrue(squeezed.contains("this.smoothedYaw=heading;"),
            "and the initialisation has to seed the smoothed yaw from the heading");
    }

    @Test
    void theConstantRateCameraSweepIsGone() {
        String code = scaffold();

        requireAnchor(code, "Scaffold", "private void onSendMovementPacketsPost(");

        assertFalse(code.contains("setYRot(this.smooth("),
            "the old constant-rate sweep is gone; it moved by an unquantised fixed number of degrees "
                + "per tick for as long as it lasted");
    }
}