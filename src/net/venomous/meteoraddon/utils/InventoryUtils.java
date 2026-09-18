package net.venomous.meteoraddon.utils;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class InventoryUtils {
   private InventoryUtils() {
   }

   public static boolean canUseInventory() {
      return MeteorClient.mc.player != null && MeteorClient.mc.level != null && MeteorClient.mc.gameMode != null;
   }

   public static boolean isValidHotbarSlot(int slot) {
      return slot >= 0 && slot <= 8;
   }

   public static boolean isValidPlayerInventorySlot(int slot) {
      return slot >= 0 && slot <= 35;
   }

   public static ItemStack getOffhandStack() {
      return MeteorClient.mc.player == null ? ItemStack.EMPTY : MeteorClient.mc.player.getOffhandItem();
   }

   public static boolean isOffhand(Item item) {
      ItemStack stack = getOffhandStack();
      return !stack.isEmpty() && stack.getItem() == item;
   }

   public static int findItem(Item item) {
      if (MeteorClient.mc.player == null) {
         return -1;
      }

      int size = MeteorClient.mc.player.getInventory().getContainerSize();

      for (int slot = 0; slot < size; slot++) {
         ItemStack stack = MeteorClient.mc.player.getInventory().getItem(slot);
         if (!stack.isEmpty() && stack.getItem() == item) {
            return slot;
         }
      }

      return -1;
   }

   public static int findItemInHotbar(Item item) {
      if (MeteorClient.mc.player == null) {
         return -1;
      }

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = MeteorClient.mc.player.getInventory().getItem(slot);
         if (!stack.isEmpty() && stack.getItem() == item) {
            return slot;
         }
      }

      return -1;
   }

   public static int countItem(Item item) {
      if (MeteorClient.mc.player == null) {
         return 0;
      }

      int count = 0;
      int size = MeteorClient.mc.player.getInventory().getContainerSize();

      for (int slot = 0; slot < size; slot++) {
         ItemStack stack = MeteorClient.mc.player.getInventory().getItem(slot);
         if (!stack.isEmpty() && stack.getItem() == item) {
            count += stack.getCount();
         }
      }

      ItemStack offhand = MeteorClient.mc.player.getOffhandItem();
      if (!offhand.isEmpty() && offhand.is(item)) {
         count += offhand.getCount();
      }

      return count;
   }

   public static ItemStack getArmorStack(EquipmentSlot slot) {
      return MeteorClient.mc.player == null ? ItemStack.EMPTY : MeteorClient.mc.player.getItemBySlot(slot);
   }

   public static int getDurabilityPercent(ItemStack stack) {
      if (!stack.isEmpty() && stack.isDamageableItem()) {
         int maxDamage = stack.getMaxDamage();
         if (maxDamage <= 0) {
            return 100;
         }

         int remaining = maxDamage - stack.getDamageValue();
         return Math.round(remaining * 100.0F / maxDamage);
      } else {
         return 100;
      }
   }
}
