package net.venomous.meteoraddon.utils;

/**
 * When an aim assist is allowed to move the player's view at all.
 *
 * <p>Free of Minecraft types so it unit-tests on a plain JVM, in the same spirit as {@link AimEase},
 * {@link ClickTiming}, {@link SneakTiming} and {@link ScaffoldCadence}.</p>
 *
 * <h2>Why an aim assist needs a "when" as well as a "where"</h2>
 *
 * <p>An aim assist that tracks a target every tick will keep nudging the camera at an entity you
 * are not fighting - walking past someone, mining a block, holding a hoe. A human's view only moves
 * when they decide to move it, so an aim that keeps adjusting with no attack behind it is a
 * behaviour no mouse produces. Vape models the same idea with {@code aimassist.click_aim}
 * ("Only aim while mouse is down"), {@code aimassist.check_block_break} ("Prevents from aiming
 * while breaking blocks") and {@code aimassist.limit_to_items}.</p>
 *
 * <p>Venomous already had this guard on the clicker - {@code AutoClickerPlus.only-while-holding}
 * refuses to click unless the attack key is down - but the aimer did not, which meant the clicker
 * would not swing at nobody while the aimer happily tracked them.</p>
 *
 * <p>The three conditions compose rather than overlap: with the attack key held, looking at an
 * <em>entity</em> is attacking and passes, while looking at a <em>block</em> is mining and is
 * refused. Neither guard cancels the other for the case it exists to catch.</p>
 */
public final class AimGuard {
    private AimGuard() {
    }

    /**
     * Whether aiming is permitted this tick.
     *
     * @param attackDown    the player is holding the attack key
     * @param breakingBlock the player is mining - attack held, aimed at a block, not in creative
     * @param holdingOk     the held item passes the whitelist, or no whitelist is configured
     * @param clickAim      enforce "only aim while attacking"
     * @param checkBlock    enforce "do not aim while mining"
     * @param limitToItems  enforce the held-item whitelist
     * @return true if the aim assist may adjust the view this tick
     */
    public static boolean mayAim(boolean attackDown, boolean breakingBlock, boolean holdingOk,
                                 boolean clickAim, boolean checkBlock, boolean limitToItems) {
        if (clickAim && !attackDown) return false;
        if (checkBlock && breakingBlock) return false;
        if (limitToItems && !holdingOk) return false;
        return true;
    }

    /**
     * Whether a held item passes an empty-or-contains whitelist.
     *
     * <p>An empty whitelist means "unrestricted" rather than "nothing is allowed", so switching the
     * feature on without picking items does not silently disable the module.</p>
     */
    public static boolean itemAllowed(java.util.Collection<?> whitelist, Object heldItem) {
        if (whitelist == null || whitelist.isEmpty()) return true;
        return heldItem != null && whitelist.contains(heldItem);
    }
}