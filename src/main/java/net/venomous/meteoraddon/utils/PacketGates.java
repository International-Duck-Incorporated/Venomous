package net.venomous.meteoraddon.utils;

import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.BundleDelimiterPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;

/**
 * Which packets a delay engine must never hold.
 *
 * <p>Every packet that keeps the connection itself alive, synchronised, or streaming world data
 * lives in the {@code common} protocol family: keep-alives and their replies, the ping/pong
 * pairs the anticheats measure latency through, the chunk-batch acknowledgements. Holding those
 * does not desync anyone - it just makes the server believe the connection is dying, and the
 * latency checks read the hold as exactly the lag it is pretending not to be. Bundles and bundle
 * delimiters ride the same rule: they carry chunk data, and a world that arrives late is a world
 * the client cannot play in.</p>
 *
 * <p>A {@code null} packet is treated as a pass-through so a delay queue can never accidentally
 * swallow a missing reference.</p>
 */
public final class PacketGates {
    private PacketGates() {
    }

    /** True when the packet must pass through a delay queue untouched. */
    public static boolean isRealtimePassthrough(Packet<?> packet) {
        if (packet == null) return true;

        // The whole common protocol family: keep-alives, pings, resource-pack traffic, ...
        if (packet.getClass().getName().startsWith("net.minecraft.network.protocol.common.")) {
            return true;
        }

        // Bundles carry chunk data; holding them stalls world streaming.
        if (packet instanceof BundlePacket || packet instanceof BundleDelimiterPacket) {
            return true;
        }

        // Chunk-batch framing keeps the connection's throughput accounting honest.
        return packet instanceof ClientboundChunkBatchFinishedPacket
            || packet instanceof ClientboundChunkBatchStartPacket
            || packet instanceof ServerboundChunkBatchReceivedPacket;
    }
}
