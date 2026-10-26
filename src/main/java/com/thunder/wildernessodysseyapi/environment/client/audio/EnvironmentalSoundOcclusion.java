package com.thunder.wildernessodysseyapi.environment.client.audio;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.environment.audio.EnvironmentalAudioConfig;
import com.thunder.wildernessodysseyapi.environment.audio.SoundOcclusionModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.List;

/**
 * Shared, event-time attenuation for sparse outdoor sound emitters.
 *
 * <p>Runs only on the client thread and reads loaded chunks. It owns no sound
 * instances, world references, asynchronous work, filters or native audio resources.
 * Existing emitters retain sound position, category, pitch, cadence and ownership.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class EnvironmentalSoundOcclusion {

    private static final int MAXIMUM_BLOCK_READS_PER_TICK = 1_024;
    private static final int MAXIMUM_SHAPE_BOXES = 32;
    private static long budgetTick = Long.MIN_VALUE;
    private static int remainingBlockReads;

    private EnvironmentalSoundOcclusion() {
    }

    /** Returns an obstruction multiplier without changing the emitter's existing volume mix. */
    public static float gain(ClientLevel level, double sourceX, double sourceY, double sourceZ) {
        Minecraft minecraft = Minecraft.getInstance();
        if (level == null || !minecraft.isSameThread() || level != minecraft.level || minecraft.player == null
                || !EnvironmentalAudioConfig.ENABLE_OCCLUSION.get()
                || ModList.get().isLoaded("sound_physics_remastered")) {
            return 1.0f;
        }
        double strength = EnvironmentalAudioConfig.OCCLUSION_STRENGTH.get();
        if (strength <= 0.0) {
            return 1.0f;
        }
        if (budgetTick != level.getGameTime()) {
            budgetTick = level.getGameTime();
            remainingBlockReads = MAXIMUM_BLOCK_READS_PER_TICK;
        }
        Vec3 listener = minecraft.gameRenderer.getMainCamera().isInitialized()
                ? minecraft.gameRenderer.getMainCamera().getPosition() : minecraft.player.getEyePosition();
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        float transmission = SoundOcclusionModel.gain(
                new SoundOcclusionModel.Point(listener.x, listener.y, listener.z),
                new SoundOcclusionModel.Point(sourceX, sourceY, sourceZ),
                path -> sampleLoss(level, position, path));
        return (float) (1.0 - strength + strength * transmission);
    }

    /** Resets the small query budget on world/dimension unload; no world state is retained. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ClientLevel) {
            budgetTick = Long.MIN_VALUE;
            remainingBlockReads = 0;
        }
    }

    private static double sampleLoss(
            ClientLevel level, BlockPos.MutableBlockPos position, SoundOcclusionModel.CellPath path
    ) {
        if (remainingBlockReads <= 0) {
            return Double.NaN;
        }
        remainingBlockReads--;
        position.set(path.x(), path.y(), path.z());
        if (level.isOutsideBuildHeight(position)) {
            return 0.0;
        }
        if (!level.hasChunk(path.x() >> 4, path.z() >> 4)) {
            return Double.NaN;
        }
        BlockState state = level.getBlockState(position);
        if (state.isAir()) {
            return 0.0;
        }
        VoxelShape shape = state.getCollisionShape(level, position);
        if (shape.isEmpty()) {
            return 0.0;
        }
        double coefficient = state.is(BlockTags.WOOL) ? 2.8
                : state.is(BlockTags.LEAVES) ? 0.15
                : state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS) ? 1.0 : 1.6;
        if (shape == Shapes.block()) {
            return path.length() * coefficient;
        }
        List<AABB> boxes = shape.toAabbs();
        if (boxes.size() > MAXIMUM_SHAPE_BOXES) {
            // An unusually detailed modded shape gets a bounded, conservative
            // approximation instead of an unbounded per-box intersection loop.
            boxes = List.of(shape.bounds());
        }
        double thickness = 0.0;
        for (AABB box : boxes) {
            thickness += path.intersectionLength(
                    position.getX() + box.minX, position.getY() + box.minY, position.getZ() + box.minZ,
                    position.getX() + box.maxX, position.getY() + box.maxY, position.getZ() + box.maxZ);
        }
        return Math.min(path.length(), thickness) * coefficient;
    }
}
