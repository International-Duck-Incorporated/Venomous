package net.venomous.meteoraddon.modules.utility;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.venomous.meteoraddon.VenomousAddon;

public class AutoToolPlus extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> mining = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("mining")).description("Switch to the best tool while mining.")).defaultValue(true)).build());
   private final Setting<Boolean> weaponFallback = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("weapon-fallback")).description("Switch to a weapon when attacking an entity.")).defaultValue(true))
            .build()
      );
   private final Setting<Boolean> durabilitySafety = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("durability-safety")).description("Avoid tools below the durability threshold.")).defaultValue(true))
            .build()
      );
   private final Setting<Integer> minDurability = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("anti-break-threshold"))
                     .description("Minimum durability percent allowed for selected tools."))
                  .defaultValue(8))
               .range(0, 100)
               .sliderMax(30)
               .visible(() -> (Boolean)this.durabilitySafety.get()))
            .build()
      );
   private final Setting<AutoToolPlus.EnchantPreference> enchantPreference = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)((meteordevelopment.meteorclient.settings.EnumSetting.Builder)new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
                     .name("enchant-preference"))
                  .description("Prefer Silk Touch or Fortune when tool speeds are close."))
               .defaultValue(AutoToolPlus.EnchantPreference.None))
            .build()
      );
   private final Setting<Boolean> restore = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("restore-slot")).description("Restore previous slot after releasing attack.")).defaultValue(true))
            .build()
      );
   private int previousSlot = -1;

   public AutoToolPlus() {
      super(VenomousAddon.CATEGORY, "auto-tool-plus", "Fast auto-tool with durability safety, enchant preference and weapon fallback.");
   }

   @Override
   public void onActivate() {
      this.previousSlot = -1;
   }

   @Override
   public void onDeactivate() {
      this.restoreSlot();
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null && this.mc.hitResult != null) {
         if (!this.mc.options.keyAttack.isDown()) {
            this.restoreSlot();
         } else {
            if (this.previousSlot == -1) {
               this.previousSlot = this.mc.player.getInventory().getSelectedSlot();
            }

            if ((Boolean)this.mining.get() && this.mc.hitResult.getType() == Type.BLOCK) {
               HitResult hitResult = this.mc.hitResult;
               if (this.mc.hitResult instanceof BlockHitResult) {
                  BlockHitResult bhr = (BlockHitResult)hitResult;
                  BlockState blockState = this.mc.level.getBlockState(bhr.getBlockPos());
                  int best2 = this.bestTool(blockState);
                  if (best2 != -1) {
                     this.mc.player.getInventory().setSelectedSlot(best2);
                  }

                  return;
               }
            }

            if ((Boolean)this.weaponFallback.get() && this.mc.hitResult.getType() == Type.ENTITY) {
               HitResult hitResult = this.mc.hitResult;
               int best;
               EntityHitResult ehr;
               if (this.mc.hitResult instanceof EntityHitResult && (ehr = (EntityHitResult)hitResult).getEntity() != null && (best = this.bestWeapon()) != -1) {
                  this.mc.player.getInventory().setSelectedSlot(best);
               }
            }
         }
      }
   }

   private int bestTool(BlockState state) {
      int best = -1;
      float bestSpeed = 1.0F;
      int bestEnchant = -1;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (this.validDurability(stack)) {
            float speed = stack.getDestroySpeed(state);
            int enchant = this.enchantScore(stack);
            if (speed > bestSpeed || Math.abs(speed - bestSpeed) < 0.05F && enchant > bestEnchant) {
               best = i;
               bestSpeed = speed;
               bestEnchant = enchant;
            }
         }
      }

      return best;
   }

   private int bestWeapon() {
      int best = -1;
      double bestScore = 0.0;

      for (int i = 0; i < 9; i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         double score;
         if (this.validDurability(stack) && (score = this.weaponScore(stack.getItem())) > bestScore) {
            bestScore = score;
            best = i;
         }
      }

      return best;
   }

   private double weaponScore(Item item) {
      String n = item.toString().toLowerCase();
      if (item instanceof MaceItem || n.contains("mace")) {
         return 9.0;
      } else if (item instanceof AxeItem || n.contains("axe")) {
         return n.contains("netherite") ? 8.5 : (n.contains("diamond") ? 8.0 : 7.0);
      } else if (n.contains("sword")) {
         return n.contains("netherite") ? 8.0 : (n.contains("diamond") ? 7.5 : (n.contains("iron") ? 6.0 : 5.0));
      } else {
         return 0.0;
      }
   }

   private boolean validDurability(ItemStack stack) {
      if (stack.isEmpty()) {
         return false;
      }

      if ((Boolean)this.durabilitySafety.get() && stack.isDamageableItem()) {
         int max = stack.getMaxDamage();
         if (max <= 0) {
            return true;
         }

         int pct = Math.round((max - stack.getDamageValue()) * 100.0F / max);
         return pct >= (Integer)this.minDurability.get();
      } else {
         return true;
      }
   }

   private int enchantScore(ItemStack stack) {
      return switch ((AutoToolPlus.EnchantPreference)this.enchantPreference.get()) {
         case None -> 0;
         case SilkTouch -> stack.getEnchantments().keySet().stream().anyMatch(h -> h.is(Enchantments.SILK_TOUCH)) ? 2 : 0;
         case Fortune -> stack.getEnchantments().keySet().stream().anyMatch(h -> h.is(Enchantments.FORTUNE)) ? 2 : 0;
      };
   }

   private void restoreSlot() {
      if ((Boolean)this.restore.get() && this.previousSlot != -1 && this.mc.player != null) {
         this.mc.player.getInventory().setSelectedSlot(this.previousSlot);
      }

      this.previousSlot = -1;
   }

   public enum EnchantPreference {
      None,
      SilkTouch,
      Fortune;
   }
}
