package com.thunder.wildernessodysseyapi.temporalrift.echo;

import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.Set;

/**
 * Records server-observed Echo evidence in the existing NeoForge player persistence lifecycle.
 *
 * <p>Personal AI memories are untrusted dialogue, so they cannot grant discoveries.
 * The bounded evidence lives in PlayerPersisted NBT, follows ordinary player saves
 * and respawns, and needs no separate global store, file writes or synchronization.</p>
 */
public final class EchoDiscoveryManager {
    private static final String ROOT = "wildernessodysseyapi_echo_discoveries";
    private static final String EVIDENCE = "evidence";
    private static final String FRACTURE = "fracture_observed";
    private static final String MATCHES = "matching_chunks";
    private static final String LAST_SURVEY = "last_survey";
    private static final int REQUIRED_MATCHES = 3;

    private EchoDiscoveryManager() { }

    /** Records first arrival without claiming independent history or the cause of the fracture. */
    public static void observeVisit(ServerPlayer player) {
        observe(player, EchoDiscoveryStage.TERRAIN_SIMILARITY);
    }

    /**
     * Samples one nearby column at most every five seconds, using only already loaded chunks.
     * Three distinct matching chunks provide repeat correspondence; time spent standing still does not.
     */
    public static void tick(ServerPlayer player) {
        if (!inEcho(player)) {
            return;
        }
        observeVisit(player);
        CompoundTag state = state(player);
        if ((state.getInt(EVIDENCE) & EchoDiscoveryStage.TERRAIN_CORRESPONDENCE.evidenceBit()) != 0) {
            return;
        }
        long gameTime = player.serverLevel().getGameTime();
        long previousSurvey = state.getLong(LAST_SURVEY);
        if (state.contains(LAST_SURVEY) && gameTime >= previousSurvey && gameTime - previousSurvey < 100) {
            return;
        }
        state.putLong(LAST_SURVEY, gameTime);
        save(player, state);
        BlockPos position = player.blockPosition();
        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        long[] matches = state.getLongArray(MATCHES);
        for (long match : matches) {
            if (match == chunkKey) {
                return;
            }
        }
        ServerLevel earth = player.server.overworld();
        LevelChunk earthChunk = earth.getChunkSource().getChunkNow(chunkX, chunkZ);
        LevelChunk echoChunk = player.serverLevel().getChunkSource().getChunkNow(chunkX, chunkZ);
        if (earthChunk == null || echoChunk == null) {
            return;
        }
        // Heightmap and biome samples are constant-time reads; no blocks or neighbor chunks are scanned.
        int x = position.getX();
        int z = position.getZ();
        int earthHeight = earthChunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        int echoHeight = echoChunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
        if (Math.abs(earthHeight - echoHeight) > 3
                || !earthChunk.getNoiseBiome(x >> 2, earthHeight >> 2, z >> 2).equals(
                        echoChunk.getNoiseBiome(x >> 2, echoHeight >> 2, z >> 2))) {
            return;
        }
        int length = Math.min(matches.length, REQUIRED_MATCHES - 1);
        long[] updated = Arrays.copyOf(matches, length + 1);
        updated[length] = chunkKey;
        state.putLongArray(MATCHES, updated);
        save(player, state);
        if (updated.length >= REQUIRED_MATCHES) {
            observe(player, EchoDiscoveryStage.TERRAIN_CORRESPONDENCE);
        }
    }

    /** Called only after observing authored divergent infrastructure, never for mere region instability. */
    public static void observeStructure(ServerPlayer player) {
        observe(player, EchoDiscoveryStage.INDEPENDENT_HISTORY);
    }

    /** Called only for a visible, actually placed build echo; queued or skipped transfers provide no evidence. */
    public static void observeRealityEcho(ServerPlayer player) {
        observe(player, EchoDiscoveryStage.MATERIAL_SYNCHRONIZATION);
    }

    /** Records a known nearby fracture; it cannot explain synchronization without material evidence. */
    public static void observeFracture(ServerPlayer player) {
        if (!inEcho(player)) {
            return;
        }
        CompoundTag state = state(player);
        if (!state.getBoolean(FRACTURE)) {
            state.putBoolean(FRACTURE, true);
            state.putInt(EVIDENCE, EchoDiscoveryStage.withAlignmentHypothesis(state.getInt(EVIDENCE), true));
            save(player, state);
        }
    }

    /** Captures earned facts on the logical server before Aether's existing asynchronous request. */
    public static Set<String> contextTags(ServerPlayer player) {
        return EchoDiscoveryStage.contextTags(state(player).getInt(EVIDENCE));
    }

    /** Returns the highest earned finding for diagnostics without implying missing intermediate evidence. */
    public static EchoDiscoveryStage stage(ServerPlayer player) {
        return EchoDiscoveryStage.fromEvidence(state(player).getInt(EVIDENCE));
    }

    private static void observe(ServerPlayer player, EchoDiscoveryStage stage) {
        if (!inEcho(player)) {
            return;
        }
        CompoundTag state = state(player);
        int previous = state.getInt(EVIDENCE);
        int evidence = EchoDiscoveryStage.withAlignmentHypothesis(previous | stage.evidenceBit(), state.getBoolean(FRACTURE));
        if (evidence != previous) {
            state.putInt(EVIDENCE, evidence);
            save(player, state);
        }
    }

    private static boolean inEcho(ServerPlayer player) {
        return player != null && player.serverLevel().dimension().equals(TemporalRiftDimensions.THE_ECHO_KEY);
    }

    private static CompoundTag state(ServerPlayer player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(ROOT);
    }

    private static void save(ServerPlayer player, CompoundTag state) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        persisted.put(ROOT, state);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
    }
}
