package com.thunder.wildernessodysseyapi.worldgen.biome;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.thunder.wildernessodysseyapi.environment.glacial.config.GlacialConfig;
import com.thunder.wildernessodysseyapi.worldgen.coast.config.CoastalWorldgenConfig;
import dev.worldgen.lithostitched.api.worldgen.biomeinjector.BiomeInjector;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Encodes the real Lithostitched injectors against biome holders, rather than checking Java source text. */
class CoastalBiomeInjectionTest {
    private final Map<ModConfigSpec.ConfigValue<?>, Object> previous = new HashMap<>();
    private MappedRegistry<Biome> biomes;

    @BeforeEach
    void registryAndConfiguration() throws ReflectiveOperationException {
        for (var value : List.of(CoastalWorldgenConfig.ENABLE_BEACH_BIOME_FAMILY,
                CoastalWorldgenConfig.ENABLE_TEMPERATE_BEACH, CoastalWorldgenConfig.ENABLE_DUNE_BEACH,
                CoastalWorldgenConfig.ENABLE_TROPICAL_BEACH, CoastalWorldgenConfig.ENABLE_ROCKY_COAST,
                CoastalWorldgenConfig.ENABLE_COLD_BEACH, CoastalWorldgenConfig.ENABLE_GLACIAL_BEACH,
                GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM)) config(value, true);
        biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        for (var key : List.of(Biomes.BEACH, Biomes.STONY_SHORE, Biomes.SNOWY_BEACH,
                ModBiomes.TEMPERATE_BEACH_KEY, ModBiomes.DUNE_BEACH_KEY, ModBiomes.TROPICAL_BEACH_KEY,
                ModBiomes.ROCKY_COAST_KEY, ModBiomes.COLD_BEACH_KEY, ModBiomes.GLACIAL_BEACH_KEY)) {
            biomes.register(key, new Biome.BiomeBuilder().temperature(0.5F).downfall(0.5F)
                    .hasPrecipitation(true).specialEffects(new BiomeSpecialEffects.Builder()
                            .fogColor(0).waterColor(0).waterFogColor(0).skyColor(0).build())
                    .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY)
                    .build(), RegistrationInfo.BUILT_IN);
        }
        biomes.freeze();
    }

    @AfterEach
    void restoreConfiguration() throws ReflectiveOperationException {
        Field cache = ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
        cache.setAccessible(true);
        for (var entry : previous.entrySet()) cache.set(entry.getKey(), entry.getValue());
    }

    private void config(ModConfigSpec.ConfigValue<?> value, Object replacement) throws ReflectiveOperationException {
        Field cache = ModConfigSpec.ConfigValue.class.getDeclaredField("cachedValue");
        cache.setAccessible(true);
        if (!previous.containsKey(value)) previous.put(value, cache.get(value));
        cache.set(value, replacement);
    }

    private Map<String, JsonObject> injectors() {
        var ops = RegistryOps.create(JsonOps.INSTANCE, HolderLookup.Provider.create(Stream.of(biomes.asLookup())));
        Map<String, JsonObject> encoded = new LinkedHashMap<>();
        BiomeCompatibilityBootstrap.registerCoastalInjectors(biomes.asLookup(), (id, injector) -> {
            assertEquals(650, injector.priority());
            assertEquals("minecraft:overworld", injector.dimension().location().toString());
            encoded.put(id.getPath(), BiomeInjector.CODEC.encodeStart(ops, injector).getOrThrow().getAsJsonObject());
        });
        return encoded;
    }

    @Test
    void allNonPolarVariantsRegisterAgainstTheirVanillaShoreSlots() {
        var actual = injectors();
        assertEquals(5, actual.size());
        for (var entry : Map.of("temperate_beach", "beach", "dune_beach", "beach", "tropical_beach", "beach",
                "rocky_coast", "stony_shore", "cold_beach", "snowy_beach").entrySet()) {
            var injector = actual.get(entry.getKey() + "_from_" + entry.getValue());
            assertEquals("wildernessodysseyapi:" + entry.getKey(), injector.get("replacement").getAsString());
            // A single holder set has the compact string form in the runtime codec.
            assertEquals("minecraft:" + entry.getValue(), injector.get("targets").getAsString());
        }
        assertEquals(6, BiomeCompatibilityBootstrap.enabledCoastalBiomes().size());
    }

    @Test
    void warmDryAndWarmHumidFiltersAreDistinctAndCodecRoundTripSafe() {
        var actual = injectors();
        JsonObject dry = actual.get("dune_beach_from_beach").getAsJsonObject("parameters");
        JsonObject humid = actual.get("tropical_beach_from_beach").getAsJsonObject("parameters");
        assertNotEquals(dry, humid);
        assertTrue(dry.has("temperature") && dry.has("humidity"));
        assertTrue(humid.has("temperature") && humid.has("humidity"));
        var ops = RegistryOps.create(JsonOps.INSTANCE, HolderLookup.Provider.create(Stream.of(biomes.asLookup())));
        for (var json : actual.values()) assertDoesNotThrow(() -> BiomeInjector.CODEC.parse(ops, json).getOrThrow());
    }

    @Test
    void optInIndividualSwitchesAndPolarDependencyControlRegistrationAndStartupExpectations() throws ReflectiveOperationException {
        config(CoastalWorldgenConfig.ENABLE_TROPICAL_BEACH, false);
        config(GlacialConfig.ENABLE_POLAR_BIOME_SYSTEM, false);
        assertEquals(4, injectors().size());
        assertFalse(BiomeCompatibilityBootstrap.enabledCoastalBiomes().contains(ModBiomes.TROPICAL_BEACH_KEY));
        assertFalse(BiomeCompatibilityBootstrap.enabledCoastalBiomes().contains(ModBiomes.GLACIAL_BEACH_KEY));
        config(CoastalWorldgenConfig.ENABLE_BEACH_BIOME_FAMILY, false);
        assertTrue(injectors().isEmpty());
        assertTrue(BiomeCompatibilityBootstrap.enabledCoastalBiomes().isEmpty());
    }
}
