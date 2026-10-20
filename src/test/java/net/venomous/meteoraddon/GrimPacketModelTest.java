package net.venomous.meteoraddon;

import net.venomous.meteoraddon.utils.GrimPacketModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the client-side model of Grim's packet accounting.
 *
 * <p>Three of Grim's checks are decided by the shape of the client's packet stream, and all three
 * have fired in the live log: the timer check, the tick timer and the duplicate look. The model
 * exists so the client can print the numbers Grim works from, and this is what keeps it honest -
 * a model that does not agree with Grim's source is worse than no model at all, because it would
 * be believed.</p>
 *
 * <p>The rules, from Grim's source:</p>
 * <ul>
 *   <li>{@code checks/GrimProcessor.java} - the end-of-tick packet counts as the tick only when
 *       no movement packet arrived before it;</li>
 *   <li>{@code checks/impl/timer/TickTimer.java} - more than one movement packet between two
 *       end-of-tick packets is a flag;</li>
 *   <li>{@code checks/impl/aim/AimDuplicateLook.java} - a rotation packet with the same yaw and
 *       the same pitch as the previous rotation packet is a flag.</li>
 * </ul>
 */
public class GrimPacketModelTest {
    /** A moving client: a movement packet every tick, and an end-of-tick packet after it. */
    private static void movingTick(GrimPacketModel model, float yaw, float pitch) {
        model.onFlyingPacket(yaw, pitch, true);
        model.onTickEnd();
    }

    /**
     * A moving client counts twenty packets a second, and no more.
     *
     * <p>Grim's timer check adds fifty milliseconds to its balance for every packet it counts and
     * compares the balance against the wall clock, so the count is what decides whether the check
     * fires. Twenty a second is the rate the balance is written against, and a client that stays
     * there never flags.</p>
     */
    @Test
    void aMovingClientCountsTwentyASecond() {
        GrimPacketModel model = new GrimPacketModel();

        for (int tick = 0; tick < 20; tick++) movingTick(model, 90.0F + tick, 60.0F);

        GrimPacketModel.Window window = model.snapshot();

        assertEquals(20, window.flying, "one movement packet per tick");
        assertEquals(20, window.tickEnd, "one end-of-tick packet per tick");
        assertEquals(20, window.counted,
            "the movement packet counts and the end-of-tick packet does not, because a movement "
                + "packet arrived before it");
        assertEquals(0, window.emptyTicks);
        assertEquals(0, window.doubleFlyingTicks);
    }

    /**
     * A standing client counts twenty a second as well.
     *
     * <p>Standing still, the client sends a movement packet every twentieth tick and an
     * end-of-tick packet every tick. Grim counts the end-of-tick packet when no movement packet
     * arrived first, so the count comes to the same twenty - which is the whole reason the
     * end-of-tick packet is counted at all.</p>
     */
    @Test
    void aStandingClientCountsTwentyASecond() {
        GrimPacketModel model = new GrimPacketModel();

        for (int tick = 0; tick < 20; tick++) {
            if (tick % 20 == 0) model.onFlyingPacket(90.0F, 60.0F, true);
            model.onTickEnd();
        }

        GrimPacketModel.Window window = model.snapshot();

        assertEquals(1, window.flying, "one movement packet every twentieth tick");
        assertEquals(20, window.tickEnd);
        assertEquals(20, window.counted,
            "the nineteen end-of-tick packets with no movement packet in front of them, plus the "
                + "one movement packet");
        assertEquals(19, window.emptyTicks);
    }

    /**
     * Two movement packets in one tick are what Grim's tick timer reports.
     *
     * <p>It flags a movement packet that arrives when the last packet was not an end-of-tick
     * packet, and it flags an end-of-tick packet that arrives with more than one movement packet
     * behind it. It is the check that says a client may not send two movement packets in a
     * tick.</p>
     */
    @Test
    void twoMovementPacketsInOneTickAreFlagged() {
        GrimPacketModel model = new GrimPacketModel();

        model.onFlyingPacket(90.0F, 60.0F, true);
        model.onFlyingPacket(90.0F, 60.0F, true);
        model.onTickEnd();

        GrimPacketModel.Window window = model.snapshot();

        assertEquals(1, window.doubleFlyingTicks, "the tick carried two movement packets");
        assertEquals(2, window.counted,
            "both movement packets count and the end-of-tick packet does not, because a movement "
                + "packet arrived before it - so this one tick counted two packets where the "
                + "balance is written for one, and the balance runs ahead by fifty milliseconds");
    }

    /**
     * A duplicate rotation is what Grim's duplicate look reports.
     */
    @Test
    void aRepeatedRotationIsFlagged() {
        GrimPacketModel model = new GrimPacketModel();

        model.onFlyingPacket(90.0F, 60.0F, true);
        model.onTickEnd();
        model.onFlyingPacket(90.0F, 60.0F, true);
        model.onTickEnd();

        GrimPacketModel.Window window = model.snapshot();

        assertEquals(1, window.duplicateLooks, "the same yaw and the same pitch twice");
        assertEquals(90.0F, window.duplicateYaw);
        assertEquals(60.0F, window.duplicatePitch);
    }

    /**
     * A rotation that moved is not a duplicate.
     */
    @Test
    void aChangedRotationIsNotADuplicate() {
        GrimPacketModel model = new GrimPacketModel();

        model.onFlyingPacket(90.0F, 60.0F, true);
        model.onTickEnd();
        model.onFlyingPacket(90.0F, 60.1F, true);
        model.onTickEnd();

        assertEquals(0, model.snapshot().duplicateLooks,
            "the pitch moved, so the rotation is not a repeat");
    }

    /**
     * A movement packet without a rotation is not a rotation packet at all.
     *
     * <p>The vanilla client sends a position-only packet when the player moved but did not look
     * anywhere, and Grim does not treat that as a rotation - so it cannot be a duplicate.</p>
     */
    @Test
    void aPositionOnlyPacketIsNotARotation() {
        GrimPacketModel model = new GrimPacketModel();

        model.onFlyingPacket(0.0F, 0.0F, false);
        model.onTickEnd();
        model.onFlyingPacket(0.0F, 0.0F, false);
        model.onTickEnd();

        GrimPacketModel.Window window = model.snapshot();

        assertEquals(0, window.rotationPackets);
        assertEquals(0, window.duplicateLooks);
        assertEquals(2, window.counted, "both are still movement packets, and both count");
    }

    /**
     * The window resets, but the state that spans it does not.
     *
     * <p>The movement packets since the last end-of-tick packet and the last rotation belong to
     * the packet stream, not to the window. If they were reset with the counters, a tick that
     * straddled two windows would be counted as two clean ticks and the double-packet case would
     * never be seen.</p>
     */
    @Test
    void theStateSpansTheWindows() {
        GrimPacketModel model = new GrimPacketModel();

        model.onFlyingPacket(90.0F, 60.0F, true);
        model.snapshot();

        model.onFlyingPacket(90.0F, 60.0F, true);
        model.onTickEnd();

        GrimPacketModel.Window window = model.snapshot();

        assertEquals(1, window.doubleFlyingTicks,
            "the second movement packet followed the first one across the window boundary, and "
                + "that is exactly the case Grim's tick timer reports");
        assertEquals(1, window.duplicateLooks,
            "and the rotation repeated across the boundary too");
    }

    /**
     * The report has to be readable at a glance.
     */
    @Test
    void theWindowPrintsTheNumbersThatMatter() {
        GrimPacketModel model = new GrimPacketModel();

        movingTick(model, 90.0F, 60.0F);

        String text = model.snapshot().toString();

        assertTrue(text.contains("counted=1"), text);
        assertTrue(text.contains("doubleFlying=0"), text);
        assertTrue(text.contains("duplicateLooks=0"), text);
    }
}
