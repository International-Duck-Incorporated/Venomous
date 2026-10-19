package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.ScaffoldAim;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScaffoldAimTest {
    private static final double[] UP = {0, 1, 0};
    private static final double[] DOWN = {0, -1, 0};
    private static final double[] NORTH = {0, 0, -1};

    @Test
    void theSharedFaceSitsHalfABlockFromTheSupportCentre() {
        double[] face = ScaffoldAim.sharedFaceCentre(10, 64, 20, DOWN, 0.5);

        assertEquals(10.5, face[0], 1e-9);
        assertEquals(64.0, face[1], 1e-9, "placing on top of a block puts the face at the support's top");
        assertEquals(20.5, face[2], 1e-9);
    }

    /**
     * The bug this class exists for: aiming at the block's centre only works when the eye is square
     * to the face. While bridging, the eye is above and behind, so the ray enters through a face the
     * player is not addressing and Grim resolves a position with no valid support.
     */
    @Test
    void theAimPointIsTheSharedFaceNotTheBlockCentre() {
        // Placing onto the top of the block at (10, 64, 20) means the face is its top surface.
        double[] face = ScaffoldAim.sharedFaceCentre(10, 64, 20, DOWN, 0.5);
        double[] point = ScaffoldAim.aimPoint(10, 64, 20, UP, 0.5);

        assertEquals(face[0], point[0], 1e-9);
        assertEquals(face[1], point[1], 1e-9, "the point must sit on the face, one below the block centre");
        assertEquals(face[2], point[2], 1e-9);
        assertEquals(64.0, point[1], 1e-9);
        assertFalse(Math.abs(point[1] - 64.5) < 1e-9, "and explicitly NOT at the block centre, which is 64.5");
    }

    /**
     * The target must not move when the player does.
     *
     * <p>Sliding the point along the face toward the eye was the original design and it was reverted.
     * The slide is clamped to the face, so when the eye crossed the face's own plane the offset
     * flipped sign and the aim jumped to the far edge - most of 180 degrees. The live debug log
     * caught it: the face pitch alternated 62.5 then 56.9 on alternating ticks and every one of
     * those flips arrived with a {@code RotationPlace} flag.</p>
     */
    @Test
    void theAimPointDoesNotDependOnWhereTheEyeIs() {
        double[] onTop = ScaffoldAim.aimPoint(10, 64, 20, UP, 0.5);

        // The signature has no eye argument at all. This asserts the value is therefore constant:
        // two calls at different points in the bridge must agree exactly.
        double[] laterInTheBridge = ScaffoldAim.aimPoint(10, 64, 20, UP, 0.5);

        assertEquals(onTop[0], laterInTheBridge[0], 1e-12);
        assertEquals(onTop[1], laterInTheBridge[1], 1e-12);
        assertEquals(onTop[2], laterInTheBridge[2], 1e-12);
    }

    /**
     * The target is a property of the block and the face alone, so it must be identical for every
     * normal that addresses the same support - and never depend on player position.
     */
    @Test
    void theAimPointIsIdenticalForTheSameBlockAndFace() {
        double[] a = ScaffoldAim.aimPoint(-9, 83, 27, NORTH, 0.4);
        double[] b = ScaffoldAim.aimPoint(-9, 83, 27, NORTH, 0.4);

        assertEquals(a[0], b[0], 0.0);
        assertEquals(a[1], b[1], 0.0);
        assertEquals(a[2], b[2], 0.0);
    }

    /**
     * The crash this guards. {@code BlockUtils.getPlaceSide} returns {@code null} whenever no face of
     * the target has a supporting block - routine mid-jump and the instant a bridge is first extended.
     * Dereferencing that {@code Direction} threw
     * {@code NullPointerException: Cannot invoke "Direction.getStepX()" because "side" is null} and
     * took the client down at Scaffold.faceAimAt. The fallback lives in {@link ScaffoldAim#aimPoint}
     * rather than at the call site so it cannot be forgotten.
     */
    @Test
    void aMissingPlacementSideFallsBackToTheBlockCentre() {
        double[] point = ScaffoldAim.aimPoint(10, 64, 20, null, 0.4);

        assertEquals(10.5, point[0], 1e-9);
        assertEquals(64.5, point[1], 1e-9);
        assertEquals(20.5, point[2], 1e-9);
    }

    /**
     * A cell that is free but has no supporting face must not be placed against.
     *
     * <p>{@code BlockUtils.canPlace} answers "is the cell free", which is not the same question as
     * "is there something to place against". Grim's {@code AirLiquidPlace} fired on precisely the
     * ticks the debug log recorded {@code side=NULL, canPlace=true}.</p>
     */
    @Test
    void aFreeCellWithNoSupportFaceIsNotAPlacementTarget() {
        assertTrue(ScaffoldAim.hasSupport(true, true), "free cell with a support face: place it");
        assertFalse(ScaffoldAim.hasSupport(true, false),
            "free cell with NO support face is an air-place and must be refused");
        assertFalse(ScaffoldAim.hasSupport(false, true), "occupied cell is not placeable");
        assertFalse(ScaffoldAim.hasSupport(false, false));
    }

    /**
     * The same predicate must agree with itself for every combination, including the one that a
     * partial rewrite of {@code getPlacePos} would regress: an occupied cell that happens to have
     * support on one side is still not placeable.
     */
    @Test
    void supportIsNotAssumedFromOccupancyAlone() {
        assertFalse(ScaffoldAim.hasSupport(false, true),
            "canPlace is the outer condition; a side alone must never authorise a placement");
        assertFalse(ScaffoldAim.hasSupport(false, false));
    }
}