package net.venomous.meteoraddon.mixin;

import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientIntentionPacket.class)
public interface ClientIntentionPacketAccessor {
   @Mutable
   @Accessor("hostName")
   void venomous$setHostName(String var1);
}
