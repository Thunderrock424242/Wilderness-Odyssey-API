package com.thunder.wildernessodysseyapi.developmentstudio;

import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioRegionBlockSnapshot;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioResetPolicy;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegion;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegionRegistry;
import com.thunder.wildernessodysseyapi.developmentstudio.region.StudioTestRegionType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the small persisted-region trust boundary independently from a loaded world. */
class StudioTestRegionTest {

    @Test
    void defaultRegionsStayBoundedAndOnlyExpectedLabsCanReset() {
        List<StudioTestRegion> regions = StudioTestRegionRegistry.resolve(new BlockPos(100, 70, -50));

        assertEquals(4, regions.size());
        assertTrue(regions.stream().allMatch(region ->
                region.volume() > 0 && region.volume() <= StudioTestRegionRegistry.MAX_REGION_VOLUME));
        assertEquals(StudioResetPolicy.BLOCK_SNAPSHOT, region(regions,
                StudioTestRegionRegistry.STRUCTURE_LAB).resetPolicy());
        assertEquals(StudioResetPolicy.TAGGED_ENTITIES, region(regions,
                StudioTestRegionRegistry.ENTITY_LAB).resetPolicy());
        assertEquals(StudioResetPolicy.NONE, region(regions,
                StudioTestRegionRegistry.WATER_LAB).resetPolicy());
    }

    @Test
    void regionDecoderRejectsOversizedOrMalformedSavedBounds() {
        StudioTestRegion oversized = new StudioTestRegion(
                ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "oversized"),
                "Oversized",
                ResourceLocation.withDefaultNamespace("overworld"),
                BlockPos.ZERO,
                new BlockPos(40, 40, 40),
                StudioTestRegionType.STRUCTURE,
                StudioResetPolicy.BLOCK_SNAPSHOT
        );
        assertFalse(StudioTestRegion.load(oversized.save()).isPresent());

        CompoundTag malformed = oversized.save();
        malformed.putString("reset_policy", "DELETE_CHUNKS");
        assertFalse(StudioTestRegion.load(malformed).isPresent());
    }

    @Test
    void snapshotConstructorRejectsDuplicateAndOutOfBoundsEntries() {
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:stone");
        ResourceLocation regionId = ResourceLocation.fromNamespaceAndPath(
                "wildernessodysseyapi", "structure_lab"
        );
        ResourceLocation dimension = ResourceLocation.withDefaultNamespace("overworld");

        assertThrows(IllegalArgumentException.class, () -> new StudioRegionBlockSnapshot(
                regionId,
                dimension,
                BlockPos.ZERO,
                new BlockPos(1, 0, 0),
                List.of(
                        new StudioRegionBlockSnapshot.Entry(BlockPos.ZERO, state, null),
                        new StudioRegionBlockSnapshot.Entry(BlockPos.ZERO, state, null)
                )
        ));
        assertThrows(IllegalArgumentException.class, () -> new StudioRegionBlockSnapshot(
                regionId,
                dimension,
                BlockPos.ZERO,
                new BlockPos(1, 0, 0),
                List.of(new StudioRegionBlockSnapshot.Entry(new BlockPos(2, 0, 0), state, null))
        ));
    }

    private static StudioTestRegion region(List<StudioTestRegion> regions, ResourceLocation id) {
        return regions.stream().filter(region -> region.id().equals(id)).findFirst().orElseThrow();
    }
}
