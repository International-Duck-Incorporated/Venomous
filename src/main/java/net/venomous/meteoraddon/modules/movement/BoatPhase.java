package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class BoatPhase extends Module {
   public BoatPhase() {
      super(VenomousAddon.CATEGORY, "boat-phase", "Enables no-physics movement for your current boat.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if ((this.mc.player == null ? null : this.mc.player.getVehicle()) instanceof Boat boat) {
         boat.noPhysics = true;
         Vec3 velocity = boat.getDeltaMovement();
         double dy = 0.0;
         if (this.mc.options.keyJump.isDown()) {
            dy = 0.1;
         } else if (this.mc.options.keyShift.isDown()) {
            dy = -0.1;
         } else {
            dy = 0.005;
         }

         boat.setDeltaMovement(velocity.x, dy, velocity.z);
      }
   }

   public void onDeactivate() {
      if ((this.mc.player == null ? null : this.mc.player.getVehicle()) instanceof Boat boat) {
         boat.noPhysics = false;
      }
   }
}
