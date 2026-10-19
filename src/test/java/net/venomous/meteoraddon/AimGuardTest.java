package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.AimGuard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AimGuardTest {
    private static boolean aim(boolean attackDown, boolean breakingBlock, boolean holdingOk) {
        return AimGuard.mayAim(attackDown, breakingBlock, holdingOk, true, true, true);
    }

    /**
     * The bug this guards. Aimbot tracked the nearest target every tick with no condition on what
     * the player was doing, so it nudged the view at someone being walked past, or while mining.
     * AutoClickerPlus already refused to click unless the attack key was down - the clicker had the
     * guard and the aimer did not, so the two disagreed about whether the player was fighting.
     */
    @Test
    void refusesToAimWhenThePlayerIsNotAttacking() {
        assertFalse(aim(false, false, true),
            "click-aim must refuse while the attack key is up, however valid the target is");
    }

    @Test
    void aimsWhileAttackingAnEntity() {
        assertTrue(aim(true, false, true),
            "attack held against an entity is an attack, and must be allowed");
    }

    @Test
    void refusesWhileMiningABlock() {
        assertFalse(aim(true, true, true),
            "attack held against a block is digging, not aiming - a person digging is not "
                + "also lining up a shot");
    }

    @Test
    void anEmptyWhitelistMeansUnrestricted() {
        assertTrue(AimGuard.itemAllowed(Set.of(), "sword"),
            "switching limit-to-items on without picking items must not silently disable the module");
        assertTrue(AimGuard.itemAllowed((java.util.Collection<?>) null, "sword"),
            "and a null whitelist is likewise unrestricted");
    }

    @Test
    void aPopulatedWhitelistGatesOnTheHeldItem() {
        Set<String> allowed = Set.of("sword", "axe");

        assertTrue(AimGuard.itemAllowed(allowed, "sword"));
        assertTrue(AimGuard.itemAllowed(allowed, "axe"));
        assertFalse(AimGuard.itemAllowed(allowed, "hoe"));
    }

    @Test
    void theItemGateIsIgnoredWhileTheFeatureIsOff() {
        assertTrue(AimGuard.mayAim(true, false, false, true, true, false),
            "a disallowed item must not matter while limit-to-items is off");
    }

    /**
     * The guards compose rather than overlap: each has to catch its own case without cancelling
     * the other for the situation it exists for.
     */
    @Test
    void turningEachGuardOffOnlyRelaxesItsOwnCondition() {
        // Not attacking, but click-aim off and everything else on.
        assertTrue(AimGuard.mayAim(false, false, true, false, true, true));

        // Mining, but check-block-break off.
        assertTrue(AimGuard.mayAim(true, true, true, true, false, true));

        // Item disallowed, but limit-to-items off.
        assertTrue(AimGuard.mayAim(true, false, false, true, true, false));
    }

    @Test
    void withNoGuardsEnabledNothingIsEverBlocked() {
        for (boolean down : new boolean[] {false, true}) {
            for (boolean breaking : new boolean[] {false, true}) {
                for (boolean held : new boolean[] {false, true}) {
                    assertTrue(AimGuard.mayAim(down, breaking, held, false, false, false),
                        "with click-aim, check-block-break and limit-to-items all off, aiming must "
                            + "be permitted for every combination (attack=" + down + ", breaking="
                            + breaking + ", itemAllowed=" + held + ")");
                }
            }
        }
    }

    /** Guards are independent, so any one of them can be the reason aiming is refused. */
    @Test
    void eachGuardAloneIsSufficientToRefuse() {
        assertFalse(AimGuard.mayAim(false, false, true, true, false, false), "click-aim alone");
        assertFalse(AimGuard.mayAim(true, true, true, false, true, false), "check-block-break alone");
        assertFalse(AimGuard.mayAim(true, false, false, true, true, true), "limit-to-items alone");
    }

    @Test
    void allowedItemsStartsEmptySoTheGateIsInertUntilConfigured() {
        assertTrue(AimGuard.itemAllowed(List.of(), "sword"),
            "Aimbot.allowed-items must default to empty; the regression test asserts this so a "
                + "non-empty default cannot ship a module that refuses to aim at anything");
    }
}