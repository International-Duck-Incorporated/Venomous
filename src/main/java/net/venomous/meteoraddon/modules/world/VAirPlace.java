package net.venomous.meteoraddon.modules.world;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.venomous.meteoraddon.VenomousAddon;

public class VAirPlace extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Double> range = this.sgGeneral.add(
        new DoubleSetting.Builder()
            .name("range")
            .description("Distance from crosshair to place blocks in mid-air.")
            .defaultValue(4.0)
            .min(1.0)
            .sliderMax(6.0)
            .build()
    );

    private final Setting<Integer> delay = this.sgGeneral.add(
        new IntSetting.Builder()
            .name("delay")
            .description("Tick delay between mid-air block placements.")
            .defaultValue(2)
            .min(0)
            .sliderMax(10)
            .build()
    );

    private final Setting<Boolean> swing = this.sgGeneral.add(
        new BoolSetting.Builder()
            .name("swing")
            .description("Swing your hand when placing.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Boolean> render = this.sgRender.add(
        new BoolSetting.Builder()
            .name("render")
            .description("Render the mid-air placement target.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = this.sgRender.add(
        new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the placement block is rendered.")
            .defaultValue(ShapeMode.Both)
            .visible(this.render::get)
            .build()
    );

    private final Setting<SettingColor> sideColor = this.sgRender.add(
        new ColorSetting.Builder()
            .name("side-color")
            .description("Side color of the preview.")
            .defaultValue(new SettingColor(80, 180, 255, 30))
            .visible(() -> this.render.get() && this.shapeMode.get().sides())
            .build()
    );

    private final Setting<SettingColor> lineColor = this.sgRender.add(
        new ColorSetting.Builder()
            .name("line-color")
            .description("Line color of the preview.")
            .defaultValue(new SettingColor(80, 180, 255, 200))
            .visible(() -> this.render.get() && this.shapeMode.get().lines())
            .build()
    );

    private BlockPos targetPos;
    private int timer = 0;

    public VAirPlace() {
        super(VenomousAddon.CATEGORY, "v-air-place", "Allows placing blocks directly in mid-air on empty targets.");
    }

    @Override
    public void onActivate() {
        this.targetPos = null;
        this.timer = 0;
    }

    @Override
    public void onDeactivate() {
        this.targetPos = null;
        this.timer = 0;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null || this.mc.gameMode == null) return;

        if (this.timer > 0) {
            this.timer--;
        }

        this.targetPos = null;

        ItemStack mainHand = this.mc.player.getMainHandItem();
        ItemStack offHand = this.mc.player.getOffhandItem();
        InteractionHand hand = null;

        if (mainHand.getItem() instanceof BlockItem) {
            hand = InteractionHand.MAIN_HAND;
        } else if (offHand.getItem() instanceof BlockItem) {
            hand = InteractionHand.OFF_HAND;
        }

        if (hand == null) return;

        if (this.mc.hitResult != null && this.mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult bhr = (BlockHitResult) this.mc.hitResult;
            if (!this.mc.level.getBlockState(bhr.getBlockPos()).isAir()) {
                return;
            }
        }

        Vec3 eyePos = this.mc.player.getEyePosition();
        Vec3 lookAngle = this.mc.player.getLookAngle();
        Vec3 endPos = eyePos.add(lookAngle.scale(this.range.get()));
        BlockPos pos = BlockPos.containing(endPos);

        if (this.mc.level.getBlockState(pos).isAir() && !this.isEntityIntersecting(pos)) {
            this.targetPos = pos;

            if (this.mc.options.keyUse.isDown() && this.timer <= 0) {
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                this.mc.gameMode.useItemOn(this.mc.player, hand, hit);

                if (this.swing.get()) {
                    this.mc.player.swing(hand);
                }
                this.timer = this.delay.get();
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (this.render.get() && this.targetPos != null) {
            event.renderer.box(this.targetPos, (Color) this.sideColor.get(), (Color) this.lineColor.get(), this.shapeMode.get(), 0);
        }
    }

    private boolean isEntityIntersecting(BlockPos pos) {
        AABB box = new AABB(pos);
        return !this.mc.level.getEntitiesOfClass(Entity.class, box, e -> e != this.mc.player).isEmpty();
    }
}
