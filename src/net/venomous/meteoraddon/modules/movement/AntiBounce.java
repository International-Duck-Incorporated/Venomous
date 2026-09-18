package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiBounce extends Module {
   public AntiBounce() {
      super(VenomousAddon.CATEGORY, "v-anti-bounce", "Prevents bouncing on slime blocks and beds.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.player.getDeltaMovement().y > 0.0 && this.mc.player.onGround()) {
         this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, 0.0, this.mc.player.getDeltaMovement().z);
      }
   }
}
