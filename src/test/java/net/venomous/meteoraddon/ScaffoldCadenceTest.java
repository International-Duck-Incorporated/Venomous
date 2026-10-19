package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.ScaffoldCadence;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScaffoldCadenceTest {
    /**
     * The bug this guards: the cadence was counted in <em>ticks</em>, which silently assumes 20 TPS.
     *
     * <p>The anticheat measures the gap in wall-clock milliseconds, and so does a Minecraft jump.
     * A tick-based cooldown of five ticks is 250ms at 20 TPS - correct - but 1250ms at the 4 FPS
     * the client actually runs at, which is longer than a jump lasts. The live log caught the
     * consequence: the player jumped while bridging, the module could not place the block in time
     * because it was still inside the cooldown from the previous placement, and they fell off the
     * bridge.</p>
     *
     * <p>The gap is now a duration, so it cannot stretch with the frame rate.</p>
     */
    @Test
    void thePlacementGapDoesNotGrowWithTheFrameRate() {
        double gap = ScaffoldCadence.delayMillis(ScaffoldCadence.DEFAULT_PLACE_DELAY, 0);

        assertTrue(gap <= 300.0,
            "the gap the module enforces is " + gap + "ms; a tick-counted cooldown stretched with "
                + "the frame rate, and a jump is over before the cooldown expires");
    }

    /**
     * A jump is over in well under a second, so the module has to be able to place inside one.
     *
     * <p>This is the symptom the player reported: bridging was fine, and jumping while bridging
     * dropped them off the bridge.</p>
     */
    @Test
    void thePlacementGapIsShortEnoughToCatchAJump() {
        double gap = ScaffoldCadence.delayMillis(ScaffoldCadence.DEFAULT_PLACE_DELAY, 0);

        assertTrue(gap <= 400.0,
            "the gap is " + gap + "ms, which is longer than the airtime of a jump; the block "
                + "underneath is not placed before the player lands");
    }

    /**
     * The jitter has to be small enough to stay inside one tick at a low frame rate.
     *
     * <p>It used to be counted in ticks, so it moved in 50ms steps. On a 250ms tick a 100ms jitter
     * puts the deadline on the far side of the next tick boundary and the placement waits for the
     * tick after that - doubling the gap. At 4 FPS that is the difference between catching a jump
     * and falling off the bridge.</p>
     */
    @Test
    void theJitterIsSmallEnoughToFitInsideOneTickAtFourFramesPerSecond() {
        double tickAtFourFps = 1000.0 / 4.0;

        double worst = ScaffoldCadence.delayMillis(
            ScaffoldCadence.DEFAULT_PLACE_DELAY, ScaffoldCadence.CADENCE_JITTER_MS);

        assertTrue(worst <= tickAtFourFps,
            "the worst-case gap is " + worst + "ms against a " + tickAtFourFps
                + "ms tick at 4 FPS, so the placement is pushed a whole extra tick out");
    }

    /**
     * The bug this guards. {@code place-delay} defaulted to 3, giving
     * {@code 3 + random(0..2)} ticks - a 200ms mean. AntiCheat-Addition's
     * {@code ScaffoldAverageBatchProcessor} holds a straight bridge to a 238ms minimum and flags
     * anything faster, so the out-of-the-box configuration was detectable no matter how clean the
     * rotation was. Observed as {@code failed scaffold x15} then {@code x20}: decoding its
     * {@code 1.1 * (238 - actual) + 5} puts the measured average at ~229ms and ~224ms, which is
     * 200ms nominal plus scheduling overhead.
     */
    @Test
    void shippedDefaultClearsAntiCheatAdditionMinimum() {
        double mean = ScaffoldCadence.meanIntervalMillis(
            ScaffoldCadence.DEFAULT_PLACE_DELAY, ScaffoldCadence.CADENCE_JITTER_MS);

        assertTrue(mean >= ScaffoldCadence.ACADDITION_MIN_STRAIGHT_DELAY_MS,
            "default place-delay of " + ScaffoldCadence.DEFAULT_PLACE_DELAY + " averages " + mean
                + "ms, under the " + ScaffoldCadence.ACADDITION_MIN_STRAIGHT_DELAY_MS
                + "ms AntiCheat-Addition expects between straight-bridge placements");
    }

    /**
     * Every jitter roll has to clear the floor, not just the mean.
     */
    @Test
    void everyJitterRollStillClearsTheAntiCheatFloor() {
        for (int roll = 0; roll <= ScaffoldCadence.CADENCE_JITTER_MS; roll++) {
            double gap = ScaffoldCadence.delayMillis(ScaffoldCadence.DEFAULT_PLACE_DELAY, roll);

            assertTrue(gap >= ScaffoldCadence.ACADDITION_MIN_STRAIGHT_DELAY_MS,
                "roll " + roll + " gives a " + gap + "ms gap, under the "
                    + ScaffoldCadence.ACADDITION_MIN_STRAIGHT_DELAY_MS + "ms floor");
        }
    }

    /**
     * A setting below the floor is floored rather than obeyed.
     *
     * <p>The anticheat's floor is not a preference, so no configuration may ask for a gap it would
     * flag. The setting still means "ticks at 20 TPS" to the player; what it cannot mean is
     * "faster than the anticheat allows".</p>
     */
    @Test
    void aSettingBelowTheFloorIsRaisedToIt() {
        double gap = ScaffoldCadence.delayMillis(1, 0);

        assertTrue(gap >= ScaffoldCadence.ACADDITION_MIN_STRAIGHT_DELAY_MS,
            "place-delay 1 asks for 50ms, which the anticheat flags; the gap must be raised to the "
                + ScaffoldCadence.ACADDITION_MIN_STRAIGHT_DELAY_MS + "ms floor");
    }

    @Test
    void jitterOnlyEverLengthensTheGap() {
        double base = ScaffoldCadence.delayMillis(ScaffoldCadence.DEFAULT_PLACE_DELAY, 0);

        for (int roll = 0; roll <= ScaffoldCadence.CADENCE_JITTER_MS; roll++) {
            assertEquals(base + roll,
                ScaffoldCadence.delayMillis(ScaffoldCadence.DEFAULT_PLACE_DELAY, roll),
                "roll " + roll + " should sit at or above the base delay");
        }
    }

    @Test
    void anOutOfRangeRollIsClampedRatherThanTrusted() {
        double base = ScaffoldCadence.delayMillis(ScaffoldCadence.DEFAULT_PLACE_DELAY, 0);

        assertEquals(base + ScaffoldCadence.CADENCE_JITTER_MS,
            ScaffoldCadence.delayMillis(ScaffoldCadence.DEFAULT_PLACE_DELAY, 999));
        assertEquals(base,
            ScaffoldCadence.delayMillis(ScaffoldCadence.DEFAULT_PLACE_DELAY, -5));
    }

    @Test
    void aPlaceDelayBelowOneIsLiftedRatherThanFreezingTheModule() {
        assertTrue(ScaffoldCadence.delayMillis(0, 0) > 0.0,
            "place-delay 0 must not produce a zero gap, which would place every tick");
        assertTrue(ScaffoldCadence.delayMillis(-4, 0) > 0.0,
            "a negative place-delay must not produce a zero gap either");
    }

    /**
     * The setting and the guard have to move together.
     *
     * <p>The tests above all read {@link ScaffoldCadence#DEFAULT_PLACE_DELAY}, so hardcoding a
     * literal in the module instead - {@code .defaultValue(3)} - leaves the whole suite green while
     * putting the shipped bug straight back. Verified: that mutation survived every other test here.
     * So the module source is asserted to reference the constant, which is the same
     * source-scanning seam {@code DeadSettingTest} and {@code OrphanModuleTest} already use here.</p>
     */
    @Test
    void theModuleTakesItsPlaceDelayDefaultFromThisConstant() throws IOException {
        String source = Files.readString(locate(
            "src/main/java/net/venomous/meteoraddon/modules/world/Scaffold.java"));

        int name = source.indexOf(".name(\"place-delay\")");
        assertTrue(name >= 0, "Scaffold no longer registers a place-delay setting");

        // The defaultValue call belongs to the same builder as the setting name.
        int defaultValue = source.indexOf(".defaultValue(", name);
        int nextName = source.indexOf(".name(", name + 1);
        assertTrue(defaultValue > 0 && (nextName < 0 || defaultValue < nextName),
            "could not find the place-delay default next to its setting name");

        String line = source.substring(defaultValue, source.indexOf('\n', defaultValue));

        assertTrue(line.contains("ScaffoldCadence.DEFAULT_PLACE_DELAY"),
            "Scaffold's place-delay default is a literal (" + line.trim() + ") rather than "
                + "ScaffoldCadence.DEFAULT_PLACE_DELAY. Change one without the other and the "
                + "cadence guard above stops describing what actually ships.");
    }

    /**
     * The module has to gate on the duration, not on a tick counter.
     *
     * <p>This is the seam that actually reaches the bug: the arithmetic in {@link ScaffoldCadence}
     * could be perfect while the module still counted ticks, and the player would still fall off the
     * bridge. Source-scanned for that reason - see {@code DeadSettingTest} for the same approach.</p>
     */
    @Test
    void theModuleGatesOnTheDurationRatherThanATickCount() throws IOException {
        String source = Files.readString(locate(
            "src/main/java/net/venomous/meteoraddon/modules/world/Scaffold.java"));

        assertTrue(source.contains("ScaffoldCadence.delayMillis("),
            "the module must ask ScaffoldCadence for a millisecond gap");

        assertTrue(source.contains("nowMs()") && source.contains("nextPlaceAtMs"),
            "the placement gate must compare wall-clock time against a deadline");

        assertTrue(!source.contains("placeCooldown"),
            "a tick-counted cooldown is what made the gap stretch with the frame rate");

        assertTrue(!source.contains("repeatedAttempts %"),
            "the per-attempt modulo throttle is a second tick-shaped gate on the same path; the "
                + "duration gate replaces it");
    }

    /** Gradle can run tests from the project root or a subproject, so search upward. */
    private static Path locate(String relative) {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();

        while (dir != null) {
            Path candidate = dir.resolve(relative);
            if (Files.isRegularFile(candidate)) return candidate;

            dir = dir.getParent();
        }

        throw new AssertionError("could not find " + relative + " above " + System.getProperty("user.dir"));
    }
}