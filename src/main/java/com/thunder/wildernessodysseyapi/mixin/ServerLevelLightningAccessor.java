package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes Minecraft's protected natural-lightning target resolver.
 *
 * <p>Using the vanilla resolver preserves lightning-rod attraction and exposed
 * living-entity targeting without copying fragile POI and entity-search code.</p>
 */
@Mixin(ServerLevel.class)
public interface ServerLevelLightningAccessor {

    /** Resolves the same final target used by vanilla natural lightning. */
    @Invoker("findLightningTargetAround")
    BlockPos wildernessodysseyapi$findLightningTargetAround(BlockPos position);
}
