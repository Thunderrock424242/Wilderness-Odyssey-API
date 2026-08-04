package com.thunder.wildernessodysseyapi.watersystem.water.authority;

import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterBody;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterInteractionResult;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterSample;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedLocalFlow;
import com.thunder.wildernessodysseyapi.watersystem.water.hydrology.WatershedServices;
import com.thunder.wildernessodysseyapi.watersystem.water.config.WildernessWaterRules;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Adapts the existing {@link WildernessWaterAuthority} to the public API.
 *
 * <p>This class owns no water data. It translates stable API concepts into the
 * current canonical/large-body hybrid model and is therefore the only class
 * that should need adjustment when that storage model evolves.</p>
 */
public final class AuthorityWaterAccess implements WaterAccess {

    private static final ThreadLocal<WaterSample> SCRATCH = ThreadLocal.withInitial(WaterSample::new);

    @Override
    public boolean isWaterAt(Level level, BlockPos position) {
        return level != null && WildernessWaterAuthority.isWaterAt(level, position);
    }

    @Override
    public boolean isWaterAt(Level level, Vec3 position) {
        return isWaterAt(level, BlockPos.containing(position));
    }

    @Override
    public long getWaterUnits(Level level, BlockPos position) {
        if (level == null || position == null) {
            return 0L;
        }
        WildernessWaterAuthority.CellAuthority authority =
                WildernessWaterAuthority.sample(level, position);
        return authority.water() && authority.authorityOwned()
                ? authority.volumeUnits()
                : 0L;
    }

    @Override
    public boolean isSubmerged(Level level, AABB bounds) {
        WaterSample sample = SCRATCH.get();
        double x = (bounds.minX + bounds.maxX) * 0.5;
        double z = (bounds.minZ + bounds.maxZ) * 0.5;
        sample(level, x, bounds.minY, z, 0.0f, sample);
        return sample.water() && bounds.minY < sample.surfaceHeight();
    }

    @Override
    public double getSurfaceHeight(Level level, double x, double z) {
        return WildernessWaterAuthority.getSurfaceHeight(level, x, z, 0.0f);
    }

    @Override
    public double getDepth(Level level, Vec3 position) {
        if (!isWaterAt(level, position)) {
            return 0.0;
        }
        double surface = getSurfaceHeight(level, position.x, position.z);
        return Double.isNaN(surface) ? 0.0 : Math.max(0.0, surface - position.y);
    }

    @Override
    public Vec3 getCurrent(Level level, Vec3 position) {
        WaterSample result = SCRATCH.get();
        sample(level, position.x, position.y, position.z, 0.0f, result);
        if (!result.water()) {
            return Vec3.ZERO;
        }
        return new Vec3(result.currentX(), result.currentY(), result.currentZ());
    }

    @Override
    public Optional<WaterBody> getWaterBody(Level level, BlockPos position) {
        WildernessWaterAuthority.SurfaceAuthority surface = WildernessWaterAuthority.sampleSurface(
                level,
                position.getX() + 0.5,
                position.getZ() + 0.5,
                0.0f
        );
        if (surface.valid()) {
            WaterBody.Kind kind = switch (surface.waterType()) {
                case "OCEAN" -> WaterBody.Kind.LARGE_OCEAN;
                case "RIVER" -> WaterBody.Kind.LARGE_RIVER;
                default -> WaterBody.Kind.LARGE_POND;
            };
            return Optional.of(new WaterBody(
                    level.dimension(),
                    ChunkPos.asLong(surface.chunkX(), surface.chunkZ()),
                    kind,
                    surface.surfaceHeight(),
                    surface.depth(),
                    surface.estimatedVolumeUnits(),
                    new Vec3(surface.currentX(), surface.currentY(), surface.currentZ())
            ));
        }

        WildernessWaterAuthority.CellAuthority cell = WildernessWaterAuthority.sample(level, position);
        if (!cell.water() || !cell.authorityOwned()) {
            return Optional.empty();
        }
        return Optional.of(new WaterBody(
                level.dimension(),
                position.asLong(),
                WaterBody.Kind.LOCAL_VOLUME,
                position.getY() + cell.surfaceFillHeight(),
                WildernessWaterAuthority.getWaterDepth(level, position),
                cell.volumeUnits(),
                new Vec3(cell.velocityX(), cell.velocityY(), cell.velocityZ())
        ));
    }

    @Override
    public WatershedConditions getWatershedConditions(Level level, BlockPos position) {
        return WildernessWaterAuthority.getWatershedConditions(level, position);
    }

    @Override
    public WatershedLocalFlow getLocalWatershedFlow(Level level, BlockPos position) {
        return WatershedServices.localFlow(level, position);
    }

    @Override
    public boolean canAddWater(Level level, BlockPos position) {
        return level instanceof ServerLevel serverLevel
                && WildernessWaterRules.isEnabled(serverLevel)
                && com.thunder.wildernessodysseyapi.watersystem.water.volume.CanonicalWater
                .canAcceptVolume(serverLevel, position);
    }

    @Override
    public void sample(
            Level level,
            double x,
            double y,
            double z,
            float partialTick,
            WaterSample result
    ) {
        result.clear();
        if (level == null || !WildernessWaterRules.isEnabled(level)) {
            return;
        }

        BlockPos pos = BlockPos.containing(x, y, z);
        WildernessWaterAuthority.CellAuthority cell = WildernessWaterAuthority.sample(level, pos);
        WildernessWaterAuthority.SurfaceAuthority surface = WildernessWaterAuthority.sampleSurface(
                level,
                x,
                z,
                partialTick
        );
        if (surface.valid()) {
            boolean water = cell.water() && cell.authorityOwned() && y <= surface.surfaceHeight();
            result.set(
                    water,
                    surface.surfaceHeight(),
                    water ? Math.max(0.0, surface.surfaceHeight() - y) : 0.0,
                    surface.currentX(),
                    surface.currentY(),
                    surface.currentZ(),
                    surface.normalX(),
                    surface.normalY(),
                    surface.normalZ()
            );
            return;
        }
        if (cell.water() && cell.authorityOwned()) {
            double surfaceHeight = pos.getY() + cell.surfaceFillHeight();
            result.set(
                    y <= surfaceHeight,
                    surfaceHeight,
                    Math.max(0.0, surfaceHeight - y),
                    cell.velocityX(),
                    cell.velocityY(),
                    cell.velocityZ(),
                    0.0,
                    1.0,
                    0.0
            );
        }
    }

    @Override
    public WaterInteractionResult addWater(
            Level level,
            BlockPos position,
            long amountUnits,
            boolean simulate
    ) {
        WaterInteractionResult rejected = validateMutation(level, amountUnits, simulate);
        if (rejected != null) {
            return rejected;
        }
        int requested = boundedAmount(amountUnits);
        int transferred = WildernessWaterAuthority.addWaterVolume(level, position, requested, simulate);
        return WaterInteractionResult.transferred(amountUnits, transferred, simulate);
    }

    @Override
    public WaterInteractionResult removeWater(
            Level level,
            BlockPos position,
            long amountUnits,
            boolean simulate
    ) {
        WaterInteractionResult rejected = validateMutation(level, amountUnits, simulate);
        if (rejected != null) {
            return rejected;
        }
        int requested = boundedAmount(amountUnits);
        int transferred = WildernessWaterAuthority.removeWaterVolume(level, position, requested, simulate);
        return WaterInteractionResult.transferred(amountUnits, transferred, simulate);
    }

    private static WaterInteractionResult validateMutation(Level level, long amountUnits, boolean simulate) {
        long request = Math.max(0L, amountUnits);
        if (level == null || amountUnits <= 0L) {
            return new WaterInteractionResult(
                    WaterInteractionResult.Outcome.POSITION_UNAVAILABLE,
                    request,
                    0L,
                    simulate
            );
        }
        if (!WildernessWaterRules.isEnabled(level)) {
            return new WaterInteractionResult(
                    WaterInteractionResult.Outcome.DISABLED,
                    request,
                    0L,
                    simulate
            );
        }
        if (!(level instanceof ServerLevel)) {
            return new WaterInteractionResult(
                    WaterInteractionResult.Outcome.CLIENT_READ_ONLY,
                    request,
                    0L,
                    simulate
            );
        }
        return null;
    }

    private static int boundedAmount(long amountUnits) {
        return (int) Math.min(Integer.MAX_VALUE, amountUnits);
    }
}
