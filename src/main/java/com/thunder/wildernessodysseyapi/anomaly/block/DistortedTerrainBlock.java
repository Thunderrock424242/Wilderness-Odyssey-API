package com.thunder.wildernessodysseyapi.anomaly.block;

import com.thunder.wildernessodysseyapi.anomaly.config.AnomalyClientConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/** Ordinary solid terrain with sparse client crack motes and no server ticking. */
public final class DistortedTerrainBlock extends Block {
    private static final Direction[] FACES = Direction.values();

    private final AnomalyBlockMaterial material;
    private final DustParticleOptions crackMote;

    public DistortedTerrainBlock(Properties properties, AnomalyBlockMaterial material) {
        super(properties);
        this.material = material;
        int color = material.energyRgb();
        this.crackMote = new DustParticleOptions(new Vector3f(
                ((color >> 16) & 255) / 255.0F,
                ((color >> 8) & 255) / 255.0F,
                (color & 255) / 255.0F
        ), 0.45F);
    }

    /** Exposes material intent to optional visual integrations without shader dependencies. */
    public AnomalyBlockMaterial material() {
        return material;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!material.shouldEmitCrackMote(level.isClientSide,
                AnomalyClientConfig.crackParticlesEnabled(), random.nextInt(material.moteSampleBound()))) {
            return;
        }

        // Inspect only one sampled, already-loaded neighbor. Buried seams do not
        // spend particles, and a chunk edge must never cause loading for visuals.
        Direction face = FACES[random.nextInt(FACES.length)];
        BlockPos neighbor = pos.relative(face);
        if (!level.hasChunkAt(neighbor) || level.getBlockState(neighbor).isSolidRender(level, neighbor)) {
            return;
        }

        double x = pos.getX() + (face.getAxis() == Direction.Axis.X
                ? 0.5D + face.getStepX() * 0.51D : 0.2D + random.nextDouble() * 0.6D);
        double y = pos.getY() + (face.getAxis() == Direction.Axis.Y
                ? 0.5D + face.getStepY() * 0.51D : 0.2D + random.nextDouble() * 0.6D);
        double z = pos.getZ() + (face.getAxis() == Direction.Axis.Z
                ? 0.5D + face.getStepZ() * 0.51D : 0.2D + random.nextDouble() * 0.6D);
        level.addParticle(crackMote, x, y, z,
                face.getStepX() * 0.008D, 0.008D + face.getStepY() * 0.004D, face.getStepZ() * 0.008D);
    }
}
