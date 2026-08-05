package com.thunder.wildernessodysseyapi.anomaly;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies dimension-aware gateway return data and legacy fallbacks. */
class AnomalyGatewayTravelDataTest {

    @Test
    void roundTripsBeforeGatewayAndCoordinates() {
        CompoundTag data = new CompoundTag();
        BlockPos gatewayPos = new BlockPos(-145, 72, 913);

        AnomalyGatewayTravelData.store(data, TemporalRiftDimensions.THE_BEFORE_KEY, gatewayPos);
        AnomalyGatewayTravelData.ReturnTarget target = AnomalyGatewayTravelData.read(
                data,
                Level.OVERWORLD,
                BlockPos.ZERO
        );

        assertEquals(TemporalRiftDimensions.THE_BEFORE_KEY, target.dimension());
        assertEquals(gatewayPos, target.gatewayPos());
    }

    @Test
    void legacyCoordinatesFallBackToOverworld() {
        CompoundTag data = new CompoundTag();
        BlockPos legacyGateway = new BlockPos(20, 64, -30);
        data.putInt("anomaly_gateway_return_x", legacyGateway.getX());
        data.putInt("anomaly_gateway_return_y", legacyGateway.getY());
        data.putInt("anomaly_gateway_return_z", legacyGateway.getZ());

        AnomalyGatewayTravelData.ReturnTarget target = AnomalyGatewayTravelData.read(
                data,
                Level.OVERWORLD,
                BlockPos.ZERO
        );

        assertEquals(Level.OVERWORLD, target.dimension());
        assertEquals(legacyGateway, target.gatewayPos());
    }

    @Test
    void rejectsUnsupportedStoredDimensionAndClearsConsumedLink() {
        CompoundTag data = new CompoundTag();
        BlockPos storedPos = new BlockPos(9, 80, 11);
        AnomalyGatewayTravelData.store(data, Level.NETHER, storedPos);

        AnomalyGatewayTravelData.ReturnTarget rejected = AnomalyGatewayTravelData.read(
                data,
                Level.OVERWORLD,
                BlockPos.ZERO
        );
        assertEquals(Level.OVERWORLD, rejected.dimension());
        assertEquals(storedPos, rejected.gatewayPos());

        AnomalyGatewayTravelData.clear(data);
        BlockPos fallback = new BlockPos(1, 2, 3);
        AnomalyGatewayTravelData.ReturnTarget cleared = AnomalyGatewayTravelData.read(
                data,
                Level.OVERWORLD,
                fallback
        );
        assertEquals(Level.OVERWORLD, cleared.dimension());
        assertEquals(fallback, cleared.gatewayPos());
    }
}
