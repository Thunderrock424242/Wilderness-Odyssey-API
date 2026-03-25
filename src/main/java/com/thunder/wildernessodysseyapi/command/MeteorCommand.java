package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Spawns command-driven meteor impacts near the command executor.
 */
public final class MeteorCommand {
    private static final int MIN_SIZE = 3;
    private static final int MAX_SIZE = 12;
    private static final int MIN_DISTANCE = 12;
    private static final int MAX_DISTANCE = 128;

    private MeteorCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("meteor")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("summon")
                        .then(Commands.argument("size", IntegerArgumentType.integer(MIN_SIZE, MAX_SIZE))
                                .executes(ctx -> summonMeteor(
                                        ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "size"),
                                        48
                                ))
                                .then(Commands.argument("range", IntegerArgumentType.integer(MIN_DISTANCE, MAX_DISTANCE))
                                        .executes(ctx -> summonMeteor(
                                                ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "size"),
                                                IntegerArgumentType.getInteger(ctx, "range")
                                        ))))));
    }

    private static int summonMeteor(CommandSourceStack source, int size, int range) {
        if (!(source.getLevel() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("Meteor command can only run on a server level."));
            return 0;
        }

        Vec3 origin = source.getPosition();
        RandomSource random = level.random;
        double angle = random.nextDouble() * Mth.TWO_PI;
        double distance = MIN_DISTANCE + random.nextDouble() * Math.max(1, range - MIN_DISTANCE);

        int targetX = Mth.floor(origin.x + Math.cos(angle) * distance);
        int targetZ = Mth.floor(origin.z + Math.sin(angle) * distance);
        BlockPos impactPos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(targetX, 0, targetZ));

        // If an overhang exists, ray trace down so impact feels grounded to visible terrain.
        Vec3 start = new Vec3(targetX + 0.5, impactPos.getY() + 40, targetZ + 0.5);
        Vec3 end = new Vec3(targetX + 0.5, impactPos.getY() - 20, targetZ + 0.5);
        BlockHitResult hitResult = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            impactPos = hitResult.getBlockPos().above();
        }

        createImpact(level, impactPos, size, random);

        BlockPos finalImpactPos = impactPos;
        source.sendSuccess(() -> Component.literal(String.format(
                "Meteor impact created at %d, %d, %d (size %d)",
                finalImpactPos.getX(),
                finalImpactPos.getY(),
                finalImpactPos.getZ(),
                size
        )), true);
        return Command.SINGLE_SUCCESS;
    }

    private static void createImpact(ServerLevel level, BlockPos impactPos, int size, RandomSource random) {
        int craterRadius = size + 2;
        carveCrater(level, impactPos, craterRadius);
        placeMeteorMass(level, impactPos, size, random);
        igniteArea(level, impactPos, size + 3, random);
        spawnDebris(level, impactPos, size, random);

        float explosionPower = size * 0.9F;
        level.explode(null, impactPos.getX() + 0.5, impactPos.getY(), impactPos.getZ() + 0.5,
                explosionPower, Level.ExplosionInteraction.TNT);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                impactPos.getX() + 0.5,
                impactPos.getY() + 1.0,
                impactPos.getZ() + 0.5,
                1,
                0,
                0,
                0,
                0);
    }

    private static void carveCrater(ServerLevel level, BlockPos impactPos, int radius) {
        int radiusSq = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    int distSq = x * x + (y * 2) * (y * 2) + z * z;
                    if (distSq > radiusSq) {
                        continue;
                    }
                    BlockPos cursor = impactPos.offset(x, y - 1, z);
                    if (!level.isOutsideBuildHeight(cursor)) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    private static void placeMeteorMass(ServerLevel level, BlockPos impactPos, int size, RandomSource random) {
        BlockPos center = impactPos.above(Math.max(1, size / 2));
        int radiusSq = size * size;
        for (int x = -size; x <= size; x++) {
            for (int y = -size; y <= size; y++) {
                for (int z = -size; z <= size; z++) {
                    int distSq = x * x + y * y + z * z;
                    if (distSq > radiusSq) {
                        continue;
                    }

                    BlockPos cursor = center.offset(x, y, z);
                    if (level.isOutsideBuildHeight(cursor)) {
                        continue;
                    }

                    // Crying obsidian is both the shell and the inner core.
                    BlockState state = Blocks.CRYING_OBSIDIAN.defaultBlockState();
                    if (distSq >= (radiusSq - Math.max(2, size))) {
                        state = Blocks.CRYING_OBSIDIAN.defaultBlockState();
                    }
                    level.setBlock(cursor, state, 3);
                }
            }
        }

        // Scatter extra crying obsidian around the outside rim.
        int ring = size + 2;
        for (int i = 0; i < size * 8; i++) {
            BlockPos rim = impactPos.offset(random.nextInt(ring * 2 + 1) - ring, 0, random.nextInt(ring * 2 + 1) - ring);
            BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, rim);
            if (!level.isOutsideBuildHeight(top)) {
                level.setBlock(top, Blocks.CRYING_OBSIDIAN.defaultBlockState(), 3);
            }
        }
    }

    private static void igniteArea(ServerLevel level, BlockPos impactPos, int radius, RandomSource random) {
        for (int i = 0; i < radius * 8; i++) {
            BlockPos around = impactPos.offset(random.nextInt(radius * 2 + 1) - radius, 0, random.nextInt(radius * 2 + 1) - radius);
            BlockPos firePos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, around).above();
            if (BaseFireBlock.canBePlacedAt(level, firePos, net.minecraft.core.Direction.UP)) {
                level.setBlock(firePos, BaseFireBlock.getState(level, firePos), 3);
            }
        }
    }

    private static void spawnDebris(ServerLevel level, BlockPos impactPos, int size, RandomSource random) {
        int pieces = size * 12;
        for (int i = 0; i < pieces; i++) {
            BlockState debrisState = random.nextFloat() < 0.7F
                    ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                    : Blocks.OBSIDIAN.defaultBlockState();

            FallingBlockEntity debris = FallingBlockEntity.fall(level, impactPos.above(2 + random.nextInt(3)), debrisState);
            double vx = (random.nextDouble() - 0.5D) * 1.0D;
            double vy = 0.2D + random.nextDouble() * 0.6D;
            double vz = (random.nextDouble() - 0.5D) * 1.0D;
            debris.setDeltaMovement(vx, vy, vz);
            debris.time = 1;
        }
    }
}
