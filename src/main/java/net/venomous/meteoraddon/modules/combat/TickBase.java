package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class TickBase extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> onlyOnAttack = this.sgGeneral
      .add(new BoolSetting.Builder().name("only-on-attack").description("Only delay ticks when attacking.").defaultValue(true).build());
   private final Setting<Integer> maxDelay = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("max-delay")
            .description("Maximum ticks to delay.")
            .defaultValue(5)
            .range(1, 20)
            .sliderMax(10)
            .build()
      );
   private final Setting<Double> healthThreshold = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("health-threshold")
            .description("Don't delay if health is below this.")
            .defaultValue(6.0)
            .range(0.0, 36.0)
            .sliderMax(20.0)
            .build()
      );
   private int delayedTicks = 0;
   private boolean shouldBurst = false;

   public TickBase() {
      super(VenomousAddon.CATEGORY, "tickbase", "Delays movement ticks to burst them during attacks for a timing advantage.");
   }

   public void onActivate() {
      this.delayedTicks = 0;
      this.shouldBurst = false;
   }

   public void onDeactivate() {
      if (this.delayedTicks > 0) {
         for (int i = 0; i < this.delayedTicks; i++) {
            this.sendPositionPacket();
         }
      }

      this.delayedTicks = 0;
      this.shouldBurst = false;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         if (this.mc.player.getHealth() <= (Double)this.healthThreshold.get()) {
            if (this.delayedTicks > 0) {
               for (int i = 0; i < this.delayedTicks; i++) {
                  this.sendPositionPacket();
               }

               this.delayedTicks = 0;
            }
         } else if (this.shouldBurst && this.delayedTicks > 0) {
            for (int i = 0; i < this.delayedTicks; i++) {
               this.sendPositionPacket();
            }

            this.delayedTicks = 0;
            this.shouldBurst = false;
         } else if (!this.shouldBurst && this.delayedTicks < (Integer)this.maxDelay.get()) {
            this.delayedTicks++;
         }
      }
   }

   private void sendPositionPacket() {
      if (this.mc.player != null && this.mc.player.connection != null) {
         this.mc.player.connection.send(new Pos(this.mc.player.getX(), this.mc.player.getY(), this.mc.player.getZ(), this.mc.player.onGround(), false));
      }
   }

   public void triggerBurst() {
      if (this.isActive() && this.delayedTicks > 0) {
         this.shouldBurst = true;
      }
   }

   public String getInfoString() {
      return this.delayedTicks + "t";
   }
}
