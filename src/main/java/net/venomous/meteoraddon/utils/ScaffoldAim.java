package net.venomous.meteoraddon.utils;

/**
 * Where to point when placing a block against a given face, and whether placing is legal at all.
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
 * air. The centre of the face being placed against is always on the correct face and always inside
 * the block, whatever the eye is doing.</p>
 *
 * <h2>Why the face centre, and not the point nearest the eye</h2>
 *
 * <p>Sliding the point along the face toward the eye looks more like a human, and it was what this
 * class did first. It does not survive contact with movement: the slide is clamped to the face, so
 * when the eye crosses the face's own plane the offset flips sign and the target jumps to the far
 * edge - a swing of most of 180 degrees. On a live run the face pitch alternated between 62.5 and
 * 56.9 degrees on alternating ticks, and each of those flips arrived with a {@code RotationPlace}
 * flag. The face centre is the same face from either side and never moves, so it is what ships.</p>
 */
public final class ScaffoldAim {
    private ScaffoldAim() {
    }

    /**
     * The aim point for a placement, falling back to the block centre when there is no valid side.
     *
     * <p>This is the single entry point the module uses, so the null case lives here where it is
     * unit-tested. It is not hypothetical: {@code BlockUtils.getPlaceSide} returns {@code null}
     * whenever no face of the target has a supporting block, which happens routinely mid-jump and
     * the instant a bridge is first extended. Calling {@code side.getStepX()} on that result
     * crashed the client with a NullPointerException.</p>
     *
     * @param blockX/Y/Z the block being placed, in block coordinates
     * @param normal     unit face normal from the support towards the new block, or {@code null}
     * @param halfExtent 0.5 for a full block; the face spans {@code [-half, +half]} per axis
     */
    public static double[] aimPoint(double blockX, double blockY, double blockZ, double[] normal,
                                    double halfExtent) {
        if (normal == null) {
            // No support on any face: there is nothing sensible to aim at, and the placement is
            // refused anyway by hasSupport. The block centre is the safe, always-valid answer.
            return new double[] {blockX + 0.5, blockY + 0.5, blockZ + 0.5};
        }

        return sharedFaceCentre(
            blockX - normal[0], blockY - normal[1], blockZ - normal[2], normal, halfExtent);
    }

    /**
     * Whether a placement should be attempted at all.
     *
     * <p>{@code BlockUtils.canPlace} answers "is this cell free" and returns true even when no face
     * of it has support - which is exactly the case where placing produces an air-place. Grim's
     * {@code AirLiquidPlace} fired on precisely the ticks the debug log showed {@code side=NULL},
     * with {@code canPlace=true}. A placement with no support face must not be attempted.</p>
     */
    public static boolean hasSupport(boolean canPlace, boolean hasSide) {
        return canPlace && hasSide;
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
}