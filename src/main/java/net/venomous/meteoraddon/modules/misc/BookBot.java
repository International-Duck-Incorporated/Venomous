package net.venomous.meteoraddon.modules.misc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundEditBookPacket;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.venomous.meteoraddon.VenomousAddon;

public class BookBot extends Module {
   private static final int MAX_LINES_PER_PAGE = 14;
   private static final float MAX_LINE_WIDTH = 114.0F;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<String> bookName = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("book-name")).description("Name template for signed books. Use %count% for book number."))
               .defaultValue("Venom doesnt miss"))
            .build()
      );
   private final Setting<Boolean> signBooks = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("sign-books"))
                  .description("Sign the books after writing."))
               .defaultValue(true))
            .build()
      );
   private final Setting<Boolean> asciiOnly = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("ascii-only"))
                  .description("Only use ASCII characters."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Integer> pages = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("pages"))
                  .description("Number of pages per book."))
               .defaultValue(50))
            .range(1, 100)
            .sliderMax(100)
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("delay"))
                  .description("Delay between books in ticks."))
               .defaultValue(40))
            .min(1)
            .sliderMax(200)
            .build()
      );
   private int bookCount = 0;
   private int tickCounter = 0;
   private final Random random = new Random();

   public BookBot() {
      super(VenomousAddon.CATEGORY, "bookbot", "Automatically writes and signs books with generated text.");
   }

   public void onActivate() {
      this.bookCount = 0;
      this.tickCounter = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && ++this.tickCounter >= (Integer)this.delay.get()) {
         this.tickCounter = 0;
         int bookSlot = -1;

         for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.getItem() == Items.WRITABLE_BOOK) {
               bookSlot = i;
               break;
            }
         }

         if (bookSlot == -1) {
            this.info("No writable book found in hotbar. Disabling.", new Object[0]);
            this.toggle();
         } else {
            int prevSlot = this.mc.player.getInventory().getSelectedSlot();
            this.mc.player.getInventory().setSelectedSlot(bookSlot);
            List<String> pageTexts = new ArrayList<>();
            List<Filterable<Component>> filteredPages = new ArrayList<>();

            for (int p = 0; p < this.pages.get(); p++) {
               String pageText = this.generatePage();
               pageTexts.add(pageText);
               filteredPages.add(Filterable.passThrough(Component.literal(pageText)));
            }

            String title = ((String)this.bookName.get()).replace("%count%", String.valueOf(this.bookCount));
            ItemStack bookStack = this.mc.player.getInventory().getItem(bookSlot);
            bookStack.set(
               DataComponents.WRITTEN_BOOK_CONTENT,
               new WrittenBookContent(Filterable.passThrough(title), this.mc.player.getGameProfile().name(), 0, filteredPages, true)
            );
            this.mc.player.connection.send(new ServerboundEditBookPacket(bookSlot, pageTexts, this.signBooks.get() ? Optional.of(title) : Optional.empty()));
            this.bookCount++;
            this.mc.player.getInventory().setSelectedSlot(prevSlot);
         }
      }
   }

   private String generatePage() {
      StringBuilder page = new StringBuilder();
      int lineIndex = 0;
      float lineWidth = 0.0F;

      while (lineIndex < 14) {
         char c = this.asciiOnly.get() ? (char)(this.random.nextInt(94) + 33) : (char)(this.random.nextInt(93) + 33);
         float charWidth = this.mc.font.width(String.valueOf(c));
         if (lineWidth + charWidth > 114.0F) {
            lineIndex++;
            lineWidth = 0.0F;
            if (lineIndex < 14) {
               page.append('\n');
            }

            if (lineWidth + charWidth <= 114.0F) {
               page.append(c);
               lineWidth += charWidth;
            }
         } else {
            page.append(c);
            lineWidth += charWidth;
         }
      }

      return page.toString();
   }

   public String getInfoString() {
      return String.valueOf(this.bookCount);
   }
}
