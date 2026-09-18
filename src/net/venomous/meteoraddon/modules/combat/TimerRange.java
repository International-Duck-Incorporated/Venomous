package net.venomous.meteoraddon.modules.combat;

import java.util.concurrent.ThreadLocalRandom;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.player.Player;
import net.venomous.meteoraddon.VenomousAddon;

public class TimerRange extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> chance = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("chance")).description("Chance of activating (0-100%).")).defaultValue(100)).min(0).max(100).build()
      );
   private final Setting<Double> normalSpeed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("normal-speed"))
               .description("Timer speed when not near enemy."))
            .defaultValue(0.9)
            .min(0.1)
            .max(10.0)
            .build()
      );
   private final Setting<Double> inRangeSpeed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("in-range-speed"))
               .description("Timer speed when in range of enemy."))
            .defaultValue(0.95)
            .min(0.1)
            .max(10.0)
            .build()
      );
   private final Setting<Double> boostSpeed = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("boost-speed"))
               .description("Timer speed boost when approaching enemy."))
            .defaultValue(2.0)
            .min(0.1)
            .max(10.0)
            .build()
      );
   private final Setting<Double> distanceToSpeedUp = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("distance-to-speed-up"))
               .description("Distance at which to speed up."))
            .defaultValue(3.5)
            .min(0.0)
            .max(10.0)
            .build()
      );
   private final Setting<Double> distanceToPause = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("distance-to-pause"))
               .description("Distance at which to pause (reset timer)."))
            .defaultValue(3.0)
            .min(0.0)
            .max(10.0)
            .build()
      );
   private final Setting<Double> distanceToStartWorking = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("distance-to-start-working"))
               .description("Max distance to start working."))
            .defaultValue(100.0)
            .min(0.0)
            .max(500.0)
            .build()
      );
   private final Setting<Double> timerBalanceLimit = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                  .name("timer-balance-limit"))
               .description("Timer balance limit."))
            .defaultValue(20.0)
            .min(0.0)
            .max(50.0)
            .build()
      );
   private final Setting<Boolean> pauseOnFlag = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("pause-on-flag"))
                  .description("Pause when you get flagged by the server."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> onlyOnGround = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("only-on-ground"))
                  .description("Only activate when on ground."))
               .defaultValue(false))
            .build()
      );
   private boolean reachedTheLimit = false;
   private double balanceTimer = 0.0;
   private double lastSetTimer = 1.0;

   public TimerRange() {
      super(VenomousAddon.CATEGORY, "v-timer-range", "Adjusts timer speed based on distance to enemies.");
   }

   public void onActivate() {
      this.balanceTimer = (Double)this.timerBalanceLimit.get();
      this.reachedTheLimit = false;
      this.lastSetTimer = 1.0;
   }

   public void onDeactivate() {
      Timer timer = (Timer)Modules.get().get(Timer.class);
      if (timer != null) {
         timer.setOverride(1.0);
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null && (!(Boolean)this.onlyOnGround.get() || this.mc.player.onGround())) {
         double newTimerSpeed = this.updateTimerSpeed();
         if (newTimerSpeed != -1.0) {
            this.lastSetTimer = newTimerSpeed;
         }

         double balanceChange = this.lastSetTimer - 1.0;
         if ((this.balanceTimer > 0.0 || balanceChange > 0.0) && (this.balanceTimer < (Double)this.timerBalanceLimit.get() * 2.0 || balanceChange < 0.0)) {
            this.balanceTimer += balanceChange;
         }

         if (this.balanceTimer <= (Double)this.timerBalanceLimit.get()) {
            this.lastSetTimer = (Double)this.inRangeSpeed.get();
         }

         if (this.balanceTimer <= 0.0) {
            this.reachedTheLimit = false;
         }

         Timer timer = (Timer)Modules.get().get(Timer.class);
         if (timer != null) {
            timer.setOverride(this.lastSetTimer);
         }
      }
   }

   private double updateTimerSpeed() {
      Player enemy = this.findClosestEnemy((Double)this.distanceToPause.get());
      if (enemy != null) {
         return 1.0;
      }

      Player farEnemy = this.findClosestEnemy((Double)this.distanceToStartWorking.get());
      if (farEnemy == null) {
         return -1.0;
      }

      if ((Integer)this.chance.get() != 100 && ThreadLocalRandom.current().nextInt(100) > (Integer)this.chance.get()) {
         return -1.0;
      }

      Player nearEnemy = this.findClosestEnemy((Double)this.distanceToSpeedUp.get());
      if (nearEnemy == null) {
         return (Double)this.normalSpeed.get();
      }

      if (this.balanceTimer < (Double)this.timerBalanceLimit.get() * 2.0 && !this.reachedTheLimit) {
         return (Double)this.boostSpeed.get();
      }

      this.reachedTheLimit = true;
      return (Double)this.inRangeSpeed.get();
   }

   private Player findClosestEnemy(double maxDist) {
      if (this.mc.level != null && this.mc.player != null) {
         Player closest = null;
         double closestDist = maxDist;

         for (Player player : this.mc.level.players()) {
            if (player != this.mc.player) {
               double dist = this.mc.player.distanceTo(player);
               if (dist < closestDist) {
                  closestDist = dist;
                  closest = player;
               }
            }
         }

         return closest;
      } else {
         return null;
      }
   }

   @EventHandler
   private void onPacketReceive(Receive event) {
      if (event.packet instanceof ClientboundPlayerPositionPacket && (Boolean)this.pauseOnFlag.get()) {
         this.balanceTimer = (Double)this.timerBalanceLimit.get() * 2.0;
      }
   }
}
