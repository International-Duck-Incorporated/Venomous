package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.SneakRepress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SneakRepressTest {

    @Test
    void aFreshCrouchWaitsAfterAPlacement() {
        assertFalse(SneakRepress.allowsPress(0L, SneakRepress.DEFAULT_DELAY_MS),
            "a crouch pressed on the heels of a placement is the place-and-squeeze tell");
        assertFalse(SneakRepress.allowsPress(SneakRepress.DEFAULT_DELAY_MS - 1L,
            SneakRepress.DEFAULT_DELAY_MS), "right up to the delay");
        assertTrue(SneakRepress.allowsPress(SneakRepress.DEFAULT_DELAY_MS,
            SneakRepress.DEFAULT_DELAY_MS), "at the delay it is allowed");
        assertTrue(SneakRepress.allowsPress(10_000L, SneakRepress.DEFAULT_DELAY_MS),
            "and long after it");
    }

    /**
     * Nothing has ever been placed: the time since the last placement is unbounded in the module.
     */
    @Test
    void beforeTheFirstPlacementTheCrouchIsFree() {
        assertTrue(SneakRepress.allowsPress(Long.MAX_VALUE, SneakRepress.DEFAULT_DELAY_MS),
            "a player who has never placed a block has no placement for the press to collide with");
    }

    /**
     * A configured delay below the floor is clamped: below the floor the press lands within the
     * movement window that follows the placement, which is the PacketOrderF shape the module
     * already refuses on the same tick.
     */
    @Test
    void aTighterConfigurationStillCannotSqueezeIntoTheFloor() {
        int belowFloor = SneakRepress.MINIMUM_DELAY_MS - 10;

        assertFalse(SneakRepress.allowsPress(0L, belowFloor),
            "a sub-floor delay must not let the press land straight after the placement");
        assertFalse(SneakRepress.allowsPress(SneakRepress.MINIMUM_DELAY_MS - 1L, belowFloor),
            "right up to the floor");
        assertTrue(SneakRepress.allowsPress(SneakRepress.MINIMUM_DELAY_MS, belowFloor),
            "but from the floor onwards it is allowed");
    }
}
