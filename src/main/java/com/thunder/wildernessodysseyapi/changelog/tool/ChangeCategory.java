package com.thunder.wildernessodysseyapi.changelog.tool;

enum ChangeCategory {
    ADDED("Added"),
    CHANGED("Changed"),
    FIXED("Fixed"),
    REMOVED("Removed");

    private final String heading;

    ChangeCategory(String heading) {
        this.heading = heading;
    }

    String heading() {
        return heading;
    }
}
