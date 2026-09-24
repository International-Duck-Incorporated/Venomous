package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.venomous.meteoraddon.VenomousAddon;

public class TextFieldProtect extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> blockChat = this.sgGeneral
      .add(new Builder().name("block-chat").description("Block interaction with chat input.").defaultValue(false).build());
   private final Setting<Boolean> blockSign = this.sgGeneral
      .add(new Builder().name("block-sign").description("Block interaction with sign text fields.").defaultValue(true).build());
   private final Setting<Boolean> blockBook = this.sgGeneral
      .add(new Builder().name("block-book").description("Block interaction with book text fields.").defaultValue(true).build());
   private final Setting<Boolean> blockAnvil = this.sgGeneral
      .add(
         new Builder().name("block-anvil").description("Block interaction with anvil text fields.").defaultValue(false)
            .build()
      );

   public TextFieldProtect() {
      super(VenomousAddon.CATEGORY, "textfieldprotect", "Prevents accidental text input in specific GUI text fields.");
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
