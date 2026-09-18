package net.venomous.meteoraddon.modules.fun;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class Twerk extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> delay = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("delay")).description("Sneak toggle speed.")).defaultValue(2)).min(1).max(20).build());

   public Twerk() {
      super(VenomousAddon.CATEGORY, "v-twerk", "Automatically sneaks and unsneaks.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         boolean shouldSneak = this.mc.player.tickCount % ((Integer)this.delay.get() * 2) < (Integer)this.delay.get();
         this.mc.options.keyShift.setDown(shouldSneak);
      }
   }
}
