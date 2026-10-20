package net.venomous.meteoraddon.utils;

/**
 * A running model of Grim's packet accounting, on the client.
 *
 * <p>Three of Grim's checks are decided entirely by the shape of the client's packet stream, and
 * all three have fired in the live log. None of them can be reproduced from the game's own state,
 * because the state that matters is the packet order - so the only way to see what Grim sees is to
 * model it here, in the client, and print the numbers.</p>
 *
 * <h2>What Grim counts</h2>
 *
 * <p>Grim's {@code Timer} check is a balance. Every packet it considers a tick adds fifty
 * milliseconds to a balance, and the check fires when the balance is ahead of the wall clock -
 * which is a statement about the client's <em>packet rate</em>, not about its tick rate. The
 * packets it counts are:</p>
 *
 * <ul>
 *   <li>every movement packet - position, position and rotation, or rotation on its own; and</li>
 *   <li>the end-of-tick packet, but only when no movement packet arrived before it in that
 *       tick.</li>
 * </ul>
 *
 * <p>So a vanilla client counts exactly twenty packets a second: either a movement packet every
 * tick and no end-of-tick packets, or - standing still - an end-of-tick packet every tick and a
 * movement packet every twentieth one. Twenty a second is the rate the balance is written
 * against, and anything above it makes the balance run ahead until the check fires.</p>
 *
 * <p>Grim's {@code TickTimer} is a different check on the same packets, and it is the one that
 * says what the shape has to be: no more than one movement packet between two end-of-tick
 * packets. Two movement packets in one tick is a flag.</p>
 *
 * <p>Grim's {@code AimDuplicateLook} is a third: a rotation packet whose yaw and pitch are both
 * identical to the previous rotation packet's. The vanilla client cannot produce one, because it
 * only sends a rotation when the rotation changed - so a duplicate means something sent one
 * outside the normal movement packet.</p>
 *
 * <h2>Why this is not a guess</h2>
 *
 * <p>Every rule above is transcribed from Grim's own source, and the source is the version this
 * addon was written against:</p>
 *
 * <ul>
 *   <li>{@code checks/impl/timer/Timer.java} - the balance, the fifty milliseconds a tick, and
 *       the floor that keeps a lagging client from being punished for sending in bursts;</li>
 *   <li>{@code checks/GrimProcessor.java} - which packets are "tick" packets, and the rule that
 *       the end-of-tick packet only counts when no movement packet came first;</li>
 *   <li>{@code checks/impl/timer/TickTimer.java} - the one-movement-packet-per-tick rule;</li>
 *   <li>{@code checks/impl/aim/AimDuplicateLook.java} - the duplicate rotation rule;</li>
 *   <li>{@code events/packets/CheckManagerListener.java} - where the "a movement packet arrived
 *       before the end of the tick" flag is set and cleared.</li>
 * </ul>
 *
 * <p>The model is deliberately free of Minecraft types, so it can be tested on a plain JVM - the
 * same rule the rest of this addon's helpers follow.</p>
 */
public final class GrimPacketModel {
    /** The model the mixin feeds. */
    private static final GrimPacketModel INSTANCE = new GrimPacketModel();

    /**
     * The model the packet hook feeds.
     *
     * <p>One for the client: the packet stream is one stream, and the numbers are only
     * meaningful against each other.</p>
     */
    public static GrimPacketModel get() {
        return INSTANCE;
    }

    /**
     * One window of the client's packet stream.
     *
     * <p>Everything Grim decides is decided over a window rather than in a single packet, so the
     * numbers are per window and the window is as long as the caller makes it.</p>
     */
    public static final class Window {
        /** The movement packets - position, position and rotation, or rotation on its own. */
        public int flying;
        /** The end-of-tick packets. */
        public int tickEnd;
        /** What Grim's Timer check counts: the movement packets, plus the end-of-tick packets
         *  that had no movement packet in front of them. */
        public int counted;
        /** The ticks that carried more than one movement packet. Grim's TickTimer flags these. */
        public int doubleFlyingTicks;
        /** The end-of-tick packets that had no movement packet in front of them. */
        public int emptyTicks;
        /** The packets that carried a rotation. */
        public int rotationPackets;
        /** The rotation packets that repeated the previous rotation exactly. Grim's
         *  AimDuplicateLook flags these. */
        public int duplicateLooks;
        /** The yaw of the last duplicate rotation. */
        public float duplicateYaw;
        /** The pitch of the last duplicate rotation. */
        public float duplicatePitch;

        @Override
        public String toString() {
            return "flying=" + this.flying
                + " tickEnd=" + this.tickEnd
                + " counted=" + this.counted
                + " doubleFlying=" + this.doubleFlyingTicks
                + " emptyTicks=" + this.emptyTicks
                + " rotations=" + this.rotationPackets
                + " duplicateLooks=" + this.duplicateLooks;
        }
    }

    /** Whether a movement packet has been seen since the last end-of-tick packet. */
    private int flyingSinceTickEnd;

    /** Whether the last rotation packet carried a rotation. */
    private boolean haveRotation;
    private float lastYaw;
    private float lastPitch;

    private int flying;
    private int tickEnd;
    private int counted;
    private int doubleFlyingTicks;
    private int emptyTicks;
    private int rotationPackets;
    private int duplicateLooks;
    private float duplicateYaw;
    private float duplicatePitch;

    /**
     * A movement packet left the client.
     *
     * @param yaw         the yaw it carried
     * @param pitch       the pitch it carried
     * @param hasRotation whether it carried a rotation at all
     */
    public void onFlyingPacket(float yaw, float pitch, boolean hasRotation) {
        this.flying++;
        this.flyingSinceTickEnd++;
        this.counted++;

        // Grim's TickTimer: a second movement packet before the end of the tick.
        if (this.flyingSinceTickEnd == 2) this.doubleFlyingTicks++;

        if (!hasRotation) return;

        this.rotationPackets++;

        // Grim's AimDuplicateLook: the same yaw and the same pitch as the last rotation packet.
        if (this.haveRotation && yaw == this.lastYaw && pitch == this.lastPitch) {
            this.duplicateLooks++;
            this.duplicateYaw = yaw;
            this.duplicatePitch = pitch;
        }

        this.haveRotation = true;
        this.lastYaw = yaw;
        this.lastPitch = pitch;
    }

    /**
     * The end-of-tick packet left the client.
     *
     * <p>This is the packet Mojang added in 1.21.2 so the server knows a client tick finished.
     * Grim counts it as the tick when no movement packet arrived first, which is what keeps a
     * standing player's count at twenty a second.</p>
     */
    public void onTickEnd() {
        this.tickEnd++;

        if (this.flyingSinceTickEnd == 0) {
            this.counted++;
            this.emptyTicks++;
        }

        this.flyingSinceTickEnd = 0;
    }

    /**
     * The window's numbers, and the counters back to zero.
     *
     * <p>The state that spans windows - the movement packets since the last end-of-tick packet,
     * and the last rotation - is deliberately left alone: it belongs to the packet stream, not to
     * the window.</p>
     */
    public Window snapshot() {
        Window window = new Window();

        window.flying = this.flying;
        window.tickEnd = this.tickEnd;
        window.counted = this.counted;
        window.doubleFlyingTicks = this.doubleFlyingTicks;
        window.emptyTicks = this.emptyTicks;
        window.rotationPackets = this.rotationPackets;
        window.duplicateLooks = this.duplicateLooks;
        window.duplicateYaw = this.duplicateYaw;
        window.duplicatePitch = this.duplicatePitch;

        this.flying = 0;
        this.tickEnd = 0;
        this.counted = 0;
        this.doubleFlyingTicks = 0;
        this.emptyTicks = 0;
        this.rotationPackets = 0;
        this.duplicateLooks = 0;

        return window;
    }
}
