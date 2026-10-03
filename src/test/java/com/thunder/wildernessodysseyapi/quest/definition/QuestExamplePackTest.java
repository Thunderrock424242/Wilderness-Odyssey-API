package com.thunder.wildernessodysseyapi.quest.definition;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class QuestExamplePackTest {
    @Test
    void suppliedAcceptancePackHasACompleteValidGraphAndOnlySupportedRewards() throws Exception {
        Path root = Path.of(System.getProperty("wildernessodysseyapi.projectDir"), "docs/quests/examples/phase-1-test-pack/data/wildernessodysseyapi/wo_quests");
        var sources = new ArrayList<QuestSourceDocument>(); var codec = new QuestDefinitionCodec();
        for (var kind : QuestSourceDocument.Kind.values()) {
            String directory = switch (kind) { case CAMPAIGN -> "campaigns"; case CHAPTER -> "chapters"; case QUEST -> "quests"; };
            try (var files = Files.newDirectoryStream(root.resolve(directory), "*.json")) {
                for (var file : files) try (var input = Files.newInputStream(file)) {
                    String name = file.getFileName().toString();
                    sources.add(new QuestSourceDocument(kind, QuestFixtures.id("wildernessodysseyapi:" + name.substring(0, name.length() - 5)), codec.readBounded(input), "example"));
                }
            }
        }
        var report = codec.decode(sources, QuestFixtures.lookup());
        assertTrue(report.accepted(), () -> report.findings().toString());
        var snapshot = report.snapshot().orElseThrow(); assertEquals(2, snapshot.quests().size());
        assertTrue(snapshot.quests().values().stream().allMatch(QuestDefinition::optional));
        assertTrue(snapshot.quests().values().stream().flatMap(quest -> quest.objectives().stream()).noneMatch(
                objective -> objective.parameters() instanceof ObjectiveDefinition.CustomEvent));
    }
}
