package net.venomous.meteoraddon.modules.network;

import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.utils.PacketGates;

/**
 * Holds the connection's own packets for a while before letting them go.
 *
 * <p>Outbound mode delays everything this client sends (minus the connection keep-alives), so
 * the server keeps acting on an older you: hits land against a position you have already left.
 * Inbound mode delays what the server sends, so this client keeps acting on an older world.
 * Both together is the full stutter.</p>
 *
 * <p>Ported from Raven B4's {@code FakeLag}, whose delay engine is a whole subsystem - a
 * request/handler/timeout lattice shared by every network module. Here each module owns its
 * queue outright, the convention the rest of this addon already runs on, so the module reads
 * top to bottom with no indirection.</p>
 *
 * <p>The cost is stated plainly: a burst of released movement packets reads as a timer anomaly
 * to any server that counts them, and prediction anticheats simulate the position jump on
 * release. Short delays are the usable range; this module is for servers that tolerate lag, not
 * for ones that grade it.</p>
 */
public class FakeLag extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<FakeLag.Mode> mode = this.sgGeneral.add(
        new EnumSetting.Builder<FakeLag.Mode>()
            .name("mode")
            .description("Which direction of the connection to delay.")
            .defaultValue(FakeLag.Mode.Outbound)
            .build()
    );

    private final Setting<Integer> delayMs = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("delay-ms")
            .description("How long a held packet waits before it is released.")
            .defaultValue(200)
            .range(20, 1500)
            .sliderMax(1000)
            .build()
    );

    private final Setting<Boolean> flushOnDamage = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("flush-on-damage")
            .description("Release everything the moment you take damage, so the knockback is applied to a position the server knows about.")
            .defaultValue(true)
            .build()
    );

    /** Held packets in send order; written from the netty threads, read from the tick. */
    private final Queue<FakeLag.Held> held = new ConcurrentLinkedQueue<>();
    /** Re-entrancy guard: releases go straight to the connection, not back through the queue. */
    private boolean flushing = false;

    public FakeLag() {
        super(VenomousAddon.CATEGORY, "fake-lag", "Delays your packets (outbound), the server's (inbound), or both, to fight on stale positions.");
    }

    @Override
    public void onActivate() {
        this.held.clear();
        this.flushing = false;
    }

    @Override
    public void onDeactivate() {
        this.flushAll();
    }

    @EventHandler
    private void onSend(Send event) {
        if (this.flushing || this.mc.player == null || this.mc.getConnection() == null) return;
        if (!this.holdsOutbound() || PacketGates.isRealtimePassthrough(event.packet)) return;

        event.cancel();
        this.held.add(new FakeLag.Held(event.packet, System.currentTimeMillis() + this.delayMs.get(), true));
    }

    @EventHandler
    private void onReceive(Receive event) {
        if (this.mc.player == null || this.mc.getConnection() == null) {
            this.held.clear();
            return;
        }

        Packet<?> packet = event.packet;

        // The triggers that end a hold: a teleport means the server moved you and every held
        // packet is now describing a stale world; damage is the moment a stale position costs
        // the most; a disconnect means nothing will ever be released again.
        if (packet instanceof ClientboundPlayerPositionPacket
            || packet instanceof ClientboundDisconnectPacket
            || this.flushOnDamage.get() && packet instanceof ClientboundHurtAnimationPacket hurt
                && hurt.id() == this.mc.player.getId()) {
            if (this.isHolding()) this.flushAll();
            return;
        }

        if (!this.holdsInbound() || this.flushing || PacketGates.isRealtimePassthrough(packet)) return;

        event.cancel();
        this.held.add(new FakeLag.Held(packet, System.currentTimeMillis() + this.delayMs.get(), false));
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.getConnection() == null) {
            this.held.clear();
            return;
        }

        this.releaseDue();
    }

    /** Releases every held packet whose wait has elapsed, inbound replays first-come-first-served. */
    private void releaseDue() {
        long now = System.currentTimeMillis();
        this.flushing = true;
        try {
            Iterator<FakeLag.Held> iterator = this.held.iterator();
            while (iterator.hasNext()) {
                FakeLag.Held entry = iterator.next();
                if (entry.releaseAt > now) continue;

                this.deliver(entry);
                iterator.remove();
            }
        } finally {
            this.flushing = false;
        }
    }

    private void flushAll() {
        this.flushing = true;
        try {
            FakeLag.Held entry;
            while ((entry = this.held.poll()) != null) {
                this.deliver(entry);
            }
        } finally {
            this.flushing = false;
        }
    }

    private void deliver(FakeLag.Held entry) {
        try {
            if (entry.outbound) {
                this.mc.getConnection().send(entry.packet);
            } else {
                this.replayInbound(entry.packet);
            }
        } catch (Exception ignored) {
            // A replayed packet whose world moved on under it is dropped rather than fatal -
            // the connection's own ordering carries the rest.
        }
    }

    @SuppressWarnings("unchecked")
    private void replayInbound(Packet<?> packet) {
        ((Packet<ClientGamePacketListener>) packet).handle(this.mc.player.connection);
    }

    private boolean holdsOutbound() {
        return this.mode.get() == FakeLag.Mode.Outbound || this.mode.get() == FakeLag.Mode.Both;
    }

    private boolean holdsInbound() {
        return this.mode.get() == FakeLag.Mode.Inbound || this.mode.get() == FakeLag.Mode.Both;
    }

    private boolean isHolding() {
        return !this.held.isEmpty();
    }

    @Override
    public String getInfoString() {
        return this.held.size() + " held";
    }

    /** A packet with its release deadline, and which direction it was travelling. */
    private record Held(Packet<?> packet, long releaseAt, boolean outbound) {
    }

    public enum Mode {
        Outbound,
        Inbound,
        Both;
    }
}
