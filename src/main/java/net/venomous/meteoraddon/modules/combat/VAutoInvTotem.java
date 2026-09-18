package net.venomous.meteoraddon.modules.combat;

import meteordevelopment.meteorclient.events.game.GameJoinedEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.VenomousAddon;

public class VAutoInvTotem extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> delay;
   private final Setting<Boolean> moveFromHotbar;
   private final Setting<Boolean> disableLogs;
   private final Setting<Boolean> openInv;
   private final Setting<Integer> invOpenDelay;
   private final Setting<Integer> invCloseDelay;
   private final Setting<Boolean> hoverMode;
   private final Setting<Double> hoverHealthThreshold;
   private boolean needsTotem = false;
   private int delayTicks = 0;
   private boolean hadTotemInOffhand = false;
   private boolean shouldOpenInv = false;
   private int invOpenTicks = 0;
   private int invCloseTicks = 0;
   private boolean invAutoOpened = false;

   public VAutoInvTotem() {
      super(VenomousAddon.CATEGORY, "v-auto-inv-totem", "Moves totems to offhand on totem pop or low health (merged with HoverTotem).");
      this.delay = this.sgGeneral
         .add(
            ((Builder)((Builder)((Builder)new Builder().name("delay")).description("Delay in ticks before moving totem.")).defaultValue(3))
               .min(1)
               .max(20)
               .sliderMin(1)
               .sliderMax(20)
               .build()
         );
      this.moveFromHotbar = this.sgGeneral
         .add(
            ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("move-from-hotbar"))
                     .description("Also move totems from hotbar slots."))
                  .defaultValue(true))
               .build()
         );
      this.disableLogs = this.sgGeneral
         .add(
            ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("disable-logs"))
                     .description("Disable chat messages about totem movements."))
                  .defaultValue(true))
               .build()
         );
      this.openInv = this.sgGeneral
         .add(
            ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("open-inv"))
                     .description("Automatically open inventory when totem pops."))
                  .defaultValue(false))
               .build()
         );
      this.invOpenDelay = this.sgGeneral
         .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("inv-open-delay"))
                        .description("Ticks to wait before opening inventory after totem pop."))
                     .defaultValue(2))
                  .min(1)
                  .max(10)
                  .sliderMin(1)
                  .sliderMax(10)
                  .visible(this.openInv::get))
               .build()
         );
      this.invCloseDelay = this.sgGeneral
         .add(
            ((Builder)((Builder)((Builder)((Builder)new Builder().name("inv-close-delay")).description("Ticks to wait before closing inventory after opening."))
                     .defaultValue(8))
                  .min(5)
                  .max(20)
                  .sliderMin(5)
                  .sliderMax(20)
                  .visible(this.openInv::get))
               .build()
         );
      this.hoverMode = this.sgGeneral
         .add(
            ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                        .name("hover-mode"))
                     .description("Also move totem to offhand when health drops below threshold (from HoverTotem merge)."))
                  .defaultValue(true))
               .build()
         );
      this.hoverHealthThreshold = this.sgGeneral
         .add(
            ((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)((meteordevelopment.meteorclient.settings.DoubleSetting.Builder)new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
                        .name("hover-health"))
                     .description("Health threshold to trigger totem equip in hover mode."))
                  .defaultValue(10.0)
                  .range(0.0, 36.0)
                  .sliderMax(36.0)
                  .visible(this.hoverMode::get))
               .build()
         );
   }

   public void onActivate() {
      this.resetState();
   }

   public void onDeactivate() {
      this.resetInvAutoState();
   }

   private void resetState() {
      if (this.mc.player != null) {
         this.hadTotemInOffhand = this.hasTotemInOffhand();
      }

      this.needsTotem = false;
      this.delayTicks = 0;
      this.resetInvAutoState();
   }

   private void resetInvAutoState() {
      this.shouldOpenInv = false;
      this.invOpenTicks = 0;
      this.invCloseTicks = 0;
      this.invAutoOpened = false;
   }

   @EventHandler
   private void onGameJoined(GameJoinedEvent event) {
      this.resetState();
      if (!(Boolean)this.disableLogs.get()) {
         this.info("Auto Inv Totem state reset for new world/reconnection.", new Object[0]);
      }
   }

   @EventHandler
   private void onPacketReceive(Receive event) {
      if (event.packet instanceof ClientboundEntityEventPacket packet
         && packet.getEventId() == 35
         && this.mc.player != null
         && packet.getEntity(this.mc.level) == this.mc.player
         && (Boolean)this.openInv.get()
         && this.mc.screen == null) {
         this.shouldOpenInv = true;
         this.invOpenTicks = (Integer)this.invOpenDelay.get();
         if (!(Boolean)this.disableLogs.get()) {
            this.info("Totem pop detected! Will auto-open inventory in %d ticks.", new Object[]{this.invOpenTicks});
         }
      }
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null) {
         this.handleAutoInventory();
         if ((Boolean)this.hoverMode.get() && this.mc.player.getHealth() <= (Double)this.hoverHealthThreshold.get() && !this.hasTotemInOffhand()) {
            this.moveTotemToOffhand();
         }

         boolean currentlyHasTotem = this.hasTotemInOffhand();
         if (this.hadTotemInOffhand && !currentlyHasTotem) {
            this.needsTotem = true;
            if (!(Boolean)this.disableLogs.get()) {
               this.info("Totem popped! Open inventory to auto-equip a new one.", new Object[0]);
            }

            if (this.mc.screen instanceof InventoryScreen) {
               if (!(Boolean)this.disableLogs.get()) {
                  this.info("Inventory already open - moving totem immediately!", new Object[0]);
               }

               this.delayTicks = (Integer)this.delay.get();
            }
         }

         this.hadTotemInOffhand = currentlyHasTotem;
         if (currentlyHasTotem && this.needsTotem) {
            this.needsTotem = false;
            this.delayTicks = 0;
         }
      }
   }

   private void handleAutoInventory() {
      if (this.shouldOpenInv && this.invOpenTicks > 0) {
         this.invOpenTicks--;
         if (this.invOpenTicks == 0 && this.mc.screen == null) {
            this.mc.setScreen(new InventoryScreen(this.mc.player));
            this.invAutoOpened = true;
            this.invCloseTicks = (Integer)this.invCloseDelay.get();
            this.shouldOpenInv = false;
            if (!(Boolean)this.disableLogs.get()) {
               this.info("Auto-opened inventory, will close in %d ticks.", new Object[]{this.invCloseTicks});
            }
         }
      }

      if (this.invAutoOpened && this.invCloseTicks > 0) {
         this.invCloseTicks--;
         if (this.invCloseTicks == 0 && this.mc.screen instanceof InventoryScreen) {
            this.mc.setScreen(null);
            this.invAutoOpened = false;
            if (!(Boolean)this.disableLogs.get()) {
               this.info("Auto-closed inventory.", new Object[0]);
            }
         }
      }

      if (this.invAutoOpened && !(this.mc.screen instanceof InventoryScreen)) {
         this.invAutoOpened = false;
         this.invCloseTicks = 0;
      }
   }

   @EventHandler
   private void onOpenScreen(OpenScreenEvent event) {
      if (event.screen instanceof InventoryScreen && this.needsTotem && this.mc.player != null) {
         this.delayTicks = (Integer)this.delay.get();
      }
   }

   @EventHandler
   private void onTickDelayed(Post event) {
      if (this.delayTicks > 0 && this.mc.player != null) {
         this.delayTicks--;
         if (this.delayTicks == 0) {
            this.moveTotemToOffhand();
         }
      }
   }

   private void moveTotemToOffhand() {
      int totemSlot = this.findTotemSlot();
      if (totemSlot == -1) {
         if (!(Boolean)this.disableLogs.get()) {
            this.info("No totem found in inventory!", new Object[0]);
         }
      } else {
         try {
            int containerSlot = totemSlot < 9 ? totemSlot + 36 : totemSlot;
            if (!(Boolean)this.disableLogs.get()) {
               this.info("Found totem in slot %d (container slot %d)", new Object[]{totemSlot, containerSlot});
            }

            ItemStack offhandStack = this.mc.player.getOffhandItem();
            if (offhandStack.isEmpty()) {
               this.mc.gameMode.handleContainerInput(0, containerSlot, 40, ContainerInput.SWAP, this.mc.player);
               if (!(Boolean)this.disableLogs.get()) {
                  this.info("Swapped totem to empty offhand.", new Object[0]);
               }
            } else {
               this.mc.gameMode.handleContainerInput(0, containerSlot, 0, ContainerInput.PICKUP, this.mc.player);
               this.mc.gameMode.handleContainerInput(0, 45, 0, ContainerInput.PICKUP, this.mc.player);
               this.mc.gameMode.handleContainerInput(0, containerSlot, 0, ContainerInput.PICKUP, this.mc.player);
               if (!(Boolean)this.disableLogs.get()) {
                  this.info("3-click swapped totem to offhand.", new Object[0]);
               }
            }

            this.needsTotem = false;
         } catch (Exception e) {
            if (!(Boolean)this.disableLogs.get()) {
               this.error("Failed to move totem: " + e.getMessage(), new Object[0]);
            }
         }
      }
   }

   private int findTotemSlot() {
      for (int i = 9; i < 36; i++) {
         if (this.mc.player.getInventory().getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
            return i;
         }
      }

      if ((Boolean)this.moveFromHotbar.get()) {
         for (int i = 0; i < 9; i++) {
            if (this.mc.player.getInventory().getItem(i).getItem() == Items.TOTEM_OF_UNDYING) {
               return i;
            }
         }
      }

      return -1;
   }

   private boolean hasTotemInOffhand() {
      if (this.mc.player == null) {
         return false;
      }

      ItemStack offhandStack = this.mc.player.getOffhandItem();
      return !offhandStack.isEmpty() && offhandStack.is(Items.TOTEM_OF_UNDYING);
   }
}
