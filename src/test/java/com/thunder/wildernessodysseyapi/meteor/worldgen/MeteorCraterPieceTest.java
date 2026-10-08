package com.thunder.wildernessodysseyapi.meteor.worldgen;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeteorCraterPieceTest {
    @Test
    void savedPieceRetainsItsGeometrySeedAndPlacementBounds() {
        MeteorCraterPlan plan = new MeteorCraterPlan(new BlockPos(-8, 82, 24), 125, 32, 16, 60, 91471L);
        MeteorCraterPiece piece = new MeteorCraterPiece(plan, -64, 319);

        MeteorCraterPiece restored = new MeteorCraterPiece(piece.createTag(null));

        assertEquals(plan, restored.plan());
        assertEquals(piece.getBoundingBox(), restored.getBoundingBox());
    }
}
