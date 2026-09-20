package net.venomous.meteoraddon.modules.esp;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.DoubleSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class SkeletonESP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Double> lineWidth = this.sgGeneral
      .add(
         ((Builder)((Builder)new Builder().name("line-width")).description("Width of the skeleton lines."))
            .defaultValue(2.0)
            .min(0.5)
            .max(5.0)
            .sliderMax(5.0)
            .build()
      );
   private final Setting<Boolean> showPlayers = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-players"))
                  .description("Render skeletons on players."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> showSelf = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("show-self"))
                  .description("Render your own skeleton."))
               .defaultValue(false))
            .build()
      );
   private final Setting<SettingColor> color = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("color"))
               .description("Skeleton line color."))
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .build()
      );

   public SkeletonESP() {
      super(VenomousAddon.CATEGORY, "skeleton-esp", "Renders skeleton overlay on players showing their pose.");
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (this.mc.level != null && this.mc.player != null) {
         for (Player player : this.mc.level.players()) {
            if ((player != this.mc.player || (Boolean)this.showSelf.get()) && (Boolean)this.showPlayers.get()) {
               this.renderSkeleton(event, player);
            }
         }
      }
   }

   private void renderSkeleton(Render3DEvent event, Player player) {
      float tickDelta = event.tickDelta;
      Color lineColor = (Color)this.color.get();
      Vec3 head = player.getPosition(tickDelta).add(0.0, player.getEyeHeight(), 0.0);
      Vec3 body = player.getPosition(tickDelta).add(0.0, player.getEyeHeight() * 0.5, 0.0);
      Vec3 feet = player.getPosition(tickDelta);
      float width = (float)((Double)this.lineWidth.get()).doubleValue();
      event.renderer.line(head.x, head.y, head.z, body.x, body.y, body.z, lineColor);
      event.renderer.line(body.x, body.y, body.z, feet.x, feet.y, feet.z, lineColor);
      double armOffset = 0.3;
      Vec3 leftShoulder = new Vec3(body.x - armOffset, body.y, body.z);
      Vec3 rightShoulder = new Vec3(body.x + armOffset, body.y, body.z);
      Vec3 leftHand = new Vec3(body.x - armOffset * 1.5, body.y - 0.4, body.z);
      Vec3 rightHand = new Vec3(body.x + armOffset * 1.5, body.y - 0.4, body.z);
      event.renderer.line(body.x, body.y, body.z, leftShoulder.x, leftShoulder.y, leftShoulder.z, lineColor);
      event.renderer.line(leftShoulder.x, leftShoulder.y, leftShoulder.z, leftHand.x, leftHand.y, leftHand.z, lineColor);
      event.renderer.line(body.x, body.y, body.z, rightShoulder.x, rightShoulder.y, rightShoulder.z, lineColor);
      event.renderer.line(rightShoulder.x, rightShoulder.y, rightShoulder.z, rightHand.x, rightHand.y, rightHand.z, lineColor);
      double legOffset = 0.15;
      Vec3 leftKnee = new Vec3(feet.x - legOffset, feet.y + 0.5, feet.z);
      Vec3 rightKnee = new Vec3(feet.x + legOffset, feet.y + 0.5, feet.z);
      Vec3 leftFoot = new Vec3(feet.x - legOffset, feet.y, feet.z);
      Vec3 rightFoot = new Vec3(feet.x + legOffset, feet.y, feet.z);
      event.renderer.line(feet.x, feet.y + 0.5, feet.z, leftKnee.x, leftKnee.y, leftKnee.z, lineColor);
      event.renderer.line(leftKnee.x, leftKnee.y, leftKnee.z, leftFoot.x, leftFoot.y, leftFoot.z, lineColor);
      event.renderer.line(feet.x, feet.y + 0.5, feet.z, rightKnee.x, rightKnee.y, rightKnee.z, lineColor);
      event.renderer.line(rightKnee.x, rightKnee.y, rightKnee.z, rightFoot.x, rightFoot.y, rightFoot.z, lineColor);
   }
}
