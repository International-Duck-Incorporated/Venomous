package net.venomous.meteoraddon.modules.combat;

import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.venomous.meteoraddon.VenomousAddon;

public class AutoClickerPlus extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> minCps = this.sgGeneral
      .add(
         new Builder().name("min-cps").description("Minimum clicks per second.").defaultValue(7)
            .range(1, 50)
            .sliderMax(20)
            .build()
      );
   private final Setting<Integer> maxCps = this.sgGeneral
      .add(
         new Builder().name("max-cps").description("Maximum clicks per second.").defaultValue(10)
            .range(1, 50)
            .sliderMax(20)
            .build()
      );
   private final Setting<AutoClickerPlus.Technique> technique = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.EnumSetting.Builder()
            .name("technique")
            .description("Click technique / pattern.")
            .defaultValue(AutoClickerPlus.Technique.Stabilized)
            .build()
      );
   private final Setting<Boolean> leftClick = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("left-click")
            .description("Simulate left clicks (attack).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> rightClick = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("right-click")
            .description("Simulate right clicks (use).")
            .defaultValue(false)
            .build()
      );
   private final Setting<Boolean> onlyWhileHolding = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("only-while-holding")
            .description("Only click while holding the attack key.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> jitter = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("jitter")
            .description("Add random jitter to click timing (humanlike).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Double> jitterAmount = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("jitter-amount")
            .description("How much jitter to add (0-1).")
            .defaultValue(0.3)
            .range(0.0, 1.0)
            .sliderMax(1.0)
            .visible(this.jitter::get)
            .build()
      );
   private final Setting<Boolean> respectCooldown = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("respect-cooldown")
            .description("Respect attack cooldown (humanlike).")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> vulcanSpeedRandomization = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("vulcan-speed-randomization")
            .description("Add ±100ms randomization to bypass Vulcan AutoClicker checks.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Integer> vulcanRandomizationMs = this.sgGeneral
      .add(
         new Builder().name("vulcan-randomization-ms")
            .description("±Milliseconds of randomization for Vulcan bypass.")
            .defaultValue(100)
            .range(20, 300)
            .visible(this.vulcanSpeedRandomization::get)
            .build()
      );
   private final Setting<Double> doubleClickChance = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("double-click-chance")
            .description("Chance to double-click (0-1). Only for DoubleClick technique.")
            .defaultValue(0.15)
            .range(0.0, 1.0)
            .sliderMax(0.5)
            .visible(() -> this.technique.get() == AutoClickerPlus.Technique.DoubleClick)
            .build()
      );
   private final Random random = new Random();
   private int ticksUntilClick = 0;
   private double[] clickTimings;
   private int timingIndex = 0;

   public AutoClickerPlus() {
      super(VenomousAddon.CATEGORY, "auto-clicker-plus", "Auto-clicker with Vulcan-aware randomization and humanlike patterns.");
   }

   public void onActivate() {
      this.ticksUntilClick = 0;
      this.generateClickTimings();
   }

   @Override
   public void onDeactivate() {
      this.ticksUntilClick = 0;
      this.timingIndex = 0;
      this.clickTimings = null;
   }

   private void generateClickTimings() {
      int configuredMin = (Integer)this.minCps.get();
      int configuredMax = (Integer)this.maxCps.get();
      int minCps = Math.min(configuredMin, configuredMax);
      int maxCps = Math.max(configuredMin, configuredMax);
      int avgCps = (minCps + maxCps) / 2;
      if ((Boolean)this.vulcanSpeedRandomization.get()) {
         avgCps = Math.max(9, Math.min(12, avgCps));
      }

      int samples = 40;
      this.clickTimings = new double[samples];
      switch ((AutoClickerPlus.Technique)this.technique.get()) {
         case Stabilized:
            double baseInterval = 20.0 / avgCps;

            for (int i = 0; i < samples; i++) {
               double variation = (this.random.nextDouble() - 0.5) * 2.0 * (maxCps - minCps) / avgCps;
               this.clickTimings[i] = Math.max(1.0, baseInterval + variation);
            }
            break;
         case Butterfly:
            double fastInterval = 20.0 / Math.max(1, avgCps + 2);
            double slowInterval = 20.0 / Math.max(1, avgCps - 2);

            for (int i = 0; i < samples; i++) {
               this.clickTimings[i] = i % 2 == 0 ? fastInterval : slowInterval;
               if ((Boolean)this.jitter.get()) {
                  this.clickTimings[i] = this.clickTimings[i] + (this.random.nextDouble() - 0.5) * (Double)this.jitterAmount.get();
               }

               this.clickTimings[i] = Math.max(1.0, this.clickTimings[i]);
            }
            break;
         case NormalDistribution:
            double mean = 20.0 / avgCps;
            double stdDev = mean * 0.25;

            for (int i = 0; i < samples; i++) {
               this.clickTimings[i] = Math.max(1.0, mean + this.random.nextGaussian() * stdDev);
            }
            break;
         case Drag:
            for (int i = 0; i < samples; i++) {
               if (i % 5 < 3) {
                  this.clickTimings[i] = Math.max(1.0, 20.0 / Math.max(1, avgCps + 5));
               } else {
                  this.clickTimings[i] = Math.max(1.0, 20.0 / Math.max(1, avgCps - 3));
               }
            }
            break;
         case Efficient:
            double interval = 20.0 / avgCps;

            for (int i = 0; i < samples; i++) {
               this.clickTimings[i] = Math.max(1.0, interval + (this.random.nextDouble() - 0.5) * 0.5);
            }
            break;
         case DoubleClick:
            double baseInterval2 = 20.0 / avgCps;

            for (int i = 0; i < samples; i++) {
               if (this.random.nextDouble() < (Double)this.doubleClickChance.get()) {
                  this.clickTimings[i] = Math.max(1.0, baseInterval2 * 0.1);
               } else {
                  this.clickTimings[i] = Math.max(1.0, baseInterval2 + (this.random.nextDouble() - 0.5) * 1.5);
               }

               if ((Boolean)this.jitter.get()) {
                  this.clickTimings[i] = this.clickTimings[i] + (this.random.nextDouble() - 0.5) * (Double)this.jitterAmount.get() * 0.5;
               }
            }
            break;
         case Spamming:
            for (int i = 0; i < samples; i++) {
               double cps = minCps + this.random.nextDouble() * (maxCps - minCps) * 2.0;
               this.clickTimings[i] = Math.max(1.0, 20.0 / Math.max(1.0, cps) + this.random.nextGaussian() * 0.3);
            }
      }

      if ((Boolean)this.vulcanSpeedRandomization.get()) {
         for (int i = 0; i < samples; i++) {
            double msVariation = (this.random.nextDouble() - 0.5) * 2.0 * ((Integer)this.vulcanRandomizationMs.get()).intValue();
            this.clickTimings[i] = this.clickTimings[i] + msVariation / 50.0;
            this.clickTimings[i] = Math.max(1.0, this.clickTimings[i]);
         }
      }

      this.timingIndex = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null
         && !this.mc.player.isDeadOrDying()
         && (!(Boolean)this.onlyWhileHolding.get() || this.mc.options.keyAttack.isDown())
         && (!(Boolean)this.respectCooldown.get() || !(this.mc.player.getAttackStrengthScale(0.0F) < 0.9F))) {
         this.ticksUntilClick--;
         if (this.ticksUntilClick <= 0) {
            this.doClick();
            if (this.clickTimings == null || this.timingIndex >= this.clickTimings.length) {
               this.generateClickTimings();
            }

            double nextInterval = this.clickTimings[this.timingIndex++];
            this.ticksUntilClick = Math.max(1, (int)Math.round(nextInterval));
         }
      }
   }

   private void doClick() {
      if ((Boolean)this.leftClick.get()) {
         if (this.mc.hitResult != null && this.mc.hitResult.getType() == Type.ENTITY) {
            this.mc.gameMode.attack(this.mc.player, ((EntityHitResult)this.mc.hitResult).getEntity());
         } else {
            this.mc.player.swing(InteractionHand.MAIN_HAND);
         }

         if (this.technique.get() == AutoClickerPlus.Technique.DoubleClick && this.random.nextDouble() < (Double)this.doubleClickChance.get()) {
            this.mc.player.swing(InteractionHand.MAIN_HAND);
         }
      }

      if ((Boolean)this.rightClick.get()) {
         this.mc.gameMode.useItem(this.mc.player, InteractionHand.MAIN_HAND);
      }
   }

   public String getInfoString() {
      int avg = ((Integer)this.minCps.get() + (Integer)this.maxCps.get()) / 2;
      return avg + " CPS " + ((AutoClickerPlus.Technique)this.technique.get()).name();
   }

   public enum Technique {
      Stabilized,
      Butterfly,
      NormalDistribution,
      Drag,
      Efficient,
      DoubleClick,
      Spamming;
   }
}
