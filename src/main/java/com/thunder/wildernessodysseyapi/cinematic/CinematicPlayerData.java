package com.thunder.wildernessodysseyapi.cinematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Persistent per-player cinematic progress stored in the existing player-data system.
 *
 * <p>Started and completed ids are separate so an interrupted automatic intro
 * can be retried, while players from older worlds are not unexpectedly enrolled
 * merely because they already have a cryo spawn assignment.</p>
 */
public final class CinematicPlayerData {
    static final String ROOT_TAG = "wildernessodysseyapi_cinematics";
    private static final String STARTED_TAG = "automatic_started";
    private static final String COMPLETED_TAG = "completed";

    private CinematicPlayerData() {
    }

    public static boolean hasAutomaticStarted(Player player, ResourceLocation sequenceId) {
        return contains(root(player), STARTED_TAG, sequenceId);
    }

    public static boolean hasCompleted(Player player, ResourceLocation sequenceId) {
        return contains(root(player), COMPLETED_TAG, sequenceId);
    }

    public static void markAutomaticStarted(Player player, ResourceLocation sequenceId) {
        add(player, STARTED_TAG, sequenceId);
    }

    public static void markCompleted(Player player, ResourceLocation sequenceId) {
        add(player, COMPLETED_TAG, sequenceId);
    }

    /** Copies progress when Minecraft replaces the player entity after death or End return. */
    public static void copy(Player original, Player replacement) {
        CompoundTag originalRoot = original.getPersistentData().getCompound(ROOT_TAG);
        if (!originalRoot.isEmpty()) {
            replacement.getPersistentData().put(ROOT_TAG, originalRoot.copy());
        }
    }

    private static CompoundTag root(Player player) {
        return player.getPersistentData().getCompound(ROOT_TAG);
    }

    private static boolean contains(CompoundTag root, String listName, ResourceLocation sequenceId) {
        ListTag values = root.getList(listName, Tag.TAG_STRING);
        String expected = sequenceId.toString();
        for (int index = 0; index < values.size(); index++) {
            if (expected.equals(values.getString(index))) {
                return true;
            }
        }
        return false;
    }

    private static void add(Player player, String listName, ResourceLocation sequenceId) {
        CompoundTag root = root(player);
        if (contains(root, listName, sequenceId)) {
            return;
        }
        ListTag values = root.getList(listName, Tag.TAG_STRING);
        values.add(StringTag.valueOf(sequenceId.toString()));
        root.put(listName, values);
        player.getPersistentData().put(ROOT_TAG, root);
    }
}
