package net.venomous.meteoraddon.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.venomous.meteoraddon.utils.ScaffoldCamera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Puts the scaffold module's angle on the camera, at the one moment that makes the numbers agree.
 *
 * <p>At the head of the player's tick: after the mouse has been read, so the mouse cannot move the
 * camera while the module is bridging, and before the movement is computed, so the walk the client
 * performs is along the angle the server is told. Write the camera anywhere else and one of those
 * two is false - the camera is nudged by the mouse after the module set it, or the walk is along an
 * angle the server was never told, and Grim's Simulation check reports the difference in
 * millimetres of movement.</p>
 */
@Mixin(LocalPlayer.class)
public class ScaffoldCameraMixin {
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void venomous$applyScaffoldCamera(CallbackInfo ci) {
        if (!ScaffoldCamera.isTaken()) return;

        LocalPlayer player = (LocalPlayer)(Object)this;
        player.setYRot(ScaffoldCamera.yaw());
        player.setXRot(ScaffoldCamera.pitch());
    }
}
