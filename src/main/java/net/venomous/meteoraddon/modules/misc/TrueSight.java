package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class TrueSight extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<Boolean> invisibleEntities = this.sgGeneral
      .add(
         new BoolSetting.Builder().name("invisible-entities")
            .description("Highlight invisible living entities that the client knows about.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> range = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("range")
            .description("Maximum render range for invisible entities.")
            .defaultValue(96.0)
            .range(8.0, 256.0)
            .sliderMax(128.0)
            .build()
      );
   private final Setting<ShapeMode> shapeMode = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How boxes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
      );
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("side-color")
            .description("Box side color.")
            .defaultValue(new SettingColor(255, 255, 255, 45))
            .build()
      );
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("line-color")
            .description("Box line color.")
            .defaultValue(new SettingColor(255, 255, 255, 220))
            .build()
      );

   public TrueSight() {
      super(VenomousAddon.CATEGORY, "true-sight", "Highlights invisible entities visible to the client.");
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if ((Boolean)this.invisibleEntities.get() && this.mc.player != null && this.mc.level != null) {
         double maxSq = (Double)this.range.get() * (Double)this.range.get();

         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity && entity != this.mc.player && entity.isInvisible() && !(this.mc.player.distanceToSqr(entity) > maxSq)) {
               event.renderer.box(entity.getBoundingBox(), (Color)this.sideColor.get(), (Color)this.lineColor.get(), (ShapeMode)this.shapeMode.get(), 0);
            }
         }
      }
   }
}
