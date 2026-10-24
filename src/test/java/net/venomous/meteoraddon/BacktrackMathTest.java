package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.BacktrackMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the backtrack hold decision. The entire module is one geometry question - is the
 * withheld position a better target than the one we already show - and a sign error in the ray
 * math holds packets for exactly the wrong ticks.
 */
public class BacktrackMathTest {
    // An eye at the origin looking down +Z, the whole test's frame of reference.
    private static final double[] EYE = {0.0, 64.0, 0.0};
    private static final double[] LOOK = {0.0, 0.0, 1.0};

    @Test
    void rayEntryDistanceHitsWhatIsInFront() {
        // A box from z=3 to z=4 straight ahead: the ray enters at 3.
        double entry = BacktrackMath.rayEntryDistance(EYE, LOOK,
            new double[] {-1, 63, 3, 1, 65, 4}, 6.0);
        assertEquals(3.0, entry, 1e-9);
    }

    @Test
    void rayEntryDistanceMissesWhatIsOffTheLine() {
        double entry = BacktrackMath.rayEntryDistance(EYE, LOOK,
            new double[] {-1, 63, 3, 1, 65, 4}, 6.0);
        assertEquals(3.0, entry, 1e-9);

        // Same box shifted to the side, out of the ray's path.
        double miss = BacktrackMath.rayEntryDistance(EYE, LOOK,
            new double[] {5, 63, 3, 7, 65, 4}, 6.0);
        assertEquals(-1.0, miss, 1e-9);

        // And behind the eye.
        double behind = BacktrackMath.rayEntryDistance(EYE, LOOK,
            new double[] {-1, 63, -4, 1, 65, -3}, 6.0);
        assertEquals(-1.0, behind, 1e-9);
    }

    @Test
    void rayEntryDistanceRespectsTheMaxDistance() {
        double entry = BacktrackMath.rayEntryDistance(EYE, LOOK,
            new double[] {-1, 63, 3, 1, 65, 4}, 2.0);
        assertEquals(-1.0, entry, 1e-9, "a box beyond the range is a miss");
    }

    @Test
    void aRayStartingInsideCountsAsDistanceZero() {
        double entry = BacktrackMath.rayEntryDistance(EYE, LOOK,
            new double[] {-1, 63.5, -1, 1, 65, 2}, 6.0);
        assertEquals(0.0, entry, 1e-9);
    }

    @Test
    void aRayParallelToAnAxisMustStillSpendItsLifeInsideTheSlabs() {
        // Looking straight up +Z from inside the y-slab is a hit; from below the box it is not,
        // even though the ray never crosses the y-slabs (it is parallel to them).
        double inside = BacktrackMath.rayEntryDistance(EYE, LOOK,
            new double[] {-1, 60, 3, 1, 70, 4}, 6.0);
        assertEquals(3.0, inside, 1e-9);

        double[] highEye = {0.0, 80.0, 0.0};
        double outside = BacktrackMath.rayEntryDistance(highEye, LOOK,
            new double[] {-1, 60, 3, 1, 70, 4}, 6.0);
        assertEquals(-1.0, outside, 1e-9);
    }

    // ------------------------------------------------------------------ the hold decision

    @Test
    void holdsWhenTheNewPositionIsFurtherAlongTheRay() {
        // Old position at 3 blocks down the ray, new position at 4: the target is walking away,
        // the stale position is the hittable one.
        assertTrue(BacktrackMath.shouldHoldPacket(
            new double[] {0.0, 64.0, 3.0}, new double[] {0.0, 64.0, 4.0},
            EYE, LOOK, 2.0, 5.0, 0.1));
    }

    @Test
    void flushesWhenTheNewPositionIsCloser() {
        // New position at 3 when we already show 4: the target walked towards us, the fresh
        // position is the better target.
        assertFalse(BacktrackMath.shouldHoldPacket(
            new double[] {0.0, 64.0, 4.0}, new double[] {0.0, 64.0, 3.0},
            EYE, LOOK, 2.0, 5.0, 0.1));
    }

    @Test
    void flushesWhenTheNewPositionIsInsideTheMinimumRange() {
        // At 1.5 blocks the new position is closer than the inner edge: no hold, just fight.
        assertFalse(BacktrackMath.shouldHoldPacket(
            new double[] {0.0, 64.0, 1.5}, new double[] {0.0, 64.0, 1.7},
            EYE, LOOK, 2.0, 5.0, 0.1));
    }

    @Test
    void flushesWhenTheNewPositionIsBeyondTheMaximumRange() {
        assertFalse(BacktrackMath.shouldHoldPacket(
            new double[] {0.0, 64.0, 4.0}, new double[] {0.0, 64.0, 7.0},
            EYE, LOOK, 2.0, 5.0, 0.1));
    }

    @Test
    void flushesWhenTheNewPositionIsOffTheRay() {
        assertFalse(BacktrackMath.shouldHoldPacket(
            new double[] {0.0, 64.0, 3.0}, new double[] {6.0, 64.0, 3.0},
            EYE, LOOK, 2.0, 5.0, 0.1),
            "a target strafing sideways out of the cone is not a hold");
    }

    @Test
    void theHitboxExpansionDecidesBandEdgeCases() {
        // New position at 3 with minRange 2.5: the plain box enters at 2.7 (inside the band),
        // but a fat 0.6 border grows the front face to 2.1 (below it) and forces the flush.
        double[] oldPos = {0.0, 64.0, 2.0};
        double[] newPos = {0.0, 64.0, 3.0};

        assertTrue(BacktrackMath.shouldHoldPacket(oldPos, newPos, EYE, LOOK, 2.5, 5.0, 0.1),
            "a thin border keeps the entry inside the reach band");
        assertFalse(BacktrackMath.shouldHoldPacket(oldPos, newPos, EYE, LOOK, 2.5, 5.0, 0.6),
            "a fat border pulls the box's front face below the minimum range, so no hold");
    }
}
