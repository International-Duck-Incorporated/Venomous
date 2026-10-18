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
            ScaffoldCadence.DEFAULT_PLACE_DELAY,
            ScaffoldCadence.CADENCE_JITTER_TICKS,
            ScaffoldCadence.DEFAULT_TICKS_PER_SECOND);

        assertTrue(mean >= ScaffoldCadence.ACADDITION_MIN_STRAIGHT_DELAY_MS,
            "default place-delay of " + ScaffoldCadence.DEFAULT_PLACE_DELAY + " averages " + mean
                + "ms, under the " + ScaffoldCadence.ACADDITION_MIN_STRAIGHT_DELAY_MS
                + "ms AntiCheat-Addition expects between straight-bridge placements");
    }

    /**
     * The old value is kept as an explicit assertion rather than left implicit, so the regression
     * is documented where it was fixed and cannot quietly return.
     */
    @Test
    void theOldDefaultIsBelowTheMinimumAndWouldFlag() {
        double mean = ScaffoldCadence.meanIntervalMillis(
            3, ScaffoldCadence.CADENCE_JITTER_TICKS, ScaffoldCadence.DEFAULT_TICKS_PER_SECOND);

        assertTrue(mean < ScaffoldCadence.ACADDITION_MIN_STRAIGHT_DELAY_MS,
            "place-delay 3 is expected to be too fast and to flag; if this now passes the "
                + "anticheat's minimum has changed and the default should be re-derived");
    }

    @Test
    void jitterOnlyEverLengthensTheGap() {
        for (int roll = 0; roll <= ScaffoldCadence.CADENCE_JITTER_TICKS; roll++) {
            assertEquals(ScaffoldCadence.DEFAULT_PLACE_DELAY + roll,
                ScaffoldCadence.cooldownTicks(ScaffoldCadence.DEFAULT_PLACE_DELAY, roll,
                    ScaffoldCadence.CADENCE_JITTER_TICKS),
                "roll " + roll + " should sit at or above the base delay");
        }
    }

    @Test
    void anOutOfRangeRollIsClampedRatherThanTrusted() {
        assertEquals(ScaffoldCadence.DEFAULT_PLACE_DELAY + ScaffoldCadence.CADENCE_JITTER_TICKS,
            ScaffoldCadence.cooldownTicks(ScaffoldCadence.DEFAULT_PLACE_DELAY, 99,
                ScaffoldCadence.CADENCE_JITTER_TICKS));
        assertEquals(ScaffoldCadence.DEFAULT_PLACE_DELAY,
            ScaffoldCadence.cooldownTicks(ScaffoldCadence.DEFAULT_PLACE_DELAY, -5,
                ScaffoldCadence.CADENCE_JITTER_TICKS));
    }

    @Test
    void aPlaceDelayBelowOneIsLiftedToOneRatherThanFreezingTheModule() {
        // The base is what gets lifted; jitter still adds on top of it.
        assertEquals(1, ScaffoldCadence.cooldownTicks(0, 0, 2));
        assertEquals(1, ScaffoldCadence.cooldownTicks(-4, 0, 2));
        assertEquals(3, ScaffoldCadence.cooldownTicks(-4, 2, 2));
    }

    @Test
    void millisecondsScaleWithTheServerTickRate() {
        assertEquals(50.0, ScaffoldCadence.ticksToMillis(1, 20.0), 1e-9);
        assertEquals(25.0, ScaffoldCadence.ticksToMillis(1, 40.0), 1e-9);
        assertEquals(100.0, ScaffoldCadence.ticksToMillis(2, 20.0), 1e-9);
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
