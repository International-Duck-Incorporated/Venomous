package net.venomous.meteoraddon.modules.misc;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.venomous.meteoraddon.VenomousAddon;

public class GUICloser extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<List<String>> filters = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("filters"))
                  .description("List of title substrings. If a container screen title contains any of these, it gets auto-closed."))
               .defaultValue(new ArrayList<>(List.of("Vote"))))
            .build()
      );
   private final Setting<Boolean> printTitles = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("print-titles"))
                  .description("Print container screen titles to chat for debugging."))
               .defaultValue(false))
            .build()
      );

   public GUICloser() {
      super(VenomousAddon.CATEGORY, "guicloser", "Automatically closes container screens matching filter titles.");
   }

   @EventHandler
   private void onOpenScreen(OpenScreenEvent event) {
      if (event.screen instanceof AbstractContainerScreen<?> screen) {
         String var6 = screen.getTitle().getString();
         if ((Boolean)this.printTitles.get()) {
            this.info("Container: %s", new Object[]{var6});
         }

         for (String filter : this.filters.get()) {
            if (var6.contains(filter)) {
               this.info("Closed container: %s", new Object[]{var6});
               event.cancel();
               return;
            }
         }
      }
   }
}
