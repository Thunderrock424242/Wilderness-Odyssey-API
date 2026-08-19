package com.thunder.wildernessodysseyapi.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards Minecraft's chunk lifecycle from Wilderness Odyssey performance controls.
 *
 * <p>The performance, async, and Data Engine packages may classify coordinates
 * and defer their own optional work, but they must never load chunks, control
 * tickets, wrap generation, or decide when chunks are sent or unloaded.</p>
 */
class ChunkLifecycleIsolationTest {
    private static final List<String> OPTIMIZATION_PACKAGES = List.of(
            "async",
            "dataengine",
            "performance"
    );
    private static final List<String> CHUNK_OWNERS = List.of(
            "mixin",
            "worldgen"
    );
    private static final List<Pattern> FORBIDDEN_CHUNK_CONTROL = List.of(
            Pattern.compile("\\bimport\\s+net\\.minecraft\\.server\\.level\\."
                    + "(?:ChunkMap|ServerChunkCache|DistanceManager|GenerationChunkHolder|ChunkHolder|Ticket|TicketType)\\b"),
            Pattern.compile("\\bimport\\s+net\\.minecraft\\.world\\.level\\.chunk\\."
                    + "(?:ChunkAccess|LevelChunk|ProtoChunk|ImposterProtoChunk)\\b"),
            Pattern.compile("\\bimport\\s+net\\.minecraft\\.world\\.level\\.chunk\\.status\\."),
            Pattern.compile("\\.(?:getChunk|getChunkNow|addRegionTicket|removeRegionTicket|"
                    + "updateChunkForced|setChunkForced|setViewDistance)\\s*\\(")
    );
    private static final Pattern OPTIMIZATION_REFERENCE = Pattern.compile(
            "com\\.thunder\\.wildernessodysseyapi\\.(?:async|dataengine|performance)\\."
                    + "|\\b(?:AsyncTaskManager|DataEngine|TickEngine|BackgroundEfficiencyManager)\\b"
    );

    @Test
    void optimizationInfrastructureCannotOwnChunkLifecycle() throws IOException {
        Path sourceRoot = sourceRoot();
        List<String> violations = new ArrayList<>();

        for (String packageName : OPTIMIZATION_PACKAGES) {
            inspectTree(
                    sourceRoot.resolve(packageName),
                    source -> FORBIDDEN_CHUNK_CONTROL.stream().anyMatch(pattern -> pattern.matcher(source).find()),
                    "optimization infrastructure references a chunk-lifecycle API",
                    violations
            );
        }

        assertTrue(violations.isEmpty(), () -> "Chunk lifecycle boundary violations:\n"
                + String.join("\n", violations));
    }

    @Test
    void chunkOwnersCannotDependOnOptimizationGovernors() throws IOException {
        Path sourceRoot = sourceRoot();
        List<String> violations = new ArrayList<>();

        for (String packageName : CHUNK_OWNERS) {
            inspectTree(
                    sourceRoot.resolve(packageName),
                    source -> OPTIMIZATION_REFERENCE.matcher(source).find(),
                    "chunk-owning package references an optimization governor",
                    violations
            );
        }

        assertTrue(violations.isEmpty(), () -> "Chunk lifecycle dependency violations:\n"
                + String.join("\n", violations));
    }

    @Test
    void noSourceCombinesChunkControlWithOptimizationGovernance() throws IOException {
        Path sourceRoot = sourceRoot();
        List<String> violations = new ArrayList<>();

        inspectTree(
                sourceRoot,
                source -> OPTIMIZATION_REFERENCE.matcher(source).find()
                        && FORBIDDEN_CHUNK_CONTROL.stream().anyMatch(pattern -> pattern.matcher(source).find()),
                "source combines chunk control with optimization governance",
                violations
        );

        assertTrue(violations.isEmpty(), () -> "Chunk lifecycle coupling violations:\n"
                + String.join("\n", violations));
    }

    private static Path sourceRoot() {
        Path projectRoot = Path.of(System.getProperty(
                "wildernessodysseyapi.projectDir",
                System.getProperty("user.dir")
        ));
        return projectRoot.resolve(Path.of(
                "src",
                "main",
                "java",
                "com",
                "thunder",
                "wildernessodysseyapi"
        )).toAbsolutePath().normalize();
    }

    private static void inspectTree(
            Path root,
            Predicate<String> violates,
            String message,
            List<String> violations
    ) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path sourceFile : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (violates.test(Files.readString(sourceFile))) {
                    violations.add(sourceRoot().relativize(sourceFile) + ": " + message);
                }
            }
        }
    }
}
