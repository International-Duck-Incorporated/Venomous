package net.venomous.meteoraddon.modules.world;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.world.Timer;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

public class TimerPlus extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<TimerPlus.Mode> mode = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("mode"))
                  .description("Timer pattern. Custom uses your exact values, NCP/Intave/Grim pulse timer boosts."))
               .defaultValue(TimerPlus.Mode.NCP))
            .build()
      );
   private final Setting<Boolean> onlyMoving = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("only-moving"))
                  .description("Only boost timer while movement keys are pressed."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Double> boost = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("boost"))
                  .description("Boost timer multiplier."))
               .defaultValue(2.0)
               .range(0.1, 10.0)
               .sliderMax(5.0)
               .visible(() -> this.mode.get() == TimerPlus.Mode.Custom))
            .build()
      );
   private final Setting<Double> recharge = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                     .name("recharge"))
                  .description("Recharge/idle timer multiplier."))
               .defaultValue(1.0)
               .range(0.05, 2.0)
               .sliderMax(2.0)
               .visible(() -> this.mode.get() == TimerPlus.Mode.Custom))
            .build()
      );
   private final Setting<Integer> boostTicks = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("boost-ticks"))
                     .description("Ticks to boost before recharging."))
                  .defaultValue(27))
               .range(1, 200)
               .sliderMax(80)
               .visible(() -> this.mode.get() == TimerPlus.Mode.Custom))
            .build()
      );
   private final Setting<Integer> rechargeTicks = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                        .name("recharge-ticks"))
                     .description("Ticks to recharge before boosting again."))
                  .defaultValue(352))
               .range(1, 1000)
               .sliderMax(500)
               .visible(() -> this.mode.get() == TimerPlus.Mode.Custom))
            .build()
      );
   private Timer timer;
   private int phaseTicks;
   private boolean boosting;

   public TimerPlus() {
      super(VenomousAddon.CATEGORY, "timer-plus", "Pulse/custom timer override using Meteor's built-in Timer module safely.");
   }

   public void onActivate() {
      this.timer = (Timer)Modules.get().get(Timer.class);
      this.phaseTicks = 0;
      this.boosting = true;
   }

   public void onDeactivate() {
      if (this.timer != null) {
         this.timer.setOverride(1.0);
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.timer == null) {
         this.timer = (Timer)Modules.get().get(Timer.class);
      }

      if (this.timer != null && this.mc.player != null) {
         if ((Boolean)this.onlyMoving.get() && !this.isMoving()) {
            this.timer.setOverride(1.0);
         } else {
            TimerPlus.Preset preset = this.preset();
            this.phaseTicks++;
            if (this.boosting && this.phaseTicks >= preset.boostTicks) {
               this.boosting = false;
               this.phaseTicks = 0;
            } else if (!this.boosting && this.phaseTicks >= preset.rechargeTicks) {
               this.boosting = true;
               this.phaseTicks = 0;
            }

            this.timer.setOverride(this.boosting ? preset.boost : preset.recharge);
         }
      }
   }

   public String getInfoString() {
      TimerPlus.Preset p = this.preset();
      int total = this.boosting ? p.boostTicks : p.rechargeTicks;
      double pct = total <= 0 ? 100.0 : this.phaseTicks * 100.0 / total;
      return (this.boosting ? "Boost " : "Recharge ") + String.format("%.0f%%", Math.min(100.0, pct));
   }

   private boolean isMoving() {
      return this.mc.options.keyUp.isDown() || this.mc.options.keyDown.isDown() || this.mc.options.keyLeft.isDown() || this.mc.options.keyRight.isDown();
   }

   private TimerPlus.Preset preset() {
      return switch ((TimerPlus.Mode)this.mode.get()) {
         case NCP -> new TimerPlus.Preset(2.0, 1.0, 27, 352);
         case Intave -> new TimerPlus.Preset(1.25, 1.0, 30, 105);
         case Grim -> new TimerPlus.Preset(2.0, 1.0, 14, 16);
         case Vulcan -> new TimerPlus.Preset(1.5, 0.8, 20, 80);
         case Custom -> new TimerPlus.Preset(
            (Double)this.boost.get(), (Double)this.recharge.get(), (Integer)this.boostTicks.get(), (Integer)this.rechargeTicks.get()
         );
      };
   }

   public enum Mode {
      NCP,
      Intave,
      Grim,
      Vulcan,
      Custom;
   }

   private record Preset(double boost, double recharge, int boostTicks, int rechargeTicks) {
   }
}
