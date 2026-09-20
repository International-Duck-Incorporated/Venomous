package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.venomous.meteoraddon.VenomousAddon;

public class BreachSwap extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> autoSwap = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("auto-swap-breach-mace")).description("Automatically finds and swaps to a breach mace."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> targetSlot = null;
   private final Setting<Boolean> checkWeapon = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("check-weapon")).description("Only activate when holding a sword or axe.")).defaultValue(true))
            .build()
      );
   private final Setting<Boolean> allowSword = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)((Builder)new Builder().name("allow-sword")).description("Allow activation when holding a sword.")).defaultValue(true))
               .visible(this.checkWeapon::get))
            .build()
      );
   private final Setting<Boolean> allowAxe = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)((Builder)new Builder().name("allow-axe")).description("Allow activation when holding an axe.")).defaultValue(true))
               .visible(this.checkWeapon::get))
            .build()
      );
   private final Setting<Boolean> swapBack = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("swap-back")).description("Swap back after delay.")).defaultValue(true)).build());
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("swap-back-delay"))
                     .description("Delay in ticks before swapping back."))
                  .defaultValue(8))
               .min(1)
               .sliderMax(20)
               .visible(this.swapBack::get))
            .build()
      );
   private int prevSlot = -1;
   private int swapDelay = 0;

   public BreachSwap() {
      super(VenomousAddon.CATEGORY, "breach-swap", "Swaps to breach mace on attack.");
   }

   private int findBreachMace() {
      int best = -1;
      int highest = 0;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (!stack.isEmpty()) {
            String ench = stack.getEnchantments().toString();
            if (ench.contains("breach")) {
               try {
                  int l = Integer.parseInt(ench.substring(ench.lastIndexOf("=>") + 2).replaceAll("[^0-9]", ""));
                  if (l > highest) {
                     highest = l;
                     best = i;
                  }
               } catch (NumberFormatException ignored) {
                  // Enchantment string didn't carry a parseable level; skip this stack.
               }
            }
         }
      }

      return best;
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      if (this.mc.player != null && this.mc.level != null) {
         if ((Boolean)this.checkWeapon.get()) {
            String n = this.mc.player.getMainHandItem().getItem().toString().toLowerCase();
            boolean isSword = n.contains("sword");
            boolean isAxe = this.mc.player.getMainHandItem().getItem() instanceof AxeItem;
            if ((!(Boolean)this.allowSword.get() || !isSword) && (!(Boolean)this.allowAxe.get() || !isAxe)) {
               return;
            }
         }

         if ((Boolean)this.swapBack.get()) {
            this.prevSlot = this.mc.player.getInventory().getSelectedSlot();
         }

         if ((Boolean)this.autoSwap.get()) {
            int s = this.findBreachMace();
            if (s != -1) {
               InvUtils.swap(s, false);
            }
         }

         if ((Boolean)this.swapBack.get() && this.prevSlot != -1) {
            this.swapDelay = (Integer)this.delay.get();
         }
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.swapDelay > 0 && --this.swapDelay == 0 && this.prevSlot != -1) {
         InvUtils.swap(this.prevSlot, false);
         this.prevSlot = -1;
      }
   }
}
