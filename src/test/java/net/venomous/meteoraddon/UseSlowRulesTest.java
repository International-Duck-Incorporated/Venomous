package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.KeepSprintRules;
import net.venomous.meteoraddon.utils.NoSlowRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards the two slowdown rule sets: the using-item override and the post-attack restoration.
 * Both share one shape - a slider that may only move the multiplier up from vanilla - and a
 * sign error in either moves the player the wrong way.
 */
public class UseSlowRulesTest {
    // ------------------------------------------------------------------ NoSlow

    @Test
    void noSlowDefaultsToExactVanillaParity() {
        // The default slider value is 80, and 80% slow is vanilla's 0.2.
        assertEquals(NoSlowRules.VANILLA_MULTIPLIER,
            NoSlowRules.multiplier(true, false, false, false, false,
                false, false, false, 80.0), 1e-6);
    }

    @Test
    void noSlowOnlyEverMovesTheMultiplierUp() {
        assertEquals(1.0F, NoSlowRules.multiplier(true, false, false, false, false,
            false, false, false, 0.0), 1e-6, "0% slow means no slowdown at all");

        assertEquals(0.5F, NoSlowRules.multiplier(true, false, false, false, false,
            false, false, false, 50.0), 1e-6, "half the slowdown, half the multiplier above vanilla");

        // The slider cannot make things worse than vanilla even if it is asked to.
        assertEquals(NoSlowRules.VANILLA_MULTIPLIER,
            NoSlowRules.multiplier(true, false, false, false, false,
                false, false, false, 120.0), 1e-6, "values past 80 clamp to vanilla");
    }

    @Test
    void noSlowIsVanillaWhenNotUsingAnything() {
        assertEquals(-1.0F, NoSlowRules.multiplier(false, false, false, false, false,
            false, false, false, 0.0), 1e-6);
    }

    @Test
    void theFiltersHandTheDecisionBackToVanilla() {
        // Sword-only, and the item in use is not a sword.
        assertEquals(-1.0F, NoSlowRules.multiplier(true, false, false, false, false,
            true, false, false, 0.0), 1e-6, "sword-only must leave non-swords alone");
        assertTrue(NoSlowRules.multiplier(true, true, false, false, false,
            true, false, false, 0.0) > 0.0F, "sword-only applies to swords");

        // Bows are excluded when asked, but splashes are not potion-drinking.
        assertEquals(-1.0F, NoSlowRules.multiplier(true, false, true, false, false,
            false, true, false, 0.0), 1e-6, "disable-bow must leave bows alone");

        assertEquals(-1.0F, NoSlowRules.multiplier(true, false, false, true, false,
            false, false, true, 0.0), 1e-6, "disable-potions must leave drinkable potions alone");

        assertTrue(NoSlowRules.multiplier(true, false, false, true, true,
            false, false, true, 0.0) > 0.0F,
            "disable-potions must not extend to thrown potions - the original is explicit there");
    }

    // ------------------------------------------------------------------ KeepSprint

    @Test
    void keepSprintDefaultsToExactVanillaMotion() {
        assertEquals(0.6F, KeepSprintRules.motionMultiplier(40.0), 1e-6,
            "the default 40% slow restores exactly vanilla's post-attack motion");
    }

    @Test
    void keepSprintOnlyEverMovesTheMultiplierUp() {
        assertEquals(1.0F, KeepSprintRules.motionMultiplier(0.0), 1e-6,
            "0% slow keeps all of the speed going into the swing");
        assertEquals(0.8F, KeepSprintRules.motionMultiplier(20.0), 1e-6);
    }

    @Test
    void keepSprintClampsBothWays() {
        assertEquals(0.6F, KeepSprintRules.motionMultiplier(90.0), 1e-6,
            "values past 40 clamp to vanilla - the module never loses more than vanilla");
        assertEquals(1.0F, KeepSprintRules.motionMultiplier(-30.0), 1e-6,
            "negative slider values clamp to keeping everything");
    }
}
