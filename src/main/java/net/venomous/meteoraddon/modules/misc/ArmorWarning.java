package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.VenomousAddon;

public class ArmorWarning extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> durability = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("durability")).description("Warn when armor/elytra durability is at or below this percent."))
               .defaultValue(20))
            .range(1, 100)
            .sliderMax(50)
            .build()
      );
   private final Setting<Integer> totems = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("totems")).description("Warn when totem count is at or below this value.")).defaultValue(1))
            .range(0, 10)
            .sliderMax(5)
            .build()
      );
   private final Setting<Boolean> chat = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("chat"))
                  .description("Send chat warnings."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> sound = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("sound"))
                  .description("Play a warning sound."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Integer> cooldown = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("cooldown")).description("Ticks between repeated warnings.")).defaultValue(100))
            .range(20, 600)
            .sliderMax(200)
            .build()
      );
   private int timer;

   public ArmorWarning() {
      super(VenomousAddon.CATEGORY, "armor-warning", "Warns about low armor, elytra durability and low totem count.");
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.player != null) {
         try {
            if (this.timer-- <= 0) {
               String warning = this.getWarning();
               if (warning != null) {
                  this.timer = (Integer)this.cooldown.get();
                  if ((Boolean)this.chat.get()) {
                     this.warning(warning, new Object[0]);
                  }

                  if ((Boolean)this.sound.get()) {
                     this.mc.player.playSound((SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 1.0F, 1.4F);
                  }
               }
            }
         } catch (Exception var3) {
         }
      }
   }

   private String getWarning() {
      for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
         ItemStack stack = this.mc.player.getItemBySlot(slot);
         if (stack != null && !stack.isEmpty() && this.low(stack)) {
            if (stack.getItem() == Items.ELYTRA) {
               return "Elytra durability low: " + this.percent(stack) + "%";
            }

            return slot.getName() + " durability low: " + this.percent(stack) + "%";
         }
      }

      int count = this.countTotems();
      return count <= this.totems.get() ? "Totems low: " + count : null;
   }

   private boolean low(ItemStack stack) {
      return stack.isDamageableItem() && this.percent(stack) <= (Integer)this.durability.get();
   }

   private int percent(ItemStack stack) {
      int max = stack.getMaxDamage();
      return max <= 0 ? 100 : (int)((max - stack.getDamageValue()) * 100.0 / max);
   }

   private int countTotems() {
      int count = 0;

      for (int i = 0; i < this.mc.player.getInventory().getContainerSize(); i++) {
         ItemStack stack = this.mc.player.getInventory().getItem(i);
         if (stack.getItem() == Items.TOTEM_OF_UNDYING) {
            count += stack.getCount();
         }
      }

      return count;
   }
}
