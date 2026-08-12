package com.thunder.wildernessodysseyapi.developmentstudio.inspection;

import com.thunder.wildernessodysseyapi.developmentstudio.StudioText;

/** One bounded label/value pair displayed by the Studio Inspector page. */
public record StudioInspectionLine(String label, String value) {
    private static final int MAX_LABEL = 48;
    private static final int MAX_VALUE = 256;

    public StudioInspectionLine {
        label = StudioText.singleLine(label, MAX_LABEL);
        value = StudioText.singleLine(value, MAX_VALUE);
    }
}
