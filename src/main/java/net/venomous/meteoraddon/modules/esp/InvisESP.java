package net.venomous.meteoraddon.modules.esp;

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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.EnumSetting;

public class InvisESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgPlayers = this.settings.createGroup("Players");
   private final SettingGroup sgMobs = this.settings.createGroup("Mobs");
   private final Setting<ShapeMode> shapeMode = this.sgGeneral
      .add(
         new EnumSetting.Builder<ShapeMode>().name("shape-mode").description("How the box should be rendered.").defaultValue(ShapeMode.Both).build()
      );
   private final Setting<Double> lineWidth = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("line-width")
            .description("Thickness of the box lines.")
            .defaultValue(2.0)
            .min(0.1)
            .max(10.0)
            .sliderMax(5.0)
            .visible(() -> this.shapeMode.get() != ShapeMode.Sides)
            .build()
      );
   private final Setting<Double> fadeDistance = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("fade-distance")
            .description("Distance at which boxes start to fade (0 = no fade).")
            .defaultValue(0.0)
            .min(0.0)
            .max(100.0)
            .sliderMax(50.0)
            .build()
      );
   private final Setting<Boolean> onlyInvis = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("only-invisible")
            .description("Only show hitboxes for invisible entities.")
            .defaultValue(true)
            .build()
      );
   /**
    * Renders a fixed block-sized box instead of the entity hitbox.
    *
    * <p>Absorbed from the former {@code prophunt-esp} module, which rendered exactly this shape over
    * invisible mobs (the fake blocks used in prophunt) and was otherwise a strict subset of this
    * module's behaviour.
    */
   private final Setting<Boolean> blockSizedBoxes = this.sgMobs.add(
      new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
         .name("block-sized-boxes")
         .description("Draw a 1x1 block box over invisible mobs instead of their hitbox (prophunt fake blocks).")
         .defaultValue(false)
         .build()
   );

   private final Setting<Boolean> notifications = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("notifications")
            .description("Show chat feedback.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> showPlayers = this.sgPlayers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("show-players")
            .description("Show hitboxes for players.")
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> playerColor = this.sgPlayers
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("player-color")
            .description("Color of player hitboxes.")
            .defaultValue(new SettingColor(255, 0, 0, 100))
            .visible(this.showPlayers::get)
            .build()
      );
   private final Setting<SettingColor> playerLineColor = this.sgPlayers
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("player-line-color")
            .description("Color of player hitbox lines.")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .visible(() -> (Boolean)this.showPlayers.get() && this.shapeMode.get() != ShapeMode.Sides)
            .build()
      );
   private final Setting<Boolean> showSelf = this.sgPlayers
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("show-self")
            .description("Show your own hitbox when invisible.")
            .defaultValue(false)
            .visible(this.showPlayers::get)
            .build()
      );
   private final Setting<Boolean> showMobs = this.sgMobs
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("show-mobs")
            .description("Show hitboxes for mobs.")
            .defaultValue(true)
            .build()
      );
   private final Setting<SettingColor> mobColor = this.sgMobs
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("mob-color")
            .description("Color of mob hitboxes.")
            .defaultValue(new SettingColor(0, 255, 0, 100))
            .visible(this.showMobs::get)
            .build()
      );
   private final Setting<SettingColor> mobLineColor = this.sgMobs
      .add(
         new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
            .name("mob-line-color")
            .description("Color of mob hitbox lines.")
            .defaultValue(new SettingColor(0, 255, 0, 255))
            .visible(() -> (Boolean)this.showMobs.get() && this.shapeMode.get() != ShapeMode.Sides)
            .build()
      );
   private final Setting<Boolean> showPassive = this.sgMobs
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("show-passive")
            .description("Show hitboxes for passive mobs (animals).")
            .defaultValue(false)
            .visible(this.showMobs::get)
            .build()
      );
   private final Setting<Boolean> showHostile = this.sgMobs
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("show-hostile")
            .description("Show hitboxes for hostile mobs.")
            .defaultValue(true)
            .visible(this.showMobs::get)
            .build()
      );

   public InvisESP() {
      super(VenomousAddon.CATEGORY, "invis-esp", "Shows 3D hitbox for invisible players and mobs.");
   }

   public void onActivate() {
      if ((Boolean)this.notifications.get()) {
         this.info("Invisible hitboxes will now be displayed.", new Object[0]);
      }
   }

   @EventHandler
   private void onRender3D(Render3DEvent event) {
      if (this.mc.level != null && this.mc.player != null) {
         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity livingEntity
               && (entity != this.mc.player || (Boolean)this.showSelf.get())
               && (!(Boolean)this.onlyInvis.get() || entity.isInvisible())) {
               if (entity instanceof Player) {
                  if ((Boolean)this.showPlayers.get()) {
                     this.renderHitbox(event, entity, (SettingColor)this.playerColor.get(), (SettingColor)this.playerLineColor.get());
                  }
               } else if (entity instanceof Mob mob && (Boolean)this.showMobs.get()) {
                  boolean isHostile = mob.isAggressive() || mob.getTarget() != null;
                  if ((!isHostile || (Boolean)this.showHostile.get()) && (isHostile || (Boolean)this.showPassive.get())) {
                     this.renderHitbox(event, entity, (SettingColor)this.mobColor.get(), (SettingColor)this.mobLineColor.get());
                  }
               }
            }
         }
      }
   }

   private void renderHitbox(Render3DEvent event, Entity entity, SettingColor fillColor, SettingColor lineColor) {
      AABB box = this.blockSizedBoxes.get() && entity instanceof Mob && entity.isInvisible()
         ? new AABB(-0.5, 0.0, -0.5, 0.5, 1.0, 0.5).move(entity.position())
         : entity.getBoundingBox();
      double distance = this.mc.player.distanceToSqr(entity);
      double fadeStart = (Double)this.fadeDistance.get();
      Color finalFillColor = fillColor.copy();
      Color finalLineColor = lineColor.copy();
      if (fadeStart > 0.0 && distance > fadeStart * fadeStart) {
         double fadeFactor = Math.max(0.0, 1.0 - (Math.sqrt(distance) - fadeStart) / fadeStart);
         finalFillColor.a = (int)(fillColor.a * fadeFactor);
         finalLineColor.a = (int)(lineColor.a * fadeFactor);
      }

      event.renderer.box(box, finalFillColor, finalLineColor, (ShapeMode)this.shapeMode.get(), 0);
   }

   public String getInfoString() {
      int count = 0;
      if (this.mc.level != null && this.mc.player != null) {
         for (Entity entity : this.mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity && entity != this.mc.player && (!(Boolean)this.onlyInvis.get() || entity.isInvisible())) {
               if (entity instanceof Player && (Boolean)this.showPlayers.get()) {
                  count++;
               } else if (entity instanceof Mob && (Boolean)this.showMobs.get()) {
                  count++;
               }
            }
         }
      }

      return count + " entities";
   }
}
