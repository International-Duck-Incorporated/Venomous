package net.venomous.meteoraddon.modules.detection;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiCheatDetect extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

   private final Setting<Boolean> autoAlert = this.sgGeneral.add(
      new BoolSetting.Builder()
         .name("auto-alert")
         .description("Send a chat notification when an anticheat is detected.")
         .defaultValue(true)
         .build()
   );

   private ServerAnalyzer analyzer;

   public AntiCheatDetect() {
      super(VenomousAddon.CATEGORY, "anti-cheat-detect", "Identifies active server anti-cheat software by analyzing packet transactions and ping patterns.");
   }

   @Override
   public void onActivate() {
      this.analyzer = new ServerAnalyzer();
      this.analyzer.setListener(result -> {
         if (this.autoAlert.get()) {
            this.info("Server Anti-Cheat: %s", result);
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
   public WWidget getWidget(GuiTheme theme) {
      WTable table = theme.table();

      WButton rescanBtn = table.add(theme.button("Re-scan Anti-Cheat")).widget();
      rescanBtn.action = () -> {
         if (this.analyzer != null) {
            this.analyzer.reset();
            this.info("Restarted anti-cheat packet analysis.");
         }
      };

      return table;
   }

   @Override
   public String getInfoString() {
      if (this.analyzer == null) {
         return null;
      }
      String detection = this.analyzer.getLastDetection();
      if (detection != null) {
         return detection.replace("Likely ", "");
      }
      return "Analyzing (" + this.analyzer.getSampleCount() + "/6)...";
   }
}
