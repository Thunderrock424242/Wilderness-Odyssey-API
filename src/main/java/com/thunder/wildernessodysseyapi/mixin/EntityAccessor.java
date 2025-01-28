package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The interface Entity accessor.
 */
@Mixin(Entity.class)
public interface EntityAccessor {
    /**
     * Gets level.
     *
     * @return the level
     */
    @Accessor("level")
    Level getLevel();
}
