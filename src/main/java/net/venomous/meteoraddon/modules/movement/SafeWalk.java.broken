package net.venomous.meteoraddon.modules.movement;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class SafeWalk extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgBehavior = this.settings.createGroup("Behavior");

    private final Setting<Double> edgeDistance = this.sgGeneral
        .add(new DoubleSetting.Builder()
            .name("edge-distance")
            .description("Distance from edge to start sneaking.")
            .defaultValue(0.3)
            .min(0.05)
            .max(1.5)
            .sliderMax(1.5)
            .build());

    private final Setting<Integer> lookDown = this.sgGeneral
        .add(new IntSetting.Builder()
            .name("look-down")
            .description("Minimum downward look angle to activate.")
            .defaultValue(25)
            .min(0)
            .max(90)
            .sliderMax(90)
            .build());

    private final Setting<Boolean> randomizeHold = this.sgBehavior
        .add(new BoolSetting.Builder()
            .name("randomize-hold")
            .description("Randomize sneak hold time to look more natural.")
            .defaultValue(true)
            .build());

    private final Setting<Integer> holdTimeMin = this.sgBehavior
        .add(new IntSetting.Builder()
            .name("hold-time-min")
            .description("Minimum sneak hold time in milliseconds.")
            .defaultValue(100)
            .min(0)
            .max(500)
            .sliderMax(500)
            .visible(this.randomizeHold::get)
            .build());

    private final Setting<Integer> holdTimeMax = this.sgBehavior
        .add(new IntSetting.Builder()
            .name("hold-time-max")
            .description("Maximum sneak hold time in milliseconds.")
            .defaultValue(200)
            .min(0)
            .max(500)
            .sliderMax(500)
            .visible(this.randomizeHold::get)
            .build());

    private final Setting<Boolean> scaffoldAware = this.sgBehavior
        .add(new BoolSetting.Builder()
            .name("scaffold-aware")
            .description("Disable when Scaffold is active to avoid conflicts.")
            .defaultValue(true)
            .build());

    private boolean sneaking = false;
    private long holdUntilMs = 0L;
    private int clearTicks = 0;
    private static final int RELEASE_CLEAR_TICKS = 3;

    public SafeWalk() {
        super(VenomousAddon.CATEGORY, "safe-walk", "Automatically sneaks at ledges to prevent falling.");
    }

    @Override
    public void onActivate() {
        this.sneaking = false;
        this.holdUntilMs = 0L;
        this.clearTicks = 0;
    }

    @Override
    public void onDeactivate() {
        if (this.sneaking) {
            this.setSneaking(false);
        }
        this.holdUntilMs = 0L;
        this.clearTicks = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) {
            return;
        }

        // Check if Scaffold is active
        if (this.scaffoldAware.get() && this.isScaffoldActive()) {
            if (this.sneaking) {
                this.setSneaking(false);
            }
            return;
        }

        boolean atEdge = this.conditionsMet();
        long now = System.currentTimeMillis();

        this.clearTicks = atEdge ? 0 : this.clearTicks + 1;

        if (this.sneaking && now < this.holdUntilMs) {
            return; // Still holding sneak
        }

        if (atEdge) {
            this.sneaking = true;
            if (this.randomizeHold.get()) {
                this.holdUntilMs = now + this.getRandomHoldTime();
            } else {
                this.holdUntilMs = now + this.holdTimeMin.get();
            }
            this.setSneaking(true);
            return;
        }

        // Flicker handling - keep sneak for a few ticks after leaving edge
        if (this.sneaking && this.holdsThroughFlicker(this.clearTicks)) {
            return;
        }

        if (this.sneaking) {
            this.sneaking = false;
            this.setSneaking(false);
        }
    }

    private boolean conditionsMet() {
        Player player = this.mc.player;
        if (player == null || this.mc.level == null) return false;
        if (!player.onGround() || player.isPassenger() || player.getAbilities().flying) return false;

        // Check look down angle
        if (player.getXRot() < this.lookDown.get()) return false;

        return this.dropAhead(this.edgeDistance.get());
    }

    private boolean dropAhead(double distance) {
        Player player = this.mc.player;
        Vec3 velocity = player.getDeltaMovement();
        Vec3 direction;

        if (velocity.horizontalDistanceSqr() > 1.0E-6D) {
            direction = new Vec3(velocity.x, 0.0D, velocity.z).normalize();
        } else {
            direction = Vec3.directionFromRotation(0.0F, player.getYRot());
        }

        Vec3 probe = player.position().add(direction.scale(distance));
        BlockPos underProbe = BlockPos.containing(probe).below();

        // Check if there's solid ground ahead
        if (!this.mc.level.isOutsideBuildHeight(underProbe)) {
            if (this.isStandable(this.mc.level.getBlockState(underProbe), underProbe)) {
                return false;
            }
        }

        // Check for empty space below the probe point
        double half = Math.max(0.05D, player.getBbWidth() / 2.0D - 0.05D);
        AABB below = new AABB(
            probe.x - half, probe.y - 0.55D, probe.z - half,
            probe.x + half, probe.y, probe.z + half
        );

        return this.mc.level.noCollision(player, below);
    }

    private boolean isStandable(net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
        // Check if the block can be stood on
        return state.isFaceSturdy(this.mc.level, pos, net.minecraft.core.Direction.UP);
    }

    private boolean holdsThroughFlicker(int clearTicks) {
        return this.sneaking && clearTicks < RELEASE_CLEAR_TICKS;
    }

    private int getRandomHoldTime() {
        int min = this.holdTimeMin.get();
        int max = this.holdTimeMax.get();
        return min + (int) (Math.random() * (max - min + 1));
    }

    private void setSneaking(boolean sneak) {
        if (this.mc.player == null || this.mc.options == null) return;

        // Send sneak key state to server
        if (sneak) {
            this.mc.options.keyShift.setDown(true);
        } else {
            this.mc.options.keyShift.setDown(false);
        }
    }

    private boolean isScaffoldActive() {
        // Check if Scaffold module is active
        meteordevelopment.meteorclient.systems.modules.Module scaffold =
            meteordevelopment.meteorclient.systems.modules.Modules.get().get(net.venomous.meteoraddon.modules.world.Scaffold.class);
        return scaffold != null && scaffold.isActive();
    }

    @Override
    public String getInfoString() {
        return this.sneaking ? "sneaking" : "idle";
    }
}
