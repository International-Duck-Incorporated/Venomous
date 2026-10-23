package net.venomous.meteoraddon.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.venomous.meteoraddon.modules.movement.NoJumpDelay;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    /**
     * The vanilla jump cooldown, ticked down once per tick and set to ten when a jump starts.
     * {@link NoJumpDelay} clamps it every tick to its setting.
     */
    @Accessor("noJumpDelay")
    int venomous$getNoJumpDelay();

    @Accessor("noJumpDelay")
    void venomous$setNoJumpDelay(int delay);
}
