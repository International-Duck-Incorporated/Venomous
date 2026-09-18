package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.venomous.meteoraddon.VenomousAddon;

public class LagNotifier extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> warnBelow = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("warn-below-tps")).description("Fire a chat warning when estimated TPS drops below this value."))
               .defaultValue(15))
            .min(1)
            .sliderMax(20)
            .build()
      );
   private final Setting<Boolean> recoveryAlert = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("recovery-alert"))
                  .description("Also notify when the server recovers from lag."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> alwaysShow = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("always-show-info"))
                  .description("Show TPS in the module info string at all times."))
               .defaultValue(true))
            .build()
      );
   private long lastPacketMs = 0L;
   private double smoothTps = 20.0;
   private boolean lagging = false;

   public LagNotifier() {
      super(VenomousAddon.CATEGORY, "v-lag-notifier", "Estimates server TPS from time-packet intervals and alerts when the server lags.");
   }

   public void onDeactivate() {
      this.lastPacketMs = 0L;
      this.smoothTps = 20.0;
      this.lagging = false;
   }

   @EventHandler
   private void onReceive(Receive event) {
      if (event.packet instanceof ClientboundSetTimePacket && this.mc.player != null) {
         long now = System.currentTimeMillis();
         if (this.lastPacketMs != 0L) {
            long delta = now - this.lastPacketMs;
            double measured = Math.min(20.0, 20000.0 / delta);
            this.smoothTps = 0.7 * this.smoothTps + 0.3 * measured;
            this.smoothTps = Math.round(this.smoothTps * 10.0) / 10.0;
            boolean nowLagging = this.smoothTps < ((Integer)this.warnBelow.get()).intValue();
            if (nowLagging && !this.lagging) {
               this.warning("Server lag detected — TPS %.1f", new Object[]{this.smoothTps});
            } else if (!nowLagging && this.lagging && (Boolean)this.recoveryAlert.get()) {
               this.info("Server recovered — TPS %.1f", new Object[]{this.smoothTps});
            }

            this.lagging = nowLagging;
         }

         this.lastPacketMs = now;
      }
   }

   public String getInfoString() {
      return !this.alwaysShow.get() && !this.lagging ? null : String.format("%.1f TPS", this.smoothTps);
   }
}
