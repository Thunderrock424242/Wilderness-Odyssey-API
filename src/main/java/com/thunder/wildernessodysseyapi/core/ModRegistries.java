package com.thunder.wildernessodysseyapi.core;

import com.thunder.wildernessodysseyapi.anomaly.registry.AnomalyBlocks;
import com.thunder.wildernessodysseyapi.cryo.block.CryoTubeBlock;
import com.thunder.wildernessodysseyapi.effect.SimpleStatusEffect;
import com.thunder.wildernessodysseyapi.item.ModCreativeTabs;
import com.thunder.wildernessodysseyapi.item.ModItems;
import com.thunder.wildernessodysseyapi.item.ModSoundEvents;
import com.thunder.wildernessodysseyapi.lorebook.loot.ModLootConditions;
import com.thunder.wildernessodysseyapi.lorebook.loot.ModLootFunctions;
import com.thunder.wildernessodysseyapi.meteor.worldgen.MeteorFeature;
import com.thunder.wildernessodysseyapi.radiation.RadiationEffect;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlockEntities;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlocks;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftWorldgen;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.worldgen.processor.ModProcessors;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Owns the mod-level deferred registers and delegates feature registry setup.
 *
 * <p>Deferred registers are attached to the mod event bus so NeoForge creates
 * objects during the correct registry phase instead of during class loading.</p>
 */
public final class ModRegistries {

    private static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, MOD_ID);

    /** Radiation effect applied while a player occupies a radiation zone. */
    public static final DeferredHolder<MobEffect, RadiationEffect> RADIATION_EFFECT =
            MOB_EFFECTS.register("radiation", RadiationEffect::new);
    /** Cold-stress effect used by the cloak breathing system. */
    public static final DeferredHolder<MobEffect, SimpleStatusEffect> CRYO_SHAKES_EFFECT =
            MOB_EFFECTS.register("cryo_shakes", () -> SimpleStatusEffect.harmful(0x8BD7FF));
    /** Low-oxygen effect used by Echo environments and cloak feedback. */
    public static final DeferredHolder<MobEffect, SimpleStatusEffect> ECHO_HYPOXIA_EFFECT =
            MOB_EFFECTS.register("echo_hypoxia", () -> SimpleStatusEffect.harmful(0x6A37C8));
    /** Temporal desynchronization effect used by rift-related gameplay. */
    public static final DeferredHolder<MobEffect, SimpleStatusEffect> DESYNCED_EFFECT =
            MOB_EFFECTS.register("desynced", () -> SimpleStatusEffect.harmful(0xB24CFF));
    /** Meteor feature type referenced by the configured and placed feature data. */
    public static final DeferredHolder<Feature<?>, MeteorFeature> METEOR_IMPACT_FEATURE =
            FEATURES.register("meteor_impact", MeteorFeature::new);

    private ModRegistries() {
    }

    /**
     * Attaches every feature registry to NeoForge's mod event bus.
     *
     * @param modEventBus the bus that owns registry lifecycle events
     */
    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
        FEATURES.register(modEventBus);
        ModProcessors.PROCESSORS.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModEntities.register(modEventBus);
        ModLootFunctions.LOOT_FUNCTIONS.register(modEventBus);
        ModLootConditions.LOOT_CONDITIONS.register(modEventBus);
        TemporalRiftBlocks.register(modEventBus);
        TemporalRiftBlockEntities.register(modEventBus);
        TemporalRiftWorldgen.register(modEventBus);
        AnomalyBlocks.register(modEventBus);
        CryoTubeBlock.register(modEventBus);
        ModItems.register(modEventBus);
        ModSoundEvents.register(modEventBus);
        WildernessFluidRegistry.register(modEventBus);
    }
}
