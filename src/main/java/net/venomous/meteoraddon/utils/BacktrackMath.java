package net.venomous.meteoraddon.utils;

/**
 * The geometry behind the backtrack hold: is the target's <em>new</em> position a worse target
 * than the one our client still believes in?
 *
 * <p>Backtracking works by holding the movement packets of one entity, so this client keeps
 * aiming at where the server has already stopped pretending they are. Holding is only worth it
 * when the position being withheld is further along the aim ray than the one we would otherwise
 * see - the target strafing off the crosshair - and when both sit inside the reach band the
 * player actually fights in. Any other case flushes: holding packets for a target walking
 * towards you just delays your own view.</p>
 *
 * <p>Everything here is plain doubles on plain arrays, so the whole decision table unit tests
 * without a game. Boxes are {@code {minX, minY, minZ, maxX, maxY, maxZ}}; directions are
 * expected pre-normalised.</p>
 */
public final class BacktrackMath {
    private BacktrackMath() {
    }

    /**
     * The player-sized box for an entity standing at a position.
     *
     * <p>0.6 wide, 1.8 tall - the standing player box, matching what the ray checks fight
     * against.</p>
     */
    public static double[] boxAt(double x, double y, double z) {
        return new double[] {x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3};
    }

    /**
     * Distance along the ray to the first hit on a box, or -1 for a miss.
     *
     * <p>Slab intersection: for each axis the ray must be inside the slab pair at the same
     * parameter as the others. The result is the entry parameter; a ray starting inside a box
     * counts as a hit at distance 0.</p>
     *
     * @param eye     ray origin, {@code {x, y, z}}
     * @param dir     ray direction, pre-normalised
     * @param box     the box, {@code {minX, minY, minZ, maxX, maxY, maxZ}}
     * @param maxDist distances beyond this do not count as hits
     * @return the entry distance in the same units as the inputs, or -1
     */
    public static double rayEntryDistance(double[] eye, double[] dir, double[] box, double maxDist) {
        double tMin = 0.0D;
        double tMax = maxDist;

        for (int axis = 0; axis < 3; axis++) {
            double origin = eye[axis];
            double direction = dir[axis];
            double min = box[axis];
            double max = box[axis + 3];

            if (Math.abs(direction) < 1.0E-9D) {
                // Parallel to this axis: it never crosses the slabs, so it must already be inside.
                if (origin < min || origin > max) return -1.0D;
                continue;
            }

            double t1 = (min - origin) / direction;
            double t2 = (max - origin) / direction;
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }

            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);

            if (tMin > tMax) return -1.0D;
        }

        return tMin;
    }

    /**
     * Whether the entity's new position is worth holding packets for.
     *
     * <p>Both positions are tested with boxes grown by {@code border} (the attack hitbox
     * expansion a server applies on top of the player box). The new position is holdable when
     * the aim ray does not reach it at the minimum distance but does at the maximum - it has
     * drifted into the band a hit would need a stale position for - and when it lies further
     * along the ray than the position this client still holds, meaning time is on the stale
     * side, not the fresh one.</p>
     *
     * @param oldPos   the position this client currently shows, {@code {x, y, z}}
     * @param newPos   the position the withheld packet describes
     * @param eye      the eye position
     * @param look     the look direction, pre-normalised
     * @param minRange the inner edge of the reach band
     * @param maxRange the outer edge of the reach band
     * @param border   hitbox expansion applied to both boxes
     * @return true to keep holding, false to flush
     */
    public static boolean shouldHoldPacket(double[] oldPos, double[] newPos,
                                           double[] eye, double[] look,
                                           double minRange, double maxRange,
                                           double border) {
        double[] newBox = grow(boxAt(newPos[0], newPos[1], newPos[2]), border);
        double[] oldBox = grow(boxAt(oldPos[0], oldPos[1], oldPos[2]), border);

        double newEntry = rayEntryDistance(eye, look, newBox, maxRange);
        if (newEntry < 0.0D) return false;
        if (newEntry < minRange) return false;

        // The old position has a longer leash - a target walking away is compared against a
        // box that may already be beyond reach, so the old test uses the wider range the
        // original gave it.
        double oldEntry = rayEntryDistance(eye, look, oldBox, maxRange + 2.0D);
        if (oldEntry < 0.0D) return false;

        return newEntry > oldEntry;
    }

    /** Grows a box by {@code border} on every face. */
    public static double[] grow(double[] box, double border) {
        return new double[] {
            box[0] - border, box[1] - border, box[2] - border,
            box[3] + border, box[4] + border, box[5] + border
        };
    }
}
