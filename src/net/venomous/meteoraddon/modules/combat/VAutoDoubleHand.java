package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult.Type;
import net.venomous.meteoraddon.VenomousAddon;

public class VAutoDoubleHand extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> healthThreshold = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("health-threshold")).description("Only activate when health is below this value."))
            .defaultValue(20.0)
            .min(0.0)
            .max(36.0)
            .sliderMax(36.0)
            .build()
      );
   private final Setting<Boolean> onlyWhileHoldingWeapon = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("only-weapon"))
                  .description("Only activate while holding a sword or axe."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> requireTarget = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("require-target"))
                  .description("Only activate when looking at a target entity."))
               .defaultValue(false))
            .build()
      );

   public VAutoDoubleHand() {
      super(VenomousAddon.CATEGORY, "v-auto-double-hand", "Automatically uses items in both hands simultaneously.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null && this.mc.gameMode != null && !(this.mc.player.getHealth() > (Double)this.healthThreshold.get())) {
         ItemStack mainHand = this.mc.player.getMainHandItem();
         if ((Boolean)this.onlyWhileHoldingWeapon.get()) {
            String n = mainHand.getItem().toString().toLowerCase();
            if (!n.contains("sword") && !(mainHand.getItem() instanceof AxeItem)) {
               return;
            }
         }

         if (!(Boolean)this.requireTarget.get() || this.mc.hitResult != null && this.mc.hitResult.getType() != Type.MISS) {
            ItemStack offHand = this.mc.player.getOffhandItem();
            if (!offHand.isEmpty() && !this.mc.player.isUsingItem()) {
               this.mc.gameMode.useItem(this.mc.player, InteractionHand.OFF_HAND);
            }
         }
      }
   }
}
