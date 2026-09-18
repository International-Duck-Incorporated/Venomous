package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class VMaceKill extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> fallHeight = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("fall-height")).description("The fall height to fake when attacking with a mace.")).defaultValue(22))
            .min(1)
            .max(170)
            .build()
      );
   private final Setting<Boolean> onlyOnGround = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("only-on-ground"))
                  .description("Only activate when standing on the ground."))
               .defaultValue(true))
            .build()
      );

   public VMaceKill() {
      super(VenomousAddon.CATEGORY, "v-mace-kill", "Fakes fall height to deal massive mace damage.");
   }

   @EventHandler
   private void onAttack(AttackEntityEvent event) {
      if (this.mc.player != null
         && this.mc.level != null
         && this.mc.player.getMainHandItem().getItem() == Items.MACE
         && (!(Boolean)this.onlyOnGround.get() || this.mc.player.onGround())) {
         int height = this.determineHeight();
         if (height > 0) {
            Vec3 pos = this.mc.player.getPosition(0.0F);
            this.mc.player.connection.send(new Pos(pos.x, pos.y + height, pos.z, false, this.mc.player.horizontalCollision));
            this.mc.player.connection.send(new Pos(pos.x, pos.y, pos.z, false, this.mc.player.horizontalCollision));
         }
      }
   }

   private int determineHeight() {
      AABB box = this.mc.player.getBoundingBox();

      for (int i = (Integer)this.fallHeight.get(); i >= 1; i--) {
         AABB newBox = box.move(0.0, i, 0.0);
         if (this.mc.level.noBlockCollision(this.mc.player, newBox)) {
            return i;
         }
      }

      return 0;
   }
}
