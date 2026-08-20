package com.thunder.wildernessodysseyapi.worldgen.processor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockEntityNbtSanitizingProcessorTest {

    @Test
    void rejectsBrushablePayloadForChest() {
        assertFalse(BlockEntityNbtSanitizingProcessor.validateMetadata(
                true, "minecraft:brushable_block", true, false).compatible());
    }

    @Test
    void rejectsChestPayloadForOrdinaryTerrain() {
        assertFalse(BlockEntityNbtSanitizingProcessor.validateMetadata(
                false, "minecraft:chest", true, false).compatible());
    }

    @Test
    void preservesMatchingChestPayload() {
        assertTrue(BlockEntityNbtSanitizingProcessor.validateMetadata(
                true, "minecraft:chest", true, true).compatible());
    }

    @Test
    void preservesMatchingBrushablePayload() {
        assertTrue(BlockEntityNbtSanitizingProcessor.validateMetadata(
                true, "minecraft:brushable_block", true, true).compatible());
    }

    @Test
    void rejectsMissingMalformedAndUnknownIds() {
        assertFalse(BlockEntityNbtSanitizingProcessor.validateMetadata(
                true, null, false, false).compatible());
        assertFalse(BlockEntityNbtSanitizingProcessor.validateMetadata(
                true, "not a valid id", false, false).compatible());
        assertFalse(BlockEntityNbtSanitizingProcessor.validateMetadata(
                true, "wildernessodysseyapi:not_registered", false, false).compatible());
    }

}
