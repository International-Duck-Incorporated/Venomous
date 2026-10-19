package net.venomous.meteoraddon.utils;

/**
 * The camera the scaffold module is holding, and whether it is holding it at all.
 *
 * <p>The module owns the camera while it is bridging, and gives it straight back the moment it is
 * not: no target, or the player is fighting and cannot use a camera that is being driven.</p>
 *
 * <p>The angle is applied at the head of the player's tick, after the mouse has been read and
 * before the movement is computed. That position matters more than it looks. The client's movement
 * is computed from the camera's yaw, and the server predicts that movement from the yaw in the
 * movement packet, so the two have to be the same angle or the client walks a line the server did
 * not predict - a millimetre of movement per tick, which is Grim's Simulation check.</p>
 */
public final class ScaffoldCamera {
    private static boolean active = false;
    private static float yaw = 0.0F;
    private static float pitch = 0.0F;

    private ScaffoldCamera() {
    }

    /** Takes the camera. */
    public static void take(float yaw, float pitch) {
        ScaffoldCamera.active = true;
        ScaffoldCamera.yaw = yaw;
        ScaffoldCamera.pitch = pitch;
    }

    /** Gives the camera back to the player. */
    public static void release() {
        ScaffoldCamera.active = false;
    }

    public static boolean isTaken() {
        return ScaffoldCamera.active;
    }

    public static float yaw() {
        return ScaffoldCamera.yaw;
    }

    public static float pitch() {
        return ScaffoldCamera.pitch;
    }
}
