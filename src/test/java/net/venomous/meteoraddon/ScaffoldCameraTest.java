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
 * <p>Originally the camera was pinned to whatever the server was told, which hijacked the player's
 * view. Then it was decoupled entirely, so it never tracked the placement at all and stopped looking
 * at the face being built. It now eases onto that face - slower than the server rotation, and
 * GCD-quantised, because an arbitrary float written to {@code setYRot} is an angle no mouse could
 * produce.</p>
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

        requireAnchor(body, "aimAndPlace",
            "float[] face = this.faceAimAt(pos);",
            "this.easeCameraOnto(face);");

        String normalised = squeeze(body);

        int face = normalised.indexOf("float[]face=this.faceAimAt(pos);");
        int camera = normalised.indexOf("this.easeCameraOnto(face);");
        int server = normalised.indexOf("this.serverAimAt(pos);");

        assertTrue(face >= 0 && camera > face,
            "the face aim must be computed first and handed to the camera");
        assertTrue(server > camera,
            "the server rotation must be eased from the same point, so both agree on one face");

        assertFalse(normalised.contains("this.easeCameraOnto(this.serverAimAt"),
            "the camera must not be driven from the server rotation; that is what pinned the two "
                + "together in the first place");
    }

    @Test
    void theCameraIsGcdQuantised() {
        String body = methodBody(source(SCAFFOLD), "private void easeCameraOnto(float[] face)");

        requireAnchor(body, "easeCameraOnto", "RotationGcd.quantize");

        assertTrue(squeeze(body).contains("setYRot(yaw+RotationGcd.quantize(sensitivity,yawStep))"),
            "every camera delta must be a whole multiple of the sensitivity GCD; writing an "
                + "arbitrary float to setYRot produces an angle no mouse can generate, which is "
                + "what rotation checks fingerprint");
    }

    /**
     * The camera yaw must be written <em>unwrapped</em>.
     *
     * <p>This is the opposite of the obvious fix and the reason matters. Grim's {@code AimModulo360}
     * is not a range check - it is {@code |yaw| < 360 && |deltaYaw| > 320 && |lastDeltaYaw| < 30},
     * which hunts exactly one thing: a client that reports a wrapped yaw where vanilla would report
     * an unwrapped one. {@code Entity.turn} never wraps; only pitch is clamped to [-90, 90].</p>
     *
     * <p>Wrapping the value on write therefore does not fix anything - the long runs past 360 the
     * debug log showed are correct vanilla behaviour, the easing was simply taking the short way
     * round - and it manufactures the +-360 single-tick jump the check is built to catch. The
     * remaining error is wrapped instead, so the approach still converges.</p>
     */
    @Test
    void theCameraYawIsWrittenUnwrappedTheWayVanillaDoes() {
        String body = methodBody(source(SCAFFOLD), "private void easeCameraOnto(float[] face)");

        requireAnchor(body, "easeCameraOnto", "setYRot(");

        assertFalse(squeeze(body).contains("setYRot(wrapDegrees("),
            "wrapping the yaw on write creates a single-tick 360 degree jump, which is precisely "
                + "what Grim's AimModulo360 flags; Entity.turn never wraps yaw, so neither may this");

        assertTrue(squeeze(body).contains("wrapDegrees(face[0]-yaw)"),
            "the remaining error is what gets wrapped instead, so the approach still takes the "
                + "short way round and converges");
    }

    /**
     * The server rotation is snapped to the face; only the camera eases.
     *
     * <p>Easing the server rotation was tried and reverted. AntiCheat-Addition's
     * {@code ScaffoldRotation} scores rotation variance - 35 degrees or more inside two ticks, over
     * 360 degrees of summed change, or variance above 70 - and its own source comment names
     * "perfectly aiming at blocks" as a false-positive source, which is what a multi-tick approach
     * looks like from the server. The live run it was reverted on showed the face pitch alternating
     * 62.5 then 56.9 on consecutive ticks with 22 to 24 degrees of yaw per tick.</p>
     */
    @Test
    void theServerRotationSnapsWhileTheCameraEases() {
        String body = methodBody(source(SCAFFOLD), "private float[] serverAimAt(BlockPos pos)");

        requireAnchor(body, "serverAimAt", "return");

        String squeezed = squeeze(body);

        assertFalse(squeezed.contains("AimEase."),
            "the server rotation must not be eased; the visible easing belongs to the camera");

        assertFalse(squeezed.contains("maxAimStep()"),
            "a per-tick step on the server angle is exactly the variance ScaffoldRotation scores");

        assertTrue(squeezed.contains("returnthis.faceAimAt(pos);"),
            "the server angle is the face aim itself");
    }

    /**
     * The placement is refused unless the cell is free <em>and</em> has a supporting face.
     *
     * <p>{@code canPlace} alone still returns true for a free cell with nothing to place against,
     * and clicking one of those is what Grim's {@code AirLiquidPlace} reports. The debug log printed
     * {@code side=NULL, canPlace=true} on exactly the ticks that flag arrived.</p>
     */
    @Test
    void aimAndPlaceRefusesACellWithNoSupportingFace() {
        String body = methodBody(source(SCAFFOLD), "private void aimAndPlace(BlockPos pos)");

        requireAnchor(body, "aimAndPlace", "ScaffoldAim.hasSupport(");

        assertTrue(squeeze(body).contains(
                "ScaffoldAim.hasSupport(BlockUtils.canPlace(pos),BlockUtils.getPlaceSide(pos)!=null)"),
            "the guard must combine canPlace with the existence of a place side; canPlace by itself "
                + "does not distinguish a placeable cell from an air-place");

        assertFalse(squeeze(body).contains("if (!BlockUtils.canPlace(pos))"),
            "the bare canPlace check is what this replaces - it passes air-place cells through");
    }

    /**
     * The camera must travel at a fraction of the server rotation's rate.
     *
     * <p>An earlier version asserted the literal {@code * 0.6F} was present, which passed even when
     * the mutation replaced it - the rate appears twice, for the step and for the velocity
     * carry-over, and only the first was replaced. Requiring <em>every</em> occurrence to be scaled
     * is what actually holds the invariant.</p>
     */
    @Test
    void everyUseOfTheServerRateInsideTheCameraIsScaledDown() {
        String body = squeeze(methodBody(source(SCAFFOLD), "private void easeCameraOnto(float[] face)"));

        // No \s* before the lookahead: a greedy \s* can match zero-width and backtrack, which defeats
        // a negative lookahead entirely and makes this assertion pass on unscaled rates.
        assertFalse(matches("maxAimStep\\(\\)(?!\\s*\\*\\s*0\\.)", body),
            "the camera must never use the server rotation's raw rate; every occurrence of "
                + "maxAimStep() in easeCameraOnto has to be scaled down, otherwise the view locks "
                + "onto the placement instead of leading into it");
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

    @Test
    void theConstantRateCameraSweepIsGone() {
        String code = scaffold();

        requireAnchor(code, "Scaffold", "private void easeCameraOnto(float[] face)");

        assertFalse(code.contains("setYRot(this.smooth("),
            "the old constant-rate sweep is gone; it moved by an unquantised fixed number of degrees "
                + "per tick for as long as it lasted");
    }
}