package net.venomous.meteoraddon.utils.combat;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Fake fall packet trick helper (MaceKill).
 */
public final class FakeFallPackets {

    private FakeFallPackets() {}

    /**
     * Sends a position packet to spoof fall distance, then sends the real position back.
     *
     * @param player the local player entity
     * @param height the fake fall height in blocks
     */
    public static void sendFakeFall(LocalPlayer player, double height) {
        if (player == null || player.connection == null) return;

        Vec3 pos = player.getPosition(0.0F);
        boolean collided = player.horizontalCollision;
        player.connection.send(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + height, pos.z, false, collided));
        player.connection.send(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y, pos.z, false, collided));
    }
}