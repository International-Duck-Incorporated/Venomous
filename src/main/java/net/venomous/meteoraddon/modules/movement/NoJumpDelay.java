package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.mixin.LivingEntityAccessor;

/**
 * Removes the cooldown between consecutive jumps: vanilla ticks the {@code noJumpDelay} field down
 * from ten, and every tick shorter than the setting is clamped back up front, so the jump is
 * available again the grounded tick after the last one. The port clamps (rather than writes zero)
 * exactly the way the 26.2-era source it came from does, because a constant zero would defeat the
 * countdown itself - a jump would never be allowed.
 */
public class NoJumpDelay extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> jumpDelay = this.sgGeneral.add(
        new IntSetting.Builder().name("jump-delay")
            .description("The jump cooldown to enforce, in ticks. 0 jumps again immediately after landing.")
            .defaultValue(0)
            .range(0, 10)
            .sliderMax(10)
            .build()
    );

    public NoJumpDelay() {
        super(VenomousAddon.CATEGORY, "no-jump-delay", "Re-jump the instant you land, or tune a shorter jump cooldown than the vanilla ten ticks.");
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null) {
            return;
        }

        LivingEntityAccessor self = (LivingEntityAccessor) this.mc.player;
        int delay = this.jumpDelay.get();
        if (self.venomous$getNoJumpDelay() > delay) {
            self.venomous$setNoJumpDelay(delay);
        }
    }
}
