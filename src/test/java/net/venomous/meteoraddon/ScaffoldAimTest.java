package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.ScaffoldAim;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
     * The bug: aiming at the block's centre only works when the eye is square to the face. While
     * bridging, the eye is above and behind, so the ray enters through a face the player is not
     * addressing and Grim resolves a position with no valid support.
     */
    @Test
    void theAimPointSlidesAcrossTheFaceTowardsTheEye() {
        // Placing on top of the block at (10, 64, 20): face centre is (10.5, 64.0, 20.5).
        double[] face = ScaffoldAim.sharedFaceCentre(10, 64, 20, DOWN, 0.5);

        // Eye is above and well to the -Z side.
        double[] point = ScaffoldAim.nearestPointOnFace(face, UP, 10.9, 70.0, 15.0, 0.4);

        assertEquals(64.0, point[1], 1e-9, "the point must stay on the face - never pushed off it");
        assertTrue(point[2] < 20.5, "it should slide towards the eye's -Z, not stay at the centre");
        assertTrue(point[0] > 10.5, "and towards the eye's +X");
    }

    @Test
    void theAimPointIsClampedToTheBlockExtent() {
        double[] face = ScaffoldAim.sharedFaceCentre(0, 0, 0, DOWN, 0.5);

        // Eye absurdly far along both in-plane axes.
        double[] point = ScaffoldAim.nearestPointOnFace(face, UP, 500.0, 70.0, -500.0, 0.4);

        assertEquals(0.9, point[0], 1e-9, "clamped to faceCentre.x + halfExtent");
        assertEquals(0.1, point[2], 1e-9, "faceCentre.z 0.5 slid -0.4 towards the eye");
        assertEquals(0.0, point[1], 1e-9, "still on the face plane (support 0.0 - half a block)");
    }

    @Test
    void thePointNeverLeavesTheBlockEvenForALookingDownward() {
        double[] face = ScaffoldAim.sharedFaceCentre(0, 64, 0, DOWN, 0.5);

        double[] point = ScaffoldAim.nearestPointOnFace(face, UP, 0.5, 80.0, 0.5, 0.5);

        for (int axis = 0; axis < 3; axis++) {
            assertTrue(point[axis] >= face[axis] - 0.51 && point[axis] <= face[axis] + 0.51,
                "axis " + axis + " left the face: " + point[axis] + " vs " + face[axis]);
        }
    }

    /**
     * A side face must slide along its own axes only. If the normal's axis were slid too, the aim
     * would leave the face entirely - which is the failure being fixed.
     */
    @Test
    void onlyAxesTheFaceDoesNotLieAlongAreSlid() {
        double[] face = ScaffoldAim.sharedFaceCentre(10, 64, 20, NORTH, 0.5);

        // Eye far above; the +Y axis is in-plane for a NORTH-facing wall, so it may slide.
        double[] point = ScaffoldAim.nearestPointOnFace(face, NORTH, 10.5, 200.0, 20.5, 0.4);

        assertEquals(64.9, point[1], 1e-9, "a wall face can slide vertically, clamped to the extent");
        assertEquals(20.0, point[2], 1e-9, "but the Z axis is the face normal and must not move");
        assertEquals(10.5, point[0], 1e-9, "nor the X axis, which is in-plane but the eye matches");
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
    double[] point = ScaffoldAim.aimPoint(10, 64, 20, null, 10.5, 70.0, 15.0, 0.4);

    assertEquals(10.5, point[0], 1e-9);
    assertEquals(64.5, point[1], 1e-9);
    assertEquals(20.5, point[2], 1e-9);
}

@Test
void aValidSideIsUsedRatherThanTheCentre() {
    double[] nullSide = ScaffoldAim.aimPoint(10, 64, 20, null, 10.9, 70.0, 15.0, 0.4);
    double[] withSide = ScaffoldAim.aimPoint(10, 64, 20, UP, 10.9, 70.0, 15.0, 0.4);

    org.junit.jupiter.api.Assertions.assertNotEquals(nullSide[2], withSide[2],
        "a valid side must slide the point towards the eye, so the two must differ");
}

@Test
void anEyeExactlyOnTheFaceCentreLeavesThePointThere() {
        double[] face = ScaffoldAim.sharedFaceCentre(4, 70, -9, DOWN, 0.5);

        double[] point = ScaffoldAim.nearestPointOnFace(face, UP, face[0], face[1] + 5, face[2], 0.4);

        assertEquals(face[0], point[0], 1e-9);
        assertEquals(face[1], point[1], 1e-9);
        assertEquals(face[2], point[2], 1e-9);
    }
}