package net.venomous.meteoraddon.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.venomous.meteoraddon.utils.InputCompensation;
import net.venomous.meteoraddon.utils.MovementMath;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

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
 * {@code 0.001} blocks. A millimetre. Past Grim's immediate-setback threshold it also teleports
 * the player back, which reads as being frozen in place.</p>
 *
 * <p><b>The seam matters, and the obvious one was wrong.</b> Injecting into
 * {@code LocalPlayer.modifyInput} applies the rotation wherever that method is called from, and
 * ViaFabricPlus redirects it - the crash report showed the compensation sitting underneath
 * {@code redirect$dbm000$viafabricplus$moveMovementSpeedFactors}. A rotation applied more than
 * once per tick rotates the input more than once, and the movement ends up somewhere neither the
 * camera nor the server expects. That showed up as the offsets going from 0.009 to 0.156, ten
 * times worse, and the player being teleported back every tick.</p>
 *
 * <p>So the rotation is applied at {@code getMoveVector()} instead, which
 * {@code LocalPlayer.applyInput()} calls exactly once per tick. The game's own adjustments still
 * run afterwards, and the rotation preserves the length of the vector, so the scale it applies for
 * sneaking and for using an item is unaffected.</p>
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Redirect(
        method = "applyInput()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/ClientInput;getMoveVector()Lnet/minecraft/world/phys/Vec2;"
        )
    )
    private Vec2 venomous$compensateMoveVector(ClientInput input) {
        Vec2 move = input.getMoveVector();

        float yaw = InputCompensation.yaw();
        if (yaw == 0.0F || move.lengthSquared() == 0.0F) return move;

        float[] compensated = MovementMath.compensateInput(move.x, move.y, yaw);
        return new Vec2(compensated[0], compensated[1]);
    }
}
