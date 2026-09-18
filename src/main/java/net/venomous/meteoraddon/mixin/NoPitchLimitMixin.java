package net.venomous.meteoraddon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.venomous.meteoraddon.modules.exploit.NoPitchLimit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class NoPitchLimitMixin {
   @Shadow
   private float xRot;

   @Inject(method = "setXRot", at = @At("HEAD"), cancellable = true)
   private void venomous$setXRotWithoutLimit(float xRot, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc != null && mc.player != null && ((Entity)(Object)this) == mc.player && Modules.get().isActive(NoPitchLimit.class)) {
         this.xRot = xRot;
         ci.cancel();
      }
   }
}
