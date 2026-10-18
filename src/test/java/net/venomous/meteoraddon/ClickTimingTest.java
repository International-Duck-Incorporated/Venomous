package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.ClickTiming;
import net.venomous.meteoraddon.utils.ClickTiming.Technique;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the click timing profiles behind middle-click-action.
 *
 * <p>Those settings were dead until this was extracted: the module fired on the middle-click
 * down-edge immediately and never read technique, min-cps or max-cps, so the advertised
 * "humanlike timing" did not exist.</p>
 */
public class ClickTimingTest {
    private static final int SPIN = 20;

    @Test
    void stabilizedIsMetronomic() {
        int first = ClickTiming.reactionDelayTicks(Technique.Stabilized, 6, 8, new Random(1), true);
        for (int i = 0; i < SPIN; i++) {
            assertEquals(first,
                ClickTiming.reactionDelayTicks(Technique.Stabilized, 6, 8, new Random(i), false),
                "Stabilized must not vary between calls");
        }

        // 6..8 CPS has midpoint 7, and a 20 tick cycle at 7 CPS is ~2.86 -> 3 ticks.
        assertEquals(3, first);
    }

    @Test
    void normalDistributionStaysInsideTheWindow() {
        // The widest case the settings allow: 1..20 CPS.
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int i = 0; i < 500; i++) {
            int ticks = ClickTiming.reactionDelayTicks(Technique.NormalDistribution, 1, 20, new Random(i), false);
            assertTrue(ticks >= 1, "delay must never be zero or negative, got " + ticks);
            min = Math.min(min, ticks);
            max = Math.max(max, ticks);
        }

        // 1 CPS is the slowest a clamp can produce (20 ticks); 20 CPS the fastest (1 tick).
        // Sampling inside the window means it must actually reach both ends, otherwise the
        // Gaussian is being clipped away and the technique is not really in use.
        assertEquals(20, max, "NormalDistribution must be able to reach the slow end of the window");
        assertEquals(1, min, "NormalDistribution must be able to reach the fast end of the window");
    }

    @Test
    void normalDistributionActuallyVaries() {
        Set<Integer> observed = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            observed.add(ClickTiming.reactionDelayTicks(Technique.NormalDistribution, 4, 16, new Random(i), false));
        }

        assertTrue(observed.size() > 1,
            "NormalDistribution produced a single constant delay " + observed + " - it is not varying");
    }

    @Test
    void butterflyRunsAtTheTopOfTheWindow() {
        // Butterfly should never be slower than the midpoint profile it sits above.
        int butterfly = ClickTiming.reactionDelayTicks(Technique.Butterfly, 6, 8, new Random(1), true);
        int stabilized = ClickTiming.reactionDelayTicks(Technique.Stabilized, 6, 8, new Random(1), true);
        assertTrue(butterfly <= stabilized,
            "Butterfly (" + butterfly + ") should be at least as fast as Stabilized (" + stabilized + ")");

        int even = ClickTiming.reactionDelayTicks(Technique.Butterfly, 10, 20, new Random(1), true);
        int odd = ClickTiming.reactionDelayTicks(Technique.Butterfly, 10, 20, new Random(1), false);
        assertEquals(1, Math.abs(even - odd), "Butterfly must alternate by exactly one tick");
    }

    @Test
    void degenerateSettingsCannotProduceZeroOrNegative() {
        // Zero, negative and over-20 values must all still yield a usable delay.
        assertTrue(ClickTiming.reactionDelayTicks(Technique.Stabilized, 0, 0, new Random(1), true) >= 1);
        assertTrue(ClickTiming.reactionDelayTicks(Technique.NormalDistribution, -5, -10, new Random(1), true) >= 1);
        assertTrue(ClickTiming.reactionDelayTicks(Technique.Butterfly, -5, -10, new Random(1), true) >= 1);

        // The settings cap at 20 CPS, but this is a public helper and a higher window must not
        // round 20/cps down to a zero-tick delay.
        assertTrue(ClickTiming.reactionDelayTicks(Technique.Stabilized, 50, 50, new Random(1), true) >= 1,
            "50 CPS must still delay at least one tick");
        assertTrue(ClickTiming.reactionDelayTicks(Technique.Stabilized, 100, 100, new Random(1), true) >= 1,
            "100 CPS must still delay at least one tick");
    }

    @Test
    void invertedWindowIsCollapsedToTheLowerBound() {
        // An inverted window (min > max) must collapse onto min, not produce a midpoint
        // between two values that are the wrong way round. 5..3 collapses to 5..5, which is a
        // 4 tick cycle; averaging the inverted pair would give 4 CPS and a 5 tick delay.
        int inverted = ClickTiming.reactionDelayTicks(Technique.Stabilized, 5, 3, new Random(1), true);
        int collapsed = ClickTiming.reactionDelayTicks(Technique.Stabilized, 5, 5, new Random(1), true);
        assertEquals(collapsed, inverted,
            "An inverted window must behave as if max were raised to min");
    }

    @Test
    void higherCpsMeansShorterDelay() {
        int slow = ClickTiming.reactionDelayTicks(Technique.Stabilized, 2, 2, new Random(1), true);
        int fast = ClickTiming.reactionDelayTicks(Technique.Stabilized, 15, 15, new Random(1), true);
        assertTrue(fast < slow, "15 CPS (" + fast + ") must be faster than 2 CPS (" + slow + ")");
    }
}
