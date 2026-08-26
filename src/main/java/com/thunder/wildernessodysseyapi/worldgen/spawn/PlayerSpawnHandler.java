package com.thunder.wildernessodysseyapi.worldgen.spawn;

import com.thunder.wildernessodysseyapi.cinematic.CinematicManager;
import com.thunder.wildernessodysseyapi.cinematic.CinematicPlaybackOptions;
import com.thunder.wildernessodysseyapi.cinematic.CinematicPlayerData;
import com.thunder.wildernessodysseyapi.cinematic.CinematicSequences;
import com.thunder.wildernessodysseyapi.cryo.block.CryoTubeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;
import static com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER;

/**
 * Handles assigning players to cryo tubes when they first join the world.
 */
@EventBusSubscriber(modid = MOD_ID)
public class PlayerSpawnHandler {

    private static final String CRYO_ASSIGNED_TAG = "wo_cryo_assigned";
    private static final String CRYO_POS_TAG = "wo_cryo_pos";
    private static final int ASSIGNMENT_RETRY_INTERVAL_TICKS = 20;

    private static List<BlockPos> spawnBlocks = Collections.emptyList();
    private static final Set<UUID> PENDING_ASSIGNMENTS = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (tryAssignSpawn(player)) {
                PENDING_ASSIGNMENTS.remove(player.getUUID());
            } else {
                PENDING_ASSIGNMENTS.add(player.getUUID());
            }
        }
    }

    /**
     * Retries assignment once per second while waiting for cryo tubes to be discovered.
     *
     * <p>Login still performs an immediate attempt. The slower fallback bridges
     * asynchronous starter-bunker placement without routing every player
     * through persistent-data and spawn-list checks on every server tick.</p>
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player
                && shouldRetryAssignment(PENDING_ASSIGNMENTS.contains(player.getUUID()), player.tickCount)
                && tryAssignSpawn(player)) {
            PENDING_ASSIGNMENTS.remove(player.getUUID());
        }
    }

    static boolean shouldRetryAssignment(boolean pending, int playerTickCount) {
        return pending && playerTickCount % ASSIGNMENT_RETRY_INTERVAL_TICKS == 0;
    }

    public static void setSpawnBlocks(List<BlockPos> blocks) {
        spawnBlocks = blocks == null ? Collections.emptyList() : List.copyOf(blocks);
    }

    /** Removes disconnected players from the small pending retry set. */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_ASSIGNMENTS.remove(event.getEntity().getUUID());
    }

    /** Preserves the one-time cryo assignment when Minecraft replaces the player entity. */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        CompoundTag original = event.getOriginal().getPersistentData();
        CompoundTag replacement = event.getEntity().getPersistentData();
        if (original.getBoolean(CRYO_ASSIGNED_TAG)) {
            replacement.putBoolean(CRYO_ASSIGNED_TAG, true);
        }
        if (original.contains(CRYO_POS_TAG)) {
            replacement.putLong(CRYO_POS_TAG, original.getLong(CRYO_POS_TAG));
        }
    }

    /** Releases discovery and retry state before another server starts in this process. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING_ASSIGNMENTS.clear();
        spawnBlocks = Collections.emptyList();
    }

    private static boolean tryAssignSpawn(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();

        if (tag.getBoolean(CRYO_ASSIGNED_TAG)) {
            return tryResumeInterruptedIntro(player, tag);
        }
        if (spawnBlocks.isEmpty()) {
            return false;
        }

        BlockPos spawnPos = selectSpawn(player, tag);
        if (spawnPos == null) {
            return false;
        }

        tag.putBoolean(CRYO_ASSIGNED_TAG, true);
        tag.putLong(CRYO_POS_TAG, spawnPos.asLong());
        CinematicManager.PlayResult introResult = CinematicManager.play(
                player,
                CinematicSequences.CRYO_WAKEUP,
                CinematicPlaybackOptions.automatic(spawnPos)
        );
        if (!introResult.started() && !introResult.retryable()) {
            // Preserve the original spawn-placement behavior if the cinematic
            // cannot start for a non-transient reason. A shared actor that is
            // merely busy remains queued without stacking players in the pod.
            teleportPlayer(player, spawnPos);
        }
        logNonRetryableIntroFailure(player, introResult);
        return !introResult.retryable();
    }

    private static boolean tryResumeInterruptedIntro(ServerPlayer player, CompoundTag tag) {
        if (!CinematicPlayerData.hasAutomaticStarted(player, CinematicSequences.CRYO_WAKEUP.id())
                || CinematicPlayerData.hasCompleted(player, CinematicSequences.CRYO_WAKEUP.id())
                || CinematicManager.isActive(player)
                || !tag.contains(CRYO_POS_TAG)) {
            return true;
        }

        BlockPos stored = BlockPos.of(tag.getLong(CRYO_POS_TAG));
        if (!isCryoTube(player, stored)) {
            return true;
        }
        CinematicManager.PlayResult result = CinematicManager.play(
                player,
                CinematicSequences.CRYO_WAKEUP,
                CinematicPlaybackOptions.automatic(stored)
        );
        logNonRetryableIntroFailure(player, result);
        return !result.retryable();
    }

    private static void logNonRetryableIntroFailure(
            ServerPlayer player,
            CinematicManager.PlayResult result
    ) {
        if (!result.started() && !result.retryable()) {
            LOGGER.warn("Automatic cryo intro did not start for {}: {}",
                    player.getGameProfile().getName(), result.message().getString());
        }
    }

    private static BlockPos selectSpawn(ServerPlayer player, CompoundTag tag) {
        if (tag.contains(CRYO_POS_TAG)) {
            BlockPos stored = BlockPos.of(tag.getLong(CRYO_POS_TAG));
            if (isCryoTube(player, stored)) {
                return stored;
            }
            tag.remove(CRYO_POS_TAG);
        }

        RandomSource random = player.serverLevel().getRandom();
        return spawnBlocks.get(random.nextInt(spawnBlocks.size()));
    }

    private static boolean isCryoTube(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().getBlockState(pos).is(CryoTubeBlock.CRYO_TUBE.get());
    }

    private static void teleportPlayer(ServerPlayer player, BlockPos spawnPos) {
        player.teleportTo(player.serverLevel(),
                spawnPos.getX() + 0.5,
                spawnPos.getY() + 0.5,
                spawnPos.getZ() + 0.5,
                player.getYRot(),
                player.getXRot());
    }
}
