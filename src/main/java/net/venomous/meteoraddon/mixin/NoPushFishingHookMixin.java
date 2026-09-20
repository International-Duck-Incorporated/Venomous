package net.venomous.meteoraddon.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.venomous.meteoraddon.modules.movement.NoPush;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Implements the fishing-rod option of {@link NoPush}. */
@Mixin(FishingHook.class)
public abstract class NoPushFishingHookMixin {
    @Inject(method = "pullEntity", at = @At("HEAD"), cancellable = true)
    private void venomous$noRodPull(Entity entity, CallbackInfo ci) {
        if (entity instanceof LocalPlayer && NoPush.blocks(NoPush.Source.FISHING_ROD)) {
            ci.cancel();
        }
    }
}
