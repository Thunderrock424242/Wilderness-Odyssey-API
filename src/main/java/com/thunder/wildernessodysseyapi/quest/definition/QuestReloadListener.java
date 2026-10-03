package com.thunder.wildernessodysseyapi.quest.definition;

import com.thunder.wildernessodysseyapi.quest.validation.QuestContentLookup;
import com.thunder.wildernessodysseyapi.quest.validation.QuestValidationReport;
import com.thunder.wildernessodysseyapi.quest.validation.QuestValidator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Prepares bounded datapack candidates only. Reload never activates an existing world's publication. */
public final class QuestReloadListener extends SimplePreparableReloadListener<QuestReloadListener.Prepared> {
    private final Supplier<QuestContentLookup> lookup;
    private final Consumer<QuestValidationReport> candidates;
    private final Consumer<Prepared> preparedConsumer;
    private final QuestDefinitionCodec codec = new QuestDefinitionCodec();

    /** The lookup supplier and result consumer are invoked in apply, at the reload's game-thread boundary. */
    public QuestReloadListener(Supplier<QuestContentLookup> lookup, Consumer<QuestValidationReport> candidates) {
        this.lookup = lookup;
        this.candidates = candidates;
        this.preparedConsumer = null;
    }

    /** Defers validation to the tag-binding boundary rather than reading the previous generation's bound tags. */
    public QuestReloadListener(Consumer<Prepared> preparedConsumer) {
        this.lookup = null; this.candidates = null; this.preparedConsumer = preparedConsumer;
    }

    @Override
    protected Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        List<QuestSourceDocument> documents = new ArrayList<>();
        List<QuestValidationReport.Finding> findings = new ArrayList<>();
        long totalBytes = 0;
        int sourceCount = 0;
        for (var kind : QuestSourceDocument.Kind.values()) {
            String directory = "wo_quests/" + switch (kind) {
                case CAMPAIGN -> "campaigns";
                case CHAPTER -> "chapters";
                case QUEST -> "quests";
            };
            var resources = manager.listResources(directory,
                    id -> id.getPath().startsWith(directory + "/") && id.getPath().endsWith(".json"));
            for (var entry : resources.entrySet()) {
                String path = entry.getKey().getPath();
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(),
                        path.substring(directory.length() + 1, path.length() - 5));
                if (++sourceCount > QuestDefinitionCodec.MAX_QUESTS + QuestDefinitionCodec.MAX_CHAPTERS + 1) {
                    QuestValidator.error(findings, id, "resources", "The campaign contains too many source files.");
                    return new Prepared(documents, findings);
                }
                try (var stream = entry.getValue().open()) {
                    byte[] bytes = stream.readNBytes(QuestDefinitionCodec.MAX_SOURCE_BYTES + 1);
                    totalBytes += bytes.length;
                    if (totalBytes > QuestDefinitionCodec.MAX_SNAPSHOT_BYTES) {
                        QuestValidator.error(findings, id, "resources", "The campaign input exceeds 16 MiB.");
                        return new Prepared(documents, findings);
                    }
                    documents.add(new QuestSourceDocument(kind, id, codec.readBounded(new ByteArrayInputStream(bytes)), entry.getValue().sourcePackId()));
                } catch (IOException exception) {
                    // Resource paths and exception details may be private; player findings carry only a portable ID.
                    QuestValidator.error(findings, id, "source", "This resource is malformed or exceeds its JSON limits.");
                }
            }
        }
        return new Prepared(documents, findings);
    }

    @Override
    protected void apply(Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        if (preparedConsumer != null) { preparedConsumer.accept(prepared); return; }
        if (!prepared.findings().isEmpty()) {
            candidates.accept(new QuestValidationReport(Optional.empty(), prepared.findings()));
        } else {
            candidates.accept(codec.decode(prepared.documents(), lookup.get()));
        }
    }

    public record Prepared(List<QuestSourceDocument> documents, List<QuestValidationReport.Finding> findings) {
        public Prepared {
            documents = List.copyOf(documents);
            findings = List.copyOf(findings);
        }
        public QuestValidationReport validate(QuestContentLookup lookup) {
            return findings.isEmpty() ? new QuestDefinitionCodec().decode(documents, lookup) : new QuestValidationReport(Optional.empty(), findings);
        }
    }
}
