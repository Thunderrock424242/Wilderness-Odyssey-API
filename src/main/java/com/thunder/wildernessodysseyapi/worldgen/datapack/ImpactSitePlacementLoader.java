package com.thunder.wildernessodysseyapi.WorldGen.datapack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads optional datapack-provided anchor points for meteor impacts and bunkers.
 */
public class ImpactSitePlacementLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DIRECTORY = "impact_sites";

    private static final Map<ResourceKey<Level>, List<PlacementDefinition>> DEFINITIONS = new ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    public ImpactSitePlacementLoader() {
        super(GSON, DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager, ProfilerFiller profiler) {
        DEFINITIONS.clear();
        loaded = false;

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            try {
                JsonObject root = GsonHelper.convertToJsonObject(entry.getValue(), DIRECTORY);
                ResourceLocation dimensionId = ResourceLocation.tryParse(GsonHelper.getAsString(root, "dimension", "minecraft:overworld"));
                if (dimensionId == null) {
                    ModConstants.LOGGER.warn("Skipping impact site entry {} due to invalid dimension id", entry.getKey());
                    continue;
                }

                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
                BlockPos impactPos = parsePos(root, "impact");
                if (impactPos == null) {
                    ModConstants.LOGGER.warn("Skipping impact site entry {} because it lacks a valid impact position", entry.getKey());
                    continue;
                }

                BlockPos bunkerOffset = parsePos(root, "bunker_offset");
                DEFINITIONS.computeIfAbsent(dimension, dim -> new ArrayList<>())
                        .add(new PlacementDefinition(impactPos, bunkerOffset));
            } catch (Exception ex) {
                ModConstants.LOGGER.warn("Failed to parse impact site definition {}: {}", entry.getKey(), ex.getMessage());
            }
        }

        ModConstants.LOGGER.info("Loaded {} impact site anchor sets from datapacks", DEFINITIONS.values().stream().mapToInt(List::size).sum());
        loaded = true;
    }

    public static List<PlacementDefinition> get(ResourceKey<Level> dimension) {
        return DEFINITIONS.getOrDefault(dimension, List.of());
    }

    public static boolean hasConfiguredPlacements(ServerLevel level) {
        return !get(level.dimension()).isEmpty();
    }

    public static boolean isLoaded() {
        return loaded;
    }

    @Nullable
    private BlockPos parsePos(JsonObject root, String field) {
        if (!root.has(field)) {
            return null;
        }

        JsonObject obj = GsonHelper.getAsJsonObject(root, field);
        if (!obj.has("x") || !obj.has("z")) {
            return null;
        }

        int x = GsonHelper.getAsInt(obj, "x");
        int y = GsonHelper.getAsInt(obj, "y", 0);
        int z = GsonHelper.getAsInt(obj, "z");
        return new BlockPos(x, y, z);
    }

    public record PlacementDefinition(BlockPos impactPos, @Nullable BlockPos bunkerOffset) {}
}
