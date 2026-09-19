package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.McCompat;

public class Fly extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgGrim = this.settings.createGroup("Grim");
   private final SettingGroup sgVulcan = this.settings.createGroup("Vulcan");
   private final SettingGroup sgWeakAC = this.settings.createGroup("Weak AC");
   private final Setting<Fly.Mode> mode = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("mode")).description("Flight mode — select your anticheat.")).defaultValue(Fly.Mode.GrimBoat))
            .build()
      );
   private final Setting<Double> speed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("speed"))
               .description("Speed for flight."))
            .defaultValue(1.0)
            .min(0.1)
            .sliderMax(5.0)
            .build()
      );
   private final Setting<Integer> packetHeightLimit = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("packet-height-limit"))
                     .description("Height limit for Packet mode (recommended: 8)."))
                  .defaultValue(8))
               .min(1)
               .sliderMax(256)
               .visible(() -> this.mode.get() == Fly.Mode.Packet))
            .build()
      );
   private int tickCounter = 0;
   private double startY = 0.0;

   public Fly() {
      super(VenomousAddon.CATEGORY, "fly", "Flight with bypasses for Grim/Vulcan/NCP/Oxa/Negativity/LAC.");
   }

   public void onActivate() {
      this.tickCounter = 0;
      if (this.mc.player != null) {
         this.startY = this.mc.player.getY();
      }
   }

   public void onDeactivate() {
      if (this.mc.player != null) {
         this.mc.player.getAbilities().flying = false;
         this.mc.player.getAbilities().setFlyingSpeed(0.05F);
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         this.tickCounter++;
         switch ((Fly.Mode)this.mode.get()) {
            case GrimBoat:
               this.doGrimBoat();
               break;
            case GrimElytra:
               this.doGrimElytra();
               break;
            case VulcanGhostBlock:
               this.doVulcanGhostBlock();
               break;
            case VulcanLowHop:
               this.doVulcanLowHop();
               break;
            case NCPBlink:
               this.doNCPBlink();
               break;
            case Oxa_AltitudeHold:
               this.doOxa();
               break;
            case Negativity:
               this.doNegativity();
               break;
            case LAC:
               this.doLAC();
               break;
            case Vanilla:
               this.doVanilla();
               break;
            case Packet:
               this.doPacket();
         }
      }
   }

   private void doGrimBoat() {
      Entity boat = this.findNearestBoat();
      if (boat == null) {
         this.placeBoat();
      } else {
         double hSpeed = 0.8;
         double vSpeed = 0.35;
         Vec3 move = this.getMovementVector(hSpeed);
         double dy = 0.0;
         if (this.mc.options.keyJump.isDown()) {
            dy = vSpeed;
         } else if (this.mc.options.keyShift.isDown()) {
            dy = -vSpeed;
         }

         if (this.tickCounter % 5 == 0) {
            this.mc.player.setPos(boat.getX(), this.mc.player.getY(), boat.getZ());
         }

         this.mc.player.setDeltaMovement(move.x, dy, move.z);
      }
   }

   private void doGrimElytra() {
      if (!this.mc.player.isFallFlying()) {
         this.mc.player.startFallFlying();
      }

      this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, 0.0, this.mc.player.getDeltaMovement().z);
   }

   private void doOxa() {
      this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, 0.0, this.mc.player.getDeltaMovement().z);
      if (this.mc.options.keyJump.isDown()) {
         this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, 0.2, this.mc.player.getDeltaMovement().z);
      }

      if (this.mc.options.keyShift.isDown()) {
         this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, -0.2, this.mc.player.getDeltaMovement().z);
      }
   }

   private void doPacket() {
      if (this.mc.player.getY() > this.startY + ((Integer)this.packetHeightLimit.get()).intValue()) {
         this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, -0.1, this.mc.player.getDeltaMovement().z);
      } else {
         double s = (Double)this.speed.get();
         Vec3 move = this.getMovementVector(s);
         double dy = 0.0;
         if (this.mc.options.keyJump.isDown()) {
            dy = s * 0.5;
         } else if (this.mc.options.keyShift.isDown()) {
            dy = -s * 0.5;
         }

         this.mc.player.setPos(this.mc.player.getX() + move.x, this.mc.player.getY() + dy, this.mc.player.getZ() + move.z);
         this.mc.player.connection.send(new Pos(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(), false, false));
      }
   }

   private Vec3 getMovementVector(double speed) {
      float yaw = this.mc.player.getYRot();
      double fwd = this.mc.player.input.keyPresses.forward() ? 1.0 : (this.mc.player.input.keyPresses.backward() ? -1 : 0);
      double str = this.mc.player.input.keyPresses.left() ? 1.0 : (this.mc.player.input.keyPresses.right() ? -1 : 0);
      if (fwd == 0.0 && str == 0.0) {
         return Vec3.ZERO;
      }

      double angle = Math.toRadians(yaw);
      if (fwd < 0.0) {
         angle += Math.PI;
      }

      if (str > 0.0) {
         angle -= (Math.PI / 2) * (fwd == 0.0 ? 1.0 : 0.5);
      }

      if (str < 0.0) {
         angle += (Math.PI / 2) * (fwd == 0.0 ? 1.0 : 0.5);
      }

      return new Vec3(-Math.sin(angle) * speed, 0.0, Math.cos(angle) * speed);
   }

   private Entity findNearestBoat() {
      return McCompat.<Entity>stream(this.mc.level.entitiesForRendering())
         .filter(e -> e instanceof AbstractBoat && e != this.mc.player.getVehicle())
         .min((e1, e2) -> Double.compare(this.mc.player.distanceTo(e1), this.mc.player.distanceTo(e2)))
         .orElse(null);
   }

   private void placeBoat() {
      for (int i = 0; i < 9; i++) {
         ItemStack s = this.mc.player.getInventory().getItem(i);
         if (s.getItem().toString().contains("boat")) {
            int prev = this.mc.player.getInventory().getSelectedSlot();
            this.mc.player.getInventory().setSelectedSlot(i);
            this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
            this.mc.player.getInventory().setSelectedSlot(prev);
            return;
         }
      }
   }

   private void doVulcanGhostBlock() {
      this.mc.player.getAbilities().flying = true;
      this.mc.player.getAbilities().setFlyingSpeed((float)((Double)this.speed.get() * 0.05F));
   }

   private void doVulcanLowHop() {
      if (this.mc.player.onGround()) {
         this.mc.player.jumpFromGround();
      }
   }

   private void doNCPBlink() {
      this.mc.player.getAbilities().flying = true;
   }

   private void doNegativity() {
      this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, 0.0, this.mc.player.getDeltaMovement().z);
   }

   private void doLAC() {
      this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, 0.0, this.mc.player.getDeltaMovement().z);
   }

   private void doVanilla() {
      this.mc.player.getAbilities().flying = true;
      this.mc.player.getAbilities().setFlyingSpeed((float)((Double)this.speed.get() * 0.05F));
   }

   public enum Mode {
      GrimBoat,
      GrimElytra,
      VulcanGhostBlock,
      VulcanLowHop,
      NCPBlink,
      Oxa_AltitudeHold,
      Negativity,
      LAC,
      Vanilla,
      Packet;
   }
}
