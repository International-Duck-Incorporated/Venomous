package net.venomous.meteoraddon.utils;

/**
 * Where the edge is, for the scaffold's crouch.
 *
 * <p>Free of Minecraft types so it unit-tests on a plain JVM, in the same spirit as
 * {@link SneakTiming}, {@link ScaffoldAim} and {@link ScaffoldCadence}.</p>
 *
 * <h2>Why the old test was wrong</h2>
 *
 * <p>The old test asked whether the cell <em>under</em> the player was air. That is not the edge -
 * that is the moment after the player has already stepped off it. On a bridge it is true for the
 * tick or two between walking onto a cell and the block landing under the player, and false again
 * as soon as it lands, because the module fills the cell the player is standing on and not the one
 * in front of them.</p>
 *
 * <p>The live log showed exactly that: the crouch going down and coming up again on every single
 * placement, never held for more than a tick. Two things follow from it. The player never sees
 * themselves crouch, which is the report that started all of this. And each change of the crouch
 * changes the client's movement speed by seventy per cent, and the server only learns about it a
 * tick later - which is worth far more than the placement offset the crouch was there to avoid.</p>
 *
 * <p>The edge is the cell in the direction the player is walking. On a bridge that cell is empty
 * for the whole of the walk - the module fills the cell under the player - so the crouch is held
 * steadily while bridging and let go when the player stops walking or turns away from the drop.
 * That is the behaviour the setting has always claimed: "sneak at block edges".</p>
 */
public final class ScaffoldEdge {
    /**
     * Whether a block is air.
     *
     * <p>Three coordinates in, one boolean out, so the decision can be tested without a world.</p>
     */
    @FunctionalInterface
    public interface Air {
        /**
         * @param x block x
         * @param y block y
         * @param z block z
         * @return true if the block at those coordinates is air
         */
        boolean isAir(int x, int y, int z);
    }

    /** Below this, the movement is not a direction at all. */
    private static final double MINIMUM_DIRECTION = 1.0E-6D;

    /**
     * The share of a cell's width the direction has to clear before the cell counts as "the one
     * in front".
     *
     * <p>A player walking diagonally is between two cells; the diagonal cell is the one they are
     * heading for, and a direction that is mostly along one axis only moves them into the cell on
     * that axis.</p>
     */
    private static final double CELL_BIAS = 0.35D;

    private ScaffoldEdge() {
    }

    /**
     * Whether the player is at the edge of something.
     *
     * @param x     the player's x
     * @param y     the player's y
     * @param z     the player's z
     * @param dirX  the x of the direction they are walking
     * @param dirZ  the z of the direction they are walking
     * @param air   what counts as air
     * @return true if the player is over a drop, or about to step into one
     */
    public static boolean overEdge(double x, double y, double z, double dirX, double dirZ, Air air) {
        int bx = (int)Math.floor(x);
        int by = (int)Math.floor(y - 1.0D);
        int bz = (int)Math.floor(z);

        // Standing on nothing at all is the edge of everything, and it is the case that matters
        // when the player is not walking anywhere: there is no direction to step into, so the
        // cell under them is the only thing that can be asked about.
        if (air.isAir(bx, by, bz)) return true;

        double length = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (length < MINIMUM_DIRECTION) return false;

        double dx = dirX / length;
        double dz = dirZ / length;

        int ax = bx + (dx > CELL_BIAS ? 1 : (dx < -CELL_BIAS ? -1 : 0));
        int az = bz + (dz > CELL_BIAS ? 1 : (dz < -CELL_BIAS ? -1 : 0));

        return air.isAir(ax, by, az);
    }
}
