package net.venomous.meteoraddon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.venomous.meteoraddon.modules.movement.NoSlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces the multiplier the client scales its movement vector by while an item is in use.
 *
 * <p>26.2 reads the number from the item's {@code USE_EFFECTS} data component through
 * {@code LocalPlayer.itemUseSpeedMultiplier}; this is the single seam every consumer of that
 * number passes through, so one injection re-shapes the slowdown everywhere it applies and
 * nothing else about the use has to be touched.</p>
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerUseSlowMixin {
    @Inject(method = "itemUseSpeedMultiplier", at = @At("RETURN"), cancellable = true)
    private void venomous$overrideItemUseSpeedMultiplier(CallbackInfoReturnable<Float> cir) {
        Minecraft mc = Minecraft.getInstance();
        Modules modules = Modules.get();
        if (mc == null || mc.player == null || modules == null) return;

        NoSlow noSlow = modules.get(NoSlow.class);
        if (noSlow == null || !noSlow.isActive()) return;

        float overridden = noSlow.overrideMultiplier(mc.player);
        if (overridden >= 0.0F) {
            cir.setReturnValue(overridden);
        }
    }
}
