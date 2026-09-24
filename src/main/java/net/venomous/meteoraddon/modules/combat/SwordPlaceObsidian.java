package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.McCompat;

public class SwordPlaceObsidian extends Module {
   private int placeTimer = 0;

   public SwordPlaceObsidian() {
      super(VenomousAddon.CATEGORY, "sword-obi-place", "Right-click with sword to place obsidian instantly.");
   }

   @Override
   public void onActivate() {
      this.placeTimer = 0;
   }

   @Override
   public void onDeactivate() {
      this.placeTimer = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.placeTimer > 0) {
         this.placeTimer--;
         return;
      }

      if (this.mc.player != null && this.mc.level != null) {
         if (this.mc.options.keyUse.isDown() && McCompat.isSword(this.mc.player.getMainHandItem().getItem())) {
            int obi = -1;

            for (int i = 0; i < 9; i++) {
               if (this.mc.player.getInventory().getItem(i).is(Items.OBSIDIAN)) {
                  obi = i;
                  break;
               }
            }

            if (obi != -1 && this.mc.hitResult instanceof BlockHitResult bhr) {
               int prev = this.mc.player.getInventory().getSelectedSlot();
               InvUtils.swap(obi, true);
               this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, bhr);
               this.mc.player.swing(InteractionHand.MAIN_HAND);
               InvUtils.swap(prev, true);
               this.placeTimer = 2;
            }
         }
      }
   }
}
