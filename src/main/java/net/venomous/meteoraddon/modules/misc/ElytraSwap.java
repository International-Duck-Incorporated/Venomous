package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.VenomousAddon;

public class ElytraSwap extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   public ElytraSwap() {
      super(VenomousAddon.CATEGORY, "v-elytraswap", "Swap between chestplate and elytra. Toggles off after swapping.");
   }

   public void onActivate() {
      if (this.mc.player != null) {
         ItemStack chestplate = this.mc.player.getItemBySlot(EquipmentSlot.CHEST);
         boolean wearingElytra = chestplate.getItem() == Items.ELYTRA;
         int targetSlot = -1;
         if (wearingElytra) {
            targetSlot = this.findChestplate();
         } else {
            targetSlot = this.findElytra();
         }

         if (targetSlot == -1) {
            this.info(wearingElytra ? "No chestplate found." : "No elytra found.", new Object[0]);
            this.toggle();
         } else {
            this.mc.gameMode.handleContainerInput(this.mc.player.inventoryMenu.containerId, targetSlot + 9, 0, ContainerInput.PICKUP, this.mc.player);
            this.mc.gameMode.handleContainerInput(this.mc.player.inventoryMenu.containerId, 6, 0, ContainerInput.PICKUP, this.mc.player);
            if (!this.mc.player.containerMenu.getCarried().isEmpty()) {
               this.mc.gameMode.handleContainerInput(this.mc.player.inventoryMenu.containerId, targetSlot + 9, 0, ContainerInput.PICKUP, this.mc.player);
            }

            this.info(wearingElytra ? "Swapped to chestplate." : "Swapped to elytra.", new Object[0]);
            this.toggle();
         }
      }
   }

   private int findElytra() {
      for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
         if (i >= 5 && i != 40) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.ELYTRA) {
               return i - 9;
            }
         }
      }

      return -1;
   }

   private int findChestplate() {
      for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
         if (i >= 5 && i != 40) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (this.isChestplate(stack)) {
               return i - 9;
            }
         }
      }

      return -1;
   }

   private boolean isChestplate(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }

      String name = stack.getItem().toString().toLowerCase();
      return name.contains("chestplate");
   }
}
