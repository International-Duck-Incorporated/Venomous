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
        int server = normalised.indexOf("this.easedAimAt(pos);");

        assertTrue(face >= 0 && camera > face,
            "the face aim must be computed first and handed to the camera");
        assertTrue(server > camera,
            "the server rotation must be eased from the same point, so both agree on one face");

        assertFalse(normalised.contains("this.easeCameraOnto(this.easedAimAt"),
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