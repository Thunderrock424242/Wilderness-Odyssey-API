package com.wildernessodyssey.client.hardware;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads and exposes the hardware requirement thresholds declared in the bundled JSON configuration.
 */
public final class HardwareRequirementConfig {
    public static final String RESOURCE_PATH = "hardware/hardware_requirements.json";

    private static final Logger LOGGER = LogManager.getLogger("HardwareRequirementConfig");
    private static final Gson GSON = new GsonBuilder().create();

    private final EnumMap<Tier, HardwareRequirementTier> tiers;

    private HardwareRequirementConfig(EnumMap<Tier, HardwareRequirementTier> tiers) {
        this.tiers = tiers;
    }

    /**
     * Attempts to load the configuration from the mod resources.
     *
     * @return a configuration instance. When the resource is missing or malformed the instance will be empty.
     */
    public static HardwareRequirementConfig load() {
        ClassLoader loader = HardwareRequirementConfig.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(RESOURCE_PATH)) {
            if (stream == null) {
                LOGGER.warn("No hardware requirements configuration found at {}/{}", ModConstants.MOD_ID, RESOURCE_PATH);
                return new HardwareRequirementConfig(new EnumMap<>(Tier.class));
            }

            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement rootElement = GSON.fromJson(reader, JsonElement.class);
                if (!rootElement.isJsonObject()) {
                    LOGGER.error("Hardware requirement configuration root is not a JSON object");
                    return new HardwareRequirementConfig(new EnumMap<>(Tier.class));
                }

                JsonObject root = rootElement.getAsJsonObject();
                EnumMap<Tier, HardwareRequirementTier> tierMap = new EnumMap<>(Tier.class);
                for (Tier tier : Tier.values()) {
                    if (root.has(tier.configKey()) && root.get(tier.configKey()).isJsonObject()) {
                        HardwareRequirementTier requirement = parseTier(tier, root.getAsJsonObject(tier.configKey()));
                        tierMap.put(tier, requirement);
                    }
                }
                return new HardwareRequirementConfig(tierMap);
            }
        } catch (IOException | JsonParseException ex) {
            LOGGER.error("Failed to load hardware requirements configuration", ex);
            return new HardwareRequirementConfig(new EnumMap<>(Tier.class));
        }
    }

    private static HardwareRequirementTier parseTier(Tier tier, JsonObject tierObject) {
        int minCpuCores = getObjectInt(tierObject, "cpu", "cores", 0);
        long minRamMb = getLong(tierObject, "ramMB", -1L);
        long minVramMb = getLong(tierObject, "vramMB", -1L);
        String shaderPack = getString(tierObject, "shaderPack", null);

        List<String> vendorKeywords = Collections.emptyList();
        List<String> rendererKeywords = Collections.emptyList();
        if (tierObject.has("gpu") && tierObject.get("gpu").isJsonObject()) {
            JsonObject gpu = tierObject.getAsJsonObject("gpu");
            vendorKeywords = getLowercaseStringList(gpu, "vendorKeywords");
            rendererKeywords = getLowercaseStringList(gpu, "rendererKeywords");
        }

        return new HardwareRequirementTier(tier, minCpuCores, minRamMb, minVramMb, shaderPack, vendorKeywords, rendererKeywords);
    }

    private static int getObjectInt(JsonObject parent, String childKey, String field, int fallback) {
        if (!parent.has(childKey) || !parent.get(childKey).isJsonObject()) {
            return fallback;
        }
        JsonObject child = parent.getAsJsonObject(childKey);
        return getInt(child, field, fallback);
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException ex) {
            LOGGER.warn("Invalid integer for key '{}' in hardware requirements", key, ex);
            return fallback;
        }
    }

    private static long getLong(JsonObject object, String key, long fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsLong();
        } catch (NumberFormatException ex) {
            LOGGER.warn("Invalid long for key '{}' in hardware requirements", key, ex);
            return fallback;
        }
    }

    private static String getString(JsonObject object, String key, String fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) {
            return fallback;
        }
        String value = element.getAsString();
        return value != null && !value.isBlank() ? value : fallback;
    }

    private static List<String> getLowercaseStringList(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return Collections.emptyList();
        }
        JsonArray array = object.getAsJsonArray(key);
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                values.add(element.getAsString().toLowerCase(Locale.ROOT));
            }
        }
        return values;
    }

    public Optional<HardwareRequirementTier> getTier(Tier tier) {
        return Optional.ofNullable(tiers.get(tier));
    }

    public Map<Tier, HardwareRequirementTier> getTiers() {
        return Collections.unmodifiableMap(tiers);
    }

    public boolean isEmpty() {
        return tiers.isEmpty();
    }

    /**
     * Supported hardware tiers.
     */
    public enum Tier {
        LOW("low"),
        MEDIUM("medium"),
        HIGH("high");

        private final String configKey;

        Tier(String configKey) {
            this.configKey = configKey;
        }

        public String configKey() {
            return configKey;
        }
    }

    /**
     * Immutable representation of the hardware requirement thresholds for a tier.
     */
    public static final class HardwareRequirementTier {
        private final Tier tier;
        private final int minCpuCores;
        private final long minRamMb;
        private final long minVramMb;
        private final String shaderPack;
        private final List<String> gpuVendorKeywords;
        private final List<String> gpuRendererKeywords;

        private HardwareRequirementTier(Tier tier, int minCpuCores, long minRamMb, long minVramMb, String shaderPack,
                                        List<String> gpuVendorKeywords, List<String> gpuRendererKeywords) {
            this.tier = tier;
            this.minCpuCores = Math.max(0, minCpuCores);
            this.minRamMb = minRamMb;
            this.minVramMb = minVramMb;
            this.shaderPack = shaderPack;
            this.gpuVendorKeywords = List.copyOf(gpuVendorKeywords);
            this.gpuRendererKeywords = List.copyOf(gpuRendererKeywords);
        }

        public Tier tier() {
            return tier;
        }

        public int minCpuCores() {
            return minCpuCores;
        }

        public long minRamMb() {
            return minRamMb;
        }

        public long minVramMb() {
            return minVramMb;
        }

        public Optional<String> shaderPack() {
            return Optional.ofNullable(shaderPack);
        }

        public List<String> gpuVendorKeywords() {
            return gpuVendorKeywords;
        }

        public List<String> gpuRendererKeywords() {
            return gpuRendererKeywords;
        }

        public boolean hasGpuRequirement() {
            return !(gpuVendorKeywords.isEmpty() && gpuRendererKeywords.isEmpty());
        }
    }
}
