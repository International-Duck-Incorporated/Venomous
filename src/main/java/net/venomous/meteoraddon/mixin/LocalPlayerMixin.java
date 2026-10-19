package net.venomous.meteoraddon.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.venomous.meteoraddon.utils.InputCompensation;
import net.venomous.meteoraddon.utils.MovementMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rotates the movement input so the walk matches the rotation the server is being told.
 *
 * <p>{@code LocalPlayer} computes its movement from its own yaw, the camera's. The server predicts
 * that movement from the yaw in the movement packet, which the scaffold module has spoofed to point
 * at the block being placed. When the two differ - and they differ by up to half a turn whenever
 * the player is bridging backwards, because the block being placed against is behind them - the
 * client walks a different line from the one the server predicted.</p>
 *
 * <p>That difference is Grim's {@code Simulation} check, which is {@code OffsetHandler} and flags
 * when the client's position differs from the server's prediction by more than
 * {@code 0.001} blocks. A millimetre.</p>
 *
 * <p>{@code modifyInput} is the game's own hook for altering the movement vector - it is where
 * sneaking and item-use slow the player down - so it is the right seam: the vector is already
 * resolved from the keys and scaled by the game, and rotating it afterwards preserves the scale and
 * the direction together.</p>
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(
        method = "modifyInput(Lnet/minecraft/world/phys/Vec2;)Lnet/minecraft/world/phys/Vec2;",
        at = @At("RETURN"),
        // Required: setReturnValue on a CallbackInfoReturnable throws "the call modifyInput is
        // not cancellable" without it, and that crash is a hard client crash rather than a
        // warning - it took the game down on the first tick.
        cancellable = true
    )
    private void venomous$compensateInput(Vec2 input, CallbackInfoReturnable<Vec2> cir) {
        float yaw = InputCompensation.yaw();
        if (yaw == 0.0F || input.lengthSquared() == 0.0F) return;

        float[] compensated = MovementMath.compensateInput(input.x, input.y, yaw);
        cir.setReturnValue(new Vec2(compensated[0], compensated[1]));
    }
}
