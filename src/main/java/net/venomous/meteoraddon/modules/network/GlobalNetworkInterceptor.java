package net.venomous.meteoraddon.modules.network;

import java.util.ArrayDeque;
import java.util.Deque;
import meteordevelopment.meteorclient.events.packets.PacketEvent.Send;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.StatusOnly;
import net.venomous.meteoraddon.VenomousAddon;
import net.venomous.meteoraddon.modules.movement.AssertDominance;
import meteordevelopment.meteorclient.settings.BoolSetting;

public class GlobalNetworkInterceptor extends Module {
    private final SettingGroup sgKinematics;
    private final SettingGroup sgRotation;
    private final SettingGroup sgSerializer;
    private final Setting<Boolean> enforceKinematics;
    private final Setting<Double> maxHorizontalPerTick;
    private final Setting<Double> maxVerticalPerTick;
    private final Setting<Boolean> interpolateViolations;
    private final Setting<Boolean> quantizeRotations;
    private final Setting<Boolean> enforceTickOrder;
    private final Setting<Integer> maxQueueDepth;
    private double lastSentX;
    private double lastSentY;
    private double lastSentZ;
    private float lastSentYaw;
    private float lastSentPitch;
    private boolean hasLastPosition;
    private boolean hasLastRotation;
    private final Deque<Packet<?>> tickQueue = new ArrayDeque<>();
    private boolean movementSentThisTick;
    private boolean bypassing;

    public GlobalNetworkInterceptor() {
        super(VenomousAddon.CATEGORY, "network-optimizer",
            "Ensures outgoing packets are compliant with server-side checks by smoothing movements and snapping rotations."
        );
        this.sgKinematics = this.settings.createGroup("Movement Smoothing");
        this.sgRotation = this.settings.createGroup("Rotation Snapping");
        this.sgSerializer = this.settings.createGroup("Packet Queue");

        this.enforceKinematics = this.sgKinematics
            .add(
                new BoolSetting.Builder().name("smooth-movement")
                    .description("Limits movement packet distance per tick for legacy anticheats (disable on Grim/modern simulation).")
                    .defaultValue(false)
                    .build()
            );
        this.maxHorizontalPerTick = this.sgKinematics
            .add(
                new DoubleSetting.Builder()
                    .name("max-horizontal")
                    .description("Max horizontal distance per tick.")
                    .defaultValue(0.6)
                    .range(0.1, 5.0)
                    .sliderMax(1.0)
                    .visible(this.enforceKinematics::get)
                    .build()
            );
        this.maxVerticalPerTick = this.sgKinematics
            .add(
                new DoubleSetting.Builder()
                    .name("max-vertical")
                    .description("Max vertical distance per tick.")
                    .defaultValue(0.5)
                    .range(0.1, 5.0)
                    .sliderMax(1.0)
                    .visible(this.enforceKinematics::get)
                    .build()
            );
        this.interpolateViolations = this.sgKinematics
            .add(
                new BoolSetting.Builder().name("interpolate").description("Split fast movements into smaller steps.")
                    .defaultValue(false)
                    .visible(this.enforceKinematics::get)
                    .build()
            );
        this.quantizeRotations = this.sgRotation
            .add(
                new BoolSetting.Builder().name("snap-to-grid").description("Snap rotation deltas to the vanilla sensitivity GCD grid.")
                    .defaultValue(true)
                    .build()
            );
        this.enforceTickOrder = this.sgSerializer
            .add(
                new BoolSetting.Builder().name("limit-to-one-packet")
                    .description("Only allow one movement packet per tick (legacy anticheat option).")
                    .defaultValue(false)
                    .build()
            );
        this.maxQueueDepth = this.sgSerializer
            .add(
                new IntSetting.Builder()
                    .name("max-queue")
                    .description("Maximum packets to buffer before forcing a send.")
                    .defaultValue(20)
                    .range(5, 100)
                    .sliderMax(50)
                    .visible(this.enforceTickOrder::get)
                    .build()
            );
    }

    @Override
    public void onActivate() {
        super.onActivate();
        this.resetState();
        this.info("Network Optimizer active. Preventing movement and rotation flags.", new Object[0]);
    }

    @Override
    public void onDeactivate() {
        this.flushQueue();
        this.resetState();
    }

    @EventHandler
    private void onTick(Pre pre) {
        if (this.mc.player != null && this.mc.getConnection() != null) {
            this.movementSentThisTick = false;
            if (!this.tickQueue.isEmpty()) {
                if (this.enforceTickOrder.get()) {
                    Packet<?> packet = this.tickQueue.poll();
                    this.movementSentThisTick = true;
                    this.sendBypassed(packet);
                } else {
                    // Disabling the limiter also disables the only drain path. Flush instead of
                    // stranding old packets to be sent after newer movement or on deactivation.
                    this.flushQueue();
                }
            }
        } else {
            // Do not smooth a new connection against coordinates from the old one.
            this.resetState();
        }
    }

    @EventHandler
    private void onSend(Send send) {
        if (!this.bypassing
            && this.mc.player != null
            && this.mc.getConnection() != null
            && !this.isAssertDominanceActive()
            && send.packet instanceof ServerboundMovePlayerPacket serverboundMovePlayerPacket) {
            send.setCancelled(true);
            this.handleMovementPacket(serverboundMovePlayerPacket, send);
        }
    }

    private void handleMovementPacket(ServerboundMovePlayerPacket serverboundMovePlayerPacket, Send send) {
        double d = serverboundMovePlayerPacket.getX(this.mc.player.getX());
        double d2 = serverboundMovePlayerPacket.getY(this.mc.player.getY());
        double d3 = serverboundMovePlayerPacket.getZ(this.mc.player.getZ());
        float f = serverboundMovePlayerPacket.getYRot(this.mc.player.getYRot());
        float f2 = serverboundMovePlayerPacket.getXRot(this.mc.player.getXRot());
        boolean bl = serverboundMovePlayerPacket.isOnGround();
        boolean bl2 = serverboundMovePlayerPacket.horizontalCollision();
        boolean bl3 = serverboundMovePlayerPacket.hasPosition();
        boolean bl4 = serverboundMovePlayerPacket.hasRotation();

        if (bl4 && this.quantizeRotations.get()) {
            float gcd = this.computeSensitivityFactor();
            if (gcd > 0.0001F) {
                if (this.hasLastRotation) {
                    float deltaYaw = f - this.lastSentYaw;
                    float deltaPitch = f2 - this.lastSentPitch;
                    f = this.lastSentYaw + (float)(Math.round(deltaYaw / gcd) * gcd);
                    f2 = clampPitch(this.lastSentPitch + (float)(Math.round(deltaPitch / gcd) * gcd));
                } else {
                    this.hasLastRotation = true;
                }
            }
        }

        if (bl3 && this.enforceKinematics.get() && this.hasLastPosition) {
            double d4 = d - this.lastSentX;
            double d5 = d2 - this.lastSentY;
            double d6 = d3 - this.lastSentZ;
            double d7 = Math.sqrt(d4 * d4 + d6 * d6);
            double d8 = Math.abs(d5);
            double d9 = this.maxHorizontalPerTick.get();
            double d10 = this.maxVerticalPerTick.get();
            if (d7 > d9 || d8 > d10) {
                if (this.interpolateViolations.get()) {
                    int n = (int)Math.ceil(d7 / d9);
                    int n2 = (int)Math.ceil(d8 / d10);
                    int n3 = Math.max(n, n2);
                    n3 = Math.max(n3, 2);
                    double d11 = d4 / n3;
                    double d12 = d5 / n3;
                    double d13 = d6 / n3;
                    double startX = this.lastSentX;
                    double startY = this.lastSentY;
                    double startZ = this.lastSentZ;

                    for (int i = 1; i < n3; i++) {
                        double d14 = startX + d11 * i;
                        double d15 = startY + d12 * i;
                        double d16 = startZ + d13 * i;
                        Object object = bl4 && i == n3 - 1 ? new PosRot(d14, d15, d16, f, f2, bl, bl2) : new Pos(d14, d15, d16, bl, bl2);
                        this.enqueueOrSend((Packet<?>)object);
                        this.lastSentX = d14;
                        this.lastSentY = d15;
                        this.lastSentZ = d16;
                    }

                    ServerboundMovePlayerPacket serverboundMovePlayerPacket2 = rebuildPacket(d, d2, d3, f, f2, bl, bl2, bl3, bl4);
                    this.enqueueOrSend(serverboundMovePlayerPacket2);
                    this.lastSentX = d;
                    this.lastSentY = d2;
                    this.lastSentZ = d3;
                    if (bl4) {
                        this.lastSentYaw = f;
                        this.lastSentPitch = f2;
                        this.hasLastRotation = true;
                    }

                    return;
                }

                if (d7 > d9 && d7 > 0.0) {
                    double d17 = d9 / d7;
                    d = this.lastSentX + d4 * d17;
                    d3 = this.lastSentZ + d6 * d17;
                }

                if (d8 > d10) {
                    d2 = this.lastSentY + Math.signum(d5) * d10;
                }
            }
        }

        ServerboundMovePlayerPacket serverboundMovePlayerPacket3 = rebuildPacket(d, d2, d3, f, f2, bl, bl2, bl3, bl4);
        this.enqueueOrSend(serverboundMovePlayerPacket3);
        if (bl3) {
            this.lastSentX = d;
            this.lastSentY = d2;
            this.lastSentZ = d3;
            this.hasLastPosition = true;
        }

        if (bl4) {
            this.lastSentYaw = f;
            this.lastSentPitch = f2;
            this.hasLastRotation = true;
        }
    }

    private void enqueueOrSend(Packet<?> packet) {
        if (!this.enforceTickOrder.get()) {
            this.sendBypassed(packet);
        } else if (!this.movementSentThisTick) {
            this.movementSentThisTick = true;
            this.sendBypassed(packet);
        } else {
            this.tickQueue.addLast(packet);
            if (this.tickQueue.size() > this.maxQueueDepth.get()) {
                this.flushQueue();
            }
        }
    }

    private void flushQueue() {
        var connection = this.mc.getConnection();
        if (connection == null) {
            this.tickQueue.clear();
            return;
        }

        this.bypassing = true;
        try {
            while (!this.tickQueue.isEmpty()) {
                connection.send(this.tickQueue.poll());
            }
        } finally {
            this.bypassing = false;
        }
    }

    private void sendBypassed(Packet<?> packet) {
        var connection = this.mc.getConnection();
        if (connection == null) return;

        this.bypassing = true;
        try {
            connection.send(packet);
        } finally {
            this.bypassing = false;
        }
    }

    private void resetState() {
        this.tickQueue.clear();
        this.hasLastPosition = false;
        this.hasLastRotation = false;
        this.movementSentThisTick = false;
        this.bypassing = false;
        this.lastSentX = 0.0;
        this.lastSentY = 0.0;
        this.lastSentZ = 0.0;
        this.lastSentYaw = 0.0F;
        this.lastSentPitch = 0.0F;
    }

    private float computeSensitivityFactor() {
        if (this.mc.options == null) return 0.0F;
        float f = ((Double)this.mc.options.sensitivity().get()).floatValue();
        float f2 = f * 0.6F + 0.2F;
        return f2 * f2 * f2 * 1.2F;
    }

    private static float clampPitch(float f) {
        if (f > 90.0F) {
            return 90.0F;
        } else {
            return f < -90.0F ? -90.0F : f;
        }
    }

    private static ServerboundMovePlayerPacket rebuildPacket(
        double d, double d2, double d3, float f, float f2, boolean bl, boolean bl2, boolean bl3, boolean bl4
    ) {
        if (bl3 && bl4) {
            return new PosRot(d, d2, d3, f, f2, bl, bl2);
        } else if (bl3) {
            return new Pos(d, d2, d3, bl, bl2);
        } else {
            return (ServerboundMovePlayerPacket)(bl4 ? new Rot(f, f2, bl, bl2) : new StatusOnly(bl, bl2));
        }
    }

    private boolean isAssertDominanceActive() {
        Modules modules = Modules.get();
        if (modules == null) return false;

        Module module = modules.get(AssertDominance.class);
        return module != null && module.isActive();
    }

    @Override
    public String getInfoString() {
        int n = this.tickQueue.size();
        return n > 0 ? "Queue: " + n : "Active";
    }
}
