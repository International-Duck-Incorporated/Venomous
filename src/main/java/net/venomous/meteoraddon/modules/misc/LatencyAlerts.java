package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.venomous.meteoraddon.VenomousAddon;

/**
 * Detects packet loss: the gap since the last packet the server sent is measured every tick, and
 * once it crosses the latency threshold an alert is raised - at most once per the alert interval,
 * so a sustained stall does not flood chat. Ported from Raven B4's {@code LatencyAlerts}, with the
 * same measurement and the same interval guard; the hover component it hung the packet details on
 * is replaced by the details printed inline, since the addon's chat is plain text.
 */
public class LatencyAlerts extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> alertInterval = this.sgGeneral.add(
        new DoubleSetting.Builder().name("alert-interval")
            .description("Minimum seconds between alerts while the stall continues.")
            .defaultValue(3.0)
            .range(0.0, 5.0)
            .sliderMax(5.0)
            .build()
    );
    private final Setting<Double> highLatency = this.sgGeneral.add(
        new DoubleSetting.Builder().name("high-latency")
            .description("Seconds without a received packet that count as packet loss.")
            .defaultValue(0.5)
            .range(0.1, 5.0)
            .sliderMax(5.0)
            .build()
    );
    private final Setting<Boolean> ignoreLimbo = this.sgGeneral.add(
        new BoolSetting.Builder().name("ignore-limbo")
            .description("Raven's carve-out for limbo worlds, measured the same way: no alerts on The End dimension, where some servers park offline players.")
            .defaultValue(true)
            .build()
    );

    private long lastPacketTime;
    private long lastAlertTime;
    private String lastPacketName = "unknown";

    public LatencyAlerts() {
        super(VenomousAddon.CATEGORY, "latency-alerts", "Warns when packets stop arriving - the gap since the last server packet, with the packet that was last.");
    }

    public void onActivate() {
        this.quiet();
    }

    @Override
    public void onDeactivate() {
        this.quiet();
    }

    @EventHandler
    private void onPacketReceive(Receive event) {
        this.lastPacketTime = System.currentTimeMillis();
        this.lastPacketName = event.packet != null ? event.packet.getClass().getSimpleName() : "unknown";
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null) {
            this.quiet();
            return;
        }

        if (this.mc.hasSingleplayerServer() || (this.ignoreLimbo.get() && this.inLimbo())) {
            this.quiet();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastPacketTime >= this.highLatency.get() * 1000.0
            && now - this.lastAlertTime >= this.alertInterval.get() * 1000.0) {
            this.info("Packet loss: no packets for %sms (last: %s)", now - this.lastPacketTime, this.lastPacketName);
            this.lastAlertTime = now;
        }
    }

    /** Vanilla world the original considered a server queue stall sitting idle, so no alerts. */
    private boolean inLimbo() {
        return this.mc.level != null && "the_end".equals(this.mc.level.dimension().identifier().getPath());
    }

    private void quiet() {
        this.lastPacketTime = System.currentTimeMillis();
        this.lastAlertTime = System.currentTimeMillis();
    }
}
