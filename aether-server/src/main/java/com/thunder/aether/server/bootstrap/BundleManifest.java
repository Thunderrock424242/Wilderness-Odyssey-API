package com.thunder.aether.server.bootstrap;

import com.thunder.aether.server.api.JsonHttp;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Verified inventory of a platform-specific distribution; large payloads are never parsed into memory. */
public record BundleManifest(int format, String platform, String ollamaVersion, String executable,
        String sourceModel, String targetModel, List<Asset> assets) {
    public BundleManifest {
        require(format == 1 && Set.of("windows-amd64", "linux-amd64", "linux-arm64").contains(platform));
        require(ollamaVersion != null && ollamaVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+"));
        require(sourceModel != null && sourceModel.matches("[a-z0-9._-]+:[a-zA-Z0-9._-]+"));
        require(targetModel != null && targetModel.matches("[a-z0-9._-]+:[a-zA-Z0-9._-]+"));
        require(assets != null && !assets.isEmpty() && assets.size() <= 10000);
        assets = List.copyOf(assets);
        Set<String> names = new HashSet<>();
        long total = 0;
        for (Asset asset : assets) {
            require(names.add(asset.path().toLowerCase(Locale.ROOT)));
            total = Math.addExact(total, asset.size());
            require(total <= 64L * 1024 * 1024 * 1024);
        }
        for (String name : names) {
            for (int slash = name.indexOf('/'); slash >= 0; slash = name.indexOf('/', slash + 1)) {
                require(!names.contains(name.substring(0, slash)));
            }
        }
        require(executable != null && executable.startsWith("runtime/")
                && assets.stream().anyMatch(asset -> asset.path().equals(executable)));
    }

    /** One installed file, relative to the application-owned data directory. */
    public record Asset(String path, long size, String sha256) {
        public Asset {
            require(path != null && path.length() <= 512 && !path.contains("\\") && !path.contains(":"));
            require(path.startsWith("runtime/") || path.startsWith("models/") || path.startsWith("licenses/"));
            for (String part : path.split("/", -1)) {
                require(!part.matches("(?i)(con|prn|aux|nul|com[1-9¹²³]|lpt[1-9¹²³])(?:\\..*)?") && !part.isBlank() && !part.equals(".") && !part.equals("..")
                        && !part.endsWith(".") && !part.endsWith(" ")
                        && part.chars().noneMatch(c -> c < 32 || "<>|\"?*".indexOf(c) >= 0));
            }
            require(size >= 0 && size <= 16L * 1024 * 1024 * 1024);
            require(sha256 != null && sha256.matches("[0-9a-f]{64}"));
        }
    }

    /** Reads only the small descriptor; bundled weights stay on the resource stream. */
    public static BundleManifest read(InputStream input) throws IOException {
        if (input == null) { throw new IOException("This is a gateway-only JAR. Use the bundled distribution or --external."); }
        try (input) {
            byte[] bytes = input.readNBytes(1_048_577);
            if (bytes.length > 1_048_576) { throw new IOException("Bundle manifest is too large."); }
            try { return JsonHttp.JSON.fromJson(JsonHttp.object(bytes), BundleManifest.class); }
            catch (RuntimeException failure) { throw new IOException("Bundle manifest is invalid."); }
        }
    }

    /** Native payload selection never silently falls back to a different OS/architecture. */
    public static String currentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String suffix = switch (arch) { case "amd64", "x86_64" -> "amd64"; case "aarch64", "arm64" -> "arm64"; default -> "unsupported"; };
        return (os.startsWith("windows") ? "windows" : os.equals("linux") ? "linux" : "unsupported") + "-" + suffix;
    }

    /** Rejects an incompatible native payload before extraction or process startup. */
    public void requireCurrentPlatform() throws IOException {
        if (!platform.equals(currentPlatform())) { throw new IOException("Bundle is for " + platform + "; this host is " + currentPlatform() + "."); }
    }

    private static void require(boolean condition) {
        if (!condition) { throw new IllegalArgumentException("Invalid bundle manifest."); }
    }
}
