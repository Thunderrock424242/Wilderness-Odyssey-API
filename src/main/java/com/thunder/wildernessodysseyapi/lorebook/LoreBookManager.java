package com.thunder.wildernessodysseyapi.lorebook;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class LoreBookManager {
    public static final String LORE_ID_TAG = ModConstants.MOD_ID + ":lore_id";
    private static final String ROOT_TAG = ModConstants.MOD_ID + "_lore_books";
    private static final String COLLECTED_TAG = "collected";
    private static final String LAST_SCAN_TAG = "last_scan";
    private static final long SCAN_INTERVAL_TICKS = 40L;

    private static volatile LoreBookConfig cachedConfig;

    private LoreBookManager() {
    }

    public static LoreBookConfig config() {
        if (cachedConfig == null) {
            cachedConfig = LoreBookConfig.load();
        }
        return cachedConfig;
    }

    public static Optional<LoreBookConfig.LoreBookEntry> nextEntry(ServerPlayer player) {
        Set<String> collected = getCollected(player);
        for (LoreBookConfig.LoreBookEntry entry : config().books()) {
            if (entry.id() == null || entry.id().isBlank()) {
                continue;
            }
            if (!collected.contains(entry.id())) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public static boolean hasCollected(ServerPlayer player, String id) {
        return getCollected(player).contains(id);
    }

    public static void markCollected(ServerPlayer player, String id) {
        if (id == null || id.isBlank()) {
            return;
        }
        CompoundTag root = player.getPersistentData().getCompound(ROOT_TAG);
        ListTag list = root.getList(COLLECTED_TAG, StringTag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (id.equals(list.getString(i))) {
                player.getPersistentData().put(ROOT_TAG, root);
                return;
            }
        }
        list.add(StringTag.valueOf(id));
        root.put(COLLECTED_TAG, list);
        player.getPersistentData().put(ROOT_TAG, root);
    }

    public static Set<String> getCollected(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(ROOT_TAG);
        ListTag list = root.getList(COLLECTED_TAG, StringTag.TAG_STRING);
        Set<String> collected = new HashSet<>();
        for (int i = 0; i < list.size(); i++) {
            collected.add(list.getString(i));
        }
        return collected;
    }

    public static ItemStack createBookStack(LoreBookConfig.LoreBookEntry entry) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        String title = entry.title() == null ? "Lore" : entry.title();
        String author = entry.author() == null ? "Unknown" : entry.author();
        List<Filterable<Component>> pages = new ArrayList<>();
        if (entry.pages() != null) {
            for (String page : entry.pages()) {
                pages.add(Filterable.passThrough(Component.literal(page == null ? "" : page)));
            }
        }

        stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough(title), author, 0, pages, true));

        String id = entry.id() == null ? "" : entry.id();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(LORE_ID_TAG, id));
        return stack;
    }

    public static Optional<String> loreIdFrom(ItemStack stack) {
        if (!stack.is(Items.WRITTEN_BOOK)) {
            return Optional.empty();
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) {
            return Optional.empty();
        }
        CompoundTag tag = customData.copyTag();
        if (!tag.contains(LORE_ID_TAG)) {
            return Optional.empty();
        }
        return Optional.ofNullable(tag.getString(LORE_ID_TAG));
    }

    public static void scanInventory(ServerPlayer player) {
        CompoundTag root = player.getPersistentData().getCompound(ROOT_TAG);
        long lastScan = root.getLong(LAST_SCAN_TAG);
        long gameTime = player.level().getGameTime();
        if (gameTime - lastScan < SCAN_INTERVAL_TICKS) {
            return;
        }
        root.putLong(LAST_SCAN_TAG, gameTime);
        player.getPersistentData().put(ROOT_TAG, root);

        scanStacks(player.getInventory().items, player);
        scanStacks(player.getInventory().armor, player);
        scanStacks(player.getInventory().offhand, player);
    }

    private static void scanStacks(List<ItemStack> stacks, ServerPlayer player) {
        for (ItemStack stack : stacks) {
            loreIdFrom(stack).ifPresent(id -> markCollected(player, id));
        }
    }
}
