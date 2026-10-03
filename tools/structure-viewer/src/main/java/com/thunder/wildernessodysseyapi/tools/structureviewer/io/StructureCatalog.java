package com.thunder.wildernessodysseyapi.tools.structureviewer.io;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Bounded template index. A corrupt mod archive cannot prevent other mods from being browsed. */
public final class StructureCatalog {
    private static final int MAX_STRUCTURES = 20000;
    private StructureCatalog() {}
    public record Entry(String group, String path, StructureSource source, boolean feature) {
        public Entry(String group,String path,StructureSource source){this(group,path,source,TemplateFeatures.isFeature(path));}
        public String searchText() { return (group + " " + path).toLowerCase(Locale.ROOT); }
        @Override public String toString() { return path.replaceFirst("/structures?/", "/"); }
    }
    public record Result(List<Entry> entries, List<String> diagnostics, int archives) {
        public Result { entries = List.copyOf(entries); diagnostics = List.copyOf(diagnostics); }
    }

    /** Only templates under data/namespace/structure[s] are eligible, never tags or worldgen JSON. */
    public static boolean isTemplate(String name) {
        return !name.contains("..") && !name.contains("\\")
                && name.matches("data/[a-z0-9_.-]+/structures?/.+\\.(nbt|json)");
    }

    /** Discovers top-level mod JARs and data-pack ZIPs, plus the existing developer resource roots. */
    public static Result scan(Path root, Path build) throws IOException {
        List<Entry> entries = new ArrayList<>(); List<String> issues = new ArrayList<>();
        List<Path> roots = StructureDiscovery.roots(root, build);
        for (Path file : StructureDiscovery.discover(roots)) {
            Path owner = roots.stream().filter(file::startsWith).findFirst().orElse(root);
            entries.add(new Entry(owner.toString().replace(root.toString(), "Project"),
                    owner.relativize(file).toString().replace('\\','/'), StructureSource.file(file)));
            if (entries.size() >= MAX_STRUCTURES) break;
        }
        int archives = 0;
        for (Path archive : archives(root)) {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Scan cancelled.");
            if (entries.size() >= MAX_STRUCTURES) { issues.add("Structure list limited to " + MAX_STRUCTURES + " entries."); break; }
            archives++;
            try (ZipFile zip = new ZipFile(archive.toFile())) {
                var contents = zip.entries(); int visited = 0;
                Set<String> seen = new HashSet<>(); Set<String> ambiguous = new HashSet<>();
                List<Entry> found = new ArrayList<>();
                while (contents.hasMoreElements()) {
                    if (++visited > 200000) { issues.add(archive.getFileName() + ": archive entry limit reached."); break; }
                    var item = contents.nextElement(); String name = item.getName();
                    if (item.isDirectory() || !isTemplate(name)) continue;
                    if (!seen.add(name)) { ambiguous.add(name); continue; }
                    if (item.getSize() > 64L * 1024 * 1024) { issues.add(archive.getFileName() + ": oversized template " + name); continue; }
                    found.add(new Entry(root.relativize(archive).toString().replace('\\','/'), name.substring(5), new StructureSource(archive, name)));
                    if (found.size() + entries.size() >= MAX_STRUCTURES) { issues.add("Structure list limited to " + MAX_STRUCTURES + " entries."); break; }
                }
                if (!ambiguous.isEmpty()) issues.add(archive.getFileName() + ": ambiguous duplicate template entries omitted.");
                found.removeIf(item -> ambiguous.contains(item.source().entry()));
                found.sort(Comparator.comparing(Entry::path)); entries.addAll(found);
            } catch (IOException | RuntimeException error) {
                issues.add("Could not scan " + archive.getFileName() + ": " + error.getMessage());
            }
        }
        return new Result(entries, issues, archives);
    }

    /** The same deterministic mod order is shared with asset discovery. */
    public static List<Path> archives(Path root) throws IOException {
        Set<Path> result = new LinkedHashSet<>();
        for (String folder : List.of("mods", "run/mods", "datapacks")) {
            Path directory = root.resolve(folder);
            if (!Files.isDirectory(directory)) continue;
            try (var files = Files.list(directory)) {
                files.filter(Files::isRegularFile).filter(p -> {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return name.endsWith(".jar") || folder.equals("datapacks") && name.endsWith(".zip");
                }).sorted().limit(4096).forEach(result::add);
            }
        }
        return List.copyOf(result);
    }
}
