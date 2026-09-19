package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.MovementMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MovementMathTest {

    private static double length(double[] v) {
        return Math.sqrt(v[0] * v[0] + v[1] * v[1]);
    }

    @Test
    void noInputProducesNoMotion() {
        double[] v = MovementMath.velocity(37.0, 0.0, 0.0, 0.5);
        assertEquals(0.0, v[0]);
        assertEquals(0.0, v[1]);
    }

    @Test
    void diagonalIsNotFasterThanStraight() {
        double speed = 0.2873;
        double straight = length(MovementMath.velocity(0.0, 1.0, 0.0, speed));
        double diagonal = length(MovementMath.velocity(0.0, 1.0, 1.0, speed));

        assertEquals(speed, straight, 1e-9);
        assertEquals(speed, diagonal, 1e-9, "Diagonal movement must be normalised, otherwise speed checks flag");
    }

    @Test
    void forwardAtZeroYawMovesTowardsPositiveZ() {
        double[] v = MovementMath.velocity(0.0, 1.0, 0.0, 1.0);
        assertEquals(0.0, v[0], 1e-9);
        assertEquals(1.0, v[1], 1e-9);
    }

    @Test
    void forwardAtNinetyYawMovesTowardsNegativeX() {
        double[] v = MovementMath.velocity(90.0, 1.0, 0.0, 1.0);
        assertEquals(-1.0, v[0], 1e-9);
        assertEquals(0.0, v[1], 1e-9);
    }

    @Test
    void strafeIsPerpendicularToForward() {
        double[] fwd = MovementMath.velocity(0.0, 1.0, 0.0, 1.0);
        double[] left = MovementMath.velocity(0.0, 0.0, 1.0, 1.0);
        double dot = fwd[0] * left[0] + fwd[1] * left[1];
        assertEquals(0.0, dot, 1e-9);
    }

    @Test
    void baseSpeedAppliesPotionEffects() {
        assertEquals(MovementMath.SPRINT_SPEED, MovementMath.baseSpeed(true, -1, -1), 1e-9);
        assertEquals(MovementMath.WALK_SPEED, MovementMath.baseSpeed(false, -1, -1), 1e-9);

        // Speed II -> +40%
        assertEquals(MovementMath.SPRINT_SPEED * 1.4, MovementMath.baseSpeed(true, 1, -1), 1e-9);
        // Slowness I -> -15%
        assertEquals(MovementMath.SPRINT_SPEED * 0.85, MovementMath.baseSpeed(true, -1, 0), 1e-9);
        // Never negative, no matter how strong the slowness
        assertTrue(MovementMath.baseSpeed(true, -1, 20) >= 0.0);
    }

    @Test
    void verticalMotionPrefersExplicitInput() {
        assertEquals(0.2, MovementMath.verticalMotion(3, 6, -0.08, true, false, 0.2), 1e-9);
        assertEquals(-0.2, MovementMath.verticalMotion(3, 6, -0.08, false, true, 0.2), 1e-9);
    }

    @Test
    void verticalMotionPulsesDownwardsPeriodically() {
        int period = 6;
        int pulses = 0;
        for (int tick = 1; tick <= 60; tick++) {
            double dy = MovementMath.verticalMotion(tick, period, -0.08, false, false, 0.2);
            assertTrue(dy <= 0.0, "Idle hover must never drift upwards");
            if (dy < 0.0) {
                pulses++;
            }
        }

        assertEquals(10, pulses, "Hover must emit a gravity pulse every period ticks");
    }

    @Test
    void verticalMotionWithoutPeriodIsFlat() {
        assertEquals(0.0, MovementMath.verticalMotion(7, 0, -0.08, false, false, 0.2), 1e-9);
    }

    @Test
    void clampBoundsValues() {
        assertEquals(1.0, MovementMath.clamp(5.0, 0.0, 1.0), 1e-9);
        assertEquals(0.0, MovementMath.clamp(-5.0, 0.0, 1.0), 1e-9);
        assertEquals(0.5, MovementMath.clamp(0.5, 0.0, 1.0), 1e-9);
    }
}
