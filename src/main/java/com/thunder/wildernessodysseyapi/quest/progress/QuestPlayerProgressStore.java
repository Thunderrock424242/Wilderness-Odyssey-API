package com.thunder.wildernessodysseyapi.quest.progress;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** Game-thread player persistence under a new root. Minecraft autosave is not an inventory transaction. */
public final class QuestPlayerProgressStore {
    public static final String ROOT_TAG = "wildernessodysseyapi_quests";
    private final QuestProgressCodec codec = new QuestProgressCodec();

    public QuestPlayerProgress load(ServerPlayer player) {
        requireServerThread(player);
        var persistent = player.getPersistentData();
        if (!persistent.contains(ROOT_TAG)) return QuestPlayerProgress.empty(player.getUUID());
        if (!persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)) throw new IllegalStateException("Quest data is malformed and has been preserved.");
        var result = codec.decode(persistent.getCompound(ROOT_TAG));
        var progress = result.progress().orElseThrow(() -> new IllegalStateException(result.message()));
        if (!progress.playerId().equals(player.getUUID())) throw new IllegalStateException("Quest data belongs to another player and has been preserved.");
        return progress;
    }

    public void save(ServerPlayer player, QuestPlayerProgress progress) {
        requireServerThread(player);
        replaceRoot(player.getPersistentData(), player.getUUID(), progress);
    }

    /** Shared by the actual server adapter and pure preservation tests. */
    public static void replaceRoot(CompoundTag persistent, UUID owner, QuestPlayerProgress progress) {
        if (!owner.equals(progress.playerId())) throw new IllegalArgumentException("Quest progress owner mismatch.");
        var codec = new QuestProgressCodec();
        if (persistent.contains(ROOT_TAG)) {
            if (!persistent.contains(ROOT_TAG, Tag.TAG_COMPOUND)) throw new IllegalStateException("Unsupported quest data is preserved.");
            var existing = codec.decode(persistent.getCompound(ROOT_TAG));
            if (!existing.accepted() || !existing.progress().orElseThrow().playerId().equals(owner)) {
                throw new IllegalStateException("Unsupported quest data is preserved.");
            }
        }
        persistent.put(ROOT_TAG, codec.encode(progress));
    }

    public void copy(Player original, Player replacement) {
        if (original.level().isClientSide || replacement.level().isClientSide) return;
        if (replacement.getServer() == null || !replacement.getServer().isSameThread()) throw new IllegalStateException("Quest clone must run on the server thread.");
        copyRoot(original.getPersistentData(), replacement.getPersistentData());
    }

    public static void copyRoot(CompoundTag original, CompoundTag replacement) {
        if (original.contains(ROOT_TAG)) replacement.put(ROOT_TAG, original.get(ROOT_TAG).copy());
    }

    private static void requireServerThread(ServerPlayer player) {
        if (player.getServer() == null || !player.getServer().isSameThread()) throw new IllegalStateException("Quest persistence must run on the server thread.");
    }
}
