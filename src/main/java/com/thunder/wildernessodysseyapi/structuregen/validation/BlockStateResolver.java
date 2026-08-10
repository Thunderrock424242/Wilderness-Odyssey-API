package com.thunder.wildernessodysseyapi.structuregen.validation;

import java.util.List;
import java.util.Map;

/** Registry-aware validation boundary used without coupling Blueprint parsing to Minecraft state objects. */
public interface BlockStateResolver {

    /** Validates one syntactically valid resource ID and its string property map. */
    Resolution validate(String blockId, Map<String, String> properties);

    /** Errors block generation; warnings document checks unavailable in the offline runtime. */
    record Resolution(List<String> errors, List<String> warnings) {

        public Resolution {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }

        /** Returns a successful validation with no diagnostics. */
        public static Resolution valid() {
            return new Resolution(List.of(), List.of());
        }
    }
}
