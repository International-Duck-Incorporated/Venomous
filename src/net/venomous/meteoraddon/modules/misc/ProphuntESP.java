package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.ColorSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.venomous.meteoraddon.VenomousAddon;

public class ProphuntESP extends Module {
   private final SettingGroup sgRender = this.settings.createGroup("Render");
   private final Setting<SettingColor> sideColor = this.sgRender
      .add(((Builder)((Builder)new Builder().name("side-color")).description("Box side color.")).defaultValue(new SettingColor(255, 0, 0, 45)).build());
   private final Setting<SettingColor> lineColor = this.sgRender
      .add(((Builder)((Builder)new Builder().name("line-color")).description("Box line color.")).defaultValue(new SettingColor(255, 0, 0, 220)).build());

   public ProphuntESP() {
      super(VenomousAddon.CATEGORY, "v-prophunt-esp", "Highlights invisible mobs commonly used as prophunt fake blocks.");
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.player != null && this.mc.level != null) {
         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof Mob && entity.isInvisible() && !(this.mc.player.distanceToSqr(entity) < 0.25)) {
               AABB box = new AABB(-0.5, 0.0, -0.5, 0.5, 1.0, 0.5).move(entity.position());
               event.renderer.box(box, (Color)this.sideColor.get(), (Color)this.lineColor.get(), ShapeMode.Both, 0);
            }
         }
      }
   }
}
