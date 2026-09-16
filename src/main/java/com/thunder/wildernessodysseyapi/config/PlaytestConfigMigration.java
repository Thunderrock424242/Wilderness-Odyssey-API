package com.thunder.wildernessodysseyapi.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Moves private service settings out of SERVER before NeoForge can synchronize it.
 * COMMON is saved first; retries after interrupted migration preserve existing common choices.
 */
public final class PlaytestConfigMigration {
    private static final List<String> SECTIONS = List.of(
            "verificationRelay", "telemetry", "playerTelemetry", "eventTelemetry", "feedback");

    private PlaytestConfigMigration() {
    }

    /** Migrates installation settings, failing closed without exposing parser text or secrets. */
    public static void prepare(Path commonFile, Path serverFile) {
        if (Files.notExists(serverFile)) {
            return;
        }
        try {
            CommentedConfig server = read(serverFile);
            if (SECTIONS.stream().noneMatch(name -> server.contains(List.of(name)))) {
                return;
            }
            CommentedConfig common = Files.notExists(commonFile) ? CommentedConfig.inMemory() : read(commonFile);
            for (String name : SECTIONS) {
                List<String> path = List.of(name);
                if (!server.contains(path)) {
                    continue;
                }
                if (!(server.get(path) instanceof CommentedConfig incoming)) {
                    throw new IOException("Invalid private section");
                }
                CommentedConfig destination;
                if (common.contains(path)) {
                    if (!(common.get(path) instanceof CommentedConfig existing)) {
                        throw new IOException("Invalid private section");
                    }
                    destination = existing;
                } else {
                    destination = common.createSubConfig();
                    common.set(path, destination);
                }
                mergeMissing(destination, incoming);
                copyComment(common, server, path);
                server.remove(path);
                server.removeComment(path);
            }
            writeAtomically(commonFile, common);
            writeAtomically(serverFile, server);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Cannot safely migrate private playtesting settings. "
                    + "Configuration registration stopped; check the common and server config files.");
        }
    }

    private static CommentedConfig read(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new TomlParser().parse(reader);
        }
    }

    private static void mergeMissing(CommentedConfig destination, CommentedConfig source) {
        for (String name : source.valueMap().keySet()) {
            List<String> path = List.of(name);
            Object incoming = source.get(path);
            if (!destination.contains(path)) {
                if (incoming instanceof CommentedConfig incomingSection) {
                    CommentedConfig copied = destination.createSubConfig();
                    mergeMissing(copied, incomingSection);
                    destination.set(path, copied);
                } else {
                    destination.set(path, incoming);
                }
            } else if (incoming instanceof CommentedConfig incomingSection
                    && destination.get(path) instanceof CommentedConfig existingSection) {
                mergeMissing(existingSection, incomingSection);
            }
            copyComment(destination, source, path);
        }
    }

    private static void copyComment(CommentedConfig destination, CommentedConfig source, List<String> path) {
        if (destination.getComment(path) == null && source.getComment(path) != null) {
            destination.setComment(path, source.getComment(path));
        }
    }

    private static void writeAtomically(Path file, CommentedConfig config) throws IOException {
        Path target = file.toAbsolutePath().normalize();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), target.getFileName() + ".", ".tmp");
        try {
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                new TomlWriter().write(config, writer);
            }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}