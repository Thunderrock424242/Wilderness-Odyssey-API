package com.thunder.wildernessodysseyapi.dataengine.interest;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterestManagerTest {
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final ResourceLocation NETHER = ResourceLocation.withDefaultNamespace("the_nether");
    private static final InterestRegion REGION = new InterestRegion(OVERWORLD, 100, -40);
    private static final InterestProfile PROFILE = new InterestProfile(4, 12, 32);

    @Test
    void classifiesNearRegionalDistantAndOutsidePlayers() {
        assertEquals(InterestTier.NEAR, InterestManager.classify(OVERWORLD, 103, -41, REGION, PROFILE));
        assertEquals(InterestTier.REGIONAL, InterestManager.classify(OVERWORLD, 110, -40, REGION, PROFILE));
        assertEquals(InterestTier.DISTANT, InterestManager.classify(OVERWORLD, 125, -40, REGION, PROFILE));
        assertEquals(InterestTier.NONE, InterestManager.classify(OVERWORLD, 133, -40, REGION, PROFILE));
    }

    @Test
    void rejectsWrongDimensionRegardlessOfCoordinates() {
        assertEquals(InterestTier.NONE, InterestManager.classify(NETHER, 100, -40, REGION, PROFILE));
    }
}
