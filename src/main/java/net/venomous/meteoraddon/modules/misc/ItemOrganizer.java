package net.venomous.meteoraddon.modules.misc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.meteor.KeyInputEvent;
import meteordevelopment.meteorclient.events.meteor.MouseClickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.mixin.AbstractContainerScreenAccessor;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.misc.input.KeyAction;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.InventorySorter;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.ContainerAutomator;
import meteordevelopment.meteorclient.settings.KeybindSetting;

public class ItemOrganizer extends Module {
   private static final int MAX_CATEGORIES = 8;
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final SettingGroup sgBatch = this.settings.createGroup("Batch Sorting");
   private final SettingGroup sgCategory = this.settings.createGroup("Category Sorting");
   private final Setting<Keybind> sortKey = this.sgGeneral
      .add(
         new KeybindSetting.Builder().name("sort-key").description("Key/button that sorts whichever grid your mouse is hovering.")
            .defaultValue(Keybind.fromButton(2))
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("delay")
            .description("Delay in ticks between each item move.")
            .defaultValue(1)
            .min(0)
            .sliderMax(5)
            .build()
      );
   private final Setting<Boolean> disableInCreative = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("disable-in-creative")
            .description("Disables sorting while in creative mode.")
            .defaultValue(true)
            .build()
      );
   private final Setting<Boolean> autoSortOnOpen = this.sgGeneral
      .add(
         new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
            .name("auto-sort-on-open")
            .description("Automatically sorts a container's contents as soon as you open it.")
            .defaultValue(false)
            .build()
      );
   private final Setting<Keybind> selectKey = this.sgBatch
      .add(
         new KeybindSetting.Builder().name("select-key").description("Looking at a container, adds/removes it from the batch selection.")
            .defaultValue(Keybind.none())
            .build()
      );
   private final Setting<Keybind> selectNearbyKey = this.sgBatch
      .add(
         new KeybindSetting.Builder().name("select-nearby-key")
            .description("Adds every supported container within range to the batch selection.")
            .defaultValue(Keybind.none())
            .build()
      );
   private final Setting<Double> selectRange = this.sgBatch
      .add(
         new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
            .name("select-range")
            .description("Range used by select-nearby-key.")
            .defaultValue(24.0)
            .min(0.0)
            .sliderMax(128.0)
            .build()
      );
   private final Setting<Keybind> startKey = this.sgBatch
      .add(
         new KeybindSetting.Builder().name("start-key").description("Starts walking to and internally sorting every selected container.")
            .defaultValue(Keybind.none())
            .build()
      );
   private final Setting<Integer> categoryCount = this.sgCategory
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("category-count")
            .description("How many categories to use.")
            .defaultValue(3)
            .min(1)
            .sliderMax(8)
            .build()
      );
   private final Setting<Integer> activeCategory = this.sgCategory
      .add(
         new meteordevelopment.meteorclient.settings.IntSetting.Builder()
            .name("active-category")
            .description("Which category assign-chest-key currently assigns a chest to.")
            .defaultValue(1)
            .min(1)
            .sliderMax(8)
            .build()
      );
   private final Setting<Keybind> assignChestKey = this.sgCategory
      .add(
         new KeybindSetting.Builder().name("assign-chest-key")
            .description("Looking at a container, assigns it as the destination for the active category.")
            .defaultValue(Keybind.none())
            .build()
      );
   private final Setting<Keybind> startCategorySortKey = this.sgCategory
      .add(
         new KeybindSetting.Builder().name("start-category-sort-key")
            .description("Walks your inventory's matching items out to each category's assigned chest.")
            .defaultValue(Keybind.none())
            .build()
      );
   private final Setting<String>[] categoryNames = new Setting[8];
   private final Setting<List<Item>>[] categoryItems = new Setting[8];
   private InventorySorter sorter;
   private final Set<BlockPos> selected = new LinkedHashSet<>();
   private InventorySorter batchSorter;
   private int lastBatchSize;
   private final Color selectedColor = new Color(255, 220, 0, 255);
   private final Map<Integer, BlockPos> categoryChests = new HashMap<>();
   private final Map<BlockPos, Integer> posToCategory = new HashMap<>();
   private int depositSlot;
   private int depositTimer;
   private final Color categoryChestColor = new Color(80, 160, 255, 255);
   private final ContainerAutomator automator = new ContainerAutomator(new ContainerAutomator.Callback() {
      @Override
      public boolean onOpen(AbstractContainerScreen<?> var1, BlockPos var2, int var3) {
         if (ItemOrganizer.this.batchSorter == null) {
            if (var1.getMenu().slots.isEmpty()) {
               return true;
            }

            ItemOrganizer.this.batchSorter = new InventorySorter(var1, (Slot)var1.getMenu().slots.get(0));
         }

         if (ItemOrganizer.this.batchSorter.tick((Integer)ItemOrganizer.this.delay.get())) {
            ItemOrganizer.this.batchSorter = null;
            return true;
         } else {
            return false;
         }
      }

      @Override
      public void onFinished() {
         ItemOrganizer.this.batchSorter = null;
         ChatUtils.info("Finished sorting (highlight)%d(default) container(s).", new Object[]{ItemOrganizer.this.lastBatchSize});
      }
   });
   private final ContainerAutomator categoryAutomator = new ContainerAutomator(new ContainerAutomator.Callback() {
      @Override
      public boolean onOpen(AbstractContainerScreen<?> var1, BlockPos var2, int var3) {
         Integer var4 = ItemOrganizer.this.posToCategory.get(var2);
         if (var4 == null) {
            return true;
         }

         if (var3 == 1) {
            ItemOrganizer.this.depositSlot = 0;
            ItemOrganizer.this.depositTimer = 0;
         }

         return ItemOrganizer.this.tickDeposit((List<Item>)ItemOrganizer.this.categoryItems[var4].get());
      }

      @Override
      public void onFinished() {
         ChatUtils.info("Finished depositing items into category chests.", new Object[0]);
      }
   });

   public ItemOrganizer() {
      super(VenomousAddon.CATEGORY, "item-organizer",
         "Sorts inventories/containers, and can deposit your inventory into category chests - both can use Baritone."
      );
      String[] var1 = new String[]{"Iron Tools", "Building Blocks", "Wood", "Category 4", "Category 5", "Category 6", "Category 7", "Category 8"};
      Item[][] var2 = new Item[][]{
         {Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.IRON_SWORD},
         {Items.COBBLESTONE, Items.STONE, Items.DIRT},
         {Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG, Items.DARK_OAK_LOG},
         new Item[0],
         new Item[0],
         new Item[0],
         new Item[0],
         new Item[0]
      };

      for (int var3 = 0; var3 < 8; var3++) {
         int var4 = var3;
         this.categoryNames[var3] = this.sgCategory
            .add(
               new meteordevelopment.meteorclient.settings.StringSetting.Builder()
                  .name("category-" + (var3 + 1) + "-name")
                  .description("Display name for category " + (var3 + 1) + ".")
                  .visible(() -> var4 < (Integer)this.categoryCount.get())
                  .defaultValue(var1[var3])
                  .build()
            );
         this.categoryItems[var3] = this.sgCategory
            .add(
               new meteordevelopment.meteorclient.settings.ItemListSetting.Builder()
                  .name("category-" + (var3 + 1) + "-items")
                  .description("Items belonging to category " + (var3 + 1) + ".")
                  .visible(() -> var4 < (Integer)this.categoryCount.get())
                  .defaultValue(var2[var3])
                  .build()
            );
      }
   }

   public void onDeactivate() {
      this.sorter = null;
      this.automator.stop();
      this.categoryAutomator.stop();
      this.selected.clear();
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent var1) {
      this.onDeactivate();
      this.categoryChests.clear();
   }

   @EventHandler
   private void onKey(KeyInputEvent var1) {
      if (var1.action == KeyAction.Press) {
         if (((Keybind)this.sortKey.get()).matches(var1.input) && this.sort()) {
            var1.cancel();
         } else if (((Keybind)this.selectKey.get()).matches(var1.input)) {
            this.toggleSelectLookedAt();
         } else if (((Keybind)this.selectNearbyKey.get()).matches(var1.input)) {
            this.selectNearby();
         } else if (((Keybind)this.startKey.get()).matches(var1.input)) {
            this.startBatch();
         } else if (((Keybind)this.assignChestKey.get()).matches(var1.input)) {
            this.assignChestToActiveCategory();
         } else {
            if (((Keybind)this.startCategorySortKey.get()).matches(var1.input)) {
               this.startCategorySort();
            }
         }
      }
   }

   @EventHandler
   private void onMouseClick(MouseClickEvent var1) {
      if (var1.action == KeyAction.Press) {
         if (((Keybind)this.sortKey.get()).matches(var1.input) && this.sort()) {
            var1.cancel();
         } else if (((Keybind)this.selectKey.get()).matches(var1.input)) {
            this.toggleSelectLookedAt();
         } else if (((Keybind)this.selectNearbyKey.get()).matches(var1.input)) {
            this.selectNearby();
         } else if (((Keybind)this.startKey.get()).matches(var1.input)) {
            this.startBatch();
         } else if (((Keybind)this.assignChestKey.get()).matches(var1.input)) {
            this.assignChestToActiveCategory();
         } else {
            if (((Keybind)this.startCategorySortKey.get()).matches(var1.input)) {
               this.startCategorySort();
            }
         }
      }
   }

   @EventHandler
   private void onOpenScreen(OpenScreenEvent var1) {
      this.sorter = null;
      if (!this.automator.isRunning() && !this.categoryAutomator.isRunning()) {
         if ((Boolean)this.autoSortOnOpen.get()) {
            if (var1.screen instanceof ContainerScreen || var1.screen instanceof ShulkerBoxScreen) {
               AbstractContainerScreen var2 = (AbstractContainerScreen)var1.screen;
               if (!var2.getMenu().slots.isEmpty()) {
                  this.sorter = new InventorySorter(var2, (Slot)var2.getMenu().slots.get(0));
               }
            }
         }
      }
   }

   @EventHandler
   private void onTick(Pre var1) {
      this.automator.tick();
      this.categoryAutomator.tick();
      if (this.sorter != null && this.sorter.tick((Integer)this.delay.get())) {
         this.sorter = null;
      }
   }

   public WWidget getWidget(GuiTheme var1) {
      WVerticalList var2 = var1.verticalList();
      WButton var3 = (WButton)var2.add(var1.button("Sort Selected")).expandX().widget();
      var3.action = this::startBatch;
      WButton var4 = (WButton)var2.add(var1.button("Clear Selection")).expandX().widget();
      var4.action = this.selected::clear;
      WButton var5 = (WButton)var2.add(var1.button("Deposit Into Category Chests")).expandX().widget();
      var5.action = this::startCategorySort;
      return var2;
   }

   private boolean sort() {
      if (this.sorter == null && !this.automator.isRunning() && !this.categoryAutomator.isRunning()) {
         if (this.mc.gui.screen() instanceof AbstractContainerScreen var1) {
            if (this.mc.player.isCreative() && (Boolean)this.disableInCreative.get()) {
               return false;
            }

            if (!this.mc.player.containerMenu.getCarried().isEmpty()) {
               FindItemResult var3 = InvUtils.findEmpty();
               if (!var3.found()) {
                  InvUtils.click().slot(-999);
               } else {
                  InvUtils.click().slot(var3.slot());
               }
            }

            Slot var4 = ((AbstractContainerScreenAccessor)var1).meteor$getHoveredSlot();
            if (var4 == null) {
               return false;
            }

            this.sorter = new InventorySorter(var1, var4);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void toggleSelectLookedAt() {
      if (this.mc.hitResult instanceof BlockHitResult var1 && var1.getType() == Type.BLOCK) {
         BlockPos var4 = var1.getBlockPos();
         BlockEntity var3 = this.mc.level.getBlockEntity(var4);
         if (var3 != null && ContainerAutomator.isSupportedContainer(var3)) {
            if (this.selected.add(var4)) {
               ChatUtils.info(
                  "Selected container at (highlight)%d, %d, %d(default). (highlight)%d(default) total.",
                  new Object[]{var4.getX(), var4.getY(), var4.getZ(), this.selected.size()}
               );
            } else {
               this.selected.remove(var4);
               ChatUtils.info("Deselected container at (highlight)%d, %d, %d(default).", new Object[]{var4.getX(), var4.getY(), var4.getZ()});
            }
         }
      }
   }

   private void selectNearby() {
      double var1 = (Double)this.selectRange.get() * (Double)this.selectRange.get();
      int var3 = 0;

      for (BlockEntity var5 : Utils.blockEntities()) {
         if (ContainerAutomator.isSupportedContainer(var5)) {
            BlockPos var6 = var5.getBlockPos();
            double var7 = PlayerUtils.squaredDistanceTo(var6.getX() + 0.5, var6.getY() + 0.5, var6.getZ() + 0.5);
            if (!(var7 > var1) && this.selected.add(var6)) {
               var3++;
            }
         }
      }

      ChatUtils.info("Added (highlight)%d(default) container(s). (highlight)%d(default) total selected.", new Object[]{var3, this.selected.size()});
   }

   private void startBatch() {
      if (!this.automator.isRunning() && !this.categoryAutomator.isRunning() && this.sorter == null && !this.selected.isEmpty()) {
         this.lastBatchSize = this.selected.size();
         this.automator.start(new ArrayList<>(this.selected));
         this.selected.clear();
      }
   }

   private void assignChestToActiveCategory() {
      if (this.mc.hitResult instanceof BlockHitResult var1 && var1.getType() == Type.BLOCK) {
         BlockPos var5 = var1.getBlockPos();
         BlockEntity var3 = this.mc.level.getBlockEntity(var5);
         if (var3 != null && ContainerAutomator.isSupportedContainer(var3)) {
            int var4 = Math.min((Integer)this.activeCategory.get(), (Integer)this.categoryCount.get()) - 1;
            this.categoryChests.put(var4, var5);
            ChatUtils.info(
               "Assigned (highlight)%s(default) chest to (highlight)%d, %d, %d(default).",
               new Object[]{this.categoryNames[var4].get(), var5.getX(), var5.getY(), var5.getZ()}
            );
         }
      }
   }

   private void startCategorySort() {
      if (!this.automator.isRunning() && !this.categoryAutomator.isRunning() && this.sorter == null) {
         this.posToCategory.clear();

         for (int var1 = 0; var1 < this.categoryCount.get(); var1++) {
            BlockPos var2 = this.categoryChests.get(var1);
            if (var2 != null) {
               List var3 = (List)this.categoryItems[var1].get();
               if (!var3.isEmpty() && this.inventoryHasAny(var3)) {
                  this.posToCategory.put(var2, var1);
               }
            }
         }

         if (this.posToCategory.isEmpty()) {
            ChatUtils.warning("Nothing in your inventory matches a category with an assigned chest.", new Object[0]);
         } else {
            this.categoryAutomator.start(this.posToCategory.keySet());
         }
      }
   }

   private boolean inventoryHasAny(List<Item> var1) {
      for (int var2 = 0; var2 < 36; var2++) {
         ItemStack var3 = this.mc.player.getInventory().getItem(var2);
         if (!var3.isEmpty() && var1.contains(var3.getItem())) {
            return true;
         }
      }

      return false;
   }

   private boolean tickDeposit(List<Item> var1) {
      while (this.depositSlot < 36) {
         ItemStack var2 = this.mc.player.getInventory().getItem(this.depositSlot);
         if (!var2.isEmpty() && var1.contains(var2.getItem())) {
            if (this.depositTimer++ < (Integer)this.delay.get()) {
               return false;
            }

            this.depositTimer = 0;
            if (this.depositSlot < 9) {
               InvUtils.shiftClick().slotHotbar(this.depositSlot);
            } else {
               InvUtils.shiftClick().slotMain(this.depositSlot - 9);
            }

            this.depositSlot++;
            return false;
         }

         this.depositSlot++;
      }

      return true;
   }

   @EventHandler
   private void onRender3D(Render3DEvent var1) {
      for (BlockPos var3 : this.selected) {
         var1.renderer
            .box(
               var3.getX(),
               var3.getY(),
               var3.getZ(),
               var3.getX() + 1,
               var3.getY() + 1,
               var3.getZ() + 1,
               this.selectedColor,
               this.selectedColor,
               ShapeMode.Lines,
               0
            );
      }

      for (BlockPos var5 : this.categoryChests.values()) {
         var1.renderer
            .box(
               var5.getX(),
               var5.getY(),
               var5.getZ(),
               var5.getX() + 1,
               var5.getY() + 1,
               var5.getZ() + 1,
               this.categoryChestColor,
               this.categoryChestColor,
               ShapeMode.Lines,
               0
            );
      }
   }

   public String getInfoString() {
      if (this.automator.isRunning() || this.categoryAutomator.isRunning()) {
         return "sorting";
      } else {
         return !this.selected.isEmpty() ? String.valueOf(this.selected.size()) : "";
      }
   }
}
