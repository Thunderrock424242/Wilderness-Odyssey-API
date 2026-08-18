package com.thunder.wildernessodysseyapi.vegetation.compat;

import com.thunder.wildernessodysseyapi.vegetation.api.ReactivePlantDefinition;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactivePlantRegistry;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactivePlantTrait;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Conservative built-in registrations that already have meaningful vanilla behavior. */
public final class ReactivePlantDefaults {

    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean();

    private ReactivePlantDefaults() {
    }

    /**
     * Adds sparse wet-condition opportunities to vanilla mushrooms.
     *
     * <p>The selected mushroom delegates to its existing vanilla random-tick
     * implementation. The regional scheduler supplies only an extra opportunity
     * after sustained wetness; it does not implement or accelerate spread itself.</p>
     */
    public static void bootstrap() {
        if (!BOOTSTRAPPED.compareAndSet(false, true)) {
            return;
        }
        registerMushroom(Blocks.BROWN_MUSHROOM);
        registerMushroom(Blocks.RED_MUSHROOM);
    }

    private static void registerMushroom(Block block) {
        ReactivePlantRegistry.registerIfAbsent(block, ReactivePlantDefinition.of(
                Set.of(ReactivePlantTrait.MUSHROOM, ReactivePlantTrait.MOISTURE_REACTIVE),
                context -> {
                    double opportunity = context.mushroomOpportunity();
                    double extraTickChance = Math.max(0.0, Math.min(1.0, (opportunity - 0.55) / 0.45));
                    if (context.roll(extraTickChance)) {
                        context.state().randomTick(
                                context.level(),
                                context.position(),
                                context.level().random
                        );
                    }
                    return context.state();
                }
        ));
    }
}
