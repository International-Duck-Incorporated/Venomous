package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.ClickGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClickGuardTest {
    /**
     * The gap this closes. {@code respect-cooldown} already covered the hit-window half - it waits
     * for the attack strength scale - but nothing stopped the clicker firing on empty air. A
     * perfectly-timed stream of attacks that all miss is a stronger signal than an irregular one,
     * because real misses are irregular.
     */
    @Test
    void refusesToClickWhenTheCrosshairIsOnNothing() {
        assertFalse(ClickGuard.mayClick(false, true),
            "with require-entity on and nothing under the crosshair, clicking must be refused");
    }

    @Test
    void clicksWhenAnEntityIsUnderTheCrosshair() {
        assertTrue(ClickGuard.mayClick(true, true));
    }

    @Test
    void theGateIsInertWhileDisabled() {
        assertTrue(ClickGuard.mayClick(false, false),
            "require-entity is off, so hovering nothing must not matter - defaulting it on would "
                + "silently stop the module working for anyone who enables the clicker away from "
                + "a target");
    }

    @Test
    void anEntityUnderTheCrosshairNeverBlocksEvenWithTheGateOn() {
        for (boolean hovering : new boolean[] {true, false}) {
            for (boolean require : new boolean[] {true, false}) {
                boolean allowed = ClickGuard.mayClick(hovering, require);
                if (hovering) {
                    assertTrue(allowed, "an entity under the crosshair must never block a click");
                }
            }
        }
    }
}