package net.venomous.meteoraddon.modules.world;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import meteordevelopment.meteorclient.events.entity.player.PlaceBlockEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.mixin.AbstractSignEditScreenAccessor;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.BoolSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.venomous.meteoraddon.VenomousAddon;

public class NoSignScreen extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Boolean> always = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("always"))
                  .description("Also skip the screen when opening an existing sign, not just freshly placed ones."))
               .defaultValue(false))
            .build()
      );
   private final Setting<Integer> delay = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)((meteordevelopment.meteorclient.settings.IntSetting.Builder)new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                     .name("delay"))
                  .description("Tick delay before sending each sign update packet. Some servers (2b2t) dislike it arriving too soon after the place packet."))
               .defaultValue(0))
            .range(0, 100)
            .sliderRange(0, 20)
            .build()
      );
   private final Setting<Boolean> skipWaxed = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("skip-waxed"))
                  .description("Leave waxed signs alone - they can't be edited anyway, so let the screen open normally."))
               .defaultValue(true))
            .build()
      );
   private final Map<BlockPos, Integer> placed = new HashMap<>();
   private final Queue<ServerboundSignUpdatePacket> queue = new ArrayDeque<>();
   private int tick;
   private int timer;
   private static final int PLACE_MEMORY_TICKS = 40;

   public NoSignScreen() {
      super(VenomousAddon.CATEGORY, "no-sign-screen", "Skips the sign edit screen when placing signs, leaving them blank.");
   }

   @Override
   public void onActivate() {
      this.placed.clear();
      this.queue.clear();
      this.tick = 0;
      this.timer = 0;
   }

   @Override
   public void onDeactivate() {
      this.placed.clear();
      this.queue.clear();
      this.tick = 0;
      this.timer = 0;
   }

   @EventHandler
   private void onGameLeft(GameLeftEvent var1) {
      this.onDeactivate();
   }

   @EventHandler
   private void onPlaceBlock(PlaceBlockEvent var1) {
      if (var1.block instanceof SignBlock) {
         this.placed.put(var1.blockPos.immutable(), this.tick);
      }
   }

   @EventHandler
   private void onOpenScreen(OpenScreenEvent var1) {
      if (var1.screen instanceof AbstractSignEditScreen var2) {
         SignBlockEntity var5 = ((AbstractSignEditScreenAccessor)var2).meteor$getSign();
         if (var5 != null) {
            if (!(Boolean)this.skipWaxed.get() || !var5.isWaxed()) {
               BlockPos var4 = var5.getBlockPos();
               if ((Boolean)this.always.get() || this.placed.remove(var4) != null) {
                  this.placed.remove(var4);
                  this.queue.add(new ServerboundSignUpdatePacket(var4, true, "", "", "", ""));
                  var1.cancel();
               }
            }
         }
      }
   }

   @EventHandler
   private void onTick(Post var1) {
      this.tick++;
      this.placed.entrySet().removeIf(var1x -> this.tick - var1x.getValue() > 40);
      if (this.mc.player != null && this.mc.getConnection() != null && !this.queue.isEmpty()) {
         if (this.timer < (Integer)this.delay.get()) {
            this.timer++;
         } else {
            this.mc.getConnection().send((Packet)this.queue.poll());
            this.timer = 0;
         }
      } else {
         this.timer = 0;
      }
   }

   public String getInfoString() {
      return this.always.get() ? "always" : null;
   }
}
