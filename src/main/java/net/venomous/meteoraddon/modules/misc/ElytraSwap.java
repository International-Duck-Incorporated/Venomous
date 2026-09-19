package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.VenomousAddon;

public class ElytraSwap extends Module {
   public ElytraSwap() {
      super(VenomousAddon.CATEGORY, "v-elytra-swap", "Quickly swaps between chestplate and elytra. Toggles off after swapping.");
   }

   @Override
   public void onActivate() {
      if (this.mc.player != null) {
         ItemStack chestplate = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
         boolean wearingElytra = chestplate.getItem() == Items.ELYTRA;

         if (wearingElytra) {
            FindItemResult chest = InvUtils.find(this::isChestplate);
            if (chest.found()) {
               InvUtils.move().from(chest.slot()).toArmor(2);
               this.info("Swapped to chestplate.", new Object[0]);
            } else {
               this.warning("No chestplate found in inventory.", new Object[0]);
            }
         } else {
            FindItemResult elytra = InvUtils.find(Items.ELYTRA);
            if (elytra.found()) {
               InvUtils.move().from(elytra.slot()).toArmor(2);
               this.info("Swapped to elytra.", new Object[0]);
            } else {
               this.warning("No elytra found in inventory.", new Object[0]);
            }
         }
      }
      this.toggle();
   }

   private boolean isChestplate(ItemStack stack) {
      if (stack == null || stack.isEmpty()) return false;
      Item item = stack.getItem();
      return item == Items.NETHERITE_CHESTPLATE
         || item == Items.DIAMOND_CHESTPLATE
         || item == Items.IRON_CHESTPLATE
         || item == Items.GOLDEN_CHESTPLATE
         || item == Items.CHAINMAIL_CHESTPLATE
         || item == Items.LEATHER_CHESTPLATE;
   }
}
