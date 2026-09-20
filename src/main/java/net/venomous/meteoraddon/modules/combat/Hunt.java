package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.pathing.PathManagers;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

/**
 * Walks toward a named player.
 *
 * <p>This module used to delegate unconditionally to {@link PathManagers}. Meteor only supplies a
 * real path manager when Baritone (or Voyager) is on the classpath — otherwise {@code PathManagers.get()}
 * returns a {@code NopPathManager} whose {@code moveTo} is literally an empty method body. With no
 * pathing mod installed the module therefore did nothing at all, silently.
 *
 * <p>It now detects that case and falls back to driving the movement keys directly, so Hunt works
 * standalone. Pathfinding is still preferred when it is genuinely available.
 */
public class Hunt extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<String> playerName = this.sgGeneral.add(
      new StringSetting.Builder()
         .name("player-name")
         .description("The exact name of the player to hunt.")
         .defaultValue("")
         .build()
   );

   private final Setting<Hunt.Mode> mode = this.sgGeneral.add(
      new EnumSetting.Builder<Hunt.Mode>()
         .name("mode")
         .description("Auto uses Baritone when present and falls back to direct movement otherwise.")
         .defaultValue(Hunt.Mode.Auto)
         .build()
   );

   private final Setting<Double> stopDistance = this.sgGeneral.add(
      new DoubleSetting.Builder()
         .name("stop-distance")
         .description("Stop approaching once within this many blocks of the target.")
         .defaultValue(3.0)
         .range(0.0, 10.0)
         .sliderMax(10.0)
         .build()
   );

   private final Setting<Boolean> rotate = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("rotate")
         .description("Face the target while hunting (direct movement only).")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> sprint = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("sprint")
         .description("Sprint while chasing (direct movement only).")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> autoJump = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("auto-jump")
         .description("Jump when blocked horizontally (direct movement only).")
         .defaultValue(true)
         .build()
   );

   private final Setting<Boolean> notify = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("notify")
         .description("Warn in chat when the target cannot be found.")
         .defaultValue(true)
         .build()
   );

   private boolean pressingForward = false;
   private boolean pressingSprint = false;
   private boolean warnedMissing = false;
   private boolean warnedNoPathing = false;

   public Hunt() {
      super(VenomousAddon.CATEGORY, "hunt", "Walks toward a specific player by name.");
   }

   @Override
   public void onActivate() {
      this.warnedMissing = false;
      this.warnedNoPathing = false;
   }

   @Override
   public void onDeactivate() {
      if (this.usePathing()) {
         PathManagers.get().stop();
      }
      this.releaseKeys();
   }

   /** True when a real pathing backend is installed, rather than Meteor's no-op stub. */
   private boolean pathingAvailable() {
      String name = PathManagers.get().getName();
      return name != null && !name.equalsIgnoreCase("None");
   }

   private boolean usePathing() {
      return switch (this.mode.get()) {
         case Baritone -> true;
         case Direct -> false;
         case Auto -> this.pathingAvailable();
      };
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player == null || this.mc.level == null) {
         return;
      }

      String wanted = this.playerName.get().trim();
      if (wanted.isEmpty()) {
         this.releaseKeys();
         return;
      }

      Player target = this.mc.level.players().stream()
         .filter(p -> p != this.mc.player)
         .filter(p -> p.getGameProfile().name().equalsIgnoreCase(wanted))
         .findFirst()
         .orElse(null);

      if (target == null) {
         if (this.notify.get() && !this.warnedMissing) {
            this.warning("No player named '%s' is in render distance.", wanted);
            this.warnedMissing = true;
         }
         this.releaseKeys();
         return;
      }

      this.warnedMissing = false;

      if (this.usePathing()) {
         if (this.mode.get() == Hunt.Mode.Baritone && !this.pathingAvailable()) {
            if (this.notify.get() && !this.warnedNoPathing) {
               this.warning("No pathing mod installed - install Baritone or switch mode to Direct.");
               this.warnedNoPathing = true;
            }
            return;
         }
         PathManagers.get().moveTo(target.blockPosition());
         return;
      }

      this.driveToward(target);
   }

   /** Direct movement fallback: aim at the target and hold forward. */
   private void driveToward(Player target) {
      double stop = this.stopDistance.get();
      if (this.mc.player.distanceToSqr(target) <= stop * stop) {
         this.releaseKeys();
         return;
      }

      if (this.rotate.get()) {
         Vec3 eye = target.getEyePosition();
         Rotations.rotate(Rotations.getYaw(eye), Rotations.getPitch(eye));
      } else {
         // Still need to face roughly the right way for "forward" to mean anything.
         double dx = target.getX() - this.mc.player.getX();
         double dz = target.getZ() - this.mc.player.getZ();
         this.mc.player.setYRot((float)Math.toDegrees(Math.atan2(-dx, dz)));
      }

      this.mc.options.keyUp.setDown(true);
      this.pressingForward = true;

      if (this.sprint.get()) {
         this.mc.options.keySprint.setDown(true);
         this.pressingSprint = true;
      }

      if (this.autoJump.get() && this.mc.player.horizontalCollision && this.mc.player.onGround()) {
         this.mc.player.jumpFromGround();
      }
   }

   private void releaseKeys() {
      if (this.pressingForward) {
         this.mc.options.keyUp.setDown(false);
         this.pressingForward = false;
      }
      if (this.pressingSprint) {
         this.mc.options.keySprint.setDown(false);
         this.pressingSprint = false;
      }
   }

   @Override
   public String getInfoString() {
      String name = this.playerName.get().trim();
      return name.isEmpty() ? "no target" : name;
   }

   public enum Mode {
      /** Use Baritone if installed, otherwise fall back to direct movement. */
      Auto,
      /** Force Baritone pathing (does nothing when no pathing mod is present). */
      Baritone,
      /** Always drive the movement keys directly. */
      Direct
   }
}
