package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.AimEase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the humanised aim curve scaffold drives its camera with.
 *
 * <p>The previous implementation clamped the remaining distance to a fixed per-tick maximum,
 * which yields a constant angular velocity followed by a hard stop - not something a hand
 * produces.</p>
 */
public class AimEaseTest {
    private static final float MAX_STEP = 7.0F;
    private static final float CAPTURE = 6.0F;

    /** Runs a full slew and returns the per-tick steps taken. */
    private static List<Float> slew(float start, float target, float noise) {
        List<Float> steps = new ArrayList<>();
        float position = start;
        float velocity = 0.0F;

        for (int tick = 0; tick < 200; tick++) {
            // The caller wraps the delta before handing it over, exactly as Scaffold does.
            float remaining = wrap(target - position);
            float step = AimEase.step(remaining, MAX_STEP, velocity, CAPTURE, noise);

            if (step == 0.0F) break;
            position += step;
            velocity = AimEase.nextVelocity(remaining, MAX_STEP, velocity, CAPTURE, 0.0F);
            steps.add(step);
        }

        return steps;
    }

    /** Mirrors Scaffold's angle wrapping: the short way round, always within +/-180. */
    private static float wrap(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) angle -= 360.0F;
        if (angle < -180.0F) angle += 360.0F;
        return angle;
    }

    @Test
    void easesInRatherThanStartingAtFullSpeed() {
        List<Float> steps = slew(0.0F, 90.0F, 0.0F);

        assertTrue(steps.size() > 2, "a 90 degree sweep should take several ticks");
        assertTrue(steps.get(0) < steps.get(1),
            "the first tick must be slower than the second, got " + steps.get(0) + " then " + steps.get(1));
    }

    @Test
    void deceleratesIntoTheTarget() {
        List<Float> steps = slew(0.0F, 90.0F, 0.0F);

        float first = Math.abs(steps.get(0));
        float peak = 0.0F;
        float last = Math.abs(steps.get(steps.size() - 1));

        for (float step : steps) {
            peak = Math.max(peak, Math.abs(step));
        }

        assertTrue(peak > first, "should accelerate past its starting speed, peak " + peak + " first " + first);
        assertTrue(last < peak, "should slow into the target, last " + last + " peak " + peak);
    }

    @Test
    void neverExceedsThePerTickCeilingByALot() {
        for (float step : slew(0.0F, 90.0F, 0.0F)) {
            assertTrue(Math.abs(step) <= MAX_STEP,
                "step " + step + " exceeded the ceiling " + MAX_STEP);
        }
    }

    @Test
    void reachesTheTargetAndStops() {
        List<Float> steps = slew(0.0F, 90.0F, 0.0F);
        assertFalse(steps.isEmpty(), "the slew never moved");

        float total = 0.0F;
        for (float step : steps) {
            total += step;
        }

        assertEquals(90.0F, total, 0.01, "the sweep must land on the target, landed at " + total);
    }

    @Test
    void convergesFromBothDirections() {
        float up = 0.0F;
        for (float step : slew(-80.0F, 20.0F, 0.0F)) up += step;
        assertEquals(100.0F, up, 0.01);

        float down = 0.0F;
        for (float step : slew(80.0F, -20.0F, 0.0F)) down += step;
        assertEquals(-100.0F, down, 0.01);
    }

    @Test
    void handlesAWrapAroundTurn() {
        // 350 to 10 is a 20 degree turn the short way, not 340 the long way.
        float travelled = 0.0F;
        for (float step : slew(350.0F, 10.0F, 0.0F)) {
            travelled += step;
        }

        assertEquals(20.0F, travelled, 0.01, "wrap-around turns must take the short way");
    }

    @Test
    void neverOvershootsTheTarget() {
        for (float target = -120.0F; target <= 120.0F; target += 7.0F) {
            float position = 0.0F;

            for (float step : slew(0.0F, target, 1.0F)) {
                position += step;

                if (target > 0) {
                    assertTrue(position <= target + 0.01,
                        "overshot target " + target + " at " + position);
                } else {
                    assertTrue(position >= target - 0.01,
                        "overshot target " + target + " at " + position);
                }
            }
        }
    }

    @Test
    void noiseNeverReversesTheStep() {
        // Swept directly rather than through the slew loop: the loop stops on a zero step, so a
        // strong negative noise would stall it and assert nothing. Positive noise alone cannot
        // flip the sign either, so the whole range has to be covered for this to mean anything.
        for (float noise = -60.0F; noise <= 60.0F; noise += 0.25F) {
            assertTrue(AimEase.step(40.0F, MAX_STEP, 0.0F, CAPTURE, noise) >= 0.0F,
                "positive target reversed at noise " + noise);
            assertTrue(AimEase.step(-40.0F, MAX_STEP, 0.0F, CAPTURE, noise) <= 0.0F,
                "negative target reversed at noise " + noise);
        }
    }

    @Test
    void noiseNeverReversesTheSweep() {
        // At a realistic noise level the sweep still makes forward progress every tick.
        for (float step : slew(0.0F, 80.0F, 1.0F)) {
            assertTrue(step > 0.0F, "step " + step + " moved away from the target");
        }

        for (float step : slew(80.0F, 0.0F, 1.0F)) {
            assertTrue(step < 0.0F, "step " + step + " moved away from the target");
        }
    }

    @Test
    void noiseVariesTheStepsButNotTheDestination() {
        List<Float> smooth = slew(0.0F, 60.0F, 0.0F);
        List<Float> noisy = slew(0.0F, 60.0F, 1.0F);

        assertFalse(smooth.equals(noisy), "noise must actually change the per-tick steps");

        float smoothTotal = 0.0F;
        for (float step : smooth) smoothTotal += step;

        float noisyTotal = 0.0F;
        for (float step : noisy) noisyTotal += step;

        assertEquals(60.0F, noisyTotal, 0.01, "noise must not change where the sweep lands");
    }

    @Test
    void withinCaptureSnapsExactly() {
        // Well short of the per-tick ceiling and most of the way through the curve. Easing
        // would return only a fraction of these, so exact equality proves the target is snapped
        // rather than approached. A small remaining value would not distinguish the two, because
        // the magnitude clamp would coincidentally return the same number.
        assertEquals(5.0F, AimEase.step(5.0F, MAX_STEP, 0.0F, CAPTURE, 0.0F), 1e-6);
        assertEquals(6.0F, AimEase.step(6.0F, MAX_STEP, 0.0F, CAPTURE, 0.0F), 1e-6);
        assertEquals(-4.0F, AimEase.step(-4.0F, MAX_STEP, 0.0F, CAPTURE, 0.0F), 1e-6);
        assertEquals(0.0F, AimEase.step(0.0F, MAX_STEP, 5.0F, CAPTURE, 0.0F), 1e-6);
    }

    @Test
    void captureSnapHoldsEvenAgainstLargeNoise() {
        // Noise must not be able to push the view past a target it has already reached.
        assertEquals(5.0F, AimEase.step(5.0F, MAX_STEP, 7.0F, CAPTURE, 50.0F), 1e-6);
    }

    @Test
    void velocityIsCarriedSoAccelerationIsPossible() {
        float v0 = 0.0F;
        float v1 = AimEase.nextVelocity(90.0F, MAX_STEP, v0, CAPTURE, 0.0F);
        float v2 = AimEase.nextVelocity(88.0F, MAX_STEP, v1, CAPTURE, 0.0F);

        assertTrue(v1 > v0, "velocity must build across ticks, " + v0 + " -> " + v1);
        assertTrue(v2 > v1, "velocity must keep building, " + v1 + " -> " + v2);
    }

    @Test
    void aSweepToTheLeftRunsAsFastAsOneToTheRight() {
        // The bug this guards: nextVelocity returned the signed step while step() consumed a
        // magnitude, so a leftward velocity was compared against a positive target and the ramp
        // collapsed toward zero. Sweeping left crawled while sweeping right moved normally.
        List<Float> right = slew(0.0F, 80.0F, 0.0F);
        List<Float> left = slew(0.0F, -80.0F, 0.0F);

        assertEquals(right.size(), left.size(),
            "a mirror-image sweep must take the same number of ticks, took "
                + right.size() + " right vs " + left.size() + " left");
        assertEquals(sum(right), Math.abs(sum(left)), 1e-3,
            "mirror sweeps must cover the same distance");
    }

    @Test
    void velocityNeverExceedsTheCeiling() {
        // A magnitude carried across ticks must not be able to compound past maxStep.
        float velocity = 0.0F;
        for (int tick = 0; tick < 30; tick++) {
            velocity = AimEase.nextVelocity(90.0F, MAX_STEP, velocity, CAPTURE, 0.0F);
            assertTrue(velocity <= MAX_STEP + 1e-4,
                "carried velocity " + velocity + " exceeded the ceiling " + MAX_STEP);
        }
    }

    private static float sum(List<Float> values) {
        float total = 0.0F;
        for (float value : values) total += value;
        return total;
    }
}
