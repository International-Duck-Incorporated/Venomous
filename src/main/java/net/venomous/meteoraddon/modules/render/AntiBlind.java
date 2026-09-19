package net.venomous.meteoraddon.modules.render;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.effect.MobEffects;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiBlind extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> blockNausea = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("block-nausea")).description("Block nausea effect.")).defaultValue(true)).build());
   private final Setting<Boolean> blockBlindness = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("block-blindness")).description("Block blindness effect.")).defaultValue(true)).build());
   private final Setting<Boolean> blockDarkness = this.sgGeneral
      .add(((Builder)((Builder)((Builder)new Builder().name("block-darkness")).description("Block darkness effect.")).defaultValue(true)).build());

   public AntiBlind() {
      super(VenomousAddon.CATEGORY, "anti-blind", "Blocks annoying screen effects like blindness, nausea, etc.");
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         if ((Boolean)this.blockBlindness.get() && this.mc.player.hasEffect(MobEffects.BLINDNESS)) {
            this.mc.player.removeEffect(MobEffects.BLINDNESS);
         }

         if ((Boolean)this.blockNausea.get() && this.mc.player.hasEffect(MobEffects.NAUSEA)) {
            this.mc.player.removeEffect(MobEffects.NAUSEA);
         }

         if ((Boolean)this.blockDarkness.get() && this.mc.player.hasEffect(MobEffects.DARKNESS)) {
            this.mc.player.removeEffect(MobEffects.DARKNESS);
         }
      }
   }
}
