package net.venomous.meteoraddon.modules.detection;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiCheatDetect extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<Boolean> chatFeedback = this.sgGeneral.add(
       new BoolSetting.Builder()
           .name("chat-feedback")
           .description("Display detected anti-cheat in client chat.")
           .defaultValue(true)
           .build()
   );

   private ServerAnalyzer analyzer;

   public AntiCheatDetect() {
      super(VenomousAddon.CATEGORY, "v-anti-cheat-detect", "Identifies active server anti-cheat software by analyzing network packet transactions.");
   }

   @Override
   public void onActivate() {
      this.analyzer = new ServerAnalyzer();
      this.analyzer.setListener(detection -> {
         if (this.chatFeedback.get()) {
            this.info("§7[§aVenomous§7] §e" + detection, new Object[0]);
         }
      });
      this.analyzer.enable();
   }

   @Override
   public void onDeactivate() {
      if (this.analyzer != null) {
         this.analyzer.disable();
         this.analyzer = null;
      }
   }

   @Override
   public String getInfoString() {
      if (this.analyzer != null && this.analyzer.getLastDetection() != null) {
         return this.analyzer.getLastDetection();
      }
      return "Scanning...";
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      WButton button = theme.button("Re-scan Anti-Cheat");
      button.action = () -> {
         if (this.analyzer != null) {
            this.analyzer.resetAndRescan();
            this.info("Reset transaction analyzer for active re-scan.", new Object[0]);
         }
      };
      return button;
   }
}
