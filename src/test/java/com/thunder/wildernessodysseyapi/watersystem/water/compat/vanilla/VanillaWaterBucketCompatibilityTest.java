package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.DispenserBlock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

/** Verifies exact-item registrations that vanilla water tags cannot provide. */
class VanillaWaterBucketCompatibilityTest {

    @Test
    void registersVanillaContainerBehaviorIdempotently() {
        VanillaWaterBucketCompatibility.bootstrap();
        VanillaWaterBucketCompatibility.bootstrap();

        Item waterBucket = WildernessFluidRegistry.WILDERNESS_WATER_BUCKET.get();
        assertSame(
                VanillaWaterBucketCompatibility.WILDERNESS_WATER_BUCKET_DISPENSE_BEHAVIOR,
                DispenserBlock.DISPENSER_REGISTRY.get(waterBucket)
        );
        assertSame(CauldronInteraction.FILL_WATER, CauldronInteraction.EMPTY.map().get(waterBucket));
        assertSame(CauldronInteraction.FILL_WATER, CauldronInteraction.WATER.map().get(waterBucket));
        assertSame(CauldronInteraction.FILL_WATER, CauldronInteraction.LAVA.map().get(waterBucket));
        assertSame(CauldronInteraction.FILL_WATER, CauldronInteraction.POWDER_SNOW.map().get(waterBucket));
    }
}
