package net.venomous.meteoraddon.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.modules.movement.NoPush;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements the entity / block / liquid options of {@link NoPush}.
 *
 * <p>These push sources are applied inside vanilla {@link Entity} methods, so they cannot be undone
 * from a tick handler — by the time the tick event fires the velocity change has already been
 * folded into the player's motion. Cancelling at the source is the only correct place.
 */
@Mixin(Entity.class)
public abstract class NoPushEntityMixin {
    /** Cancels entity-vs-entity shoving (crowds, mobs, boats). */
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void venomous$noEntityPush(Entity entity, CallbackInfo ci) {
        if ((Object)this instanceof LocalPlayer && NoPush.blocks(NoPush.Source.ENTITIES)) {
            ci.cancel();
        }
    }

    /** Cancels block-induced motion such as cobwebs, sweet berry bushes and powder snow. */
    @Inject(method = "makeStuckInBlock", at = @At("HEAD"), cancellable = true)
    private void venomous$noBlockPush(BlockState state, Vec3 motionMultiplier, CallbackInfo ci) {
        if ((Object)this instanceof LocalPlayer && NoPush.blocks(NoPush.Source.BLOCKS)) {
            ci.cancel();
        }
    }

    /** Stops water and lava currents from carrying the player. */
    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true)
    private void venomous$noFluidPush(CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof LocalPlayer && NoPush.blocks(NoPush.Source.LIQUIDS)) {
            cir.setReturnValue(false);
        }
    }
}
