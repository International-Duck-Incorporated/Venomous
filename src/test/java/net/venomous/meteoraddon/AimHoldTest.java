package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.AimHold;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AimHoldTest {

    /**
     * The three durations are Raven B4's, and they encode one idea: the faster the player is
     * travelling, the longer the head stays down after a place.
     */
    @Test
    void theHoldIsLongestWhileSprintingAndShortestWhileWalking() {
        assertEquals(AimHold.MOVING_HOLD_MS, AimHold.duration(false, true),
            "walking is the short hold: the head comes back up a few ticks after each place");
        assertEquals(AimHold.IDLE_HOLD_MS, AimHold.duration(false, false),
            "standing still is the medium hold: the player is looking at the edge, not the next cell");
        assertEquals(AimHold.SPRINTING_HOLD_MS, AimHold.duration(true, true),
            "sprinting is the long hold");
        assertEquals(AimHold.SPRINTING_HOLD_MS, AimHold.duration(true, false),
            "and sprinting outranks standing still, not just walking");
    }

    @Test
    void theHoldLivesInsideItsDuration() {
        long since = AimHold.MOVING_HOLD_MS - 1L;
        assertTrue(AimHold.shouldHold(false, since, false, true), "just inside the hold");
        assertFalse(AimHold.shouldHold(false, AimHold.MOVING_HOLD_MS, false, true),
            "at the duration the chase resumes");
        assertFalse(AimHold.shouldHold(false, since + 5000L, false, true),
            "and it stays resumed");
    }

    /**
     * A placement that is staged and waiting makes the hold give way: the pitch has to be working
     * to bring the server's ray onto the face being clicked, and the module judges that with the
     * ray gate. A held pitch during that window is a placement that misses its face.
     */
    @Test
    void aPendingPlacementTurnsTheHoldOff() {
        assertFalse(AimHold.shouldHold(true, 0L, false, true),
            "never hold while a placement is staged");
        assertFalse(AimHold.shouldHold(true, AimHold.SPRINTING_HOLD_MS + 10_000L, true, false),
            "not even when the hold window has long since expired");
    }

    @Test
    void beforeTheFirstPlacementTheChaseIsOn() {
        assertFalse(AimHold.shouldHold(false, Long.MAX_VALUE, true, false),
            "a player who has never placed has nothing to hold");
    }
}
