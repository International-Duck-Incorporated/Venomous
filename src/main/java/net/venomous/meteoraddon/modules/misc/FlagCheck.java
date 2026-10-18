package net.venomous.meteoraddon.modules.misc;

import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Receive;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;

/**
 * Watches for the server correcting our position, which is how an anticheat flags a client.
 *
 * <p>A correction is a {@code ClientboundPlayerPositionPacket} that arrives without a teleport
 * having been requested first. The server sends that packet for two reasons: we asked to be
 * teleported (chorus fruit, a plugin, our own teleport tools) or an anticheat rejected our
 * movement and pushed us back. The two are told apart by whether a matching accept-teleport
 * request went out recently - which is exactly the distinction that makes this detectable at
 * all, since the packets are otherwise identical.</p>
 *
 * <p>This is a signal, not a verdict. A laggy correction, a plugin teleport, or a server with
 * an unusual movement check will all produce one, and a client can be flagged without any
 * correction arriving at all.</p>
 */
public class FlagCheck extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Boolean> chat = this.sgGeneral
        .add(new BoolSetting.Builder().name("chat").description("Show flag alerts in chat.").defaultValue(true).build());

    private final Setting<Integer> graceTicks = this.sgGeneral
        .add(
            new IntSetting.Builder()
                .name("grace-ticks")
                .description("Ticks after requesting a teleport during which a correction is expected and ignored.")
                .defaultValue(20)
                .min(1)
                .max(200)
                .sliderMax(60)
                .build()
        );

    private final Setting<Double> minDisplacement = this.sgGeneral
        .add(
            new DoubleSetting.Builder()
                .name("min-displacement")
                .description("Ignore corrections that move you less than this many blocks.")
                .defaultValue(0.05)
                .min(0.0)
                .max(5.0)
                .sliderMax(1.0)
                .build()
        );

    /** How long to keep a detected flag on screen before forgetting it. */
    private static final long FLAG_MEMORY_MS = 10_000L;

    /** A correction we should not have received. */
    private record Flag(long at, double displacement) {}

    private final Deque<Flag> flags = new ArrayDeque<>();
    private long teleportRequestedAt = 0L;
    private int flagsThisSession = 0;
    private long lastAnnouncedAt = 0L;

    public FlagCheck() {
        super(VenomousAddon.CATEGORY, "flagcheck", "Reports anticheat position corrections (setbacks).");
    }

    @Override
    public void onActivate() {
        this.flags.clear();
        this.teleportRequestedAt = 0L;
        this.flagsThisSession = 0;
        this.lastAnnouncedAt = 0L;
    }

    @Override
    public void onDeactivate() {
        this.flags.clear();
        this.teleportRequestedAt = 0L;
        this.flagsThisSession = 0;
        this.lastAnnouncedAt = 0L;
    }

    @EventHandler
    private void onPacketSend(Send event) {
        // Any outbound request that would make the server echo a position back. Recording the
        // intent is what separates a correction from an expected teleport reply.
        if (event.packet instanceof ServerboundAcceptTeleportationPacket) {
            this.teleportRequestedAt = System.currentTimeMillis();
        }
    }

    @EventHandler
    private void onReceive(Receive event) {
        if (this.mc.player == null) return;
        if (!(event.packet instanceof ClientboundPlayerPositionPacket packet)) return;

        long now = System.currentTimeMillis();

        if (this.teleportRequestedAt != 0L && now - this.teleportRequestedAt <= (Integer)this.graceTicks.get() * 50L) {
            // We asked for this. Reset so the grace window cannot cover a later genuine flag.
            this.teleportRequestedAt = 0L;
            return;
        }

        // resolveRelative() has already folded the packet's relative axes into absolute
        // coordinates by the time this handler sees it, so the displacement is a plain
        // distance from where we stand to where the server is insisting we are.
        double displacement = distanceTo(packet.change().position());

        if (displacement < (Double)this.minDisplacement.get()) {
            // Sub-block nudges are ordinary chunk/position rounding, not a setback.
            return;
        }

        this.flags.addLast(new Flag(now, displacement));
        this.flagsThisSession++;

        // One chat line per correction would itself be a tell on a server that logs chat.
        if ((Boolean)this.chat.get() && now - this.lastAnnouncedAt > 1000L) {
            this.lastAnnouncedAt = now;
            this.warning(
                "Anticheat correction: %.2f blocks (flag %d this session)",
                new Object[]{displacement, this.flagsThisSession});
        }
    }

    private double distanceTo(Vec3 pos) {
        if (pos == null) return 0.0;

        double dx = pos.x - this.mc.player.getX();
        double dy = pos.y - this.mc.player.getY();
        double dz = pos.z - this.mc.player.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Flags detected in the last {@link #FLAG_MEMORY_MS}. */
    public int recentFlagCount() {
        long cutoff = System.currentTimeMillis() - FLAG_MEMORY_MS;

        while (!this.flags.isEmpty() && this.flags.peekFirst().at() < cutoff) {
            this.flags.pollFirst();
        }

        return this.flags.size();
    }

    /** Total corrections seen since the module was enabled. */
    public int getSessionFlags() {
        return this.flagsThisSession;
    }

    @Override
    public String getInfoString() {
        return this.flagsThisSession == 0 ? null : this.flagsThisSession + " flags";
    }
}
