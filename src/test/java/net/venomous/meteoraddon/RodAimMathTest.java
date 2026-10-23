package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.EntityPredict;
import net.venomous.meteoraddon.utils.RodAimMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the rod aimbot's aiming math: the motion projection it leads targets with, and the
 * angle conventions it aims by. A sign error in either sends every cast behind the runner
 * instead of ahead of them.
 */
public class RodAimMathTest {
    // ------------------------------------------------------------------ prediction

    @Test
    void zeroTicksReturnsThePositionUnchanged() {
        double[] out = EntityPredict.predict(10.0, 64.0, -3.0, 0.3, 0.1, -0.2, false, 0);
        assertArrayEquals(new double[] {10.0, 64.0, -3.0}, out, 1e-9);
    }

    @Test
    void anAirborneEntityFallsWithGravityAndDrag() {
        // First airborne tick from rest: y advances by vy (0), then vy = (0 - 0.08) * 0.98.
        double[] one = EntityPredict.predict(0.0, 64.0, 0.0, 0.0, 0.0, 0.0, false, 1);
        assertEquals(64.0, one[1], 1e-9, "the first tick from rest has no vertical travel yet");

        double[] two = EntityPredict.predict(0.0, 64.0, 0.0, 0.0, 0.0, 0.0, false, 2);
        assertEquals(64.0 - 0.0784, two[1], 1e-9,
            "the second tick travels by the first tick's velocity: (0 - 0.08) * 0.98");
    }

    @Test
    void horizontalVelocityCarriesOnTheAirDrag() {
        double[] out = EntityPredict.predict(0.0, 64.0, 0.0, 1.0, 0.0, 0.0, false, 2);
        // Tick 1 moves the full 1.0; tick 2 moves 1.0 * 0.91.
        assertEquals(1.0 + 0.91, out[0], 1e-9);
    }

    @Test
    void aGroundedEntityIsFlattenedByGroundFriction() {
        double[] out = EntityPredict.predict(0.0, 64.0, 0.0, 1.0, -0.5, 0.0, true, 3);
        // Grounded: no vertical travel, horizontal friction is 0.546 per tick.
        assertEquals(64.0, out[1], 1e-9, "a grounded entity does not sink");
        assertEquals(1.0 + 0.546 + 0.546 * 0.546, out[0], 1e-9);
    }

    @Test
    void theProjectionIsMonotonicInTicks() {
        double[] one = EntityPredict.predict(0.0, 64.0, 0.0, 0.3, 0.0, 0.4, false, 1);
        double[] three = EntityPredict.predict(0.0, 64.0, 0.0, 0.3, 0.0, 0.4, false, 3);

        assertTrue(Math.abs(three[0]) >= Math.abs(one[0]) - 1e-9,
            "a longer projection must lead at least as far horizontally");
        assertTrue(three[1] <= one[1] + 1e-9,
            "a longer airborne projection must not end higher than a shorter one");
    }

    // ------------------------------------------------------------------ angles

    @Test
    void yawZeroFacesPositiveZAndGrowsTowardsNegativeX() {
        assertEquals(0.0, RodAimMath.yawTo(0.0, 5.0), 1e-9, "a target due +Z is yaw 0");
        assertEquals(90.0, RodAimMath.yawTo(-5.0, 0.0), 1e-9, "a target due -X is yaw 90");
        assertEquals(-90.0, RodAimMath.yawTo(5.0, 0.0), 1e-9, "a target due +X is yaw -90");
        assertEquals(180.0, Math.abs(RodAimMath.yawTo(0.0, -5.0)), 1e-9, "a target due -Z is yaw 180");
    }

    @Test
    void pitchIsPositiveLookingDown() {
        double down = RodAimMath.pitchTo(0.0, 64.0, 0.0, 0.0, 62.0, 0.0);
        assertEquals(90.0, down, 1e-6, "a target two blocks below is a straight-down look");

        double up = RodAimMath.pitchTo(0.0, 64.0, 0.0, 0.0, 66.0, 0.0);
        assertEquals(-90.0, up, 1e-6, "a target two blocks above is a straight-up look");

        double level = RodAimMath.pitchTo(0.0, 64.0, 0.0, 0.0, 64.0, 5.0);
        assertEquals(0.0, level, 1e-9, "a level target is pitch 0");
    }

    @Test
    void fovTreatsTheSliderAsTheWholeCone() {
        // 180 degree cone: 80 degrees off the camera is inside (half cone 90), 100 is outside.
        assertTrue(RodAimMath.withinFov(0.0F, 80.0F, 180.0));
        assertFalse(RodAimMath.withinFov(0.0F, 100.0F, 180.0));

        // 360 sees everything, including behind.
        assertTrue(RodAimMath.withinFov(0.0F, 179.0F, 360.0));
    }

    @Test
    void fovComparesThroughTheWrap() {
        // Camera at 179, target at -179: 2 degrees apart through the wrap, not 358.
        assertTrue(RodAimMath.withinFov(179.0F, -179.0F, 20.0),
            "the cone must measure the short way around the circle");
    }
}
