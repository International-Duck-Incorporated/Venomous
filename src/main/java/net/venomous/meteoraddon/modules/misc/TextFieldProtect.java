package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.meteor.CharTypedEvent;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.venomous.meteoraddon.VenomousAddon;

public class TextFieldProtect extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> blockChat = this.sgGeneral
      .add(new BoolSetting.Builder().name("block-chat").description("Block typing in chat input.").defaultValue(false).build());
   private final Setting<Boolean> blockSign = this.sgGeneral
      .add(new BoolSetting.Builder().name("block-sign").description("Block typing in sign text fields.").defaultValue(true).build());
   private final Setting<Boolean> blockBook = this.sgGeneral
      .add(new BoolSetting.Builder().name("block-book").description("Block typing in book text fields.").defaultValue(true).build());
   private final Setting<Boolean> blockAnvil = this.sgGeneral
      .add(new BoolSetting.Builder().name("block-anvil").description("Block typing in anvil text fields.").defaultValue(false).build());

   public TextFieldProtect() {
      super(VenomousAddon.CATEGORY, "v-textfieldprotect", "Prevents accidental text input in specific GUI text fields.");
   }

   @EventHandler
   private void onCharTyped(CharTypedEvent event) {
      if (this.shouldBlockCurrentScreen()) {
         event.cancel();
      }
   }

   @EventHandler
   private void onKeyInput(KeyInputEvent event) {
      if (this.shouldBlockCurrentScreen()) {
         // Allow ESC (256) to close screens, block other typing inputs
         if (event.key() != 256) {
            event.cancel();
         }
      }
   }

   private boolean shouldBlockCurrentScreen() {
      if (!this.isActive() || this.mc.gui == null || this.mc.gui.screen() == null) {
         return false;
      }

      Screen screen = this.mc.gui.screen();
      if ((Boolean)this.blockChat.get() && screen instanceof ChatScreen) return true;
      if ((Boolean)this.blockSign.get() && screen instanceof AbstractSignEditScreen) return true;
      if ((Boolean)this.blockBook.get() && screen instanceof BookEditScreen) return true;
      if ((Boolean)this.blockAnvil.get() && screen instanceof AnvilScreen) return true;

      return false;
   }

   public boolean shouldBlockChat() {
      return this.isActive() && (Boolean)this.blockChat.get();
   }

   public boolean shouldBlockSign() {
      return this.isActive() && (Boolean)this.blockSign.get();
   }

   public boolean shouldBlockBook() {
      return this.isActive() && (Boolean)this.blockBook.get();
   }

   public boolean shouldBlockAnvil() {
      return this.isActive() && (Boolean)this.blockAnvil.get();
   }
}
