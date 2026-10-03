package com.thunder.wildernessodysseyapi.tools.structureviewer.ui;

/** Saved library views; Structures keeps uncertain templates visible and hides clear decorations. */
public enum LibraryView {
    STRUCTURES("Structures"),ALL("All templates"),FEATURES("Features");
    private final String label;
    LibraryView(String label){this.label=label;}
    @Override public String toString(){return label;}
}
