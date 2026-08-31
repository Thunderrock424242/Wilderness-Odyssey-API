package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the narrow vanilla identity-check mixins remain wired to their exact 1.21.1 call sites. */
class WaterBucketMixinContractTest {

    private static final Path PROJECT = Path.of(
            System.getProperty("wildernessodysseyapi.projectDir", ".")
    );

    @Test
    void waterloggingMixinTranslatesContainerChecksAndStoredFluid() throws IOException {
        String source = readMixin("BucketItemWaterloggingMixin.java");

        assertTrue(source.contains("method = \"canBlockContainFluid\""));
        assertTrue(source.contains("require = 2"),
                "Both canPlaceLiquid calls in BucketItem.emptyContents must remain wrapped");
        assertTrue(source.contains("LiquidBlockContainer;")
                        && source.contains("placeLiquid(Lnet/minecraft/world/level/LevelAccessor;"),
                "The exact LiquidBlockContainer.placeLiquid call must remain wrapped");
        assertTrue(source.contains("Fluids.WATER.getSource(false)"),
                "Waterlogged hosts must retain vanilla water storage semantics");
    }

    @Test
    void bucketableMixinOnlyBridgesTheExactWaterBucketComparison() throws IOException {
        String source = readMixin("BucketableWaterBucketMixin.java");

        assertTrue(source.contains("@Mixin(Bucketable.class)"));
        assertTrue(source.contains("Lnet/minecraft/world/item/ItemStack;getItem()"));
        assertTrue(source.contains("WILDERNESS_WATER_BUCKET.get()"));
        assertTrue(source.contains("? Items.WATER_BUCKET"));
    }

    @Test
    void bucketPlacementUsesCanonicalFlowWithoutSphEffects() throws IOException {
        String source = readMixin("BucketPlaceMixin.java");

        assertTrue(source.contains("CanonicalWater.placeBucket(serverLevel, pos);"));
        assertFalse(source.contains("SphLocalEffectPayload"));
        assertFalse(source.contains("createBucketSplash"));
    }

    @Test
    void bothCompatibilityMixinsAreRegistered() throws IOException {
        String config = Files.readString(PROJECT.resolve(
                "src/main/resources/mixins.wildernessodysseyapi.json"
        ));

        assertTrue(config.contains("\"BucketItemWaterloggingMixin\""));
        assertTrue(config.contains("\"BucketableWaterBucketMixin\""));
    }

    @Test
    void customBucketPublishesTheStandardWaterBucketTag() throws IOException {
        String tag = Files.readString(PROJECT.resolve(
                "src/main/resources/data/c/tags/item/buckets/water.json"
        ));

        assertTrue(tag.contains("\"replace\": false"));
        assertTrue(tag.contains("\"wildernessodysseyapi:wilderness_water_bucket\""));
    }

    @Test
    void commonSetupBootstrapsExactItemCompatibility() throws IOException {
        String source = Files.readString(PROJECT.resolve(
                "src/main/java/com/thunder/wildernessodysseyapi/core/"
                        + "WildernessOdysseyAPIMainModClass.java"
        ));

        assertTrue(source.contains("VanillaWaterBucketCompatibility.bootstrap();"));
    }

    private static String readMixin(String fileName) throws IOException {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/thunder/wildernessodysseyapi/mixin/" + fileName
        ));
    }
}
