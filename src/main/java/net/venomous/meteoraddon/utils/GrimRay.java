package net.venomous.meteoraddon.utils;

/**
 * A local replica of the geometry Grim's scaffolding checks use.
 *
 * <p>This exists because the server only says "flagged", never why, and guessing from angles was
 * wrong twice already. Reproducing the check on the client turns "Grim says RotationPlace" into
 * "this ray misses this box, and here is the angle that would have hit it", which is answerable
 * without a round trip. It is what the placement is gated on, so it is part of the module rather
 * than instrumentation.</p>
 *
 * <p>The slab method and its comparisons are transcribed from Grim's
 * {@code ReachUtils.calculateIntercept} (1.8 branch), which the 2.0 scaffolding checks still call.
 * The candidate set matches {@code RotationPlace}: the current rotation, the previous yaw with the
 * current pitch, and the fully previous rotation - any one hitting is a pass.</p>
 */
public final class GrimRay {
    private GrimRay() {
    }

    /** Grim's eye positions for a living player: the standing height and the sneaking one. */
    public static final double[] EYE_HEIGHTS = {1.62D, 1.27D};

    /**
     * Whether a ray from the eye at {@code (yaw, pitch)} meets an axis-aligned box.
     *
     * <p>Direction convention copied from Grim: {@code (-sin(yaw)cos(pitch), -sin(pitch),
     * cos(yaw)cos(pitch))} in degrees, matching Minecraft's {@code getLookVector}.</p>
     *
     * @param minX/minY/minZ the box minimum
     * @param maxX/maxY/maxZ the box maximum
     */
    public static boolean hits(double eyeX, double eyeY, double eyeZ, double yaw, double pitch,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {
        return hits(eyeX, eyeY, eyeZ, yaw, pitch, minX, minY, minZ, maxX, maxY, maxZ,
            Double.POSITIVE_INFINITY);
    }

    /** As above, but bounded to a maximum distance along the ray. */
    public static boolean hits(double eyeX, double eyeY, double eyeZ, double yaw, double pitch,
                               double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ, double maxDistance) {
        double ry = Math.toRadians(yaw);
        double rp = Math.toRadians(pitch);

        double dx = -Math.sin(ry) * Math.cos(rp);
        double dy = -Math.sin(rp);
        double dz = Math.cos(ry) * Math.cos(rp);

        double tMin = Double.NEGATIVE_INFINITY;
        double tMax = Double.POSITIVE_INFINITY;

        // Grim's axis-parallel guard: squared direction below this counts as parallel.
        double parallel = 1.0000000116860974E-7;

        if (dx * dx < parallel) {
            if (eyeX < minX || eyeX > maxX) return false;
        } else {
            double t1 = (minX - eyeX) / dx;
            double t2 = (maxX - eyeX) / dx;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        if (dy * dy < parallel) {
            if (eyeY < minY || eyeY > maxY) return false;
        } else {
            double t1 = (minY - eyeY) / dy;
            double t2 = (maxY - eyeY) / dy;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        if (dz * dz < parallel) {
            if (eyeZ < minZ || eyeZ > maxZ) return false;
        } else {
            double t1 = (minZ - eyeZ) / dz;
            double t2 = (maxZ - eyeZ) / dz;
            tMin = Math.max(tMin, Math.min(t1, t2));
            tMax = Math.min(tMax, Math.max(t1, t2));
        }

        // The box is behind the eye, out of reach, or the ray grazes past it.
        return tMax >= Math.max(tMin, 0.0D) && tMin <= maxDistance;
    }

    /**
     * {@code RotationPlace}'s actual pass condition: the union of its candidate rotations and eye
     * heights, plus the "free pass" where the eye box itself overlaps the target.
     */
    public static boolean rotationPlaceWouldPass(double eyeX, double eyeZ, double feetY,
                                                  double yaw, double pitch,
                                                  double lastYaw, double lastPitch,
                                                  double minX, double minY, double minZ,
                                                  double maxX, double maxY, double maxZ) {
        for (double h : EYE_HEIGHTS) {
            double ey = feetY + h;

            if (hits(eyeX, ey, eyeZ, yaw, pitch, minX, minY, minZ, maxX, maxY, maxZ)) return true;
            if (hits(eyeX, ey, eyeZ, lastYaw, pitch, minX, minY, minZ, maxX, maxY, maxZ)) return true;
            if (hits(eyeX, ey, eyeZ, lastYaw, lastPitch, minX, minY, minZ, maxX, maxY, maxZ)) {
                return true;
            }

            // Free pass: the eye's own box already overlaps the target, so there is no angle to
            // get wrong - the player is close enough that the placement cannot be mistaken.
            if (eyeX >= minX && eyeX <= maxX
                && eyeZ >= minZ && eyeZ <= maxZ
                && ey >= minY && ey <= maxY) {
                return true;
            }
        }

        return false;
    }

    /** Grimming's reach gate, {@code BLOCK_INTERACTION_RANGE} with no slack on this check. */
    public static boolean withinReach(double eyeX, double eyeY, double eyeZ,
                                      double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ) {
        double cx = Math.max(minX, Math.min(eyeX, maxX));
        double cy = Math.max(minY, Math.min(eyeY, maxY));
        double cz = Math.max(minZ, Math.min(eyeZ, maxZ));

        double dx = cx - eyeX;
        double dy = cy - eyeY;
        double dz = cz - eyeZ;

        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= 4.5D;
    }
}