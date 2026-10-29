package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.SneakTiming;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SneakTimingTest {
    private static boolean hold(boolean atEdge, boolean onGround, boolean held, long heldForMs) {
        return SneakTiming.shouldHold(atEdge, onGround, held, heldForMs,
            SneakTiming.DEFAULT_SNEAK_DELAY_MS);
    }

    @Test
    void crouchesAtTheEdge() {
        assertTrue(hold(true, true, false, 0L), "at the edge on the ground, always crouch");
        assertTrue(hold(true, true, true, 9999L), "and keep crouching regardless of how long it has been");
    }

    /**
     * The bug this guards. The old code was a bare boolean - crouch while over an edge, stand the
     * instant it is not - so the crouch came up on the very next tick (50ms at 20 TPS). That is the
     * shape {@code Scaffold.parts.Safewalk.Timing} flags at 20 VL: movement that stops abruptly
     * without a sustained crouch.
     */
    @Test
    void keepsCrouchingAfterTheEdgeClears() {
        assertTrue(hold(false, true, true, 0L),
            "the crouch must survive the first tick after the edge clears");
        assertTrue(hold(false, true, true, 50L), "and the next one");
        assertTrue(hold(false, true, true, 199L), "and right up to the configured delay");
    }

    @Test
    void releasesOnceTheDelayHasElapsed() {
        assertFalse(hold(false, true, true, 200L), "200ms is the delay, so it is time to stand");
        assertFalse(hold(false, true, true, 400L), "and it stays released");
    }

    /**
     * A sub-tick edge blip could otherwise press and release the key inside one tick - a
     * zero-length crouch that no human hand produces, and which reads as an edge detection bug.
     */
    @Test
    void aVanishinglyShortCrouchStillGetsTheFloor() {
        // A delay below the floor, so the floor is what is actually deciding here.
        int belowFloor = SneakTiming.MINIMUM_SNEAK_MS - 20;

        assertTrue(SneakTiming.shouldHold(false, true, true, 0L, belowFloor),
            "a crouch shorter than " + SneakTiming.MINIMUM_SNEAK_MS + "ms still counts as held");
        assertTrue(SneakTiming.shouldHold(false, true, true,
            SneakTiming.MINIMUM_SNEAK_MS - 1L, belowFloor), "right up to the floor");
        assertFalse(SneakTiming.shouldHold(false, true, true,
            SneakTiming.MINIMUM_SNEAK_MS, belowFloor),
            "but a delay below the floor must not stretch the crouch past it");
    }

    @Test
    void aZeroDelaySettingStillCannotProduceAZeroLengthCrouch() {
        assertTrue(SneakTiming.shouldHold(false, true, true, 0L, 0),
            "even with the delay turned down to zero, the minimum floor applies");
        assertFalse(SneakTiming.shouldHold(false, true, true,
            SneakTiming.MINIMUM_SNEAK_MS, 0), "and the floor is still finite");
    }

    @Test
    void neverCrouchesInMidAir() {
        assertFalse(hold(true, false, false, 0L), "crouching in mid-air is meaningless");
        assertFalse(hold(false, false, true, 10L), "and a held crouch is dropped on leaving the ground");
    }

    @Test
    void doesNotPressWhenAlreadyClearAndNotHeld() {
        assertFalse(hold(false, true, false, 0L),
            "clear of the edge with nothing held must not press a crouch");
    }
}
