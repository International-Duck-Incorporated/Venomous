package net.venomous.meteoraddon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.screens.Screen;
import net.venomous.meteoraddon.modules.exploit.PortalMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "isAllowedInPortal", at = @At("HEAD"), cancellable = true)
    private void venomous$isAllowedInPortal(CallbackInfoReturnable<Boolean> cir) {
        if (Modules.get().isActive(PortalMenu.class)) {
            cir.setReturnValue(true);
        }
    }
}
