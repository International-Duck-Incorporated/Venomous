package net.venomous.meteoraddon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.venomous.meteoraddon.modules.misc.VHideScoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {
    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void venomous$extractScoreboardSidebar(GuiGraphicsExtractor extractor, DeltaTracker tracker, CallbackInfo ci) {
        if (Modules.get().isActive(VHideScoreboard.class)) {
            ci.cancel();
        }
    }
}
