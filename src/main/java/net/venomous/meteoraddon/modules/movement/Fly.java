package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.McCompat;
import net.venomous.meteoraddon.utils.MovementMath;

public class Fly extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgGrim = this.settings.createGroup("Grim");
   private final SettingGroup sgVulcan = this.settings.createGroup("Vulcan");
   private final SettingGroup sgWeakAC = this.settings.createGroup("Weak AC");

   private final Setting<Fly.Mode> mode = this.sgGeneral.add(
      new EnumSetting.Builder<Fly.Mode>()
         .name("mode")
         .description("Flight mode — select your anticheat.")
         .defaultValue(Fly.Mode.VulcanHover)
         .build()
   );

   private final Setting<Double> speed = this.sgGeneral.add(
      new DoubleSetting.Builder()
         .name("speed")
         .description("Horizontal speed in blocks per tick.")
         .defaultValue(0.28)
         .min(0.05)
         .sliderRange(0.05, 2.0)
         .build()
   );

   private final Setting<Double> verticalSpeed = this.sgGeneral.add(
      new DoubleSetting.Builder()
         .name("vertical-speed")
         .description("Vertical speed used while holding jump/sneak.")
         .defaultValue(0.2)
         .min(0.01)
         .sliderRange(0.01, 1.0)
         .build()
   );

   private final Setting<Boolean> antiKick = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("anti-kick")
         .description("Periodically emit a small downward motion so the server keeps seeing gravity.")
         .defaultValue(true)
         .build()
   );

   private final Setting<Integer> antiKickDelay = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("anti-kick-delay")
         .description("Ticks between anti-kick pulses. Lower is safer, higher is smoother.")
         .defaultValue(20)
         .min(2)
         .sliderRange(2, 80)
         .visible(this.antiKick::get)
         .build()
   );

   private final Setting<Double> antiKickMotion = this.sgGeneral.add(
      new DoubleSetting.Builder()
         .name("anti-kick-motion")
         .description("Downward motion applied during an anti-kick pulse.")
         .defaultValue(0.04)
         .min(0.001)
         .sliderRange(0.001, 0.3)
         .visible(this.antiKick::get)
         .build()
   );

   private final Setting<Double> boatHorizontal = this.sgGrim.add(
      new DoubleSetting.Builder()
         .name("boat-speed")
         .description("Horizontal speed used by the Grim boat exploit.")
         .defaultValue(0.8)
         .min(0.1)
         .sliderRange(0.1, 1.5)
         .visible(() -> this.mode.get() == Fly.Mode.GrimBoat)
         .build()
   );

   private final Setting<Integer> boatSyncInterval = this.sgGrim.add(
      new IntSetting.Builder()
         .name("boat-sync-interval")
         .description("How often to re-sync onto the boat position, in ticks.")
         .defaultValue(5)
         .min(1)
         .sliderRange(1, 20)
         .visible(() -> this.mode.get() == Fly.Mode.GrimBoat)
         .build()
   );

   private final Setting<Integer> vulcanPeriod = this.sgVulcan.add(
      new IntSetting.Builder()
         .name("hover-period")
         .description("Ticks between the simulated fall pulses of the Vulcan hover.")
         .defaultValue(6)
         .min(2)
         .sliderRange(2, 20)
         .visible(() -> this.mode.get() == Fly.Mode.VulcanHover || this.mode.get() == Fly.Mode.VulcanLowHop)
         .build()
   );

   private final Setting<Double> vulcanFall = this.sgVulcan.add(
      new DoubleSetting.Builder()
         .name("hover-fall")
         .description("Downward motion of each Vulcan hover pulse.")
         .defaultValue(0.08)
         .min(0.001)
         .sliderRange(0.001, 0.4)
         .visible(() -> this.mode.get() == Fly.Mode.VulcanHover)
         .build()
   );

   private final Setting<Double> weakAcSpeed = this.sgWeakAC.add(
      new DoubleSetting.Builder()
         .name("weak-ac-speed")
         .description("Speed used by the Vanilla / weak-anticheat modes.")
         .defaultValue(0.5)
         .min(0.05)
         .sliderRange(0.05, 3.0)
         .visible(() -> this.mode.get() == Fly.Mode.Vanilla || this.mode.get() == Fly.Mode.LAC || this.mode.get() == Fly.Mode.Negativity)
         .build()
   );

   private final Setting<Integer> packetHeightLimit = this.sgGeneral.add(
      new IntSetting.Builder()
         .name("packet-height-limit")
         .description("Height limit for Packet mode (recommended: 8).")
         .defaultValue(8)
         .min(1)
         .sliderRange(1, 256)
         .visible(() -> this.mode.get() == Fly.Mode.Packet)
         .build()
   );

   private int tickCounter;
   private double startY;
   private boolean previousFlying;
   private float previousFlyingSpeed = 0.05F;

   public Fly() {
      super(VenomousAddon.CATEGORY, "fly", "Flight with bypasses for Grim/Vulcan/NCP/Oxa/Negativity/LAC.");
   }

   @Override
   public void onActivate() {
      this.tickCounter = 0;
      if (this.mc.player != null) {
         this.startY = this.mc.player.getY();
         this.previousFlying = this.mc.player.getAbilities().flying;
         this.previousFlyingSpeed = this.mc.player.getAbilities().getFlyingSpeed();
      }
   }

   @Override
   public void onDeactivate() {
      if (this.mc.player != null) {
         // Restore whatever the player had before instead of hard-resetting creative flight.
         this.mc.player.getAbilities().flying = this.previousFlying && this.mc.player.getAbilities().mayfly;
         this.mc.player.getAbilities().setFlyingSpeed(this.previousFlyingSpeed);
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player == null || this.mc.level == null) {
         return;
      }

      this.tickCounter++;

      switch (this.mode.get()) {
         case GrimBoat -> this.doGrimBoat();
         case GrimElytra -> this.doGrimElytra();
         case VulcanHover -> this.doVulcanHover();
         case VulcanLowHop -> this.doVulcanLowHop();
         case NCPBlink -> this.doAbilitiesFly(this.speed.get());
         case Oxa_AltitudeHold -> this.doMotionFly(this.speed.get(), true);
         case Negativity, LAC -> this.doMotionFly(this.weakAcSpeed.get(), true);
         case Vanilla -> this.doAbilitiesFly(this.weakAcSpeed.get());
         case Packet -> this.doPacket();
      }
   }

   private void doGrimBoat() {
      Entity boat = this.findNearestBoat();
      if (boat == null) {
         this.placeBoat();
         return;
      }

      double[] move = this.horizontal(this.boatHorizontal.get());
      double dy = MovementMath.verticalMotion(
         this.tickCounter,
         this.antiKick.get() ? this.antiKickDelay.get() : 0,
         -this.antiKickMotion.get(),
         this.jumping(),
         this.sneaking(),
         this.verticalSpeed.get()
      );

      if (this.tickCounter % this.boatSyncInterval.get() == 0) {
         this.mc.player.setPos(boat.getX(), this.mc.player.getY(), boat.getZ());
      }

      this.mc.player.setDeltaMovement(move[0], dy, move[1]);
   }

   private void doGrimElytra() {
      if (!this.mc.player.isFallFlying() && !this.mc.player.onGround()) {
         this.mc.player.startFallFlying();
      }

      double[] move = this.horizontal(this.speed.get());
      this.mc.player.setDeltaMovement(move[0], 0.0, move[1]);
   }

   /**
    * Vulcan does not care about {@code abilities.flying} on survival servers — it runs a
    * movement prediction. Setting creative flight (what this module used to do) produces the
    * Flight (Prediction) / Hover spam seen in the wild. Instead we stay inside the predicted
    * envelope: vanilla-accurate horizontal speed, and a periodic downward pulse so the
    * server keeps observing gravity.
    */
   private void doVulcanHover() {
      double[] move = this.horizontal(Math.min(this.speed.get(), MovementMath.SPRINT_SPEED));
      double dy = MovementMath.verticalMotion(
         this.tickCounter,
         this.vulcanPeriod.get(),
         -this.vulcanFall.get(),
         this.jumping(),
         this.sneaking(),
         Math.min(this.verticalSpeed.get(), 0.42)
      );

      this.mc.player.setDeltaMovement(move[0], dy, move[1]);
      this.mc.player.setOnGround(false);
   }

   private void doVulcanLowHop() {
      double[] move = this.horizontal(Math.min(this.speed.get(), MovementMath.SPRINT_SPEED));
      double dy = this.mc.player.getDeltaMovement().y;

      // Only hop when actually standing on something, and only on the configured cadence —
      // spamming jumpFromGround() every tick is what produced Jump (Motion) flags.
      if (this.mc.player.onGround() && this.tickCounter % this.vulcanPeriod.get() == 0) {
         dy = 0.42;
      }

      this.mc.player.setDeltaMovement(move[0], dy, move[1]);
   }

   private void doMotionFly(double horizontalSpeed, boolean allowVertical) {
      double[] move = this.horizontal(horizontalSpeed);
      double dy = allowVertical
         ? MovementMath.verticalMotion(
            this.tickCounter,
            this.antiKick.get() ? this.antiKickDelay.get() : 0,
            -this.antiKickMotion.get(),
            this.jumping(),
            this.sneaking(),
            this.verticalSpeed.get()
         )
         : 0.0;

      this.mc.player.setDeltaMovement(move[0], dy, move[1]);
   }

   private void doAbilitiesFly(double horizontalSpeed) {
      this.mc.player.getAbilities().flying = true;
      this.mc.player.getAbilities().setFlyingSpeed((float)MovementMath.clamp(horizontalSpeed * 0.05, 0.001, 1.0));
   }

   private void doPacket() {
      if (this.mc.player.getY() > this.startY + this.packetHeightLimit.get()) {
         this.mc.player.setDeltaMovement(this.mc.player.getDeltaMovement().x, -0.1, this.mc.player.getDeltaMovement().z);
         return;
      }

      double s = this.speed.get();
      double[] move = this.horizontal(s);
      double dy = 0.0;
      if (this.jumping()) {
         dy = this.verticalSpeed.get();
      } else if (this.sneaking()) {
         dy = -this.verticalSpeed.get();
      }

      this.mc.player.setPos(this.mc.player.getX() + move[0], this.mc.player.getY() + dy, this.mc.player.getZ() + move[1]);
      this.mc.player.connection.send(new Pos(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(), false, false));
   }

   private double[] horizontal(double speed) {
      double fwd = this.mc.player.input.keyPresses.forward() ? 1.0 : (this.mc.player.input.keyPresses.backward() ? -1.0 : 0.0);
      double str = this.mc.player.input.keyPresses.left() ? 1.0 : (this.mc.player.input.keyPresses.right() ? -1.0 : 0.0);
      return MovementMath.velocity(this.mc.player.getYRot(), fwd, str, speed);
   }

   private boolean jumping() {
      return this.mc.options.keyJump.isDown();
   }

   private boolean sneaking() {
      return this.mc.options.keyShift.isDown();
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

   public enum Mode {
      GrimBoat,
      GrimElytra,
      VulcanHover,
      VulcanLowHop,
      NCPBlink,
      Oxa_AltitudeHold,
      Negativity,
      LAC,
      Vanilla,
      Packet;
   }
}
