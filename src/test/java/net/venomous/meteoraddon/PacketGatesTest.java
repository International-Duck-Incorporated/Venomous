package net.venomous.meteoraddon;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundBundleDelimiterPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket;
import net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ServerboundChunkBatchReceivedPacket;
import net.venomous.meteoraddon.utils.PacketGates;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PacketGatesTest {
    @BeforeAll
    static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void commonPacketsPassThrough() {
        assertTrue(PacketGates.isRealtimePassthrough(new ClientboundKeepAlivePacket(1L)));
    }

    @Test
    void bundlesPassThrough() {
        assertTrue(PacketGates.isRealtimePassthrough(new ClientboundBundlePacket(List.of())));
    }

    @Test
    void bundleDelimitersPassThrough() {
        assertTrue(PacketGates.isRealtimePassthrough(new ClientboundBundleDelimiterPacket()));
    }

    @Test
    void chunkBatchStartPassesThrough() {
        assertTrue(PacketGates.isRealtimePassthrough(ClientboundChunkBatchStartPacket.INSTANCE));
    }

    @Test
    void chunkBatchReceivedPassesThrough() {
        assertTrue(PacketGates.isRealtimePassthrough(new ServerboundChunkBatchReceivedPacket(1.0F)));
    }

    @Test
    void chunkBatchFramesPassThrough() {
        assertTrue(PacketGates.isRealtimePassthrough(new ClientboundChunkBatchFinishedPacket(5)));
    }

    @Test
    void ordinaryGamePacketsDoNotPassThrough() {
        assertFalse(PacketGates.isRealtimePassthrough(new ClientboundTakeItemEntityPacket(1, 2, 3)));
    }

    @Test
    void nullPacketPassesThrough() {
        assertTrue(PacketGates.isRealtimePassthrough(null));
    }
}
