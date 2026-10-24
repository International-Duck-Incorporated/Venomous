package net.venomous.meteoraddon.utils;

/**
 * The decision behind the using-item slowdown override.
 *
 * <p>26.2 keeps an item's slowdown on the item itself - the {@code USE_EFFECTS} data component
 * carries a speed multiplier the client scales its movement vector by while the item is in use.
 * This is the pure half of the override: given what is in hand and what the user asked for,
 * decide whether vanilla's number stands and, when it does not, what replaces it.</p>
 *
 * <p>Two properties are load-bearing. The slider only ever moves the multiplier <em>up</em> from
 * vanilla's 0.2 - the module's range is "less slow", never slower - and a filter that does not
 * apply returns a negative result meaning "leave vanilla alone", so the gate is visible at the
 * call site rather than hidden inside a number.</p>
 *
 * <p>Free of Minecraft types so it unit tests on a plain JVM.</p>
 */
public final class NoSlowRules {
    /** Vanilla's using-item movement multiplier: an 80% slowdown. */
    public static final float VANILLA_MULTIPLIER = 0.2F;
    /** The largest slowdown the slider may leave: vanilla's own. */
    public static final double MAX_SLOW_PERCENT = 80.0;

    private NoSlowRules() {
    }

    /**
     * The movement multiplier to hand the client while an item is in use.
     *
     * @param usingItem       an item is currently in use
     * @param holdingSword    the item in use is a sword
     * @param holdingBow      the item in use is a bow
     * @param holdingPotion   the item in use is a drinkable potion
     * @param holdingSplash   the item in use is a thrown (splash or lingering) potion
     * @param swordOnly       the override applies to swords only
     * @param disableBow      the override never applies to bows
     * @param disablePotions  the override never applies to drinkable potions (splashes may stay)
     * @param slowPercent     how much of vanilla's slowdown remains, 0 to 80
     * @return the multiplier to apply, or -1 to leave vanilla's untouched
     */
    public static float multiplier(boolean usingItem, boolean holdingSword, boolean holdingBow,
                                   boolean holdingPotion, boolean holdingSplash,
                                   boolean swordOnly, boolean disableBow, boolean disablePotions,
                                   double slowPercent) {
        if (!usingItem) return -1.0F;
        if (swordOnly && !holdingSword) return -1.0F;
        if (disableBow && holdingBow) return -1.0F;
        if (disablePotions && holdingPotion && !holdingSplash) return -1.0F;

        double clamped = Math.max(0.0D, Math.min(MAX_SLOW_PERCENT, slowPercent));
        float multiplier = (float) (1.0D - clamped / 100.0D);

        return Math.max(multiplier, VANILLA_MULTIPLIER);
    }
}
