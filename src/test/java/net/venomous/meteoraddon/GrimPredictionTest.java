package net.venomous.meteoraddon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Grim's movement prediction, asked directly.
 *
 * <p>These are the questions the live log could not answer. The server says "flagged" and how big
 * the difference was; the model says what the difference is made of, and which of the candidate
 * causes it fits.</p>
 *
 * <p>The one that matters most is the last group: the input compensation was removed on the
 * strength of a claim that it could never stay on the grid, and this model says that claim was
 * wrong. Rotating the input by the difference between the two yaws makes the client's walk
 * <em>exactly</em> the walk the server predicted - the rotation is a rotation, so it composes
 * with the other one and the two yaws cancel. What broke it in 7.14.0 was applying it twice.</p>
 */
public class GrimPredictionTest {
    private static final double TOLERANCE = 1.0E-9D;

    @Test
    void theOffsetIsZeroWhenTheCameraAndThePacketAgree() {
        for (double yaw : new double[] {0.0D, 37.0D, 90.0D, 180.0D, -135.0D}) {
            assertEquals(0.0D, GrimPrediction.offsetForCamera(yaw, yaw, GrimPrediction.WALK_SPEED),
                TOLERANCE, "the camera and the packet agree at " + yaw);
        }
    }

    /**
     * A third of a degree is already a millimetre.
     *
     * <p>The offset is the length of the walk times the sine of the yaw error, and the walk is a
     * fifth of a block, so the error has to stay under about a quarter of a degree for the
     * difference to stay under Grim's threshold. That is why the camera's yaw and the yaw in the
     * packet have to be the same number and not merely close.</p>
     */
    @Test
    void aThirdOfADegreeIsAlreadyAMillimetre() {
        double error = GrimPrediction.yawErrorFor(GrimPrediction.THRESHOLD, GrimPrediction.WALK_SPEED);

        assertTrue(error > 0.2D && error < 0.3D,
            "the yaw error that costs a millimetre is " + error + " degrees");

        assertTrue(GrimPrediction.offsetForCamera(0.0D, 1.0D, GrimPrediction.WALK_SPEED)
                > GrimPrediction.THRESHOLD,
            "and a whole degree of error is well over it");
    }

    /**
     * Forty-five degrees of error is nothing, when the input is a diagonal.
     *
     * <p>Grim accepts the eight directions around the yaw in the packet, and a diagonal key
     * combination is one of them - so a client walking diagonally whose camera is forty-five
     * degrees off the packet's yaw is walking exactly along a direction Grim is willing to
     * accept. This is why a module that cannot control the player's keys has to send the camera's
     * own yaw rather than something near it.</p>
     */
    @Test
    void fortyFiveDegreesOfErrorIsNothingWhenTheInputIsDiagonal() {
        double[] client = GrimPrediction.movement(
            GrimPrediction.input(1, 1), GrimPrediction.WALK_SPEED, 0.0D);

        double offset = GrimPrediction.offset(client[0], client[1], 45.0D, GrimPrediction.WALK_SPEED);

        assertEquals(0.0D, offset, TOLERANCE,
            "a diagonal walk at forty-five degrees to the packet's yaw is one of the eight "
                + "directions Grim accepts");
    }

    /**
     * The compensation makes the walk exactly the server's prediction.
     *
     * <p>Rotating the input by the difference between the packet's yaw and the camera's yaw makes
     * the client's walk, which the game computes along the camera's yaw, come out along the
     * packet's yaw instead - and along it at the same length, because a rotation preserves the
     * length of the input and the input's length is the speed.</p>
     *
     * <p>This corrects a claim made in 7.16.0, that the compensation could never stay on the grid
     * and that the 0.156 in the 7.14.0 log was its fault. It could, and it was not.</p>
     */
    @Test
    void theCompensationMakesTheWalkExactlyTheServersPrediction() {
        double[] original = GrimPrediction.input(0, 1);

        for (double serverYaw : new double[] {0.0D, 37.0D, 90.0D, 180.0D}) {
            for (double cameraYaw : new double[] {0.0D, -60.0D, 45.0D, 170.0D}) {
                double[] compensated = GrimPrediction.compensatedInput(original, serverYaw, cameraYaw);

                double[] client = GrimPrediction.movement(compensated, GrimPrediction.WALK_SPEED, cameraYaw);

                assertEquals(0.0D,
                    GrimPrediction.offset(client[0], client[1], serverYaw, GrimPrediction.WALK_SPEED),
                    TOLERANCE,
                    "server " + serverYaw + " camera " + cameraYaw);
            }
        }
    }

    /**
     * Applying the compensation twice is what broke it.
     *
     * <p>The 7.14.0 build applied the rotation inside {@code LocalPlayer.modifyInput}, which
     * ViaFabricPlus redirects, so on some ticks the input was rotated twice. The two rotations
     * compose into one turn of twice the difference, and the walk comes out at an angle that no key
     * combination produces - which is a difference of up to a quarter of a block, not a
     * millimetre.</p>
     *
     * <p>It is worth being precise about the size: the doubled turn lands on Grim's grid again
     * when it is a multiple of forty-five degrees, so the damage depends on the angle between the
     * two yaws, and the worst case is a fifth of a block. The log's 0.156 is larger than a
     * walking client's whole movement, so whatever that run did, this is not all of it.</p>
     */
    @Test
    void applyingTheCompensationTwiceIsWhatBrokeIt() {
        double[] original = GrimPrediction.input(0, 1);

        // Thirty degrees is not a multiple of forty-five, so the doubled turn is sixty degrees
        // and the walk lands between two of the directions Grim accepts.
        double[] twice = GrimPrediction.compensatedInput(
            GrimPrediction.compensatedInput(original, 30.0D, 0.0D), 30.0D, 0.0D);

        double[] client = GrimPrediction.movement(twice, GrimPrediction.WALK_SPEED, 0.0D);
        double offset = GrimPrediction.offset(client[0], client[1], 30.0D, GrimPrediction.WALK_SPEED);

        assertTrue(offset > GrimPrediction.THRESHOLD * 50.0D,
            "a doubly rotated input walks at an angle no key combination produces, and the offset "
                + "is worth " + offset + " blocks - fifty times Grim's threshold, and a quarter of "
                + "the walk itself");
    }

    /**
     * Ninety degrees of error is harmless, doubled or not.
     *
     * <p>Grim accepts the eight directions around the packet's yaw, so a turn of ninety degrees
     * lands on the grid again. Which is why the damage from a doubled compensation depends on the
     * angle, and why a run can look clean and then not.</p>
     */
    @Test
    void ninetyDegreesOfErrorLandsBackOnTheGrid() {
        double[] twice = GrimPrediction.compensatedInput(
            GrimPrediction.compensatedInput(GrimPrediction.input(0, 1), 90.0D, 0.0D), 90.0D, 0.0D);

        double[] client = GrimPrediction.movement(twice, GrimPrediction.WALK_SPEED, 0.0D);

        assertEquals(0.0D,
            GrimPrediction.offset(client[0], client[1], 90.0D, GrimPrediction.WALK_SPEED),
            TOLERANCE,
            "ninety degrees is a multiple of forty-five, so the walk lands on a direction Grim "
                + "accepts however many times the compensation is applied");
    }

    /**
     * What the residual offset in the live log corresponds to.
     *
     * <p>The log shows 0.009 again and again, once per placement. The camera and the packet carry
     * the same yaw, so it is not a rotation difference - and at a walking speed an offset of
     * 0.009 would need about two and a half degrees of yaw error, which is more than a mouse
     * moves in a tick.</p>
     */
    @Test
    void theResidualOffsetIsNotAYawDifference() {
        double error = GrimPrediction.yawErrorFor(0.009D, GrimPrediction.WALK_SPEED);

        assertTrue(error > 2.0D,
            "0.009 blocks at a walking speed is " + error + " degrees of yaw error, which is more "
                + "than a mouse moves between two ticks - so the residual is something else");
    }
}
