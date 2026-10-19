package net.venomous.meteoraddon.utils;

/**
 * The rotation the server is being told, for the input compensation.
 *
 * <p>This is a mutable global because the two halves of it live on opposite sides of a mixin: the
 * scaffold module knows the angle the server is being told, and the mixin that alters the movement
 * input runs inside the game and has no way to ask the module. The same pattern Meteor uses for
 * {@code Rotations.serverYaw}.</p>
 *
 * <p>It is only non-zero while the scaffold is on and actively spoofing a rotation.</p>
 */
public final class InputCompensation {
    /**
     * The angle to rotate the movement input by, in degrees.
     *
     * <p>This is the server's yaw minus the camera's yaw. The client computes its movement from the
     * camera's yaw; the server predicts it from the yaw in the movement packet. When those differ,
     * the client walks a slightly different line from the one the server predicted, and the
     * difference is Grim's {@code Simulation} - which flags at one millimetre.</p>
     *
     * <p>Rotating the input by this angle makes the movement the client performs identical to the
     * movement the server expects, whatever the camera is pointing at.</p>
     */
    private static float yaw;

    private InputCompensation() {
    }

    public static void set(float yaw) {
        InputCompensation.yaw = yaw;
    }

    public static float yaw() {
        return yaw;
    }

    public static void clear() {
        InputCompensation.yaw = 0.0F;
    }
}
