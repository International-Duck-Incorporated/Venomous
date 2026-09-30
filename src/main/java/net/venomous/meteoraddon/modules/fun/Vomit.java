package net.venomous.meteoraddon.modules.fun;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.ItemStack;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.IntSetting;

public class Vomit extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> delay = this.sgGeneral
      .add(new IntSetting.Builder().name("delay").description("Drop delay in ticks.").defaultValue(2).min(1).max(20).build());
   private int tickCounter = 0;

   public Vomit() {
      super(VenomousAddon.CATEGORY, "vomit", "Drops everything from your inventory, hotbar, armor, and offhand.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         this.tickCounter++;
         if (this.tickCounter >= (Integer)this.delay.get()) {
            this.tickCounter = 0;

            for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
               ItemStack stack = this.mc.player.getInventory().getItem(i);
               if (!stack.isEmpty()) {
                  InvUtils.drop().slot(i);
                  return;
               }
            }
         }
      }
   }
}
