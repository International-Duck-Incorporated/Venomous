package net.venomous.meteoraddon;

/**
 * A model of Grim's movement prediction, for tests.
 *
 * <p>This is the server's side of the question, transcribed so it can be asked without a server.
 * The live log only ever says "flagged" and how big the difference was; the model says what the
 * difference <em>is</em>, and why.</p>
 *
 * <p>It follows Grim 2.0 exactly:</p>
 *
 * <ul>
 *   <li>{@code InputTransformer.transformInputsToVector} normalises the key combination and
 *       scales it by 0.98 - the fraction of the movement speed a client actually gets.</li>
 *   <li>{@code InputTransformer.getMovementResultFromInput} rotates that input by the yaw in the
 *       movement packet: {@code x = sideways·cos - forward·sin}, {@code z = forward·cos +
 *       sideways·sin}.</li>
 *   <li>{@code PredictionEngine.loopVectors} brute-forces the nine possible key combinations
 *       (-1, 0, 1) and rotates each of them by the server's yaw, and the offset the check reports
 *       is how far the client's actual movement is from the closest of those nine.</li>
 * </ul>
 *
 * <p>That last point is the one that matters. Grim does not know which keys are held, so it accepts
 * any of the eight directions around the yaw in the packet - and nothing else. A client that
 * walks along its camera's yaw is accepted when the camera's yaw and the packet's yaw are the
 * same angle.</p>
 */
public final class GrimPrediction {
    /** Vanilla walking speed on flat ground, in blocks per tick. */
    public static final double WALK_SPEED = 0.2158D;

    /** Vanilla sprinting speed on flat ground, in blocks per tick. */
    public static final double SPRINT_SPEED = 0.2873D;

    /** Grim's movement threshold: a difference of one millimetre is enough to flag. */
    public static final double THRESHOLD = 0.001D;

    private GrimPrediction() {
    }

    /**
     * Grim's input vector for a key combination.
     *
     * @param strafe  sideways input, +1 for the left key
     * @param forward forwards input, +1 for the up key
     */
    public static double[] input(int strafe, int forward) {
        double length = Math.sqrt((double) strafe * strafe + (double) forward * forward);
        if (length < 1.0E-6D) return new double[] {0.0D, 0.0D};

        return new double[] {strafe / length * 0.98D, forward / length * 0.98D};
    }

    /**
     * The movement a yaw and an input produce.
     *
     * @param input the input vector, from {@link #input(int, int)}
     * @param speed the horizontal speed, in blocks per tick
     * @param yaw   the yaw the movement is computed from, in degrees
     */
    public static double[] movement(double[] input, double speed, double yaw) {
        double radians = Math.toRadians(yaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        return new double[] {
            (input[0] * cos - input[1] * sin) * speed,
            (input[1] * cos + input[0] * sin) * speed
        };
    }

    /**
     * The offset Grim would report for a client that moved by a given amount.
     *
     * @param clientDeltaX the client's own horizontal movement this tick
     * @param clientDeltaZ the client's own horizontal movement this tick
     * @param serverYaw    the yaw in the movement packet
     * @param speed        the horizontal speed, in blocks per tick
     * @return the distance to the nearest movement Grim is willing to accept
     */
    public static double offset(double clientDeltaX, double clientDeltaZ,
                                 double serverYaw, double speed) {
        double best = Double.MAX_VALUE;

        for (int strafe = -1; strafe <= 1; strafe++) {
            for (int forward = -1; forward <= 1; forward++) {
                double[] move = movement(input(strafe, forward), speed, serverYaw);
                double distance = Math.hypot(clientDeltaX - move[0], clientDeltaZ - move[1]);
                best = Math.min(best, distance);
            }
        }

        return best;
    }

    /** The offset for a client walking along {@code cameraYaw} while the packet says {@code serverYaw}. */
    public static double offsetForCamera(double cameraYaw, double serverYaw, double speed) {
        double[] client = movement(input(0, 1), speed, cameraYaw);
        return offset(client[0], client[1], serverYaw, speed);
    }

    /**
     * The yaw error that produces a given offset, in degrees.
     *
     * <p>The offset of a client that walks along the wrong angle is the length of its movement
     * times the sine of the error, so this is the angle the server's prediction and the client's
     * own walk disagree by.</p>
     */
    public static double yawErrorFor(double offset, double speed) {
        double ratio = Math.min(1.0D, Math.abs(offset) / speed);
        return Math.toDegrees(Math.asin(ratio));
    }

    /**
     * The input a client would have to use for its walk to match the server's prediction.
     *
     * <p>This is the compensation: rotating the input by the difference between the server's yaw
     * and the camera's yaw makes the client walk exactly the line the server predicted, and the
     * rotation preserves the input's length, so the speed is untouched.</p>
     */
    public static double[] compensatedInput(double[] input, double serverYaw, double cameraYaw) {
        double radians = Math.toRadians(serverYaw - cameraYaw);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);

        return new double[] {
            input[0] * cos - input[1] * sin,
            input[0] * sin + input[1] * cos
        };
    }
}
