package net.venomous.meteoraddon.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.venomous.meteoraddon.utils.PreRotate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires the queued interactions at the one point in the tick where they are in the right order.
 *
 * <p>A 26.2 client sends a tick's packets in this order:</p>
 *
 * <pre>{@code
 *   [the camera write]  [the input packet: the crouch]  [the movement packet]
 * }</pre>
 *
 * <p>The camera is written at the head of the player's tick, the input packet is sent once the
 * player's tick has run, and the movement packet goes out last. So there are two points an
 * interaction can leave from, and they are not the same:</p>
 *
 * <ul>
 *   <li><b>After the input packet</b> - which is where the movement packet's own pre-phase is.
 *       Grim reads the crouch as lasting from the sneak packet until the next movement packet, and
 *       flags and cancels any action that arrives inside that window:
 *       {@code PacketOrderF - action=place, sprinting=false, sneaking=true}. A cancelled
 *       placement is worse than a flag: the client is left standing on a block the server never
 *       received.</li>
 *   <li><b>Before the input packet</b> - which is where this hook is. The interaction then leaves
 *       in the same order a vanilla client's does, and the crouch that follows it cannot be
 *       mistaken for a crouch that was already on.</li>
 * </ul>
 *
 * <p>It has to be inside the player's tick rather than at the head of the game tick, because the
 * camera has to be the aim when the interaction is made: the game tick's head is before the
 * camera write, and the mouse is read between the two ticks, so the camera there is the mouse's
 * and not the module's.</p>
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerTickMixin {
    @Inject(method = "tick()V", at = @At(
        value = "INVOKE",
        target = "Lnet/minecraft/client/player/AbstractClientPlayer;tick()V",
        shift = At.Shift.AFTER))
    private void venomous$dispatchBeforeInput(CallbackInfo ci) {
        PreRotate.dispatch();
    }
}
