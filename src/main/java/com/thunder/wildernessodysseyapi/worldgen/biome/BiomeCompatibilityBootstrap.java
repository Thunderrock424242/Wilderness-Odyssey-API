package com.thunder.wildernessodysseyapi.worldgen.biome;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.glacial.GlacialBiomeManager;
import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import com.thunder.wildernessodysseyapi.worldgen.coast.config.CoastalWorldgenConfig;
import dev.worldgen.lithostitched.api.event.AddBiomeInjectorsEvent;
import dev.worldgen.lithostitched.api.registry.LithostitchedRegistries;
import dev.worldgen.lithostitched.api.worldgen.biomeinjector.BiomeInjector;
import dev.worldgen.lithostitched.api.worldgen.biomeinjector.ParameterBuilder;
import dev.worldgen.lithostitched.impl.worldgen.biomeinjector.region.Region;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Registers Wilderness Odyssey biome placement through Lithostitched.
 *
 * <p>The custom regions and one unconditional anomaly injector are data-driven
 * resources. That injector keeps Lithostitched's registry non-empty so its public
 * event is invoked; code owns the remaining config-sensitive injectors while each
 * server builds its biome source. Replacements are constrained to their matching
 * Lithostitched region and therefore never rewrite the target vanilla biomes
 * globally.</p>
 */
public final class BiomeCompatibilityBootstrap {
    // Lithostitched evaluates lower numbers first. Region-scoped replacements
    // should win over default-priority global replacements only where they match.
    private static final int REGION_REPLACEMENT_PRIORITY = 500;
    private static final int COASTAL_REPLACEMENT_PRIORITY = 650;
    private static final ResourceKey<Region> ANOMALY_REGION = regionKey("anomaly_overworld");
    private static final ResourceKey<Region> POLAR_GLACIAL_REGION = regionKey("polar_glacial_region");
    private static final ResourceKey<BiomeInjector> DATA_BOOTSTRAP_INJECTOR = ResourceKey.create(
            LithostitchedRegistries.BIOME_INJECTOR,
            id("anomaly_forest_from_forest")
    );

    private static boolean worldgenRegistered;
    private static boolean runtimeInitialized;

    private BiomeCompatibilityBootstrap() {
    }

    /** Registers Lithostitched listeners early enough to receive server biome-source events. */
    public static void registerWorldgen() {
        if (worldgenRegistered) {
            return;
        }
        worldgenRegistered = true;
        AddBiomeInjectorsEvent.EVENT.register(BiomeCompatibilityBootstrap::addBiomeInjectors);
        NeoForge.EVENT_BUS.addListener(BiomeCompatibilityBootstrap::diagnoseAppliedBiomeInjectors);
        ModConstants.LOGGER.info(
                "Registered Lithostitched biome-placement listeners; active biome-source application "
                        + "will be verified when the server finishes starting."
        );
    }

    /** Starts runtime-only biome services after normal NeoForge registration completes. */
    public static void initializeRuntime() {
        if (runtimeInitialized) {
            return;
        }
        runtimeInitialized = true;

        if (ModList.get().isLoaded("biolith")) {
            ModConstants.LOGGER.info("Biolith detected alongside Lithostitched biome placement.");
        }

        GlacialBiomeManager.bootstrap();
    }

    private static void addBiomeInjectors(
            RegistryAccess registryAccess,
            BiConsumer<ResourceLocation, BiomeInjector> registrar
    ) {
        HolderLookup.RegistryLookup<Biome> biomes = registryAccess.lookupOrThrow(Registries.BIOME);

        // The vanilla forest target is the data-driven registry bootstrap. Keep
        // the remaining unconditional anomaly targets on the existing event path.
        registerTargets(
                biomes,
                registrar,
                ANOMALY_REGION,
                ModBiomes.ANOMALY_FOREST_KEY,
                Biomes.FLOWER_FOREST,
                Biomes.BIRCH_FOREST,
                Biomes.OLD_GROWTH_BIRCH_FOREST,
                Biomes.DARK_FOREST
        );

        registerCoastalInjectors(biomes, registrar);

        if (!GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM.get()) {
            return;
        }
        if (GlacialConfig.ENABLE_ICEBERG_COAST.get()) {
            registerTargets(
                    biomes,
                    registrar,
                    POLAR_GLACIAL_REGION,
                    ModBiomes.ICEBERG_COAST_KEY,
                    Biomes.FROZEN_OCEAN,
                    Biomes.DEEP_FROZEN_OCEAN
            );
        }
        if (CoastalWorldgenConfig.ENABLE_BEACH_BIOME_FAMILY.get()
                && CoastalWorldgenConfig.ENABLE_GLACIAL_BEACH.get()) {
            registerTarget(
                    biomes,
                    registrar,
                    ModBiomes.GLACIAL_BEACH_KEY,
                    Biomes.SNOWY_BEACH,
                    REGION_REPLACEMENT_PRIORITY,
                    ParameterBuilder.create().region(POLAR_GLACIAL_REGION)
            );
        }
        if (GlacialConfig.ENABLE_GLACIAL_MELTWATER_VALLEY.get()) {
            registerTargets(
                    biomes,
                    registrar,
                    POLAR_GLACIAL_REGION,
                    ModBiomes.GLACIAL_MELTWATER_VALLEY_KEY,
                    Biomes.FROZEN_RIVER
            );
        }
        if (GlacialConfig.ENABLE_GLACIAL_BASIN.get()) {
            registerTargets(
                    biomes,
                    registrar,
                    POLAR_GLACIAL_REGION,
                    ModBiomes.POLAR_GLACIAL_BASIN_KEY,
                    Biomes.ICE_SPIKES,
                    Biomes.GROVE
            );
        }
        if (GlacialConfig.ENABLE_GLACIAL_HIGHLANDS.get()) {
            registerTargets(
                    biomes,
                    registrar,
                    POLAR_GLACIAL_REGION,
                    ModBiomes.GLACIAL_HIGHLANDS_KEY,
                    Biomes.SNOWY_SLOPES,
                    Biomes.FROZEN_PEAKS,
                    Biomes.JAGGED_PEAKS
            );
        }
        if (GlacialConfig.ENABLE_POLAR_ICE_SHEET.get()) {
            registerTargets(
                    biomes,
                    registrar,
                    POLAR_GLACIAL_REGION,
                    ModBiomes.POLAR_ICE_SHEET_KEY,
                    Biomes.SNOWY_PLAINS
            );
        }
    }

    /**
     * Verifies the read-only biome-source result after Lithostitched has applied its injectors.
     *
     * <p>This diagnostic deliberately inspects the active generator instead of treating event
     * listener registration as proof that a biome became locatable.</p>
     */
    private static void diagnoseAppliedBiomeInjectors(ServerStartedEvent event) {
        List<ResourceKey<Biome>> expectedPolarBiomes = enabledPolarBiomes();
        if (expectedPolarBiomes.isEmpty()) {
            ModConstants.LOGGER.info(
                    "No polar biome injectors are enabled; active Overworld biome-source verification skipped."
            );
            return;
        }

        Set<ResourceKey<Biome>> possibleBiomes = event.getServer()
                .overworld()
                .getChunkSource()
                .getGenerator()
                .getBiomeSource()
                .possibleBiomes()
                .stream()
                .flatMap(holder -> holder.unwrapKey().stream())
                .collect(Collectors.toSet());
        List<ResourceLocation> missingBiomes = expectedPolarBiomes.stream()
                .filter(key -> !possibleBiomes.contains(key))
                .map(ResourceKey::location)
                .toList();
        boolean dataBootstrapLoaded = event.getServer()
                .registryAccess()
                .lookupOrThrow(LithostitchedRegistries.BIOME_INJECTOR)
                .get(DATA_BOOTSTRAP_INJECTOR)
                .isPresent();

        if (missingBiomes.isEmpty()) {
            ModConstants.LOGGER.info(
                    "Lithostitched biome injectors applied to the active Overworld biome source: "
                            + "all {} enabled polar biomes are present among {} possible biomes "
                            + "(data bootstrap loaded: {}).",
                    expectedPolarBiomes.size(),
                    possibleBiomes.size(),
                    dataBootstrapLoaded
            );
            return;
        }

        ModConstants.LOGGER.warn(
                "Lithostitched listener registration did not produce a complete active Overworld biome source; "
                        + "missing {} of {} enabled polar biomes: {} (data bootstrap loaded: {}).",
                missingBiomes.size(),
                expectedPolarBiomes.size(),
                missingBiomes,
                dataBootstrapLoaded
        );
    }

    private static List<ResourceKey<Biome>> enabledPolarBiomes() {
        if (!GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM.get()) {
            return List.of();
        }
        return GlacialBiomeManager.coastToInterior().stream()
                .filter(BiomeCompatibilityBootstrap::isGenerationEnabled)
                .map(GlacialBiomeManager.Family::biomeKey)
                .toList();
    }

    private static boolean isGenerationEnabled(GlacialBiomeManager.Family family) {
        return switch (family) {
            case ICEBERG_COAST -> GlacialConfig.ENABLE_ICEBERG_COAST.get();
            case MELTWATER_VALLEY -> GlacialConfig.ENABLE_GLACIAL_MELTWATER_VALLEY.get();
            case GLACIAL_BASIN -> GlacialConfig.ENABLE_GLACIAL_BASIN.get();
            case GLACIAL_HIGHLANDS -> GlacialConfig.ENABLE_GLACIAL_HIGHLANDS.get();
            case POLAR_ICE_SHEET -> GlacialConfig.ENABLE_POLAR_ICE_SHEET.get();
        };
    }

    private static void registerCoastalInjectors(
            HolderLookup.RegistryLookup<Biome> biomes,
            BiConsumer<ResourceLocation, BiomeInjector> registrar
    ) {
        if (!CoastalWorldgenConfig.ENABLE_BEACH_BIOME_FAMILY.get()) {
            return;
        }
        if (CoastalWorldgenConfig.ENABLE_TEMPERATE_BEACH.get()) {
            registerTarget(
                    biomes,
                    registrar,
                    ModBiomes.TEMPERATE_BEACH_KEY,
                    Biomes.BEACH,
                    COASTAL_REPLACEMENT_PRIORITY,
                    ParameterBuilder.create().climateMax(
                            BiomeInjector.ClimateParameter.TEMPERATURE, 0.35)
            );
        }
        if (CoastalWorldgenConfig.ENABLE_DUNE_BEACH.get()) {
            registerTarget(
                    biomes,
                    registrar,
                    ModBiomes.DUNE_BEACH_KEY,
                    Biomes.BEACH,
                    COASTAL_REPLACEMENT_PRIORITY,
                    ParameterBuilder.create()
                            .climateMin(BiomeInjector.ClimateParameter.TEMPERATURE, 0.35)
                            .climateMax(BiomeInjector.ClimateParameter.HUMIDITY, 0.10)
            );
        }
        if (CoastalWorldgenConfig.ENABLE_TROPICAL_BEACH.get()) {
            registerTarget(
                    biomes,
                    registrar,
                    ModBiomes.TROPICAL_BEACH_KEY,
                    Biomes.BEACH,
                    COASTAL_REPLACEMENT_PRIORITY,
                    ParameterBuilder.create()
                            .climateMin(BiomeInjector.ClimateParameter.TEMPERATURE, 0.35)
                            .climateMin(BiomeInjector.ClimateParameter.HUMIDITY, 0.10)
            );
        }
        if (CoastalWorldgenConfig.ENABLE_ROCKY_COAST.get()) {
            registerTarget(
                    biomes,
                    registrar,
                    ModBiomes.ROCKY_COAST_KEY,
                    Biomes.STONY_SHORE,
                    COASTAL_REPLACEMENT_PRIORITY,
                    ParameterBuilder.create().climateRange(
                            BiomeInjector.ClimateParameter.TEMPERATURE, -2.0, 2.0)
            );
        }
        if (CoastalWorldgenConfig.ENABLE_COLD_BEACH.get()) {
            registerTarget(
                    biomes,
                    registrar,
                    ModBiomes.COLD_BEACH_KEY,
                    Biomes.SNOWY_BEACH,
                    COASTAL_REPLACEMENT_PRIORITY,
                    ParameterBuilder.create().climateRange(
                            BiomeInjector.ClimateParameter.TEMPERATURE, -2.0, 2.0)
            );
        }
    }

    private static void registerTarget(
            HolderLookup.RegistryLookup<Biome> biomes,
            BiConsumer<ResourceLocation, BiomeInjector> registrar,
            ResourceKey<Biome> replacement,
            ResourceKey<Biome> target,
            int priority,
            ParameterBuilder parameters
    ) {
        ResourceLocation injectorId = id(replacement.location().getPath()
                + "_from_" + target.location().getPath());
        BiomeInjector injector = BiomeInjector.builder(Level.OVERWORLD)
                .priority(priority)
                .replacePartially(
                        biomes.getOrThrow(target),
                        biomes.getOrThrow(replacement),
                        parameters
                );
        registrar.accept(injectorId, injector);
    }

    @SafeVarargs
    private static void registerTargets(
            HolderLookup.RegistryLookup<Biome> biomes,
            BiConsumer<ResourceLocation, BiomeInjector> registrar,
            ResourceKey<Region> region,
            ResourceKey<Biome> replacement,
            ResourceKey<Biome>... targets
    ) {
        for (ResourceKey<Biome> target : targets) {
            ResourceLocation injectorId = id(replacement.location().getPath()
                    + "_from_" + target.location().getPath());
            BiomeInjector injector = BiomeInjector.builder(Level.OVERWORLD)
                    .priority(REGION_REPLACEMENT_PRIORITY)
                    .replacePartially(
                            biomes.getOrThrow(target),
                            biomes.getOrThrow(replacement),
                            ParameterBuilder.create().region(region)
                    );
            registrar.accept(injectorId, injector);
        }
    }

    private static ResourceKey<Region> regionKey(String path) {
        return ResourceKey.create(LithostitchedRegistries.REGION, id(path));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path);
    }
}
