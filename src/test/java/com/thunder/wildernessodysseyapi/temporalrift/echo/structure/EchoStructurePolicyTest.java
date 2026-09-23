package com.thunder.wildernessodysseyapi.temporalrift.echo.structure;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class EchoStructurePolicyTest {
    private static final ResourceLocation EARTH = ResourceLocation.parse("example:towns");
    private static final ResourceLocation ECHO = ResourceLocation.parse("example:facilities");

    @Test
    void unmarkedModdedStructuresRemainShared() {
        assertEquals(Optional.of(EARTH), EchoStructurePolicy.resolve(EARTH, true, Map.of(), id -> true));
        assertEquals(Optional.of(EARTH), EchoStructurePolicy.resolve(EARTH, false, Map.of(), id -> true));
    }

    @Test
    void singleEarthPoliciesDoNotLeakIntoTheOtherWorld() {
        var earthOnly = Map.of(EARTH, new EchoStructurePolicy.Rule(EchoStructureMode.EARTH_ONLY, null));
        var echoOnly = Map.of(ECHO, new EchoStructurePolicy.Rule(EchoStructureMode.ECHO_ONLY, null));
        assertTrue(EchoStructurePolicy.resolve(EARTH, true, earthOnly, id -> true).isEmpty());
        assertTrue(EchoStructurePolicy.resolve(ECHO, false, echoOnly, id -> true).isEmpty());
        assertEquals(Optional.of(EARTH), EchoStructurePolicy.resolve(EARTH, false, earthOnly, id -> true));
        assertEquals(Optional.of(ECHO), EchoStructurePolicy.resolve(ECHO, true, echoOnly, id -> true));
    }

    @Test
    void variantsUseAuthoredTargetsAndMissingOptionalContentFallsBackToSource() {
        var rules = Map.of(EARTH, new EchoStructurePolicy.Rule(EchoStructureMode.ECHO_VARIANT, ECHO));
        assertEquals(Optional.of(EARTH), EchoStructurePolicy.resolve(EARTH, false, rules, id -> true));
        assertEquals(Optional.of(ECHO), EchoStructurePolicy.resolve(EARTH, true, rules, id -> true));
        assertEquals(Optional.of(EARTH), EchoStructurePolicy.resolve(EARTH, true, rules, id -> false));
    }
}
