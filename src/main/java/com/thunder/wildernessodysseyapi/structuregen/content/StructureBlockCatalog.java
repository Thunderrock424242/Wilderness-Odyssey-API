package com.thunder.wildernessodysseyapi.structuregen.content;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Fail-closed inventory of blocks available to an offline StructureGen run.
 *
 * <p>Implementations may capture a fully loaded NeoForge registry or read the
 * deterministic JSON snapshot produced during data generation. Classpath
 * presence alone is intentionally not treated as block availability because
 * a mod JAR does not populate Minecraft registries until its registration
 * events have completed.</p>
 */
public interface StructureBlockCatalog {

    /** Current on-disk catalog schema written by the datagen snapshot provider. */
    int SNAPSHOT_SCHEMA_VERSION = 2;

    /** Returns installed mod IDs mapped to their exact loaded versions. */
    Map<String, String> installedMods();

    /** Returns every available block keyed by its exact registry ID. */
    Map<ResourceLocation, AvailableBlockDescriptor> blocks();

    /** Returns whether the fully loaded environment contains the supplied mod ID. */
    default boolean isModAvailable(String modId) {
        return installedMods().containsKey(modId);
    }

    /** Finds an exact block registry ID without substituting a missing entry. */
    default Optional<AvailableBlockDescriptor> find(ResourceLocation id) {
        return Optional.ofNullable(blocks().get(id));
    }

    /** Classifies a registered namespace without assuming that every namespace equals a mod ID. */
    default ContentFamily family(ResourceLocation id) {
        if ("minecraft".equals(id.getNamespace())) {
            return ContentFamily.VANILLA;
        }
        if ("wildernessodysseyapi".equals(id.getNamespace())) {
            return ContentFamily.WILDERNESS_ODYSSEY;
        }
        return ContentFamily.THIRD_PARTY;
    }

    /**
     * Validates a block ID and partial state, returning an error for every unknown value.
     *
     * <p>This is the catalog's fail-closed boundary: neither an unavailable namespace nor an
     * invalid property is downgraded to a warning or silently replaced with air.</p>
     */
    default Validation validate(String blockId, Map<String, String> properties) {
        if (blockId == null) {
            return Validation.invalid("Block resource ID must not be null.");
        }
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null || !blockId.contains(":") || !id.toString().equals(blockId)) {
            return Validation.invalid("Invalid explicit block resource ID '" + blockId + "'.");
        }
        AvailableBlockDescriptor descriptor = blocks().get(id);
        if (descriptor == null) {
            return Validation.invalid("Block '" + blockId + "' is not available in this StructureGen catalog.");
        }
        if (properties == null) {
            return new Validation(Optional.of(descriptor), List.of("Properties for '" + blockId + "' must not be null."));
        }
        return new Validation(Optional.of(descriptor), descriptor.validateProperties(properties));
    }

    /** Result of resolving one requested block and its supplied state properties. */
    record Validation(Optional<AvailableBlockDescriptor> block, List<String> errors) {

        /** Creates a defensively copied validation result. */
        public Validation {
            block = Objects.requireNonNull(block, "block");
            errors = List.copyOf(errors);
        }

        /** Returns a fail-closed result for an unavailable or malformed block request. */
        public static Validation invalid(String error) {
            return new Validation(Optional.empty(), List.of(error));
        }

        /** Returns whether the ID exists and every supplied state property is valid. */
        public boolean isValid() {
            return block.isPresent() && errors.isEmpty();
        }
    }

    /** Broad content ownership boundary used by policy checks and developer reporting. */
    enum ContentFamily {
        VANILLA,
        WILDERNESS_ODYSSEY,
        THIRD_PARTY
    }
}
