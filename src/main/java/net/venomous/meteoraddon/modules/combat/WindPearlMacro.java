package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.PreRotate;

public class WindPearlMacro extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> delayTicks;
   private int tickCounter = 0;
   private boolean throwingPearl = false;

   public WindPearlMacro() {
      super(VenomousAddon.CATEGORY, "pearl-wind-macro", "Throws pearl first, then uses wind charge at 90 degrees up.");
      this.delayTicks = this.sgGeneral
         .add(
            new Builder().name("delay").description("Delay between pearl throw and wind charge.").defaultValue(1)
               .min(1)
               .max(10)
               .build()
         );
   }

   public void onActivate() {
      if (this.mc.player != null && this.mc.level != null) {
         int pearlSlot = this.findPearlSlot();
         if (pearlSlot == -1) {
            this.error("No Ender Pearl found in hotbar.", new Object[0]);
            this.toggle();
         } else {
            this.throwingPearl = true;
            this.tickCounter = 0;
            PreRotate.rotateThen(this.mc.player.getYRot(), -90.0, () -> {
               InvUtils.swap(pearlSlot, true);
               this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
               this.mc.player.swing(InteractionHand.MAIN_HAND);
               InvUtils.swapBack();
            });
         }
      } else {
         this.toggle();
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.throwingPearl && this.mc.player != null) {
         this.tickCounter++;
         if (this.tickCounter >= (Integer)this.delayTicks.get()) {
            int windSlot = this.findWindChargeSlot();
            if (windSlot == -1) {
               this.error("No Wind Charge found in hotbar.", new Object[0]);
               this.toggle();
               return;
            }

            PreRotate.rotateThen(this.mc.player.getYRot(), -90.0, () -> {
               InvUtils.swap(windSlot, true);
               this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
               this.mc.player.swing(InteractionHand.MAIN_HAND);
               InvUtils.swapBack();
               this.toggle();
            });
            this.throwingPearl = false;
         }
      }
   }

   private void throwPearl() {
      this.mc.player.swing(InteractionHand.MAIN_HAND);
      this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
   }

   private void useWindCharge() {
      this.mc.player.swing(InteractionHand.MAIN_HAND);
      this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
   }

   private int findPearlSlot() {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (stack.is(Items.ENDER_PEARL)) {
            return i;
         }
      }

      return -1;
   }

   private int findWindChargeSlot() {
      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (stack.is(Items.WIND_CHARGE)) {
            return i;
         }
      }

      return -1;
   }
}
