package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.VenomousAddon;

public class VKeyPearl extends Module {
   public VKeyPearl() {
      super(VenomousAddon.CATEGORY, "v-key-pearl", "Quick-throw ender pearl instantly when enabled.");
   }

   public void onActivate() {
      if (this.mc.player == null) {
         this.toggle();
      } else {
         int pearlSlot = -1;

         for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getItem(i).is(Items.ENDER_PEARL)) {
               pearlSlot = i;
               break;
            }
         }

         if (pearlSlot != -1) {
            InvUtils.swap(pearlSlot, true);
            this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
            this.mc.player.swing(InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
         } else {
            this.error("No pearls found!", new Object[0]);
         }

         this.toggle();
      }
   }
}
