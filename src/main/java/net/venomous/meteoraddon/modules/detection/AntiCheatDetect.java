package net.venomous.meteoraddon.modules.detection;

import meteordevelopment.meteorclient.systems.modules.Module;
import net.venomous.meteoraddon.VenomousAddon;

public class AntiCheatDetect extends Module {
   private ServerAnalyzer analyzer;

   public AntiCheatDetect() {
      super(VenomousAddon.CATEGORY, "v-anti-cheat-detect", "Identifies likely anti-cheat software by analysing ping transaction IDs.");
   }

   public void onActivate() {
      this.analyzer = new ServerAnalyzer();
      AntiCheatDetect antiCheatDetect = this;
      this.analyzer.setListener(x$0 -> antiCheatDetect.info(x$0, new Object[0]));
      this.analyzer.enable();
   }

   public void onDeactivate() {
      if (this.analyzer != null) {
         this.analyzer.disable();
         this.analyzer = null;
      }
   }
}
