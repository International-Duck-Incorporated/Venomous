package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
 * view. Then it was decoupled entirely, so it never tracked the placement at all and stopped
 * looking at the face being built. It now eases onto that face - slower than the server rotation,
 * and GCD-quantised, because an arbitrary float written to {@code setYRot} is an angle no mouse
 * could produce.
 */
public class ScaffoldCameraTest {
    private static String scaffoldSource() throws IOException {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        String rel = "src/main/java/net/venomous/meteoraddon/modules/world/Scaffold.java";

        while (dir != null) {
            Path candidate = dir.resolve(rel);
            if (Files.isRegularFile(candidate)) return Files.readString(candidate);
            dir = dir.getParent();
        }

        throw new AssertionError("could not find " + rel);
    }

    @Test
    void theCameraEasesOntoThePlacementFace() throws IOException {
        String source = scaffoldSource();

        assertTrue(source.contains("private void easeCameraOnto(float[] face)"),
            "scaffold must ease the camera towards the placement face");
        assertTrue(source.contains("this.easeCameraOnto(this.faceAimAt(pos))"),
            "and it must be driven from aimAndPlace so the camera and the server rotation agree on "
                + "the same face, without the camera being pinned to the server angle");
    }

    @Test
    void theCameraIsGcdQuantised() throws IOException {
        String source = scaffoldSource();

        assertTrue(source.contains("RotationGcd.quantize(sensitivity, yawStep)"),
            "every camera delta must be a whole multiple of the sensitivity GCD; writing an "
                + "arbitrary float to setYRot produces an angle no mouse can generate, which is "
                + "what rotation checks fingerprint");
    }

    @Test
    void theCameraRunsSlowerThanTheServerRotation() throws IOException {
        String source = scaffoldSource();
        int camera = source.indexOf("private void easeCameraOnto(float[] face)");
        assertTrue(camera > 0, "easeCameraOnto missing");

        String body = source.substring(camera, source.indexOf("\n    }", camera));

        // Every use of the server rate inside the camera must be scaled down. Checking only that a
        // particular literal is present was too weak: the rate appears twice (the step and the
        // velocity carry-over), so replacing just one of them still left the literal behind and the
        // test passed while the camera ran at full speed on one axis.
        // No \s* before the lookahead: a greedy \s* can match zero-width and backtrack, which defeats a
// negative lookahead entirely and makes the assertion pass on unscaled rates.
        java.util.regex.Matcher unscaled = java.util.regex.Pattern
            .compile("maxAimStep\\(\\)(?!\\s*\\*\\s*0\\.)").matcher(body);

        assertFalse(unscaled.find(),
            "the camera must never use the server rotation's raw rate; every occurrence of "
                + "maxAimStep() in easeCameraOnto has to be scaled down, otherwise the view locks "
                + "onto the placement instead of leading into it");
    }

    @Test
    void theCameraTracksTheSameFaceAimAsTheServerRotation() throws IOException {
        String source = scaffoldSource();

        assertFalse(source.contains("setYRot(this.smooth("),
            "the old constant-rate camera sweep is gone; it moved by an unquantised fixed number of "
                + "degrees per tick for as long as it lasted");

        int call = source.indexOf("this.easeCameraOnto(this.faceAimAt(pos))");
        assertTrue(call > 0, "camera must aim at the face, not the block centre");
    }
}