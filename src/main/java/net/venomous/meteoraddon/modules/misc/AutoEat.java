package net.venomous.meteoraddon.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent.Pre;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.venomous.meteoraddon.VenomousAddon;

public class AutoEat extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();
    private final SettingGroup sgPriority = this.settings.createGroup("Priority");
    private final SettingGroup sgBehavior = this.settings.createGroup("Behavior");

    private final Setting<Double> hungerThreshold = this.sgGeneral
        .add(new DoubleSetting.Builder()
            .name("hunger-threshold")
            .description("Eat when hunger level is at or below this value.")
            .defaultValue(10.0)
            .min(1.0)
            .max(20.0)
            .sliderMax(20.0)
            .build());

    private final Setting<Double> healthThreshold = this.sgGeneral
        .add(new DoubleSetting.Builder()
            .name("health-threshold")
            .description("Eat when health is at or below this value.")
            .defaultValue(10.0)
            .min(1.0)
            .max(20.0)
            .sliderMax(20.0)
            .build());

    private final Setting<Boolean> fillToFull = this.sgGeneral
        .add(new BoolSetting.Builder()
            .name("fill-to-full")
            .description("Continue eating until hunger is full.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> swapBack = this.sgBehavior
        .add(new BoolSetting.Builder()
            .name("swap-back")
            .description("Switch back to previous slot after eating.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> prioritizeGapples = this.sgPriority
        .add(new BoolSetting.Builder()
            .name("prioritize-gapples")
            .description("Prioritize Enchanted Golden Apples and Golden Apples.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> prioritizeBestFood = this.sgPriority
        .add(new BoolSetting.Builder()
            .name("prioritize-best-food")
            .description("Prioritize food with highest nutrition/saturation.")
            .defaultValue(true)
            .build());

    private final Setting<EatMode> eatMode = this.sgBehavior
        .add(new EnumSetting.Builder<EatMode>()
            .name("eat-mode")
            .description("When to eat.")
            .defaultValue(EatMode.Always)
            .build());

    private int previousSlot = -1;
    private ItemStack lastEatenItem = null;
    private boolean isEating = false;

    public AutoEat() {
        super(VenomousAddon.CATEGORY, "auto-eat", "Automatically eats the best food in your inventory.");
    }

    @Override
    public void onActivate() {
        this.previousSlot = -1;
        this.lastEatenItem = null;
        this.isEating = false;
    }

    @Override
    public void onDeactivate() {
        if (this.isEating) {
            this.stopEating();
        }
        this.previousSlot = -1;
        this.lastEatenItem = null;
        this.isEating = false;
    }

    @EventHandler
    private void onTick(Pre event) {
        if (this.mc.player == null || this.mc.level == null) {
            return;
        }

        // Check if we finished eating
        if (this.isEating && this.lastEatenItem != null) {
            ItemStack mainHand = this.mc.player.getMainHandItem();
            ItemStack offhand = this.mc.player.getOffhandItem();
            
            // Check if the item we were eating is no longer in use (finished eating)
            if (!mainHand.is(this.lastEatenItem.getItem()) && !offhand.is(this.lastEatenItem.getItem())) {
                this.onEatFinished();
            }
        }

        // Check if we should eat
        if (this.shouldEat()) {
            this.eatIfNecessary();
        }
    }

    private boolean shouldEat() {
        if (this.mc.player == null) return false;

        // Don't eat if already eating and not fillToFull
        if (this.isEating && !this.fillToFull.get()) {
            return false;
        }

        // Check eat mode
        if (this.eatMode.get() == EatMode.Always) {
            // Check hunger
            int foodLevel = this.mc.player.getFoodData().getFoodLevel();
            if (foodLevel <= this.hungerThreshold.get()) {
                return true;
            }
            // Check health
            float health = this.mc.player.getHealth();
            if (health <= this.healthThreshold.get()) {
                return true;
            }
        } else if (this.eatMode.get() == EatMode.HungerOnly) {
            int foodLevel = this.mc.player.getFoodData().getFoodLevel();
            if (foodLevel <= this.hungerThreshold.get()) {
                return true;
            }
        } else if (this.eatMode.get() == EatMode.HealthOnly) {
            float health = this.mc.player.getHealth();
            if (health <= this.healthThreshold.get()) {
                return true;
            }
        }

        // If fillToFull and currently eating, continue eating
        if (this.isEating && this.fillToFull.get() && this.mc.player.getFoodData().needsFood()) {
            return true;
        }

        return false;
    }

    private void eatIfNecessary() {
        if (this.mc.player == null) return;

        // Check if current hand has edible food
        if (this.isEdible(this.mc.player.getMainHandItem())) {
            this.startEating(InteractionHand.MAIN_HAND);
            return;
        }
        if (this.isEdible(this.mc.player.getOffhandItem())) {
            this.startEating(InteractionHand.OFF_HAND);
            return;
        }

        // Find best food in hotbar
        int foodSlot = this.findBestFoodSlot();
        if (foodSlot != -1) {
            if (this.swapBack.get()) {
                this.previousSlot = this.mc.player.getInventory().getSelectedSlot();
            }

            this.lastEatenItem = this.mc.player.getInventory().getItem(foodSlot).copy();
            this.isEating = true;

            this.mc.player.getInventory().setSelectedSlot(foodSlot);
            this.startEating(InteractionHand.MAIN_HAND);
        }
    }

    private void startEating(InteractionHand hand) {
        this.isEating = true;
        this.mc.options.keyUse.setDown(true);
    }

    private void stopEating() {
        this.isEating = false;
        this.mc.options.keyUse.setDown(false);
        
        if (this.swapBack.get() && this.previousSlot != -1) {
            this.mc.player.getInventory().setSelectedSlot(this.previousSlot);
            this.previousSlot = -1;
        }
        this.lastEatenItem = null;
    }

    private void onEatFinished() {
        if (this.isEating) {
            boolean shouldContinue = this.shouldEat();
            if (!shouldContinue) {
                this.stopEating();
            }
        }
    }

    private boolean isEdible(ItemStack stack) {
        if (stack.isEmpty()) return false;
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food != null;
    }

    private int findBestFoodSlot() {
        if (this.mc.player == null) return -1;

        int bestSlot = -1;
        FoodProperties bestFood = null;
        Item bestItem = null;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = this.mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) continue;

            // Prioritize Enchanted Golden Apple
            if (this.prioritizeGapples.get() && item == Items.ENCHANTED_GOLDEN_APPLE) {
                return i;
            }

            // Prioritize Golden Apple
            if (this.prioritizeGapples.get() && item == Items.GOLDEN_APPLE) {
                if (bestItem == null || bestItem != Items.ENCHANTED_GOLDEN_APPLE) {
                    bestSlot = i;
                    bestFood = food;
                    bestItem = item;
                }
                continue;
            }

            // Prioritize best nutrition/saturation
            if (this.prioritizeBestFood.get() && bestFood != null) {
                if (food.nutrition() > bestFood.nutrition() || 
                    (food.nutrition() == bestFood.nutrition() && food.saturation() > bestFood.saturation())) {
                    bestSlot = i;
                    bestFood = food;
                    bestItem = item;
                }
            } else if (bestFood == null) {
                bestSlot = i;
                bestFood = food;
                bestItem = item;
            }
        }

        return bestSlot;
    }

    private enum EatMode {
        Always,
        HungerOnly,
        HealthOnly;
    }

    @Override
    public String getInfoString() {
        if (this.isEating) {
            return "eating";
        }
        if (this.mc.player != null) {
            return this.mc.player.getFoodData().getFoodLevel() + " hunger";
        }
        return "idle";
    }
}
