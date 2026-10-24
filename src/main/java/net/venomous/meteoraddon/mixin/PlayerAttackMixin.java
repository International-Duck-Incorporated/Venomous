package net.venomous.meteoraddon.mixin;

import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.modules.combat.KeepSprint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs after vanilla has applied its own-attack penalty.
 *
 * <p>{@code Player.attack} ends by multiplying the attacker's horizontal motion by 0.6 and
 * dropping sprint. {@link KeepSprint} captures the pre-attack state from
 * {@code AttackEntityEvent}, which fires just before this method runs; at the tail the captured
 * motion is put back through the configured multiplier and the sprint flag is re-raised, so
 * what the movement packet eventually carries is the aimed-for shape rather than vanilla's
 * punishment.</p>
 */
@Mixin(Player.class)
public abstract class PlayerAttackMixin {
    @Inject(method = "attack", at = @At("TAIL"))
    private void venomous$afterOwnAttack(Entity target, CallbackInfo ci) {
        KeepSprint.afterOwnAttack((Player) (Object) this);
    }
}
