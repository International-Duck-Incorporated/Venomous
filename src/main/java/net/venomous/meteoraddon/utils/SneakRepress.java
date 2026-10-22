package net.venomous.meteoraddon.utils;

/**
 * Whether a <em>fresh</em> crouch may start on this tick, given when the last placement left.
 *
 * <p>Free of Minecraft types so it unit-tests on a plain JVM, in the same spirit as
 * {@link SneakTiming}, {@link ClickTiming} and {@link ScaffoldCadence}.</p>
 *
 * <h2>Why a delay is needed at all</h2>
 *
 * <p>The edge crouch at the top of {@code updateManagedSneak} asks the right question with the
 * wrong timing: <em>is the player over an edge</em>. When the answer flips back to true within
 * milliseconds of a placement leaving, the module presses the crouch key again straight away - and
 * what reaches the server is a place-and-squeeze pattern: an interaction packet, a sneak toggle,
 * another interaction, another sneak toggle, at the cadence of the bridge. Nobody walks the crouch
 * back on that fast; the hand stays down and thinks.</p>
 *
 * <p>The tell this removes is measurable. A bridge built at the normal cadence with an instant
 * re-press sends roughly one sneak toggle per placement, which is a metronome; a re-press that
 * waits settles the toggles into bursts that only happen at the places a player actually squeezes
 * the crouch back - long edges, corners, restarts.</p>
 *
 * <p>The timing is ported from Raven B4's scaffold, which queues its sneak press behind a
 * {@code sincePlace() > 90L} check on a 1.8 anticheat mix. The same number works here for the same
 * reason it works there: 90ms is longer than a placement window and shorter than a re-press a slow
 * hand would notice. Toggles <em>after</em> this are not delayed along with it - a release must
 * always be able to happen immediately, because a crouch held past its point is a walking-into-air
 * bug waiting to happen, and because {@link SneakTiming} already guarantees any crouch
 * that does start is held for a real duration.</p>
 *
 * @see #allowsPress
 */
public final class SneakRepress {
    /**
     * Minimum wall-clock distance between a placement leaving and a fresh crouch starting, in
     * milliseconds. Raven B4's number.
     */
    public static final int DEFAULT_DELAY_MS = 90;

    /**
     * Floor when a caller has configured something tighter, in milliseconds.
     *
     * <p>Below this the press lands within the same movement window as the placement it follows,
     * which is the "crouch does not change on a tick where a placement is going out" shape even
     * though the packets are a window apart.</p>
     */
    public static final int MINIMUM_DELAY_MS = 30;

    private SneakRepress() {
    }

    /**
     * Whether a crouch that is not currently held may start now.
     *
     * <p>Called once per tick. The module owns the timestamps; this only decides, so it stays a
     * pure function of its arguments.</p>
     *
     * @param msSinceLastPlacement milliseconds since the last placement left the client, large
     *                             when nothing has ever been placed
     * @param delayMs              configured delay for this module; anything below the floor is
     *                             clamped up to it
     * @return true when a fresh crouch is allowed
     */
    public static boolean allowsPress(long msSinceLastPlacement, int delayMs) {
        return msSinceLastPlacement >= Math.max(MINIMUM_DELAY_MS, delayMs);
    }
}
