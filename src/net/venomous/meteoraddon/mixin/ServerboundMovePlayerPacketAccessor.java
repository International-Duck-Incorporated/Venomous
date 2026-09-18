package net.venomous.meteoraddon.mixin;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerboundMovePlayerPacket.class)
public interface ServerboundMovePlayerPacketAccessor {
   @Accessor("x")
   double venomous$getX();

   @Accessor("y")
   double venomous$getY();

   @Accessor("z")
   double venomous$getZ();

   @Accessor("yRot")
   float venomous$getYRot();

   @Accessor("xRot")
   float venomous$getXRot();

   @Mutable
   @Accessor("xRot")
   void venomous$setXRot(float var1);

   @Accessor("hasPos")
   boolean venomous$hasPos();

   @Accessor("hasRot")
   boolean venomous$hasRot();

   @Accessor("onGround")
   boolean venomous$isOnGround();
}
