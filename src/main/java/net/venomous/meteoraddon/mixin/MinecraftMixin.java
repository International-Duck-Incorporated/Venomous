package net.venomous.meteoraddon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.venomous.meteoraddon.modules.exploit.AntiReducedDebugInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "showOnlyReducedInfo", at = @At("HEAD"), cancellable = true)
    private void venomous$showOnlyReducedInfo(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.get().isActive(AntiReducedDebugInfo.class)) {
            cir.setReturnValue(false);
        }
    }
}
