package com.thunder.wildernessodysseyapi.developmentstudio.structure;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.modpack.structure.ModpackStructureRegistry;
import com.thunder.wildernessodysseyapi.worldgen.structure.NBTStructurePlacer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Allowlisted internal fixtures plus preview-only templates from the real modpack registry. */
public final class StudioStructureRegistry {
    public static final ResourceLocation LAB_FIXTURE = id("development_studio_lab_fixture");
    public static final ResourceLocation TEST_SHELTER = id("test_shelter");

    private static final Map<ResourceLocation, StudioStructureDefinition> INTERNAL = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private StudioStructureRegistry() {
    }

    /** Registers deterministic internal templates; only the 5x5 fixture may mutate the lab. */
    public static synchronized void bootstrapDefaults() {
        if (bootstrapped) {
            return;
        }
        bootstrapped = true;
        register(new StudioStructureDefinition(
                LAB_FIXTURE, "Studio Lab Fixture", true, new NBTStructurePlacer(LAB_FIXTURE)
        ));
        register(new StudioStructureDefinition(
                TEST_SHELTER, "StructureGen Test Shelter", false, new NBTStructurePlacer(TEST_SHELTER)
        ));
    }

    public static synchronized void register(StudioStructureDefinition definition) {
        if (INTERNAL.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalStateException("Duplicate Studio structure id: " + definition.id());
        }
    }

    /** Resolves only registered internal or loaded modpack templates. */
    public static Optional<StudioStructureDefinition> get(ResourceLocation id) {
        bootstrapDefaults();
        StudioStructureDefinition internal = INTERNAL.get(id);
        if (internal != null) {
            return Optional.of(internal);
        }
        return ModpackStructureRegistry.get(id).map(entry -> new StudioStructureDefinition(
                entry.id(), entry.id().getPath(), false, entry.placer()
        ));
    }

    /** Builds the live preview catalog from real template sizes. */
    public static List<StudioStructureOption> options(ServerLevel level) {
        bootstrapDefaults();
        List<StudioStructureDefinition> definitions = new ArrayList<>(INTERNAL.values());
        ModpackStructureRegistry.entries().forEach(entry -> definitions.add(new StudioStructureDefinition(
                entry.id(), entry.id().getPath(), false, entry.placer()
        )));
        return definitions.stream()
                .map(definition -> new StudioStructureOption(
                        definition.id(),
                        definition.displayName(),
                        definition.placer().peekSize(level),
                        definition.labPlaceable()
                ))
                .filter(option -> option.size().getX() > 0 && option.size().getY() > 0 && option.size().getZ() > 0)
                .filter(option -> option.size().getX() <= 512
                        && option.size().getY() <= 512
                        && option.size().getZ() <= 512)
                .limit(128)
                .toList();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path);
    }
}
