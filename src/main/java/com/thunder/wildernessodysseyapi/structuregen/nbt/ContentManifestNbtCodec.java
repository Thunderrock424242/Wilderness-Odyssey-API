package com.thunder.wildernessodysseyapi.structuregen.nbt;

import com.thunder.wildernessodysseyapi.structuregen.content.ContentManifestStatus;
import com.thunder.wildernessodysseyapi.structuregen.content.RejectedMaterialCandidate;
import com.thunder.wildernessodysseyapi.structuregen.content.ResolvedMaterial;
import com.thunder.wildernessodysseyapi.structuregen.content.StructureContentManifest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Serializes the versioned StructureGen content manifest inside the namespaced NBT extension. */
final class ContentManifestNbtCodec {

    private static final String ROOT_LOCATION = "structuregen.contentManifest";
    private static final String VERIFIED_STATUS = "verified";
    private static final String PARTIAL_STATUS = "partial";
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schemaVersion", "provenanceStatus", "allowInstalledModBlocks", "preferredDecorativeMods",
            "requiredMods", "enabledFunctionalSystems", "resolvedMaterials"
    );
    private static final Set<String> MATERIAL_FIELDS = Set.of(
            "role", "intent", "selectedBlock", "properties", "sourceNamespace", "source",
            "fallbackAvailable", "rejectedCandidates"
    );
    private static final Set<String> REJECTION_FIELDS = Set.of("block", "reason");

    private ContentManifestNbtCodec() {
    }

    static CompoundTag write(StructureContentManifest manifest) {
        if (manifest.provenanceStatus() == ContentManifestStatus.ABSENT) {
            throw new IllegalArgumentException("An absent content manifest cannot be serialized");
        }
        CompoundTag root = new CompoundTag();
        root.putInt("schemaVersion", manifest.schemaVersion());
        // Persist the trust boundary independently from field shape. Rewriting readable
        // partial values into canonical NBT must never promote their provenance to verified.
        root.putString("provenanceStatus", manifest.provenanceStatus() == ContentManifestStatus.VERIFIED
                ? VERIFIED_STATUS
                : PARTIAL_STATUS);
        root.putBoolean("allowInstalledModBlocks", manifest.allowInstalledModBlocks());
        root.put("preferredDecorativeMods", StructureNbtSupport.stringList(manifest.preferredDecorativeMods()));
        root.put("requiredMods", StructureNbtSupport.stringList(manifest.requiredMods()));
        root.put("enabledFunctionalSystems", StructureNbtSupport.stringList(manifest.enabledFunctionalSystems()));

        ListTag materials = new ListTag();
        for (ResolvedMaterial material : manifest.resolvedMaterials()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("role", material.role());
            entry.putString("intent", material.intent());
            entry.putString("selectedBlock", material.selectedBlock());
            CompoundTag properties = new CompoundTag();
            material.properties().forEach(properties::putString);
            entry.put("properties", properties);
            entry.putString("sourceNamespace", material.sourceNamespace());
            entry.putString("source", material.source());
            entry.putBoolean("fallbackAvailable", material.fallbackAvailable());

            ListTag rejected = new ListTag();
            for (RejectedMaterialCandidate candidate : material.rejectedCandidates()) {
                CompoundTag rejection = new CompoundTag();
                rejection.putString("block", candidate.blockId());
                rejection.putString("reason", candidate.reason());
                rejected.add(rejection);
            }
            entry.put("rejectedCandidates", rejected);
            materials.add(entry);
        }
        root.put("resolvedMaterials", materials);
        return root;
    }

    static StructureContentManifest read(CompoundTag root, Set<String> unsupported) {
        ReadState state = new ReadState(unsupported);
        recordUnknownFields(root, ROOT_FIELDS, ROOT_LOCATION, state);
        int schemaVersion = readSchemaVersion(root, state);
        ContentManifestStatus declaredStatus = readProvenanceStatus(root, state);
        boolean allowInstalled = readRequiredBoolean(
                root, "allowInstalledModBlocks", true, ROOT_LOCATION + ".allowInstalledModBlocks", state
        );
        List<String> preferred = readRequiredStringList(
                root, "preferredDecorativeMods", ROOT_LOCATION + ".preferredDecorativeMods", state
        );
        List<String> required = readRequiredStringList(
                root, "requiredMods", ROOT_LOCATION + ".requiredMods", state
        );
        List<String> systems = readRequiredStringList(
                root, "enabledFunctionalSystems", ROOT_LOCATION + ".enabledFunctionalSystems", state
        );
        List<ResolvedMaterial> materials = readMaterials(root, state);
        ContentManifestStatus status = state.isVerified() && declaredStatus == ContentManifestStatus.VERIFIED
                ? ContentManifestStatus.VERIFIED
                : ContentManifestStatus.PARTIAL;
        return new StructureContentManifest(
                schemaVersion, allowInstalled, preferred, required, systems, materials, status
        );
    }

    private static ContentManifestStatus readProvenanceStatus(CompoundTag root, ReadState state) {
        String location = ROOT_LOCATION + ".provenanceStatus";
        if (!root.contains("provenanceStatus", Tag.TAG_STRING)) {
            state.reject(location);
            return ContentManifestStatus.PARTIAL;
        }
        String value = root.getString("provenanceStatus");
        if (VERIFIED_STATUS.equals(value)) {
            return ContentManifestStatus.VERIFIED;
        }
        if (PARTIAL_STATUS.equals(value)) {
            state.retainPartial();
            return ContentManifestStatus.PARTIAL;
        }
        state.reject(location + "=" + value);
        return ContentManifestStatus.PARTIAL;
    }

    private static int readSchemaVersion(CompoundTag root, ReadState state) {
        String location = ROOT_LOCATION + ".schemaVersion";
        if (!root.contains("schemaVersion", Tag.TAG_INT)) {
            state.reject(location);
            return root.contains("schemaVersion", Tag.TAG_ANY_NUMERIC)
                    ? root.getInt("schemaVersion")
                    : StructureContentManifest.UNKNOWN_SCHEMA_VERSION;
        }
        int schemaVersion = root.getInt("schemaVersion");
        if (schemaVersion != StructureContentManifest.CURRENT_SCHEMA_VERSION) {
            state.reject(location + "=" + schemaVersion);
        }
        return schemaVersion;
    }

    private static List<ResolvedMaterial> readMaterials(CompoundTag root, ReadState state) {
        String location = ROOT_LOCATION + ".resolvedMaterials";
        if (!(root.get("resolvedMaterials") instanceof ListTag entries)) {
            state.reject(location);
            return List.of();
        }
        if (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            state.reject(location);
            return List.of();
        }

        List<ResolvedMaterial> materials = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            String entryLocation = location + "[" + index + "]";
            recordUnknownFields(entry, MATERIAL_FIELDS, entryLocation, state);
            String role = readRequiredString(entry, "role", entryLocation + ".role", state);
            String intent = readRequiredString(entry, "intent", entryLocation + ".intent", state);
            String selectedBlock = readRequiredString(
                    entry, "selectedBlock", entryLocation + ".selectedBlock", state
            );
            Map<String, String> properties = readRequiredStringMap(
                    entry, "properties", entryLocation + ".properties", state
            );
            String sourceNamespace = readRequiredString(
                    entry, "sourceNamespace", entryLocation + ".sourceNamespace", state
            );
            String source = readRequiredString(entry, "source", entryLocation + ".source", state);
            boolean fallbackAvailable = readRequiredBoolean(
                    entry, "fallbackAvailable", false, entryLocation + ".fallbackAvailable", state
            );
            List<RejectedMaterialCandidate> rejected = readRejections(entry, entryLocation, state);
            materials.add(new ResolvedMaterial(
                    role, intent, selectedBlock, properties, sourceNamespace, source,
                    fallbackAvailable, rejected
            ));
        }
        return List.copyOf(materials);
    }

    private static List<RejectedMaterialCandidate> readRejections(
            CompoundTag material,
            String materialLocation,
            ReadState state
    ) {
        String location = materialLocation + ".rejectedCandidates";
        if (!(material.get("rejectedCandidates") instanceof ListTag entries)) {
            state.reject(location);
            return List.of();
        }
        if (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND) {
            state.reject(location);
            return List.of();
        }

        List<RejectedMaterialCandidate> rejected = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            String entryLocation = location + "[" + index + "]";
            recordUnknownFields(entry, REJECTION_FIELDS, entryLocation, state);
            String block = readRequiredString(entry, "block", entryLocation + ".block", state);
            String reason = readRequiredString(entry, "reason", entryLocation + ".reason", state);
            rejected.add(new RejectedMaterialCandidate(block, reason));
        }
        return List.copyOf(rejected);
    }

    private static Map<String, String> readRequiredStringMap(
            CompoundTag owner,
            String key,
            String location,
            ReadState state
    ) {
        if (!(owner.get(key) instanceof CompoundTag values)) {
            state.reject(location);
            return Map.of();
        }
        Map<String, String> result = new TreeMap<>();
        for (String name : values.getAllKeys()) {
            if (values.get(name) instanceof StringTag) {
                result.put(name, values.getString(name));
            } else {
                state.reject(location + "." + name);
            }
        }
        return result;
    }

    private static List<String> readRequiredStringList(
            CompoundTag owner,
            String key,
            String location,
            ReadState state
    ) {
        if (!(owner.get(key) instanceof ListTag list)) {
            state.reject(location);
            return List.of();
        }
        if (!list.isEmpty() && list.getElementType() != Tag.TAG_STRING) {
            state.reject(location);
            return List.of();
        }
        List<String> values = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            values.add(list.getString(index));
        }
        return List.copyOf(values);
    }

    private static String readRequiredString(
            CompoundTag owner,
            String key,
            String location,
            ReadState state
    ) {
        if (!owner.contains(key, Tag.TAG_STRING)) {
            state.reject(location);
            return "";
        }
        return owner.getString(key);
    }

    private static boolean readRequiredBoolean(
            CompoundTag owner,
            String key,
            boolean fallback,
            String location,
            ReadState state
    ) {
        if (!owner.contains(key, Tag.TAG_BYTE)) {
            state.reject(location);
            return fallback;
        }
        return owner.getBoolean(key);
    }

    private static void recordUnknownFields(
            CompoundTag tag,
            Set<String> supported,
            String location,
            ReadState state
    ) {
        tag.getAllKeys().stream()
                .filter(key -> !supported.contains(key))
                .map(key -> location + "." + key)
                .forEach(state::reject);
    }

    /** Tracks whether every schema field was understood while retaining precise reader annotations. */
    private static final class ReadState {

        private final Set<String> unsupported;
        private boolean verified = true;

        private ReadState(Set<String> unsupported) {
            this.unsupported = unsupported;
        }

        private void reject(String location) {
            unsupported.add(location);
            verified = false;
        }

        private void retainPartial() {
            verified = false;
        }

        private boolean isVerified() {
            return verified;
        }
    }
}
