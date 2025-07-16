package com.thunder.wildernessodysseyapi.util;

import com.thunder.wildernessodysseyapi.ModConflictChecker.Util.Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class UtilsTest {

    @ParameterizedTest
    @ValueSource(strings = {"minecraft:stone", "wildernessodysseyapi:test_item", "minecraft:block/stone"})
    @DisplayName("Valid resource locations return true")
    void testValidResourceLocations(String value) {
        assertTrue(Utils.isValidResourceLocation(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid", "minecraft:", "bad namespace:path", "minecraft:sp ace"})
    @DisplayName("Invalid resource locations return false")
    void testInvalidResourceLocations(String value) {
        assertFalse(Utils.isValidResourceLocation(value));
    }
}
