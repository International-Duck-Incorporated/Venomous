package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.MovementMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The input compensation, which is what makes freelook possible.
 *
 * <p>The client computes its movement from the camera's yaw and the server predicts that movement
 * from the yaw in the movement packet. A scaffold spoofs that yaw to point at the block being
 * placed, which is behind the player whenever they bridge backwards. Without compensation the two
 * disagree and Grim's {@code Simulation} - which is {@code OffsetHandler} and flags at one
 * millimetre - reports the difference on every movement packet.</p>
 */
public class InputCompensationTest {
    private static final float EPSILON = 1.0E-4F;

    /**
     * The two cases that matter.
     *
     * <p>The player faces north and holds back, and the server is told to look south. The client
     * must be told to walk forwards instead, because the server will read "forwards at yaw south"
     * as "walking south" and expects the player to move south.</p>
     */
    @Test
    void theCompensatedInputProducesTheMovementTheServerExpects() {
        // Facing north: the camera's yaw is 180.
        // The server is told to look south: 0.
        float[] compensated = MovementMath.compensateInput(0.0F, -1.0F, 0.0F - 180.0F);

        assertEquals(0.0F, compensated[0], EPSILON, "no strafe in, no strafe out");
        assertEquals(1.0F, compensated[1], EPSILON,
            "holding back with the server looking the other way has to become holding forwards, "
                + "or the client walks the opposite way to the one the server predicted");
    }

    /**
     * With no difference between the two yaws, the input is untouched.
     *
     * <p>This is the case that has to hold when the module is off, or when the player happens to be
     * looking the way the server is told.</p>
     */
    @Test
    void anUnchangedRotationLeavesTheInputAlone() {
        float[] compensated = MovementMath.compensateInput(0.3F, -0.8F, 0.0F);

        assertEquals(0.3F, compensated[0], EPSILON);
        assertEquals(-0.8F, compensated[1], EPSILON);
    }

    /**
     * The compensation has to be a rotation: it turns the input, it does not change its length.
     *
     * <p>The game scales the input for sneaking and for using an item, and that scaling has to
     * survive the rotation - otherwise compensating the input would quietly undo the sneak.</p>
     */
    @Test
    void theCompensationRotatesTheInputWithoutChangingItsLength() {
        float strafe = 0.6F;
        float forward = -0.8F;

        float before = (float) Math.sqrt(strafe * strafe + forward * forward);

        for (float delta : new float[] {0.0F, 37.0F, 90.0F, 145.0F, 180.0F, -110.0F}) {
            float[] compensated = MovementMath.compensateInput(strafe, forward, delta);
            float after = (float) Math.sqrt(
                compensated[0] * compensated[0] + compensated[1] * compensated[1]);

            assertEquals(before, after, EPSILON, "the input length changed at " + delta + " degrees");
        }
    }

    /**
     * A quarter turn swaps the two axes.
     *
     * <p>This pins the direction of the rotation, which is the one thing about it that could
     * plausibly be backwards - and being backwards means the player strafes the wrong way.</p>
     */
    @Test
    void aQuarterTurnRotatesTheInputByNinetyDegrees() {
        float[] compensated = MovementMath.compensateInput(1.0F, 0.0F, 90.0F);

        assertEquals(0.0F, compensated[0], EPSILON, "a strafe becomes a forward at ninety degrees");
        assertEquals(1.0F, compensated[1], EPSILON);
    }

    /**
     * And the movement it produces is the movement the server expects.
     *
     * <p>Checked end to end rather than in pieces: the client faces north and holds back, the
     * server is told to look south, and the world direction the client ends up walking has to be
     * the world direction the server expects from "forwards while looking south".</p>
     */
    @Test
    void theWorldDirectionTheClientWalksIsTheOneTheServerExpects() {
        float cameraYaw = 180.0F;
        float serverYaw = 0.0F;

        float[] compensated = MovementMath.compensateInput(0.0F, -1.0F, serverYaw - cameraYaw);

        // The world direction of a move is the input rotated by the yaw it is taken at.
        double clientRadians = Math.toRadians(cameraYaw);
        double clientX = compensated[0] * Math.cos(clientRadians)
            - compensated[1] * Math.sin(clientRadians);
        double clientZ = compensated[0] * Math.sin(clientRadians)
            + compensated[1] * Math.cos(clientRadians);

        double serverRadians = Math.toRadians(serverYaw);
        double serverX = 0.0 * Math.cos(serverRadians) - (-1.0) * Math.sin(serverRadians);
        double serverZ = 0.0 * Math.sin(serverRadians) + (-1.0) * Math.cos(serverRadians);

        assertEquals(serverX, clientX, EPSILON, "the client and the server disagree about the x axis");
        assertEquals(serverZ, clientZ, EPSILON, "the client and the server disagree about the z axis");
        assertTrue(Math.abs(clientZ) > 0.5, "and the player should actually be moving");
    }
}
