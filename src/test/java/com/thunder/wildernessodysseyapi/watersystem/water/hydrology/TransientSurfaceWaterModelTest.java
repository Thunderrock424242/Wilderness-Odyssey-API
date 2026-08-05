package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.DrainageDirection;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions.WaterFeature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies terrain depression, formation, and dry-weather recession rules. */
class TransientSurfaceWaterModelTest {

    @Test
    void depressionDepthUsesLowestRimAndRejectsOpenDrainage() {
        assertEquals(2, TransientSurfaceWaterModel.depressionDepth(
                64, 67, 66, 68, 69, 66, 67, 68, 67
        ));
        assertEquals(0, TransientSurfaceWaterModel.depressionDepth(
                64, 67, 66, 63, 69
        ));
    }

    @Test
    void saturatedClosedDepressionFormsRainPond() {
        SurfaceWaterKind kind = TransientSurfaceWaterModel.formationKind(
                conditions(1.0f, 1.0f, 0.4f, 0.55f, 0.0f),
                2,
                true,
                SurfaceWaterKind.NONE,
                0.68f,
                0.58f,
                0.78f
        );

        assertEquals(SurfaceWaterKind.RAIN_POND, kind);
    }

    @Test
    void highWaterTableAndDischargeFormSpringBeforePond() {
        SurfaceWaterKind kind = TransientSurfaceWaterModel.formationKind(
                conditions(0.8f, 0.6f, 0.0f, 0.94f, 0.02f),
                1,
                true,
                SurfaceWaterKind.NONE,
                0.68f,
                0.58f,
                0.78f
        );

        assertEquals(SurfaceWaterKind.SPRING, kind);
    }

    @Test
    void dryPondSurvivesMinimumLifetimeThenRecedes() {
        WatershedConditions dry = conditions(0.05f, 0.0f, 0.0f, 0.08f, 0.0f);

        assertTrue(TransientSurfaceWaterModel.retains(
                SurfaceWaterKind.RAIN_POND, dry, 400L, 1200,
                0.68f, 0.58f, 0.78f
        ));
        assertFalse(TransientSurfaceWaterModel.retains(
                SurfaceWaterKind.RAIN_POND, dry, 1600L, 1200,
                0.68f, 0.58f, 0.78f
        ));
    }

    private static WatershedConditions conditions(
            float saturation,
            float rainfall,
            float snowmelt,
            float storage,
            float discharge
    ) {
        return new WatershedConditions(
                77L, 68, DrainageDirection.SINK, 0.25f,
                saturation, rainfall, snowmelt,
                0.1f, storage, discharge,
                0.0f, 0.0f, 0.0f, 0.0f, 0.88f,
                false, 0, 0,
                0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
                WaterFeature.NONE
        );
    }
}
