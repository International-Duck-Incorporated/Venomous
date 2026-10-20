package net.venomous.meteoraddon.mixin;

import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.venomous.meteoraddon.utils.GrimPacketModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds the client's packet stream into {@link GrimPacketModel}.
 *
 * <p>Grim's timer and aim checks are decided by the <em>order</em> of the packets the client
 * sends, and nothing in the game's own state records that. So the only way to see what Grim sees
 * is to watch the same stream from the inside, which is what this does: one model, fed every
 * packet, printing the counts Grim's three checks work from.</p>
 *
 * <p>The hook is on {@code ClientCommonPacketListenerImpl.send}, which is where every packet the
 * client sends goes through - the movement packets and the end-of-tick packet alike. The cost is
 * two type checks per packet, and the model does nothing but add up integers.</p>
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientPacketListenerMixin {
    @Inject(method = "send", at = @At("HEAD"))
    private void venomous$watchPackets(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundMovePlayerPacket move) {
            // The status-only packet carries neither a position nor a rotation, and Grim does not
            // count it as a movement packet.
            if (!move.hasPosition() && !move.hasRotation()) return;

            GrimPacketModel.get().onFlyingPacket(move.getYRot(0.0F), move.getXRot(0.0F), move.hasRotation());

            return;
        }

        if (packet instanceof ServerboundClientTickEndPacket) {
            GrimPacketModel.get().onTickEnd();
        }
    }
}
