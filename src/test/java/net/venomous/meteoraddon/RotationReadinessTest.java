package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.RotationReadiness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the readiness decision {@link net.venomous.meteoraddon.utils.PreRotate} makes for every
 * deferred action: has the rotation the server actually holds reached the aim the action waits on.
 *
 * <p>Two contracts are pinned here. The yaw contract: an aim whose yaw is the player's own camera
 * yaw moves with the mouse, so waiting for <em>that</em> yaw measures the mouse rather than the
 * rotation - and since a queued action is discarded after five ticks of not landing, its caller
 * would act only while the player happened to be holding the mouse still. That is
 * {@code yawMatters} = {@code false}, and PreRotate used to accept the flag and then ignore it,
 * which held placements back a tick behind every flick. And the seam contract: 359 to 1 is two
 * degrees, not three hundred and fifty-eight - a plain difference would hold every action taken
 * across the 0/360 boundary.</p>
 */
public class RotationReadinessTest {
    private static final float TOLERANCE = 1.5F;

    @Test
    void aYawTheCallerDoesNotOwnNeverHoldsTheAction() {
        // The camera is on 120; the entry's staged yaw still says 5. The entry belongs to a
        // caller whose yaw is the player's own - that yaw is wherever the mouse has moved to,
        // and it is not the thing the action has to wait for.
        assertTrue(RotationReadiness.isReady(120.0, 80.0, 5.0F, 80.0F, false, TOLERANCE),
            "a camera yaw must not gate its own action: the yaw is whatever the mouse did since "
                + "the staging, and waiting for it means the action only fires while the mouse "
                + "happens to be still");
    }

    @Test
    void aYawThatMattersMustHaveLanded() {
        assertFalse(RotationReadiness.isReady(120.0, 80.0, 5.0F, 80.0F, true, TOLERANCE),
            "a real target yaw that has not landed holds the action");

        assertTrue(RotationReadiness.isReady(5.9, 80.0, 5.0F, 80.0F, true, TOLERANCE),
            "within tolerance it does not");
    }

    @Test
    void thePitchHoldsTheActionEitherWay() {
        // The aim pitch is 80, the server still holds 30. Neither call may say ready, whichever
        // way the yaw is treated: the pitch is the one thing a spoofed aim genuinely waits for.
        assertFalse(RotationReadiness.isReady(5.0, 30.0, 5.0F, 80.0F, false, TOLERANCE));
        assertFalse(RotationReadiness.isReady(5.0, 30.0, 5.0F, 80.0F, true, TOLERANCE));
    }

    @Test
    void theYawComparisonWrapsAroundTheSeam() {
        // A tolerance of 3 degrees, purely so the seam arithmetic has room to be seen: the two
        // angles are two degrees apart across the 0/360 boundary, and
        // two-thirds of a rotation the wrong way round is three.
        assertTrue(RotationReadiness.isReady(359.0, 80.0, 1.0F, 80.0F, true, 3.0F),
            "359 to 1 is two degrees across the 0/360 seam, not 358");

        assertFalse(RotationReadiness.isReady(357.5, 80.0, 1.0F, 80.0F, true, 3.0F),
            "and a difference that genuinely crosses more than the tolerance still holds");
    }

    @Test
    void theToleranceIsInclusive() {
        assertTrue(RotationReadiness.isReady(1.5, 80.0, 0.0F, 80.0F, true, TOLERANCE),
            "exactly the tolerance counts as landed, as the previous inline comparison did");

        assertFalse(RotationReadiness.isReady(1.6, 80.0, 0.0F, 80.0F, true, TOLERANCE),
            "and beyond it does not");
    }
}
