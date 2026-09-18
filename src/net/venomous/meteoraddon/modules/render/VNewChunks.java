package net.venomous.meteoraddon.modules.render;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.IntSetting.Builder;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.FluidState;
import net.venomous.meteoraddon.VenomousAddon;

public class VNewChunks extends Module {
   private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
   private final Setting<Integer> renderDistance = this.sgGeneral
      .add(
         ((Builder)((Builder)((Builder)new Builder().name("render-distance")).description("Render distance in chunks.")).defaultValue(32))
            .min(4)
            .max(128)
            .build()
      );
   private final Setting<Boolean> persist = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)((meteordevelopment.meteorclient.settings.BoolSetting.Builder)new meteordevelopment.meteorclient.settings.BoolSetting.Builder()
                     .name("persist"))
                  .description("Keep chunks when they are unloaded."))
               .defaultValue(true))
            .build()
      );
   private final Setting<SettingColor> newColor = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("new-color"))
               .description("Color for new chunks."))
            .defaultValue(new SettingColor(0, 255, 0, 80))
            .build()
      );
   private final Setting<SettingColor> oldColor = this.sgGeneral
      .add(
         ((meteordevelopment.meteorclient.settings.ColorSetting.Builder)((meteordevelopment.meteorclient.settings.ColorSetting.Builder)new meteordevelopment.meteorclient.settings.ColorSetting.Builder()
                  .name("old-color"))
               .description("Color for old chunks."))
            .defaultValue(new SettingColor(255, 0, 0, 80))
            .build()
      );
   private final Map<ChunkPos, Boolean> chunks = new ConcurrentHashMap<>();

   public VNewChunks() {
      super(VenomousAddon.CATEGORY, "v-new-chunks", "Highlights chunks that are likely newly generated.");
   }

   public void onDeactivate() {
      this.chunks.clear();
   }

   @EventHandler
   private void onPacketReceive(Receive event) {
      if (this.mc.level != null) {
         if (event.packet instanceof ClientboundLevelChunkWithLightPacket packet) {
            ChunkPos pos = new ChunkPos(packet.getX(), packet.getZ());
            if (!this.chunks.containsKey(pos)) {
               this.chunks.put(pos, false);
            }
         }

         if (event.packet instanceof ClientboundSectionBlocksUpdatePacket packet) {
            packet.runUpdates((bp, state) -> {
               FluidState fluid = state.getFluidState();
               if (!fluid.isEmpty() && !fluid.isSource()) {
                  this.chunks.put(ChunkPos.containing(bp), true);
               }
            });
         }

         if (event.packet instanceof ClientboundBlockUpdatePacket packet) {
            FluidState fluid = packet.getBlockState().getFluidState();
            if (!fluid.isEmpty() && !fluid.isSource()) {
               this.chunks.put(ChunkPos.containing(packet.getPos()), true);
            }
         }
      }
   }

   @EventHandler
   private void onRender(Render3DEvent event) {
      if (!this.chunks.isEmpty() && this.mc.player != null) {
         double maxDist = ((Integer)this.renderDistance.get()).intValue() * 16.0;
         double maxDistSq = maxDist * maxDist;

         for (Entry<ChunkPos, Boolean> entry : this.chunks.entrySet()) {
            ChunkPos chunk = entry.getKey();
            boolean isNew = entry.getValue();
            if (isNew) {
               int chunkX = chunk.getMinBlockX();
               int chunkZ = chunk.getMinBlockZ();
               double distSq = this.mc.player.distanceToSqr(chunkX + 8.0, this.mc.player.getY(), chunkZ + 8.0);
               if (distSq <= maxDistSq) {
                  Color sideColor = new Color((Color)this.newColor.get());
                  Color outlineColor = new Color((Color)this.newColor.get());
                  double y = this.mc.level.getMinY();
                  event.renderer.box(chunkX, y, chunkZ, chunkX + 16, y + 1.0, chunkZ + 16, sideColor, outlineColor, ShapeMode.Both, 0);
               }
            }
         }
      }
   }
}
