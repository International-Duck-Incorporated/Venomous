package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.VelocityGate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VelocityGateTest {
    private static boolean reduce(boolean inWater, boolean notFacing) {
        return VelocityGate.shouldReduce(true, inWater, notFacing, true, true);
    }

    @Test
    void reducesUnconditionallyWhenNoContextIsConfigured() {
        assertTrue(VelocityGate.shouldReduce(true, false, false, false, false),
            "with both gates off this must reduce every packet, preserving existing behaviour");
        assertFalse(VelocityGate.shouldReduce(false, false, false, false, false),
            "but a failed chance roll still blocks it - that is the one thing always on");
    }

    @Test
    void leavesKnockbackAloneInWater() {
        assertFalse(reduce(true, false),
            "velocity.water_check: a person in water takes the full knockback");
        assertTrue(reduce(false, false));
    }

    @Test
    void leavesKnockbackAloneWhenNotFacingTheOpponent() {
        assertFalse(reduce(false, true),
            "velocity.only_when_targeting: reduced only while face to face");
        assertTrue(reduce(false, false));
    }

    /**
     * The failure mode that would be easy to ship: a gate that fires when the check is
     * <em>off</em>. Both gates are opt-in, so off must mean off.
     */
    @Test
    void bothGatesAreInertWhileDisabled() {
        assertTrue(VelocityGate.shouldReduce(true, true, true, false, false),
            "in water and not facing anyone, but with both gates off the packet must be reduced");
    }

    @Test
    void gatesComposeIndependently() {
        assertFalse(VelocityGate.shouldReduce(true, true, false, true, false), "water gate alone");
        assertFalse(VelocityGate.shouldReduce(true, false, true, false, true), "targeting gate alone");
        assertFalse(VelocityGate.shouldReduce(true, true, true, true, true), "both together");
        assertTrue(VelocityGate.shouldReduce(true, false, false, true, true), "neither violated");
    }

    @Test
    void chanceBoundariesBehaveLikeAPercentage() {
        assertTrue(VelocityGate.rollPassed(100, 0));
        assertTrue(VelocityGate.rollPassed(100, 99), "100 percent always passes");
        assertFalse(VelocityGate.rollPassed(0, 0), "0 percent never passes");
        assertFalse(VelocityGate.rollPassed(0, 99));
        assertFalse(VelocityGate.rollPassed(-5, 0), "a negative percentage is nonsense and must not pass");
    }

    /**
     * Raising the percentage must never stop a roll that previously passed - otherwise the
     * "reduce percentage" control is not monotonic and the setting is not worth exposing.
     */
    @Test
    void chanceIsMonotonicInThePercentage() {
        for (int roll = 0; roll < 100; roll++) {
            boolean previouslyPassed = VelocityGate.rollPassed(0, roll);
            assertFalse(previouslyPassed, "0% never passes");

            for (int percent = 1; percent <= 100; percent++) {
                boolean passed = VelocityGate.rollPassed(percent, roll);
                assertEquals(roll < percent, passed,
                    "roll " + roll + " should " + (roll < percent ? "pass" : "fail")
                        + " at " + percent + "%");
            }
        }
    }

    @Test
    void chanceCoversExactlyItsShareOfRolls() {
        for (int percent = 0; percent <= 100; percent++) {
            int passed = 0;
            for (int roll = 0; roll < 100; roll++) {
                if (VelocityGate.rollPassed(percent, roll)) passed++;
            }
            assertEquals(percent, passed, percent + "% should pass exactly " + percent + " of 100 rolls");
        }
    }
}