package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.JumpResetRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The jump-reset decision without a game: every gate, the waiver of every optional gate, the arc
 * geometry and the Minecraft velocity-yaw convention the arc consumes.
 */
public class JumpResetRulesTest {
    private static boolean trigger(boolean freshHit, boolean ignoreNext, boolean onGround,
                                   boolean burning, boolean badEffect, boolean inArc,
                                   boolean aim, boolean requireAim,
                                   boolean attackDown, boolean requireMouse,
                                   boolean forwardDown, boolean requireForward,
                                   boolean rollPassed) {
        return JumpResetRules.shouldJump(
            freshHit, ignoreNext, onGround, burning, badEffect, inArc,
            aim, requireAim,
            attackDown, requireMouse,
            forwardDown, requireForward,
            rollPassed);
    }

    /**
     * Brings the caller-side shape into the tests: the module itself evaluates and passes exactly
     * these arguments in this order.
     */
    private static boolean trigger() {
        return trigger(
            true,   // freshHit
            false,  // ignoreNext
            true,   // onGround
            false,  // burning
            false,  // badEffect
            true,   // inArc
            true,   // aim
            true,   // requireAim
            true,   // attackDown
            true,   // requireMouse
            true,   // forwardDown
            true,   // requireForward
            true    // rollPassed
        );
    }

    @Test
    void firesWhenEveryGateIsClear() {
        assertTrue(trigger());
    }

    @Test
    void needsAFreshHurtTickNotADrainingOne() {
        assertFalse(JumpResetRules.shouldJump(false, false, true, false, false, true, true, true, true, true, true, true, true));
    }

    @Test
    void theHurtTimeALandingDealsIsSkippedThroughTheLatch() {
        assertFalse(JumpResetRules.shouldJump(true, true, true, false, false, true, true, true, true, true, true, true, true));
    }

    @Test
    void aJumpCanOnlyStartFromTheGround() {
        assertFalse(JumpResetRules.shouldJump(true, false, false, false, false, true, true, true, true, true, true, true, true));
    }

    @Test
    void burningTakesOverTheHurtWindow() {
        assertFalse(JumpResetRules.shouldJump(true, false, true, true, false, true, true, true, true, true, true, true, true));
    }

    @Test
    void jumpBoostPoisonAndWitherAreNotReadAsHits() {
        assertFalse(JumpResetRules.shouldJump(true, false, true, false, true, true, true, true, true, true, true, true, true));
    }

    @Test
    void movementOutsideTheArcIsRejected() {
        assertFalse(JumpResetRules.shouldJump(true, false, true, false, false, false, true, true, true, true, true, true, true));
    }

    @Test
    void aimGateWaivesWhenNotRequiredAndHoldsWhenItIs() {
        assertTrue(trigger(true, false, true, false, false, true, false, false, true, true, true, true, true));
        assertTrue(trigger(true, false, true, false, false, true, true, true, true, true, true, true, true));
        assertFalse(trigger(true, false, true, false, false, true, false, true, true, true, true, true, true));
    }

    @Test
    void mouseGateWaivesWhenNotRequiredAndHoldsWhenItIs() {
        assertTrue(trigger(true, false, true, false, false, true, true, true, false, false, true, true, true));
        assertFalse(trigger(true, false, true, false, false, true, true, true, false, true, true, true, true));
        assertTrue(trigger(true, false, true, false, false, true, true, true, true, true, true, true, true));
    }

    @Test
    void forwardGateWaivesWhenNotRequiredAndHoldsWhenItIs() {
        assertTrue(trigger(true, false, true, false, false, true, true, true, true, true, false, false, true));
        assertFalse(trigger(true, false, true, false, false, true, true, true, true, true, false, true, true));
        assertTrue(trigger(true, false, true, false, false, true, true, true, true, true, true, true, true));
    }

    @Test
    void theChanceRollSettlesTheTrigger() {
        assertFalse(trigger(true, false, true, false, false, true, true, true, true, true, true, true, false));
        assertTrue(trigger(true, false, true, false, false, true, true, true, true, true, true, true, true));
    }

    @Test
    void arcComparesAcrossTheZeroWrapSeam() {
        // facing 350, movement 10: twenty degrees apart, accepted at Raven's 330 parity
        assertTrue(JumpResetRules.withinArc(350.0F, 10.0F, 330.0F));
        // same distance the other way round
        assertTrue(JumpResetRules.withinArc(10.0F, 350.0F, 330.0F));
    }

    @Test
    void arcCountsAsAHalfOnEachSide() {
        // 90 degrees from facing: rejected by a 150 arc (+-75), accepted by a 181 arc
        assertFalse(JumpResetRules.withinArc(0.0F, 90.0F, 150.0F));
        assertTrue(JumpResetRules.withinArc(0.0F, 90.0F, 181.0F));
    }

    @Test
    void theArcBoundaryIsInclusive() {
        assertTrue(JumpResetRules.withinArc(0.0F, 90.0F, 180.0F));
    }

    @Test
    void ravenParity330RejectsOnlyNearOppositeMovement() {
        assertTrue(JumpResetRules.withinArc(0.0F, 165.0F, 330.0F));
        assertFalse(JumpResetRules.withinArc(0.0F, 166.0F, 330.0F));
        assertFalse(JumpResetRules.withinArc(0.0F, 180.0F, 330.0F));
    }

    @Test
    void motionYawMatchesMinecraftYawConventions() {
        assertEquals(0.0F, JumpResetRules.motionYawOf(0.0, 1.0), 1.0e-4);   // +Z faces yaw 0
        assertEquals(-90.0F, JumpResetRules.motionYawOf(1.0, 0.0), 1.0e-4); // +X faces yaw -90
        assertEquals(90.0F, JumpResetRules.motionYawOf(-1.0, 0.0), 1.0e-4); // -X faces yaw +90
        // -Z is the 180 seam; IEEE atan2 of negative zero X over negative Z lands on -180, the
        // documented -180..180 range either side stands
        assertEquals(180.0F, Math.abs(JumpResetRules.motionYawOf(0.0, -1.0)), 1.0e-4);
        assertEquals(-45.0F, JumpResetRules.motionYawOf(1.0, 1.0), 1.0e-4);
    }
}
