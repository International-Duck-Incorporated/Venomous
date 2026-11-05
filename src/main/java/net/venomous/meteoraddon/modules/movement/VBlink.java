package net.venomous.meteoraddon.modules.movement;

import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class VBlink extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final Setting<Boolean> cancelOutgoing = this.sgGeneral
        .add(new BoolSetting.Builder().name("cancel-outgoing").description("Cancel outgoing movement packets.").defaultValue(true).build());
    private final Setting<Integer> autoDisable = this.sgGeneral
        .add(
            new meteordevelopment.meteorclient.settings.IntSetting.Builder()
                .name("auto-disable")
                .description("Automatically disable after this many ticks. 0 = never.")
                .defaultValue(0)
                .min(0)
                .sliderMax(500)
                .build()
        );
    private final Setting<Boolean> dummy = this.sgGeneral
        .add(
            new BoolSetting.Builder().name("dummy").description("Show a fake player at your original position.").defaultValue(false).build()
        );
    private final List<Packet<?>> outgoingPackets = new ArrayList<>();
    private Vec3 startPos = null;
    private int ticks = 0;

    public VBlink() {
        super(VenomousAddon.CATEGORY, "v-blink", "Suspends movement packets, making you appear frozen to others.");
    }

    @Override
    public void onActivate() {
        this.outgoingPackets.clear();
        this.startPos = this.mc.player != null ? this.mc.player.position() : null;
        this.ticks = 0;
    }

    @Override
    public void onDeactivate() {
        if (this.mc.player != null && this.mc.player.connection != null) {
            for (Packet<?> packet : this.outgoingPackets) {
                this.mc.player.connection.send(packet);
            }
        }

        this.outgoingPackets.clear();
        this.startPos = null;
        this.ticks = 0;
    }

    @EventHandler
    private void onPacketSend(Send event) {
        if ((Boolean)this.cancelOutgoing.get() && this.mc.player != null) {
            Packet<?> packet = event.packet;
            if (packet instanceof ServerboundMovePlayerPacket) {
                event.cancel();
                this.outgoingPackets.add(packet);
            }
        }
    }

    @EventHandler
    private void onTick(Pre event) {
        this.ticks++;
        if ((Integer)this.autoDisable.get() > 0 && this.ticks >= (Integer)this.autoDisable.get()) {
            this.info("Auto-disabled after %d ticks.", new Object[]{this.ticks});
            this.toggle();
        }
    }

    /**
     * Draws a marker at the position the server still believes the player occupies.
     *
     * <p>While packets are suppressed the server keeps seeing the player at {@code startPos}, so
     * an outline drawn there is what makes the freeze legible. Nothing is drawn once the queue
     * has flushed, because at that point nothing is frozen any more.</p>
     */
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!(Boolean)this.dummy.get() || this.startPos == null || this.mc.player == null) return;
        if (this.outgoingPackets.isEmpty()) return;

        AABB box = this.mc.player.getBoundingBox().move(
            this.startPos.x - this.mc.player.getX(),
            this.startPos.y - this.mc.player.getY(),
            this.startPos.z - this.mc.player.getZ());

        Color color = new Color(120, 170, 255, 200);
        event.renderer.box(box, color, color, ShapeMode.Both, 0);
    }

    public String getInfoString() {
        return this.outgoingPackets.size() + " packets";
    }
}
