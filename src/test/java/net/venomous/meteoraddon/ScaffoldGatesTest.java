package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.ScaffoldGates;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScaffoldGatesTest {
    private static final String STONE = "stone";
    private static final String GLASS = "glass";
    private static final String LADDER = "ladder";

    @Test
    void withNoListsConfiguredEveryBlockIsAllowed() {
        assertTrue(ScaffoldGates.blockAllowed(STONE, Set.of(), Set.of()),
            "an empty whitelist and blacklist must not stop the module placing");
        assertTrue(ScaffoldGates.blockAllowed(STONE, null, null));
    }

    @Test
    void theBlacklistAlwaysWins() {
        Set<String> whitelist = Set.of(STONE, GLASS);
        Set<String> blacklist = Set.of(GLASS);

        assertFalse(ScaffoldGates.blockAllowed(GLASS, whitelist, blacklist),
            "a block on both lists must be rejected; otherwise a whitelist entry silently "
                + "resurrects something the user explicitly excluded");
        assertTrue(ScaffoldGates.blockAllowed(STONE, whitelist, blacklist),
            "and the rest of the whitelist still works");
    }

    @Test
    void aPopulatedWhitelistRestricts() {
        Set<String> whitelist = Set.of(STONE);

        assertTrue(ScaffoldGates.blockAllowed(STONE, whitelist, Set.of()));
        assertFalse(ScaffoldGates.blockAllowed(LADDER, whitelist, Set.of()),
            "with a whitelist configured, anything not on it is refused");
    }

    /**
     * The failure that would be easiest to ship: treating an empty whitelist as "nothing allowed"
     * would silently disable the whole module the moment the setting is switched on.
     */
    @Test
    void anEmptyWhitelistMeansUnrestrictedNotNothing() {
        assertTrue(ScaffoldGates.blockAllowed(STONE, List.of(), Set.of(LADDER)),
            "an empty whitelist must not mean nothing is placeable");
    }

    @Test
    void aNullBlockIsRefusedOnlyWhenSomethingActuallyRestricts() {
        assertTrue(ScaffoldGates.blockAllowed(null, List.of(), List.of()),
            "nothing to place and nothing configured is fine");
        assertFalse(ScaffoldGates.blockAllowed(null, Set.of(STONE), Set.of()),
            "but a whitelist configured with nothing in hand must not count as found");
        assertFalse(ScaffoldGates.blockAllowed(null, List.of(), Set.of(STONE)));
    }

    /**
     * Vape gates scaffold on head pitch. Minecraft pitch is -90 (down) to +90 (up), so "looking
     * down by N degrees" is {@code pitch <= -N}. A threshold of 0 must disable the gate outright
     * rather than requiring the player to be looking exactly straight up.
     */
    @Test
    void thePitchGateNeedsDownwardPitch() {
        assertTrue(ScaffoldGates.pitchAllows(-60.0F, 30), "-60 is well below the -30 threshold");
        assertTrue(ScaffoldGates.pitchAllows(-30.0F, 30), "exactly at the threshold passes");
        assertFalse(ScaffoldGates.pitchAllows(-29.9F, 30), "just above it does not");
        assertFalse(ScaffoldGates.pitchAllows(0.0F, 30), "looking at the horizon does not");
        assertFalse(ScaffoldGates.pitchAllows(45.0F, 30), "looking up certainly does not");
    }

    @Test
    void aZeroThresholdDisablesThePitchGate() {
        for (float pitch : new float[] {-90.0F, 0.0F, 90.0F}) {
            assertTrue(ScaffoldGates.pitchAllows(pitch, 0),
                "threshold 0 must allow every pitch, got " + pitch);
            assertTrue(ScaffoldGates.pitchAllows(pitch, -1),
                "a negative threshold is nonsense and must not silently block, got " + pitch);
        }
    }

    @Test
    void lookingStraightDownSatisfiesAnyThreshold() {
        assertEquals(true, ScaffoldGates.pitchAllows(-90.0F, 90),
            "the most extreme downward pitch should clear every threshold up to 90");
    }
}
