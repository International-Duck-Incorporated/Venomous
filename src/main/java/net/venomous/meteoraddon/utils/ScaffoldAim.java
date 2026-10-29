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
     * @param normal     unit face normal from the placed block towards its support, or {@code null}
     * @param halfExtent 0.5 for a full block; 0 aims at the block's centre instead
     */
    public static double[] aimPoint(double blockX, double blockY, double blockZ, double[] normal,
                                    double halfExtent) {
        if (normal == null) {
            // No support on any face: there is nothing sensible to aim at, and the placement is
            // refused anyway by hasSupport. The block centre is the safe, always-valid answer.
            return new double[] {blockX + 0.5, blockY + 0.5, blockZ + 0.5};
        }

        return clickPoint(blockX, blockY, blockZ, normal, halfExtent);
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
     * The centre of the face of {@code pos} that the placement clicks.
     *
     * <p>{@code normal} is Meteor's {@code BlockUtils.getPlaceSide} result, which points
     * <em>from the block being placed towards the block supporting it</em>. That direction is the
     * one thing about this call that has to be exactly right, and it was wrong for a long time: the
     * support is {@code pos + normal}, not {@code pos - normal}, so treating the normal as
     * support-to-block put the aim a full block past the target, out on the empty side.
     * {@code BlockUtils.place} builds its hit position as {@code atCenterOf(pos)} plus half a
     * block along that same normal, and this is that expression.</p>
     *
     * <p>The point sits on the face the placement shares with its support, which is also the point
     * a person clicks and the first thing the ray leaves the eye towards.</p>
     */
    public static double[] clickPoint(double blockX, double blockY, double blockZ, double[] normal,
                                      double halfExtent) {
        return new double[] {
            blockX + 0.5 + normal[0] * halfExtent,
            blockY + 0.5 + normal[1] * halfExtent,
            blockZ + 0.5 + normal[2] * halfExtent
        };
    }

    /**
     * The yaw to look at a face whose normal is horizontal.
     *
     * <p>This is the heading of the support's side, and it is deliberately a discrete answer rather
     * than a computed angle. The placement face is half a block from the player's feet, so the
     * angle to it is measured against a horizontal offset of a few tenths of a block: the
     * geometric yaw to it swings by ten to seventeen degrees for a fraction of a block of movement,
     * and it swings in whichever direction the player happens to be drifting.</p>
     *
     * <p>That matters because the camera and the rotation the server is told are the same angle,
     * and the camera is what the movement is computed from. A camera that chases a noisy target is
     * a feedback loop: the camera turns, the player walks a different way, the target moves, the
     * camera turns further. The live log caught that loop as a pendulum swinging thirty degrees
     * either side of the target, one swing every ten ticks.</p>
     *
     * <p>The side of the support does not move as the player walks - it only changes when the
     * bridge advances a block - so this heading is stable, and the camera settles on it instead
     * of chasing itself.</p>
     *
     * @param normal the face normal, as returned by Meteor's place-side resolution
     * @return the yaw that faces that normal, in Minecraft's convention
     */
    public static float headingFor(double[] normal) {
        if (normal == null || normal[1] != 0.0D) {
            // A vertical face has no heading of its own; the caller keeps the current one.
            return Float.NaN;
        }

        if (normal[2] < 0.0D) return 180.0F;   // north
        if (normal[2] > 0.0D) return 0.0F;     // south
        if (normal[0] > 0.0D) return -90.0F;   // east
        return 90.0F;                          // west
    }

    /**
     * The pitch that looks at a point, from an eye.
     *
     * <p>Independent of the yaw, which is the point of it. A ray is a yaw and a pitch together: the
     * yaw fixes the horizontal direction and the pitch fixes how far down it goes. The elevation of
     * a point does not depend on which way the head is turned - only whether the head is turned
     * <em>at</em> it - so the pitch that aims at a point is the same whatever the yaw is.</p>
     *
     * <p>That matters because the yaw belongs to the player. Grim's movement prediction rotates its
     * candidate movement directions by the yaw in the movement packet, so the client's movement has
     * to run along the camera's yaw for the server's prediction to match; the pitch is the only
     * axis the module can spend on aiming at the block being placed.</p>
     *
     * @return the pitch in degrees, positive when looking down, as Minecraft stores it
     */
    public static float pitchToPoint(double eyeX, double eyeY, double eyeZ,
                                      double pointX, double pointY, double pointZ) {
        double dx = pointX - eyeX;
        double dy = pointY - eyeY;
        double dz = pointZ - eyeZ;

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-6D) {
            // Straight up or straight down: there is no horizontal direction to speak of.
            return dy > 0.0D ? -90.0F : 90.0F;
        }

        return (float) -Math.toDegrees(Math.atan2(dy, horizontal));
    }
}
