package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterFluidRenderContinuity;
import net.minecraft.core.Holder;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the render comparison with registered fluids in the normal FML JUnit runtime. */
class WaterFluidRenderContinuityTest {

    private static final Map<Holder.Reference<Fluid>, List<TagKey<Fluid>>> ORIGINAL_TAGS =
            new LinkedHashMap<>();
    private static Method bindTags;

    @BeforeAll
    static void prepareWaterTags() throws ReflectiveOperationException {
        // FML JUnit registers fluids without loading a world's data pack. Bind
        // the same water tag for this fixture, and restore every holder afterward.
        bindTags = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
        bindTags.setAccessible(true);
        for (Fluid fluid : waterFluids()) {
            Holder.Reference<Fluid> holder = fluid.builtInRegistryHolder();
            List<TagKey<Fluid>> original = holder.tags().toList();
            ORIGINAL_TAGS.put(holder, original);
            List<TagKey<Fluid>> waterTags = new ArrayList<>(original);
            if (!waterTags.contains(FluidTags.WATER)) {
                waterTags.add(FluidTags.WATER);
            }
            bindTags.invoke(holder, waterTags);
        }
    }

    @AfterAll
    static void restoreTags() throws ReflectiveOperationException {
        for (var entry : ORIGINAL_TAGS.entrySet()) {
            bindTags.invoke(entry.getKey(), entry.getValue());
        }
        ORIGINAL_TAGS.clear();
    }

    @Test
    void joinsEverySourceAndFlowingWaterPairWithoutChangingGameplayIdentity() {
        assertFalse(Fluids.WATER.isSame(WildernessFluidRegistry.WILDERNESS_WATER.get()));
        for (Fluid rendered : waterFluids()) {
            for (Fluid sampled : waterFluids()) {
                assertTrue(matches(rendered, sampled),
                        () -> "Mixed water must share render heights: " + rendered + " / " + sampled);
            }
        }
        assertFalse(Fluids.WATER.isSame(WildernessFluidRegistry.WILDERNESS_WATER.get()));
    }

    @Test
    void doesNotJoinWaterToAirOrLava() {
        for (Fluid water : waterFluids()) {
            assertFalse(matches(water, Fluids.EMPTY));
            assertFalse(matches(Fluids.EMPTY, water));
            assertFalse(matches(water, Fluids.LAVA));
            assertFalse(matches(Fluids.LAVA, water));
            assertFalse(matches(water, Fluids.FLOWING_LAVA));
        }
    }

    @Test
    void preservesLavaAndEmptyFluidNativeMatches() {
        assertTrue(matches(Fluids.LAVA, Fluids.FLOWING_LAVA));
        assertTrue(matches(Fluids.EMPTY, Fluids.EMPTY));
    }

    @Test
    void preservesAnExistingRendererMatchWithoutRequiringWaterTags() {
        assertTrue(WaterFluidRenderContinuity.sameRenderedVolume(Fluids.LAVA, Fluids.EMPTY, true));
    }

    private static boolean matches(Fluid rendered, Fluid sampled) {
        return WaterFluidRenderContinuity.sameRenderedVolume(
                rendered, sampled, rendered.isSame(sampled));
    }

    private static List<Fluid> waterFluids() {
        return List.of(Fluids.WATER, Fluids.FLOWING_WATER,
                WildernessFluidRegistry.WILDERNESS_WATER.get(),
                WildernessFluidRegistry.FLOWING_WILDERNESS_WATER.get());
    }
}
