package net.minecraft.resources;

/**
 * Minimal stub for tests.
 */
public class ResourceLocation {
    private final String namespace;
    private final String path;

    private ResourceLocation(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
    }

    public static ResourceLocation tryParse(String name) {
        if (name == null) {
            return null;
        }
        if (!name.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")) {
            return null;
        }
        String[] parts = name.split(":", 2);
        return new ResourceLocation(parts[0], parts[1]);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getPath() {
        return path;
    }
}
