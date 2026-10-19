package net.venomous.meteoraddon.utils;

/**
 * Where to point when placing a block against a given face.
 *
 * <p>Free of Minecraft types so it unit-tests on a plain JVM, in the same spirit as {@link AimEase},
 * {@link ScaffoldGates} and {@link ScaffoldCadence}.</p>
 *
 * <h2>Why the face, and not the block centre</h2>
 *
 * <p>Grim's {@code RotationPlace} ("Placed a block while not looking at it") ray-traces from the
 * player's eyes along the <em>server</em> rotation and asks whether that ray reaches the block being
 * placed. {@code AirLiquidPlace} fires when the resulting position has no valid support. Both fire
 * together when the aim lands somewhere the placement cannot legitimately come from - which is what
 * a bridge over the edge looks like.</p>
 *
 * <p>Aiming at the block's centre is only right when the eye happens to be square to the face. While
 * bridging, the player is above and behind the cell they are filling, so a ray at the centre enters
 * through a face the player is not actually addressing, and the resolved click position can land on
 * air.</p>
 *
 * <p>What a person does is look at the nearest point of the face they are clicking. That point is
 * the centre of the face, slid along the face toward the eye and clamped to the block's own
 * extent. It is always on the correct face, always reachable, and it is the point whose normal
 * best faces the eye.</p>
 */
public final class ScaffoldAim {
    private ScaffoldAim() {
    }

    /**
     * The point on the shared face that is closest to the eye.
     *
     * @param faceCentre the centre of the face being clicked, in block coordinates
     * @param normal     unit face normal, pointing from the support towards the eye's side
     * @param eyeX/eyeY/eyeZ the player's eye position
     * @param halfExtent 0.5 for a full block; the face spans {@code [-half, +half]} per axis
     * @return the aim point as {@code {x, y, z}}
     */
    public static double[] nearestPointOnFace(double[] faceCentre, double[] normal,
                                              double eyeX, double eyeY, double eyeZ,
                                              double halfExtent) {
        double[] point = faceCentre.clone();

        for (int axis = 0; axis < 3; axis++) {
            // Only axes the face does not lie along may be slid across the face.
            if (Math.abs(normal[axis]) > 1.0E-6) continue;

            double eye = axis == 0 ? eyeX : axis == 1 ? eyeY : eyeZ;
            double offset = clamp(eye - point[axis], -halfExtent, halfExtent);
            point[axis] += offset;
        }

        return point;
    }

    /**
     * The face centre shared by a support block and the block being placed against it.
     *
     * <p>{@code support} is the existing block; {@code normal} points from the support towards the
     * new block. The shared face sits half a block along the normal from the support's centre.</p>
     */
    public static double[] sharedFaceCentre(double supportX, double supportY, double supportZ,
                                            double[] normal, double halfExtent) {
        return new double[] {
            supportX + 0.5 + normal[0] * halfExtent,
            supportY + 0.5 + normal[1] * halfExtent,
            supportZ + 0.5 + normal[2] * halfExtent
        };
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : value > max ? max : value;
    }
}