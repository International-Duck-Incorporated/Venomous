package net.venomous.meteoraddon.modules.esp;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.venomous.meteoraddon.VenomousAddon;

public class LightESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> range = this.sgGeneral
      .add(((Builder)((Builder)new Builder().name("range")).defaultValue(8)).min(2).max(16).sliderMax(16).build());
   private final Setting<Double> minLight = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder().name("min-light"))
            .defaultValue(0.0)
            .min(0.0)
            .max(15.0)
            .sliderMax(15.0)
            .build()
      );
   private final Setting<Double> maxLight = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder().name("max-light"))
            .defaultValue(7.0)
            .min(0.0)
            .max(15.0)
            .sliderMax(15.0)
            .build()
      );
   private final Map<BlockPos, Integer> lightLevels = new HashMap<>();

   public LightESP() {
      super(VenomousAddon.CATEGORY, "light-esp", "Displays light levels on blocks.");
   }

   @Override
   public void onActivate() {
      this.lightLevels.clear();
   }

   @Override
   public void onDeactivate() {
      this.lightLevels.clear();
   }

   @EventHandler
   private void onTick(Post event) {
      if (this.mc.player != null && this.mc.level != null) {
         this.lightLevels.clear();
         BlockPos playerPos = this.mc.player.blockPosition();
         int r = (Integer)this.range.get();

         for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
               for (int z = -r; z <= r; z++) {
                  BlockPos pos = playerPos.offset(x, y, z);
                  int light = this.mc.level.getMaxLocalRawBrightness(pos);
                  if (light >= (Double)this.minLight.get() && light <= (Double)this.maxLight.get()) {
                     this.lightLevels.put(pos, light);
                  }
               }
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      for (Entry<BlockPos, Integer> entry : this.lightLevels.entrySet()) {
         BlockPos pos = entry.getKey();
         int light = entry.getValue();
         Color color = light == 0 ? new Color(255, 50, 50, 60) : (light <= 7 ? new Color(255, 200, 50, 60) : new Color(50, 255, 50, 60));
         Color lineColor = new Color(color.r, color.g, color.b, 200);
         event.renderer.box(pos, color, lineColor, ShapeMode.Both, 0);
      }
   }

   public String getInfoString() {
      return this.lightLevels.isEmpty() ? null : this.lightLevels.size() + " blocks";
   }
}

