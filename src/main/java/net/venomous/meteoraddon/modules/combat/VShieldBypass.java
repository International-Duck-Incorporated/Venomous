package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class VShieldBypass extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> ignoreAxe = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("ignore-axe")).description("Do nothing when holding an axe, since axes already disable shields."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> behindDistance = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("behind-distance"))
               .description("Distance behind the target to briefly attack from."))
            .defaultValue(2.0)
            .range(0.5, 5.0)
            .sliderMax(4.0)
            .build()
      );

   public VShieldBypass() {
      super(VenomousAddon.CATEGORY, "v-shield-bypass", "Briefly attacks blocking targets from behind to bypass shields.");
   }

   @EventHandler
   private void onMouse(MouseClickEvent event) {
      if (this.mc.player != null
         && this.mc.gameMode != null
         && this.mc.level != null
         && this.mc.getConnection() != null
         && this.mc.screen == null
         && event.action == KeyAction.Press
         && event.button() == 0
         && this.mc.hitResult instanceof EntityHitResult hit) {
         this.bypass(hit.getEntity(), event);
      }
   }

   private void bypass(Entity target, MouseClickEvent event) {
      LivingEntity living;
      if (target instanceof LivingEntity
         && (living = (LivingEntity)target).isBlocking()
         && (!(Boolean)this.ignoreAxe.get() || !InvUtils.testInMainHand(stack -> stack.getItem() instanceof AxeItem))) {
         Vec3 original = this.mc.player.position();
         Vec3 look = living.getViewVector(1.0F).normalize();
         Vec3 newPos = living.position().subtract(look.scale((Double)this.behindDistance.get()));

         for (double y = 0.0; y <= 3.0; y += 0.25) {
            Vec3 candidate = newPos.add(0.0, y, 0.0);
            if (this.mc.level.noCollision(this.mc.player, this.mc.player.getBoundingBox().move(candidate.subtract(original)))) {
               newPos = candidate;
               break;
            }
         }

         event.setCancelled(true);
         this.mc.getConnection().send(new Pos(newPos.x, newPos.y, newPos.z, true, this.mc.player.horizontalCollision));
         this.mc.player.setPos(newPos.x, newPos.y, newPos.z);
         this.mc.gameMode.attack(this.mc.player, target);
         this.mc.player.swing(InteractionHand.MAIN_HAND);
         this.mc.getConnection().send(new Pos(original.x, original.y, original.z, true, this.mc.player.horizontalCollision));
         this.mc.player.setPos(original.x, original.y, original.z);
      }
   }
}
