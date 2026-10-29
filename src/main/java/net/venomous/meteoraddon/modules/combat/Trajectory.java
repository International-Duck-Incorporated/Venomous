package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class Trajectory extends Module {
    public Trajectory() {
        super(VenomousAddon.CATEGORY, "trajectory", "Predict projectile trajectory.");
    }

    @EventHandler
    private void onTick(Pre event) {
        // trajectory prediction placeholder
    }
}
