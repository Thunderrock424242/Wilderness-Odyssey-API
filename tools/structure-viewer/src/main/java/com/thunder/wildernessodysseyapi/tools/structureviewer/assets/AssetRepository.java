package com.thunder.wildernessodysseyapi.tools.structureviewer.assets;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Read-only asset layers: user packs, project resources, local mod JARs, then the vanilla client. */
public final class AssetRepository implements AutoCloseable {
    private record Layer(Path path, ZipFile archive) {}
    private final List<Layer> layers = new ArrayList<>();
    private final Set<String> diagnostics = new LinkedHashSet<>();

    /** Opens explicitly ordered asset sources; the first matching file wins. */
    public AssetRepository(List<Path> paths) {
        for (Path path : new LinkedHashSet<>(paths)) {
            if (!Files.exists(path)) continue;
            try { layers.add(new Layer(path.toAbsolutePath().normalize(), Files.isDirectory(path) ? null : new ZipFile(path.toFile()))); }
            catch (IOException e) { diagnostics.add("Cannot open asset source " + path + ": " + e.getMessage()); }
        }
    }

    /** Discovers known local asset locations without unpacking dependency caches or downloading assets. */
    public static AssetRepository discover(Path project, List<Path> additional) {
        List<Path> paths = new ArrayList<>(additional);
        paths.add(project.resolve("src/main/resources"));
        paths.add(project.resolve("src/generated/resources"));
        Path mods = project.resolve("run/mods");
        if (Files.isDirectory(mods)) try (var entries = Files.list(mods)) {
            entries.filter(p -> p.toString().endsWith(".jar")).sorted().forEach(paths::add);
        } catch (IOException ignored) { /* Optional local mod directory. */ }
        String explicit = System.getProperty("structureViewer.minecraftJar", "");
        if (!explicit.isBlank()) paths.add(Path.of(explicit));
        String gradleHome = System.getenv("GRADLE_USER_HOME");
        Path cache = gradleHome == null ? Path.of(System.getProperty("user.home"), ".gradle") : Path.of(gradleHome);
        paths.add(cache.resolve("caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar"));
        String appData = System.getenv("APPDATA");
        if (appData != null) paths.add(Path.of(appData, ".minecraft/versions/1.21.1/1.21.1.jar"));
        return new AssetRepository(paths);
    }

    /** Reads one asset, bounded to 8 MiB. Resource names cannot escape a source directory. */
    public Optional<byte[]> read(String name) throws IOException {
        if (!name.startsWith("assets/") || name.contains("..") || name.contains("\\") || name.contains(":"))
            throw new IOException("Invalid asset path: " + name);
        for (Layer layer : layers) {
            InputStream input;
            if (layer.archive != null) {
                var entry = layer.archive.getEntry(name);
                if (entry == null) continue;
                if (entry.getSize() > 8 * 1024 * 1024) throw new IOException("Oversized asset: " + name);
                input = layer.archive.getInputStream(entry);
            } else {
                Path file = layer.path.resolve(name).normalize();
                if (!file.startsWith(layer.path) || !Files.isRegularFile(file)) continue;
                input = Files.newInputStream(file);
            }
            try (input) {
                byte[] bytes = input.readNBytes(8 * 1024 * 1024 + 1);
                if (bytes.length > 8 * 1024 * 1024) throw new IOException("Oversized asset: " + name);
                return Optional.of(bytes);
            }
        }
        return Optional.empty();
    }

    /** Converts a namespaced resource ID to an asset path. */
    public static String path(String id, String folder, String suffix) {
        String normalized = id.contains(":") ? id : "minecraft:" + id;
        if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || normalized.contains(".."))
            throw new IllegalArgumentException("Malformed resource ID: " + id);
        return "assets/" + normalized.replace(":", "/" + folder + "/") + suffix;
    }

    /** Sources used for diagnostic reporting. */
    public List<String> sources() { return layers.stream().map(l -> l.path.toString()).toList(); }
    /** Asset source warnings. */
    public List<String> diagnostics() { return List.copyOf(diagnostics); }
    @Override public void close() {
        for (Layer layer : layers) if (layer.archive != null) try { layer.archive.close(); }
        catch (IOException e) { diagnostics.add("Closing asset source failed: " + e.getMessage()); }
    }
}
