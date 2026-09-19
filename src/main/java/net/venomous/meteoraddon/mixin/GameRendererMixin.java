package net.venomous.meteoraddon.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.venomous.meteoraddon.modules.fun.DankBobbing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "bobView", at = @At("TAIL"))
    private void venomous$onBobView(CameraRenderState cameraRenderState, PoseStack poseStack, CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;

        DankBobbing dank = modules.get(DankBobbing.class);
        if (dank != null && dank.isActive()) {
            float factor = (float) dank.getMotion();
            poseStack.scale(factor, factor, factor);
        }
    }
}
