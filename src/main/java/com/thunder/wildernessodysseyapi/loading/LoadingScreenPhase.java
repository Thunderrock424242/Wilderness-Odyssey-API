package com.thunder.wildernessodysseyapi.loading;

/** Recognized vanilla loading stages; ordinary menus and save messages are excluded. */
public enum LoadingScreenPhase {
    RESOURCES("Resource loading", "resources"),
    WORLD_DATA("Reading world data", "world_data"),
    WORLD_RESOURCES("Preparing world resources", "world_resources"),
    WORLD_PREPARATION("Preparing world creation", "world_preparation"),
    SPAWN("Generating spawn", "spawn"),
    TERRAIN("Receiving terrain", "terrain"),
    WORLD_OPERATION("World operation", "world_operation");

    private final String label;
    private final String translation;

    LoadingScreenPhase(String label, String translation) {
        this.label = label;
        this.translation = "loading.wildernessodysseyapi.stage." + translation;
    }

    public String label() {
        return label;
    }

    public String translationKey() {
        return translation;
    }

    /** Returns null for messages that do not identify a world/resource load. */
    public static LoadingScreenPhase fromMessageKey(String key) {
        return switch (key) {
            case "gui.loadingMinecraft" -> RESOURCES;
            case "selectWorld.data_read" -> WORLD_DATA;
            case "selectWorld.resource_load", "dataPack.validation.working" -> WORLD_RESOURCES;
            case "selectWorld.preparing", "menu.generatingLevel" -> WORLD_PREPARATION;
            case "menu.loadingLevel", "menu.preparingSpawn" -> SPAWN;
            case "menu.generatingTerrain" -> TERRAIN;
            default -> null;
        };
    }
}
