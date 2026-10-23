package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.HitSelectRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The hit-select decision without a game: window mechanics, the burst masks, the sticky pause, the
 * wait-for-first hold, the criticals gate and the three-block reach.
 */
public class HitSelectRulesTest {
    private static HitSelectRules.TargetState state() {
        return new HitSelectRules.TargetState();
    }

    /**
     * Boxes a unit cube at the origin for the reach tests.
     */
    private static boolean inRange(double eyeX, double eyeY, double eyeZ, double maxDistanceSq) {
        return HitSelectRules.withinHitRange(eyeX, eyeY, eyeZ, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, maxDistanceSq);
    }

    @Test
    void msToTicksRoundsUpAndStaysZeroForNonPositive() {
        assertEquals(0, HitSelectRules.msToTicks(0.0));
        assertEquals(0, HitSelectRules.msToTicks(-50.0));
        assertEquals(1, HitSelectRules.msToTicks(1.0));
        assertEquals(1, HitSelectRules.msToTicks(50.0));
        assertEquals(2, HitSelectRules.msToTicks(51.0));
        assertEquals(10, HitSelectRules.msToTicks(500.0));
    }

    @Test
    void anEyeInsideTheBoxIsZeroAway() {
        assertEquals(0.0, HitSelectRules.eyeToBoxSqr(0.5, 0.5, 0.5, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0), 1.0e-9);
    }

    @Test
    void reachMeasuresToTheClosestFaceNotTheCenter() {
        // face half a block in front of the eye
        assertTrue(inRange(1.5, 0.5, 0.5, 9.0));
        // exactly three blocks off the face, inclusive
        assertTrue(inRange(4.0, 0.5, 0.5, 9.0));
        // one hair past it misses
        assertFalse(inRange(4.001, 0.5, 0.5, 9.0));
    }

    @Test
    void theClosestPointOfABoundingBoxIsACorner() {
        // eye at (2, 2, 2): the corner (1, 1, 1) is sqrt(3) squared away
        assertEquals(3.0, HitSelectRules.eyeToBoxSqr(2.0, 2.0, 2.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0), 1.0e-9);
        // at (4, 4, 0.5) the corner (1, 1, 0.5) is 18 squared away while the center is 24.5, so a
        // cap at 20 only passes when the measure runs to the corner and not the center
        assertTrue(HitSelectRules.withinHitRange(4.0, 4.0, 0.5, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 20.0));
    }

    @Test
    void theBurstWindowRunsTenTicksFromThePassedHit() {
        HitSelectRules.TargetState state = state();
        HitSelectRules.startPredictedBurstWindow(state, 100);

        assertTrue(HitSelectRules.isPredictedBurstWindowActive(state, 100));
        assertTrue(HitSelectRules.isPredictedBurstWindowActive(state, 109));
        HitSelectRules.tickWindow(state, 110);
        assertFalse(HitSelectRules.isPredictedBurstWindowActive(state, 110));
    }

    @Test
    void thePredictedBurstMaskNeedsThePauseToRun() {
        HitSelectRules.TargetState state = state();
        HitSelectRules.startPredictedBurstWindow(state, 100);

        // a 500ms pause is ten ticks: blocked through tick 109, clear at 110
        assertEquals(HitSelectRules.BLOCK_PREDICTED_BURST, HitSelectRules.burstBlockMask(state, 109, false, 500.0));
        assertEquals(HitSelectRules.NO_BLOCK, HitSelectRules.burstBlockMask(state, 110, false, 500.0));
    }

    @Test
    void aZeroPausePredictsNothing() {
        HitSelectRules.TargetState state = state();
        HitSelectRules.startPredictedBurstWindow(state, 100);
        assertEquals(HitSelectRules.NO_BLOCK, HitSelectRules.burstBlockMask(state, 105, false, 0.0));
    }

    @Test
    void theServerCooldownMasksAFullWindowBeyondTheConfirmation() {
        HitSelectRules.TargetState state = state();
        state.lastConfirmedTargetDamageTick = 100;
        assertEquals(HitSelectRules.BLOCK_SERVER_COOLDOWN, HitSelectRules.burstBlockMask(state, 109, true, 500.0));
        assertEquals(HitSelectRules.NO_BLOCK, HitSelectRules.burstBlockMask(state, 110, true, 500.0));
    }

    @Test
    void serverTimingIgnoresThePredictedWindow() {
        HitSelectRules.TargetState state = state();
        HitSelectRules.startPredictedBurstWindow(state, 100);
        assertEquals(HitSelectRules.NO_BLOCK, HitSelectRules.burstBlockMask(state, 105, true, 500.0));
    }

    @Test
    void theStickyPauseHoldsAMaskUntilItsDurationRuns() {
        HitSelectRules.TargetState state = state();
        HitSelectRules.startPredictedBurstWindow(state, 100);

        // the caller strips the burst bit; the sticky pause reaches server-cooldown and wait-first
        int masked = HitSelectRules.burstBlockMask(state, 105, true, 500.0) | HitSelectRules.BLOCK_SERVER_COOLDOWN;
        masked &= ~HitSelectRules.BLOCK_PREDICTED_BURST;
        assertEquals(HitSelectRules.BLOCK_SERVER_COOLDOWN, masked);

        // the pause's own clock starts where the mask was applied: ten blocked ticks from 105
        assertTrue(HitSelectRules.applyPauseDuration(state, masked, 105, 500.0));
        assertTrue(HitSelectRules.applyPauseDuration(state, masked, 114, 500.0));
        assertFalse(HitSelectRules.applyPauseDuration(state, masked, 115, 500.0));
    }

    @Test
    void theStickyPauseResetsWhenTheMaskChanges() {
        HitSelectRules.TargetState state = state();
        assertTrue(HitSelectRules.applyPauseDuration(state, HitSelectRules.BLOCK_WAIT_FIRST, 100, 500.0));

        // a different mask restarts the clock from its own first tick
        assertTrue(HitSelectRules.applyPauseDuration(state, HitSelectRules.BLOCK_SERVER_COOLDOWN, 109, 500.0));
        assertTrue(HitSelectRules.applyPauseDuration(state, HitSelectRules.BLOCK_SERVER_COOLDOWN, 118, 500.0));
        assertFalse(HitSelectRules.applyPauseDuration(state, HitSelectRules.BLOCK_SERVER_COOLDOWN, 119, 500.0));
    }

    @Test
    void theStickyPauseResetReleasesAndClears() {
        HitSelectRules.TargetState state = state();
        assertTrue(HitSelectRules.applyPauseDuration(state, HitSelectRules.BLOCK_WAIT_FIRST, 100, 500.0));

        assertFalse(HitSelectRules.applyPauseDuration(state, HitSelectRules.NO_BLOCK, 101, 500.0));
        assertEquals(-1, state.rawBlockStartTick);
        assertEquals(HitSelectRules.NO_BLOCK, state.rawBlockMask);
    }

    @Test
    void theStickyPauseIsBypassedEntirelyWithoutADuration() {
        HitSelectRules.TargetState state = state();
        assertFalse(HitSelectRules.applyPauseDuration(state, HitSelectRules.BLOCK_WAIT_FIRST, 100, 0.0));
        // the reason is still recorded for the next clock to start from
        assertEquals(HitSelectRules.BLOCK_WAIT_FIRST, state.rawBlockMask);
    }

    @Test
    void waitFirstHoldsUntilTheDurationOrTheUnlock() {
        // locked, ten-tick wait: held through the ninth, released on the tenth
        assertTrue(HitSelectRules.waitingForFirstHit(500.0, true, false, 100, 109));
        assertFalse(HitSelectRules.waitingForFirstHit(500.0, true, false, 100, 110));
        // unlocked or never-started tracking never holds
        assertFalse(HitSelectRules.waitingForFirstHit(500.0, true, true, 100, 105));
        assertFalse(HitSelectRules.waitingForFirstHit(500.0, false, false, 100, 105));
        assertFalse(HitSelectRules.waitingForFirstHit(500.0, true, false, -1, 105));
        assertFalse(HitSelectRules.waitingForFirstHit(0.0, true, false, 100, 105));
    }

    @Test
    void aGroundClickIsANormalHitAndPassesTheCriticalsGate() {
        assertFalse(HitSelectRules.criticalsBlocked(true, true, false, true, false, false));
    }

    @Test
    void theCriticalsGateHoldsUnreadableAirborneClicks() {
        // airborne and cannot crit: held
        assertTrue(HitSelectRules.criticalsBlocked(true, false, false, true, false, false));
        // airborne and can crit: passes
        assertFalse(HitSelectRules.criticalsBlocked(true, false, false, true, false, true));
        // burst mode never holds anything on this gate
        assertFalse(HitSelectRules.criticalsBlocked(false, false, false, true, false, false));
    }

    @Test
    void theDamagedOnlyOptionLetsFreshExchangesThrough() {
        assertTrue(HitSelectRules.criticalsBlocked(true, false, true, true, false, false));
        assertFalse(HitSelectRules.criticalsBlocked(true, false, true, false, false, false));
    }

    @Test
    void theKnockbackExemptionReachesTheCriticalsGate() {
        assertFalse(HitSelectRules.criticalsBlocked(true, false, false, true, true, false));
    }

    @Test
    void aCriticalNeedsAirborneFallOffClimbableSoberWaterAndUnmounted() {
        // all clean
        assertTrue(HitSelectRules.canCriticalHit(1.0, false, false, false, false, false));
        // each single break is enough
        assertFalse(HitSelectRules.canCriticalHit(0.0, false, false, false, false, false));
        assertFalse(HitSelectRules.canCriticalHit(1.0, true, false, false, false, false));
        assertFalse(HitSelectRules.canCriticalHit(1.0, false, true, false, false, false));
        assertFalse(HitSelectRules.canCriticalHit(1.0, false, false, true, false, false));
        assertFalse(HitSelectRules.canCriticalHit(1.0, false, false, false, true, false));
        assertFalse(HitSelectRules.canCriticalHit(1.0, false, false, false, false, true));
    }
}
