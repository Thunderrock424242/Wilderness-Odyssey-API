package com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.bank;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverChannelColumn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Immutable data-pack bank palettes. Reloads publish a complete validated
 * generation; resource keys are resolved when used, never retained as holders.
 */
public final class RiverBankProfiles {
    private static final int MAX_PROFILES = 64;
    private static final int MAX_ENTRIES = 16;
    private static final Set<String> FIELDS = Set.of("priority", "biome_tags", "biomes", "bed", "plants");
    private static final Set<String> ENTRY_FIELDS = Set.of("block", "weight");
    private static volatile Snapshot active = new Snapshot(List.of());

    private RiverBankProfiles() { }

    /** Last fully accepted profile generation, safe for concurrent generation readers. */
    public static Snapshot snapshot() { return active; }

    /** Validates every resource before replacing the published generation. */
    static void reload(Map<ResourceLocation, JsonElement> resources, Predicate<ResourceLocation> beds,
                       Predicate<ResourceLocation> plants) {
        active = parse(resources, beds, plants);
    }

    static Snapshot parse(Map<ResourceLocation, JsonElement> resources, Predicate<ResourceLocation> beds,
                          Predicate<ResourceLocation> plants) {
        if (resources.size() > MAX_PROFILES) throw new IllegalArgumentException("Too many river bank profiles");
        List<Profile> profiles = new ArrayList<>();
        for (var resource : resources.entrySet()) {
            if (!resource.getValue().isJsonObject()) throw new IllegalArgumentException("Expected river bank object: " + resource.getKey());
            JsonObject json = resource.getValue().getAsJsonObject();
            rejectUnknown(json, FIELDS);
            int priority = json.has("priority") ? integer(json.get("priority"), -1000, 1000) : 0;
            List<ResourceLocation> tags = locations(json, "biome_tags");
            List<ResourceLocation> biomes = locations(json, "biomes");
            if (tags.isEmpty() && biomes.isEmpty()) throw new IllegalArgumentException("River bank profile needs biome selectors");
            profiles.add(new Profile(resource.getKey(), priority, tags, biomes,
                    palette(json, "bed", beds, true), palette(json, "plants", plants, false)));
        }
        profiles.sort(Comparator.comparingInt(Profile::priority).reversed().thenComparing(profile -> profile.id().toString()));
        return new Snapshot(profiles);
    }

    /** Resolves one biome-aware, deterministic riverbed without any neighboring chunk query. */
    public static BlockState bed(ChunkAccess chunk, BlockPos position, RiverChannelColumn.Kind kind) {
        if (!RiverBankConfig.paletteEnabled()) return chunk.getBlockState(position);
        if (kind == RiverChannelColumn.Kind.WATERFALL) return Blocks.STONE.defaultBlockState();
        Profile profile = profile(chunk, position, active);
        ResourceLocation selected = profile == null ? null : profile.bed(mix(position.asLong()));
        return selected == null ? Blocks.GRAVEL.defaultBlockState() : BuiltInRegistries.BLOCK.get(selected).defaultBlockState();
    }

    static Profile profile(ChunkAccess chunk, BlockPos position, Snapshot snapshot) {
        var biome = chunk.getNoiseBiome(QuartPos.fromBlock(position.getX()), QuartPos.fromBlock(position.getY()),
                QuartPos.fromBlock(position.getZ()));
        ResourceLocation biomeId = biome.unwrapKey().map(key -> key.location()).orElse(null);
        return snapshot.select(biomeId, tag -> biome.is(TagKey.create(Registries.BIOME, tag)));
    }

    /** Native, inert full-block beds only; tags cannot authorize fluid or entity-bearing palette entries. */
    static boolean supportedBed(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        return block == Blocks.GRAVEL || block == Blocks.CLAY || block == Blocks.MUD || block == Blocks.PACKED_MUD
                || block == Blocks.STONE || block == Blocks.ANDESITE || block == Blocks.TUFF
                || block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.SANDSTONE
                || block == Blocks.RED_SANDSTONE || block == Blocks.DIRT || block == Blocks.COARSE_DIRT
                || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE;
    }

    /** Single-block native plants; excludes saplings, double plants, fluid hosts and feature triggers. */
    static boolean supportedPlant(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        return block == Blocks.FERN || block == Blocks.SHORT_GRASS || block == Blocks.DEAD_BUSH
                || block == Blocks.BROWN_MUSHROOM || block == Blocks.RED_MUSHROOM
                || block == Blocks.DANDELION || block == Blocks.POPPY || block == Blocks.BLUE_ORCHID
                || block == Blocks.LILY_OF_THE_VALLEY || block == Blocks.AZURE_BLUET;
    }

    private static List<ResourceLocation> locations(JsonObject object, String name) {
        if (!object.has(name)) return List.of();
        JsonArray values = array(object.get(name));
        if (values.size() > MAX_ENTRIES) throw new IllegalArgumentException("Too many biome selectors");
        List<ResourceLocation> result = new ArrayList<>();
        for (JsonElement value : values) {
            ResourceLocation id = location(value);
            if (result.contains(id)) throw new IllegalArgumentException("Duplicate biome selector: " + id);
            result.add(id);
        }
        return List.copyOf(result);
    }

    private static List<WeightedBlock> palette(JsonObject object, String name, Predicate<ResourceLocation> permitted,
                                              boolean required) {
        if (!object.has(name)) {
            if (required) throw new IllegalArgumentException("Missing river bank palette: " + name);
            return List.of();
        }
        JsonArray values = array(object.get(name));
        if (values.size() > MAX_ENTRIES || required && values.isEmpty()) throw new IllegalArgumentException("Invalid river bank palette size");
        List<WeightedBlock> result = new ArrayList<>();
        for (JsonElement value : values) {
            if (!value.isJsonObject()) throw new IllegalArgumentException("Expected weighted block object");
            JsonObject entry = value.getAsJsonObject();
            rejectUnknown(entry, ENTRY_FIELDS);
            ResourceLocation id = location(entry.get("block"));
            if (!permitted.test(id)) throw new IllegalArgumentException("Unsupported river bank " + name + " block: " + id);
            if (result.stream().anyMatch(existing -> existing.block().equals(id))) throw new IllegalArgumentException("Duplicate palette block: " + id);
            int weight = entry.has("weight") ? integer(entry.get("weight"), 1, 1000) : 1;
            result.add(new WeightedBlock(id, weight));
        }
        return List.copyOf(result);
    }

    private static JsonArray array(JsonElement value) {
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Expected river bank array");
        return value.getAsJsonArray();
    }

    private static ResourceLocation location(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().length() > 256) throw new IllegalArgumentException("Expected bounded resource identifier");
        return ResourceLocation.parse(value.getAsString());
    }

    private static int integer(JsonElement value, int minimum, int maximum) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new IllegalArgumentException("Expected integer");
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || number != Math.rint(number) || number < minimum || number > maximum) {
            throw new IllegalArgumentException("River bank integer is out of range");
        }
        return (int) number;
    }

    private static void rejectUnknown(JsonObject object, Set<String> fields) {
        for (String key : object.keySet()) if (!fields.contains(key)) throw new IllegalArgumentException("Unknown river bank field: " + key);
    }

    static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    /** Immutable validated profile, selected before block resolution. */
    public record Profile(ResourceLocation id, int priority, List<ResourceLocation> biomeTags,
                          List<ResourceLocation> biomes, List<WeightedBlock> bed, List<WeightedBlock> plants) {
        public Profile {
            biomeTags = List.copyOf(biomeTags);
            biomes = List.copyOf(biomes);
            bed = List.copyOf(bed);
            plants = List.copyOf(plants);
        }
        public ResourceLocation bed(long entropy) { return choose(bed, entropy); }
        public ResourceLocation plant(long entropy) { return choose(plants, entropy); }
        private static ResourceLocation choose(List<WeightedBlock> palette, long entropy) {
            int total = 0;
            for (var entry : palette) total += entry.weight();
            if (total == 0) return null;
            int ticket = (int) Math.floorMod(entropy, (long) total);
            for (var entry : palette) {
                if (ticket < entry.weight()) return entry.block();
                ticket -= entry.weight();
            }
            throw new IllegalStateException("Validated river bank palette has no selection");
        }
    }

    public record WeightedBlock(ResourceLocation block, int weight) { }

    /** Stable priority-ordered generation; biome tag membership is supplied by the current registry. */
    public record Snapshot(List<Profile> profiles) {
        public Snapshot { profiles = List.copyOf(profiles); }
        public Profile select(ResourceLocation biome, Predicate<ResourceLocation> hasTag) {
            for (var profile : profiles) if (profile.biomes().contains(biome)
                    || profile.biomeTags().stream().anyMatch(hasTag)) return profile;
            return null;
        }
    }
}
