package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.RotationGcd;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the rotation grid that scaffold, aimbot and kill-aura all depend on.
 *
 * <p>A real mouse reports integer counts, so every genuine rotation delta is a multiple of the
 * sensitivity GCD. Scaffold wrote straight to setYRot with an arbitrary float, which produced
 * deltas no human could generate.</p>
 */
public class RotationGcdTest {
    @Test
    void factorMatchesTheVanillaFormula() {
        // (sens * 0.6 + 0.2)^3 * 1.2
        assertEquals(1.2 * 0.2 * 0.2 * 0.2, RotationGcd.factor(0.0), 1e-9);
        assertEquals(Math.pow(0.5 * 0.6 + 0.2, 3) * 1.2, RotationGcd.factor(0.5), 1e-9);
        assertEquals(Math.pow(1.0 * 0.6 + 0.2, 3) * 1.2, RotationGcd.factor(1.0), 1e-9);
    }

    @Test
    void quantizeLandsExactlyOnTheGrid() {
        double sens = 0.5;
        double gcd = RotationGcd.factor(sens);

        for (float delta = -40.0F; delta <= 40.0F; delta += 0.37F) {
            float quantised = RotationGcd.quantize(sens, delta);
            float steps = quantised / (float)gcd;

            assertEquals(Math.rint(steps), steps, 1e-3,
                "quantised delta " + quantised + " is not a whole number of GCD steps");
        }
    }

    @Test
    void quantizeRoundsRatherThanTruncating() {
        double sens = 0.5;
        double gcd = RotationGcd.factor(sens);

        // Just over half a step must round up. Truncating would drop it to zero, biasing every
        // step downwards; a real mouse count maps to the nearest representable angle.
        float justOver = (float)(gcd * 0.55);
        assertEquals(gcd, RotationGcd.quantize(sens, justOver), 1e-4,
            "a delta just over half a step should round up");

        // And just under must round down, pinning the half-up boundary from the other side.
        float justUnder = (float)(gcd * 0.45);
        assertEquals(0.0F, RotationGcd.quantize(sens, justUnder), 1e-4,
            "a delta just under half a step should round down");
    }

    @Test
    void quantizeIsIdentityForExactMultiples() {
        double sens = 0.5;
        double gcd = RotationGcd.factor(sens);

        for (int i = -20; i <= 20; i++) {
            float delta = (float)(gcd * i);
            assertEquals(delta, RotationGcd.quantize(sens, delta), 1e-4,
                "an exact multiple must survive quantisation unchanged");
        }
    }

    @Test
    void quantizeIsZeroForZeroDelta() {
        assertEquals(0.0F, RotationGcd.quantize(0.5, 0.0F), 1e-6);
    }

    @Test
    void degenerateSensitivityLeavesTheDeltaAlone() {
        // Non-finite or negative settings are nonsense and must not silently zero movement.
        assertEquals(7.0F, RotationGcd.quantize(-1.0, 7.0F), 1e-6);
        assertEquals(7.0F, RotationGcd.quantize(Double.NaN, 7.0F), 1e-6);
        assertEquals(7.0F, RotationGcd.quantize(Double.POSITIVE_INFINITY, 7.0F), 1e-6);
    }

    @Test
    void zeroSensitivityStillHasARealGrid() {
        // 0% is a legal sensitivity in vanilla, and the mouse still moves the crosshair in
        // 0.0096 degree steps. Treating it as degenerate would leave rotation unquantised.
        double gcd = RotationGcd.factor(0.0);
        assertEquals(0.2 * 0.2 * 0.2 * 1.2, gcd, 1e-9);

        float quantised = RotationGcd.quantize(0.0, 7.0F);
        assertEquals(Math.rint(quantised / (float)gcd), quantised / (float)gcd, 1e-3,
            "a 0% sensitivity must still quantise onto its grid");
    }

    @Test
    void quantizeNeverMovesFurtherThanHalfAStep() {
        double sens = 0.5;
        double gcd = RotationGcd.factor(sens);

        for (float delta = -90.0F; delta <= 90.0F; delta += 0.11F) {
            float quantised = RotationGcd.quantize(sens, delta);
            assertTrue(Math.abs(quantised - delta) <= gcd / 2 + 1e-4,
                "quantisation moved delta " + delta + " to " + quantised + ", further than half a step");
        }
    }

    @Test
    void quantizeFromSnapsRelativeToTheRotationTheServerHolds() {
        double sens = 0.5;
        double gcd = RotationGcd.factor(sens);

        // The server's held rotation is whatever the last packet carried, and is rarely a grid
        // multiple itself. What has to be legal is the step from it - so the sent rotation is
        // checked as a whole number of steps above the anchor, not as an absolute angle.
        float anchor = 73.417F;
        for (float target = -40.0F; target <= 90.0F; target += 0.41F) {
            float sent = RotationGcd.quantizeFrom(sens, anchor, target);
            float steps = (sent - anchor) / (float) gcd;

            assertEquals(Math.rint(steps), steps, 1e-3,
                "sent rotation " + sent + " is not a whole number of GCD steps above the anchor");
        }
    }

    @Test
    void quantizeFromNeverRoundsARequestedMovementAway() {
        double sens = 0.5;
        float anchor = 10.0F;
        float gcd = (float) RotationGcd.factor(sens);

        // The scaffold's wobble is smaller than one grid step at most sensitivities. Rounded
        // naively it would vanish and the rotation would repeat exactly - the fingerprint the
        // wobble exists to avoid - so a real but sub-grid step becomes one whole grid step.
        assertEquals(anchor + gcd, RotationGcd.quantizeFrom(sens, anchor, anchor + 0.05F), 1e-4,
            "a sub-grid nudge upwards must become +1 grid step");
        assertEquals(anchor - gcd, RotationGcd.quantizeFrom(sens, anchor, anchor - 0.05F), 1e-4,
            "a sub-grid nudge downwards must become -1 grid step");

        // But a genuinely absent movement invents nothing.
        assertEquals(anchor, RotationGcd.quantizeFrom(sens, anchor, anchor), 1e-6,
            "a zero delta must stay zero");
    }

    @Test
    void quantizeFromKeepsTwoHundredTicksOfHoldOnGridAndNearTheAim() {
        // The wire is re-anchored on the server's rotation every tick, so a held aim must neither
        // drift off the grid nor wander from where the wobble put it: this replays the scaffold's
        // hold shape - fixed aim, sub-grid sine wobble, each tick measured from the last sent
        // value - and checks both properties on every tick.
        double sens = 0.5;
        double gcd = RotationGcd.factor(sens);
        float aim = 60.0F;
        float server = aim;

        for (int tick = 0; tick < 200; tick++) {
            float wobble = 0.08F * (float) Math.sin(tick * 0.7D);
            float sent = RotationGcd.quantizeFrom(sens, server, aim + wobble);

            float steps = (sent - server) / (float) gcd;
            assertEquals(Math.rint(steps), steps, 1e-3,
                "tick " + tick + " sent a step that is not a whole number of GCD steps");

            assertTrue(Math.abs(sent - aim) <= 1.5F * gcd + 1e-4,
                "tick " + tick + " sent " + sent + ", too far from the aim " + aim);

            server = sent;
        }
    }

    @Test
    void quantizeFromLeavesTheTargetAloneForDegenerateSensitivity() {
        // Same contract as quantize: nonsense settings must not silently move the rotation.
        assertEquals(73.5F, RotationGcd.quantizeFrom(-1.0, 10.0F, 73.5F), 1e-6);
        assertEquals(73.5F, RotationGcd.quantizeFrom(Double.NaN, 10.0F, 73.5F), 1e-6);
        assertEquals(73.5F, RotationGcd.quantizeFrom(Double.POSITIVE_INFINITY, 10.0F, 73.5F), 1e-6);
    }
}
