package net.venomous.meteoraddon.modules.combat;

import java.util.HashSet;
import java.util.Set;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.IntSetting;

public class CrystalPvP extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> activateKey;
   private final Setting<Double> placeDelay;
   private final Setting<Double> breakDelay;
   private final Setting<Boolean> stopOnKill;
   private final Setting<Boolean> placeObsidianIfMissing;
   private final Setting<Boolean> antiSuicide;
   private final Setting<Double> minHealth;
   private int placeDelayCounter = 0;
   private int breakDelayCounter = 0;
   private final Set<Player> deadPlayers = new HashSet<>();
   private boolean paused = false;
   private long resumeTime = 0L;

   public CrystalPvP() {
      super(VenomousAddon.CATEGORY, "crystal-pvp", "Automatically places and breaks end crystals quickly.");
      this.activateKey = this.sgGeneral
         .add(
            new IntSetting.Builder().name("activate-key").description("Mouse button that triggers the macro (-1 = always active).")
               .defaultValue(1)
               .min(-1)
               .max(400)
               .build()
         );
      this.placeDelay = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
               .name("place-delay")
               .description("Delay in ticks between placing crystals.")
               .defaultValue(0.0)
               .min(0.0)
               .max(20.0)
               .sliderMax(20.0)
               .build()
         );
      this.breakDelay = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
               .name("break-delay")
               .description("Delay in ticks between breaking crystals.")
               .defaultValue(0.0)
               .min(0.0)
               .max(20.0)
               .sliderMax(20.0)
               .build()
         );
      this.stopOnKill = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
               .name("stop-on-kill")
               .description("Pauses the macro when a nearby player dies, then resumes after 5 seconds.")
               .defaultValue(true)
               .build()
         );
      this.placeObsidianIfMissing = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
               .name("place-obsidian-if-missing")
               .description("Places obsidian if the target block isn't obsidian or bedrock.")
               .defaultValue(true)
               .build()
         );
      this.antiSuicide = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
               .name("anti-suicide")
               .description("Prevents placing/breaking if it would kill you.")
               .defaultValue(true)
               .build()
         );
      this.minHealth = this.sgGeneral
         .add(
            new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
               .name("min-health")
               .description("Minimum health to stay active.")
               .defaultValue(10.0)
               .min(0.0)
               .sliderMax(20.0)
               .visible(this.antiSuicide::get)
               .build()
         );
   }

   public void onActivate() {
      this.resetCounters();
      this.deadPlayers.clear();
      this.paused = false;
      this.resumeTime = 0L;
   }

   public void onDeactivate() {
      this.resetCounters();
      this.deadPlayers.clear();
      this.paused = false;
   }

   private void resetCounters() {
      this.placeDelayCounter = 0;
      this.breakDelayCounter = 0;
   }

   @EventHandler
   private void onTick(Pre event) {
      if (this.mc.player != null && this.mc.level != null) {
         if (!(Boolean)this.antiSuicide.get() || !(this.mc.player.getHealth() <= (Double)this.minHealth.get())) {
            if (this.mc.gui.screen() == null) {
               this.updateCounters();
               if (this.paused && System.currentTimeMillis() >= this.resumeTime) {
                  this.paused = false;
                  this.mc.player.sendSystemMessage(Component.literal("§7[§bCrystalPvP§7] §aResumed after stop-on-kill"));
               }

               if (!this.paused
                  && ((Integer)this.activateKey.get() == -1 || this.isMouseButtonPressed((Integer)this.activateKey.get()))
                  && !this.mc.player.isUsingItem()
                  && (this.mc.player.getMainHandItem().is(Items.END_CRYSTAL) || this.mc.player.getOffhandItem().is(Items.END_CRYSTAL))) {
                  if ((Boolean)this.stopOnKill.get() && this.checkForDeadPlayers()) {
                     this.paused = true;
                     this.resumeTime = System.currentTimeMillis() + 5000L;
                     this.mc.player.sendSystemMessage(Component.literal("§7[§bCrystalPvP§7] §cPaused due to player death (will resume in 5s)"));
                  } else {
                     this.handleInteraction();
                  }
               }
            }
         }
      }
   }

   private void updateCounters() {
      if (this.placeDelayCounter > 0) {
         this.placeDelayCounter--;
      }

      if (this.breakDelayCounter > 0) {
         this.breakDelayCounter--;
      }
   }

   private boolean isMouseButtonPressed(int button) {
      if (button == 0) {
         return this.mc.options.keyAttack.isDown();
      } else {
         return button == 1 ? this.mc.options.keyUse.isDown() : false;
      }
   }

   private void handleInteraction() {
      if (this.mc.hitResult != null) {
         if (this.mc.hitResult.getType() == Type.BLOCK) {
            this.handleBlockInteraction((BlockHitResult)this.mc.hitResult);
         } else if (this.mc.hitResult.getType() == Type.ENTITY) {
            this.handleEntityInteraction((EntityHitResult)this.mc.hitResult);
         }
      }
   }

   private void handleBlockInteraction(BlockHitResult blockHitResult) {
      if (!blockHitResult.isInside() && this.placeDelayCounter <= 0) {
         BlockPos blockPos = blockHitResult.getBlockPos();
         BlockState blockState = this.mc.level.getBlockState(blockPos);
         boolean isObsidianOrBedrock = blockState.is(Blocks.OBSIDIAN) || blockState.is(Blocks.BEDROCK);
         if (!isObsidianOrBedrock && (Boolean)this.placeObsidianIfMissing.get()) {
            int obsidianSlot = this.findItemSlot(Items.OBSIDIAN);
            int crystalSlot = this.findItemSlot(Items.END_CRYSTAL);
            if (obsidianSlot != -1 && crystalSlot != -1) {
               this.mc.player.getInventory().setSelectedSlot(obsidianSlot);
               this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, blockHitResult);
               this.mc.player.swing(InteractionHand.MAIN_HAND);
               this.placeDelayCounter = ((Double)this.placeDelay.get()).intValue();
               this.mc.player.getInventory().setSelectedSlot(crystalSlot);
            }
         } else if (isObsidianOrBedrock && this.isValidCrystalPlacement(blockPos)) {
            this.mc.gameMode.useItemOn(this.mc.player, InteractionHand.MAIN_HAND, blockHitResult);
            this.mc.player.swing(InteractionHand.MAIN_HAND);
            this.placeDelayCounter = ((Double)this.placeDelay.get()).intValue();
         }
      }
   }

   private void handleEntityInteraction(EntityHitResult entityHitResult) {
      if (this.breakDelayCounter <= 0) {
         Entity entity = entityHitResult.getEntity();
         this.mc.gameMode.attack(this.mc.player, entity);
         this.mc.player.swing(InteractionHand.MAIN_HAND);
         this.breakDelayCounter = ((Double)this.breakDelay.get()).intValue();
      }
   }

   private boolean isValidCrystalPlacement(BlockPos blockPos) {
      BlockPos upPos = blockPos.above();
      if (!this.mc.level.isEmptyBlock(upPos)) {
         return false;
      }

      AABB crystalBox = new AABB(upPos.getX(), upPos.getY(), upPos.getZ(), upPos.getX() + 1, upPos.getY() + 2, upPos.getZ() + 1);
      return this.mc.level.getEntities(this.mc.player, crystalBox).isEmpty();
   }

   private boolean checkForDeadPlayers() {
      if (this.mc.level == null) {
         return false;
      }

      for (Player player : this.mc.level.players()) {
         if (player != this.mc.player && (player.isDeadOrDying() || player.getHealth() <= 0.0F) && !this.deadPlayers.contains(player)) {
            this.deadPlayers.add(player);
            return true;
         }
      }

      this.deadPlayers.removeIf(p -> !p.isDeadOrDying() && p.getHealth() > 0.0F);
      return false;
   }

   private int findItemSlot(Item item) {
      for (int i = 0; i < 9; i++) {
         if (this.mc.player.getInventory().getItem(i).is(item)) {
            return i;
         }
      }

      return -1;
   }
}
