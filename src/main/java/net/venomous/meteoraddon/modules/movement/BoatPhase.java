package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class BoatPhase extends Module {
    private Boat phasedBoat;

    public BoatPhase() {
        super(VenomousAddon.CATEGORY, "boat-phase", "Enables no-physics movement for your current boat.");
    }

    @EventHandler
    private void onTick(Pre event) {
        var vehicle = this.mc.player != null ? this.mc.player.getVehicle() : null;
        Boat current = vehicle instanceof Boat boat ? boat : null;

        if (this.phasedBoat != current) {
            if (this.phasedBoat != null) {
                this.phasedBoat.noPhysics = false;
            }
            this.phasedBoat = current;
        }

        if (current != null) {
            current.noPhysics = true;
            Vec3 velocity = current.getDeltaMovement();
            double dy = 0.0;
            if (this.mc.options.keyJump.isDown()) {
                dy = 0.1;
            } else if (this.mc.options.keyShift.isDown()) {
                dy = -0.1;
            } else {
                dy = 0.005;
            }

            current.setDeltaMovement(velocity.x, dy, velocity.z);
        }
    }

    @Override
    public void onDeactivate() {
        if (this.phasedBoat != null) {
            this.phasedBoat.noPhysics = false;
            this.phasedBoat = null;
        }
    }
}
