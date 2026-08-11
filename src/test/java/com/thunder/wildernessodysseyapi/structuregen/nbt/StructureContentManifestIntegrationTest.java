package com.thunder.wildernessodysseyapi.structuregen.nbt;

import com.google.gson.JsonObject;
import com.thunder.wildernessodysseyapi.structuregen.blueprint.BlueprintExporter;
import com.thunder.wildernessodysseyapi.structuregen.comparison.StructureComparator;
import com.thunder.wildernessodysseyapi.structuregen.comparison.StructureComparisonReport;
import com.thunder.wildernessodysseyapi.structuregen.content.ContentManifestStatus;
import com.thunder.wildernessodysseyapi.structuregen.content.RejectedMaterialCandidate;
import com.thunder.wildernessodysseyapi.structuregen.content.ResolvedMaterial;
import com.thunder.wildernessodysseyapi.structuregen.content.StructureContentManifest;
import com.thunder.wildernessodysseyapi.structuregen.inspection.StructureInspectionReport;
import com.thunder.wildernessodysseyapi.structuregen.inspection.StructureInspector;
import com.thunder.wildernessodysseyapi.structuregen.inspection.StructureReportWriter;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlock;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureBlockState;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureModel;
import com.thunder.wildernessodysseyapi.structuregen.model.StructurePosition;
import com.thunder.wildernessodysseyapi.structuregen.model.StructureSize;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies content-policy provenance across NBT, semantic comparison, and reports. */
class StructureContentManifestIntegrationTest {

    @TempDir
    Path tempDirectory;

    @Test
    void writerReaderRetainsManifestAndComparatorDetectsPolicyDifferences() throws IOException {
        StructureModel authored = model(manifest(true));
        CompoundTag encoded = new MinecraftStructureNbtWriter().compile(authored);
        CompoundTag encodedManifest = encoded.getCompound("structuregen").getCompound("contentManifest");
        StructureModel reread = new MinecraftStructureNbtReader().read(encoded, "fallback_name");

        assertEquals(authored.contentManifest(), reread.contentManifest());
        assertEquals(StructureContentManifest.CURRENT_SCHEMA_VERSION,
                encodedManifest.getInt("schemaVersion"));
        assertTrue(encodedManifest.contains("allowInstalledModBlocks", Tag.TAG_BYTE));
        assertEquals("verified", encodedManifest.getString("provenanceStatus"));
        assertTrue(encodedManifest.contains("preferredDecorativeMods", Tag.TAG_LIST));
        assertTrue(encodedManifest.contains("requiredMods", Tag.TAG_LIST));
        assertTrue(encodedManifest.contains("enabledFunctionalSystems", Tag.TAG_LIST));
        assertTrue(encodedManifest.contains("resolvedMaterials", Tag.TAG_LIST));
        assertEquals(ContentManifestStatus.VERIFIED, reread.contentManifest().provenanceStatus());
        assertEquals("supplementaries", reread.contentManifest().resolvedMaterials().getFirst().sourceNamespace());

        StructureComparisonReport roundTrip = new StructureComparator().compare(authored, reread);
        assertTrue(roundTrip.semanticallyMatches(), () -> new StructureComparator().format(roundTrip));

        StructureComparisonReport changedPolicy = new StructureComparator().compare(
                authored, copyWithManifest(reread, manifest(false))
        );
        assertFalse(changedPolicy.semanticallyMatches());
        assertFalse(changedPolicy.structureMetadataMatches());
        assertTrue(changedPolicy.details().stream().anyMatch(detail -> detail.contains("content manifest")));

        StructureComparisonReport changedStatus = new StructureComparator().compare(
                authored,
                copyWithManifest(reread, withStatus(reread.contentManifest(), ContentManifestStatus.PARTIAL))
        );
        assertFalse(changedStatus.semanticallyMatches());
        assertFalse(changedStatus.structureMetadataMatches());
    }

    @Test
    void inspectorSeparatesRequiredModIdsFromUsedNamespacesAndReportsResolutionDetails() throws IOException {
        StructureModel reread = new MinecraftStructureNbtReader().read(
                new MinecraftStructureNbtWriter().compile(model(manifest(true))), "fallback_name"
        );
        StructureInspectionReport report = new StructureInspector().inspect(
                tempDirectory.resolve("content-provenance.nbt"), reread
        );

        assertEquals(ContentManifestStatus.VERIFIED, report.contentManifestStatus());
        assertEquals(StructureContentManifest.CURRENT_SCHEMA_VERSION, report.contentManifestSchemaVersion());
        assertEquals(List.of("create", "supplementaries_core"), report.requiredMods());
        assertEquals(List.of("create", "supplementaries"), report.externalNamespacesUsed());
        assertEquals(List.of("wildernessodysseyapi:powered_bunker_grid"), report.enabledFunctionalSystems());
        assertTrue(report.resolvedMaterials().getFirst().fallbackAvailable());

        StructureReportWriter.ReportPaths paths = new StructureReportWriter().write(
                report, tempDirectory.resolve("reports"), "content-provenance"
        );
        String text = Files.readString(paths.text());
        String json = Files.readString(paths.json());

        assertTrue(text.contains("Content manifest status: verified (schemaVersion 1)"));
        assertTrue(text.contains("Required external mod IDs:\n    - create\n    - supplementaries_core"));
        assertTrue(text.contains("External namespaces used:\n    - create\n    - supplementaries"));
        assertTrue(text.contains("Explicitly enabled functional systems:\n"
                + "    - wildernessodysseyapi:powered_bunker_grid"));
        assertTrue(text.contains("roof_trim -> supplementaries:awning"));
        assertTrue(text.contains("fallback available: yes"));
        assertTrue(text.contains("skipped create:copycat_panel: preferred candidate state was unavailable"));
        assertTrue(json.contains("\"contentManifestStatus\": \"VERIFIED\""));
        assertTrue(json.contains("\"externalNamespacesUsed\""));
        assertTrue(json.contains("\"sourceNamespace\": \"supplementaries\""));
    }

    @Test
    void malformedManifestRemainsReadableButIsMarkedPartial() throws IOException {
        CompoundTag encoded = new MinecraftStructureNbtWriter().compile(model(manifest(true)));
        CompoundTag encodedManifest = encoded.getCompound("structuregen").getCompound("contentManifest");
        encodedManifest.remove("requiredMods");

        ListTag materials = encodedManifest.getList("resolvedMaterials", Tag.TAG_COMPOUND);
        CompoundTag material = materials.getCompound(0);
        material.remove("source");
        CompoundTag rejection = material.getList("rejectedCandidates", Tag.TAG_COMPOUND).getCompound(0);
        rejection.putInt("reason", 7);

        StructureModel reread = new MinecraftStructureNbtReader().read(encoded, "fallback_name");

        assertEquals(ContentManifestStatus.PARTIAL, reread.contentManifest().provenanceStatus());
        assertEquals(StructureContentManifest.CURRENT_SCHEMA_VERSION, reread.contentManifest().schemaVersion());
        assertEquals(List.of("supplementaries", "create"),
                reread.contentManifest().preferredDecorativeMods());
        assertEquals(List.of(), reread.contentManifest().requiredMods());
        assertEquals("roof_trim", reread.contentManifest().resolvedMaterials().getFirst().role());
        assertEquals("", reread.contentManifest().resolvedMaterials().getFirst().source());
        assertEquals("create:copycat_panel", reread.contentManifest().resolvedMaterials().getFirst()
                .rejectedCandidates().getFirst().blockId());
        assertEquals("", reread.contentManifest().resolvedMaterials().getFirst()
                .rejectedCandidates().getFirst().reason());
        assertTrue(reread.unsupportedFields().contains("structuregen.contentManifest.requiredMods"));
        assertTrue(reread.unsupportedFields().contains(
                "structuregen.contentManifest.resolvedMaterials[0].source"
        ));
        assertTrue(reread.unsupportedFields().contains(
                "structuregen.contentManifest.resolvedMaterials[0].rejectedCandidates[0].reason"
        ));

        CompoundTag rewritten = new MinecraftStructureNbtWriter().compile(reread);
        CompoundTag rewrittenManifest = rewritten.getCompound("structuregen").getCompound("contentManifest");
        assertEquals("partial", rewrittenManifest.getString("provenanceStatus"));
        StructureModel partialRoundTrip = new MinecraftStructureNbtReader().read(rewritten, "fallback_name");
        assertEquals(ContentManifestStatus.PARTIAL,
                partialRoundTrip.contentManifest().provenanceStatus());
        assertEquals(reread.contentManifest(), partialRoundTrip.contentManifest());
        StructureComparisonReport partialComparison = new StructureComparator().compare(reread, partialRoundTrip);
        assertTrue(partialComparison.semanticallyMatches(),
                () -> new StructureComparator().format(partialComparison));

        JsonObject partialExport = new BlueprintExporter().toJson(partialRoundTrip);
        assertFalse(partialExport.has("contentPolicy"));
        assertFalse(partialExport.getAsJsonArray("blocks").get(1).getAsJsonObject().has("usageIntent"));

        StructureInspectionReport report = new StructureInspector().inspect(
                tempDirectory.resolve("partial-content-provenance.nbt"), reread
        );
        assertEquals(ContentManifestStatus.PARTIAL, report.contentManifestStatus());
        assertTrue(new StructureReportWriter().formatText(report).contains(
                "Content manifest status: partial (schemaVersion 1; values below may be incomplete)"
        ));
        StructureReportWriter.ReportPaths paths = new StructureReportWriter().write(
                report, tempDirectory.resolve("partial-reports"), "partial-content-provenance"
        );
        assertTrue(Files.readString(paths.json()).contains("\"contentManifestStatus\": \"PARTIAL\""));
    }

    @Test
    void absentAndUnsupportedManifestVersionsAreNotReportedAsVerified() throws IOException {
        StructureModel absent = new MinecraftStructureNbtReader().read(
                new MinecraftStructureNbtWriter().compile(model(StructureContentManifest.defaults())),
                "fallback_name"
        );
        assertEquals(ContentManifestStatus.ABSENT, absent.contentManifest().provenanceStatus());

        CompoundTag unsupportedVersion = new MinecraftStructureNbtWriter().compile(model(manifest(true)));
        unsupportedVersion.getCompound("structuregen").getCompound("contentManifest")
                .putInt("schemaVersion", 99);
        StructureModel partial = new MinecraftStructureNbtReader().read(unsupportedVersion, "fallback_name");

        assertEquals(ContentManifestStatus.PARTIAL, partial.contentManifest().provenanceStatus());
        assertEquals(99, partial.contentManifest().schemaVersion());
        assertTrue(partial.unsupportedFields().contains("structuregen.contentManifest.schemaVersion=99"));
        assertEquals(List.of("create", "supplementaries_core"), partial.contentManifest().requiredMods());

        CompoundTag missingVersion = new MinecraftStructureNbtWriter().compile(model(manifest(true)));
        missingVersion.getCompound("structuregen").getCompound("contentManifest").remove("schemaVersion");
        StructureModel missingVersionModel = new MinecraftStructureNbtReader().read(
                missingVersion, "fallback_name"
        );
        assertEquals(ContentManifestStatus.PARTIAL,
                missingVersionModel.contentManifest().provenanceStatus());
        assertEquals(StructureContentManifest.UNKNOWN_SCHEMA_VERSION,
                missingVersionModel.contentManifest().schemaVersion());
        assertTrue(missingVersionModel.unsupportedFields().contains(
                "structuregen.contentManifest.schemaVersion"
        ));
        assertEquals(List.of("create", "supplementaries_core"),
                missingVersionModel.contentManifest().requiredMods());

        CompoundTag missingStatus = new MinecraftStructureNbtWriter().compile(model(manifest(true)));
        missingStatus.getCompound("structuregen").getCompound("contentManifest").remove("provenanceStatus");
        StructureModel missingStatusModel = new MinecraftStructureNbtReader().read(
                missingStatus, "fallback_name"
        );
        assertEquals(ContentManifestStatus.PARTIAL,
                missingStatusModel.contentManifest().provenanceStatus());
        assertTrue(missingStatusModel.unsupportedFields().contains(
                "structuregen.contentManifest.provenanceStatus"
        ));
        assertEquals(List.of("create", "supplementaries_core"),
                missingStatusModel.contentManifest().requiredMods());

        CompoundTag malformedExtension = new MinecraftStructureNbtWriter().compile(model(manifest(true)));
        malformedExtension.getCompound("structuregen").putString("contentManifest", "not-a-compound");
        StructureModel malformedExtensionModel = new MinecraftStructureNbtReader().read(
                malformedExtension, "fallback_name"
        );
        assertEquals(ContentManifestStatus.PARTIAL,
                malformedExtensionModel.contentManifest().provenanceStatus());
        assertTrue(malformedExtensionModel.unsupportedFields().contains("structuregen.contentManifest"));
    }

    private StructureContentManifest manifest(boolean allowInstalledModBlocks) {
        return new StructureContentManifest(
                allowInstalledModBlocks,
                List.of("supplementaries", "create"),
                List.of("create", "supplementaries_core"),
                List.of("wildernessodysseyapi:powered_bunker_grid"),
                List.of(new ResolvedMaterial(
                        "roof_trim",
                        "decorative",
                        "supplementaries:awning",
                        Map.of("facing", "north"),
                        "supplementaries",
                        "preferred_decorative_mod",
                        true,
                        List.of(new RejectedMaterialCandidate(
                                "create:copycat_panel", "preferred candidate state was unavailable"
                        ))
                ))
        );
    }

    private StructureModel model(StructureContentManifest manifest) {
        return new StructureModel(
                "content_manifest_fixture",
                new StructureSize(2, 1, 1),
                List.of(
                        block(0, "create:copycat_panel", Map.of()),
                        block(1, "supplementaries:awning", Map.of("facing", "north"))
                ),
                List.of(),
                3955,
                Map.of("fixture", "content_manifest"),
                List.of("mod_aware"),
                List.of(),
                null,
                List.of(),
                manifest
        );
    }

    private StructureModel copyWithManifest(StructureModel source, StructureContentManifest manifest) {
        return new StructureModel(
                source.name(),
                source.size(),
                source.blocks(),
                source.entities(),
                source.dataVersion(),
                source.metadata(),
                source.markers(),
                source.sourcePalettes(),
                source.rawRootSnbt(),
                source.unsupportedFields(),
                manifest
        );
    }

    private StructureContentManifest withStatus(
            StructureContentManifest manifest,
            ContentManifestStatus status
    ) {
        return new StructureContentManifest(
                manifest.schemaVersion(),
                manifest.allowInstalledModBlocks(),
                manifest.preferredDecorativeMods(),
                manifest.requiredMods(),
                manifest.enabledFunctionalSystems(),
                manifest.resolvedMaterials(),
                status
        );
    }

    private StructureBlock block(int x, String blockId, Map<String, String> properties) {
        return new StructureBlock(
                new StructurePosition(x, 0, 0),
                new StructureBlockState(blockId, properties),
                null,
                List.of()
        );
    }
}
