package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.ScaffoldEdge;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the edge test behind the scaffold's crouch.
 *
 * <p>The crouch used to ask whether the cell <em>under</em> the player was air. That is not the
 * edge - it is the moment after the player has already stepped off it - and on a bridge it
 * flickers: true for a tick after the player walks onto a cell, false again the moment the block
 * lands under them.</p>
 *
 * <p>The live log showed the consequence. The crouch went down and came straight back up on every
 * single placement, never held for more than a tick, so the player never saw themselves crouch,
 * and every one of those changes moved the client's speed by seventy per cent - a change the
 * server only learns about a tick later, and worth more than the placement offset the crouch was
 * there to avoid.</p>
 */
public class ScaffoldEdgeTest {
    /** The cells that are air in this world; everything else is solid. */
    private static ScaffoldEdge.Air airIn(Set<String> air) {
        return (x, y, z) -> air.contains(x + "," + y + "," + z);
    }

    private static Set<String> cells(String... specs) {
        Set<String> set = new HashSet<>();

        for (String spec : specs) set.add(spec);

        return set;
    }

    /**
     * The edge is the cell in the direction of travel.
     *
     * <p>The player is standing on a block and the cell they are walking into is empty: that is
     * the edge, and the crouch belongs on.</p>
     */
    @Test
    void theCellInTheDirectionOfTravelIsTheEdge() {
        // Standing at (10, 84, 20) on a solid floor, walking towards +z.
        ScaffoldEdge.Air air = airIn(cells("10,83,21"));

        assertTrue(ScaffoldEdge.overEdge(10.5D, 84.0D, 20.5D, 0.0D, 1.0D, air),
            "the cell in front of the player is empty, so they are at the edge");
    }

    /**
     * Standing on solid ground with solid ground ahead is not an edge.
     *
     * <p>This is the case the old test got right by accident, and it is the one that must not
     * turn the crouch on: a player walking along a floor is not at the edge of anything.</p>
     */
    @Test
    void solidGroundAheadIsNotAnEdge() {
        ScaffoldEdge.Air air = airIn(cells());

        assertFalse(ScaffoldEdge.overEdge(10.5D, 84.0D, 20.5D, 0.0D, 1.0D, air),
            "there is solid ground in every direction, so the player is not at an edge");
    }

    /**
     * Standing on nothing is the edge of everything.
     *
     * <p>There is no direction to step into when the player is not walking, so the cell under
     * them is the only thing that can be asked about - and if that is air they are falling.</p>
     */
    @Test
    void standingOnNothingIsAnEdge() {
        ScaffoldEdge.Air air = airIn(cells("10,83,20"));

        assertTrue(ScaffoldEdge.overEdge(10.5D, 84.0D, 20.5D, 0.0D, 0.0D, air),
            "the cell under the player is empty, so they are over a drop");
    }

    /**
     * A player who is not walking and is standing on something is not at an edge.
     */
    @Test
    void standingStillOnSolidGroundIsNotAnEdge() {
        ScaffoldEdge.Air air = airIn(cells());

        assertFalse(ScaffoldEdge.overEdge(10.5D, 84.0D, 20.5D, 0.0D, 0.0D, air),
            "no direction to step into and solid ground underneath");
    }

    /**
     * The direction of travel decides which cell counts, not the camera.
     *
     * <p>The player holds S to bridge, so they are walking backwards relative to the camera. The
     * cell they are about to step into is behind them in camera terms and in front of them in
     * travel terms, and it is the travel direction that matters.</p>
     */
    @Test
    void theTravelDirectionDecidesWhichCellCounts() {
        // Walking towards -z; the cell at z-1 is empty and the cell at z+1 is solid.
        ScaffoldEdge.Air air = airIn(cells("10,83,19"));

        assertTrue(ScaffoldEdge.overEdge(10.5D, 84.0D, 20.5D, 0.0D, -1.0D, air),
            "the cell they are walking into is empty");

        ScaffoldEdge.Air solid = airIn(cells("10,83,21"));

        assertFalse(ScaffoldEdge.overEdge(10.5D, 84.0D, 20.5D, 0.0D, -1.0D, solid),
            "the cell they are walking into is solid, and the one behind them does not matter");
    }

    /**
     * A diagonal walk steps into the diagonal cell.
     */
    @Test
    void aDiagonalWalkStepsIntoTheDiagonalCell() {
        ScaffoldEdge.Air air = airIn(cells("11,83,21"));

        assertTrue(ScaffoldEdge.overEdge(10.5D, 84.0D, 20.5D, 0.7D, 0.7D, air),
            "the diagonal cell in the direction of travel is empty");
    }

    /**
     * A direction that is mostly along one axis only moves the player into that cell.
     */
    @Test
    void aMostlyAxialDirectionUsesThatCell() {
        ScaffoldEdge.Air air = airIn(cells("11,83,20"));

        assertTrue(ScaffoldEdge.overEdge(10.5D, 84.0D, 20.5D, 0.98D, 0.2D, air),
            "the player is walking mostly along x, so the cell in x is the one that counts");
    }
}
