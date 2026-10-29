package net.venomous.meteoraddon.utils;

import java.util.Collection;

/**
 * The conditions that decide whether {@code v-scaffold} is allowed to act at all.
 *
 * <p>Free of Minecraft types so it unit-tests on a plain JVM, in the same spirit as {@link AimEase},
 * {@link ClickTiming}, {@link SneakTiming}, {@link ScaffoldCadence} and {@link AimGuard}.</p>
 *
 * <h2>Block choice</h2>
 *
 * <p>Vape's Scaffold carries a {@code Blacklist} ("Scaffold will not use these blocks") with a
 * picker, and a {@code Whitelist} ("Only activates scaffold when using..."). Ours picked any
 * {@code BlockItem} in the hotbar with no way to exclude anything, which is a real problem on a
 * server that hands you glass, ladders and glowstone - bridging with those looks nothing like a
 * person building with one material.</p>
 *
 * <h2>Pitch gate</h2>
 *
 * <p>Vape also gates placement on head pitch: {@code Pitch check} plus a {@code Pitch} threshold,
 * described as "Scaffold will not activate unless...". We have {@code pitch-assist}, which does the
 * opposite - it pitches you <em>down</em> near an edge to defeat NCP/Vulcan pitch checks. A gate is
 * the complementary control: refuse to place unless the head is pointed somewhere a person about to
 * bridge would be pointed. Together the two bracket the plausible range.</p>
 */
public final class ScaffoldGates {
    private ScaffoldGates() {
    }

    /**
     * Whether a block may be placed.
     *
     * <p>A blacklist entry always wins. A whitelist only restricts when it actually has entries, so
     * configuring the list and leaving it empty does not silently stop the module placing.</p>
     *
     * @param inWhitelist the block appears in the whitelist
     * @param inBlacklist the block appears in the blacklist
     * @param whitelist   the whitelist setting, possibly empty
     * @param blacklist   the blacklist setting, possibly empty
     */
    public static boolean blockAllowed(boolean inWhitelist, boolean inBlacklist,
                                       Collection<?> whitelist, Collection<?> blacklist) {
        if (inBlacklist) return false;
        if (whitelist == null || whitelist.isEmpty()) return true;
        return inWhitelist;
    }

    /**
     * Convenience for {@link #blockAllowed} over two membership sets.
     *
     * <p>{@code block} may be null, which is not allowed unless neither list restricts.</p>
     */
    public static boolean blockAllowed(Object block, Collection<?> whitelist, Collection<?> blacklist) {
        if (block == null) {
            return (whitelist == null || whitelist.isEmpty()) && (blacklist == null || blacklist.isEmpty());
        }

        return blockAllowed(contains(whitelist, block), contains(blacklist, block), whitelist, blacklist);
    }

    private static boolean contains(Collection<?> set, Object block) {
        return set != null && set.contains(block);
    }

    /**
     * Whether the head is pointed somewhere plausible for bridging.
     *
     * <p>Minecraft's pitch runs -90 (straight down) to +90 (straight up), so "looking down by at
     * least {@code threshold} degrees" is {@code pitch <= -threshold}. A threshold of 0 disables the
     * gate, which is the default.</p>
     *
     * @param playerPitch   the player's current X rotation
     * @param thresholdDeg  degrees of downward pitch required
     */
    public static boolean pitchAllows(float playerPitch, int thresholdDeg) {
        if (thresholdDeg <= 0) return true;
        return playerPitch <= -thresholdDeg;
    }
}
