package net.venomous.meteoraddon.utils;

import net.minecraft.network.protocol.Packet;

/**
 * Which packets a delay engine must never hold.
 *
 * <p>Every packet that keeps the connection itself alive, synchronised, or streaming world data
 * lives in the {@code common} protocol family: keep-alives and their replies, the ping/pong
 * pairs the anticheats measure latency through, the chunk-batch acknowledgements. Holding those
 * does not desync anyone - it just makes the server believe the connection is dying, and the
 * latency checks read the hold as exactly the lag it is pretending not to be. Bundles ride the
 * same rule: they carry chunk data, and a world that arrives late is a world the client cannot
 * play in.</p>
 */
public final class PacketGates {
    private PacketGates() {
    }

    /** True when the packet must pass through a delay queue untouched. */
    public static boolean isRealtimePassthrough(Packet<?> packet) {
        return packet.getClass().getName().startsWith("net.minecraft.network.protocol.common.");
    }
}
