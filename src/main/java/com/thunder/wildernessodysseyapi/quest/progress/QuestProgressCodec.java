package com.thunder.wildernessodysseyapi.quest.progress;

import com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Versioned player NBT. Unsupported/corrupt input returns a preserved copy, never an empty replacement. */
public final class QuestProgressCodec {
    public CompoundTag encode(QuestPlayerProgress progress) {
        var root = new CompoundTag();
        root.putInt("schemaVersion", 1);
        root.putUUID("player", progress.playerId());
        root.putLong("revision", progress.revision());
        var runs = new ListTag();
        progress.runs().forEach((key, value) -> {
            var run = new CompoundTag();
            run.putString("quest", key.quest().toString());
            run.putLong("number", key.number());
            run.putInt("definitionVersion", value.definitionVersion());
            run.putString("definitionHash", value.definitionHash());
            run.putBoolean("completed", value.completed());
            run.putLong("completedTick", value.completedTick());
            run.putBoolean("archived", value.archived());
            var counts = new CompoundTag();
            value.counts().forEach((id, count) -> counts.putLong(id.toString(), count));
            run.put("counts", counts);
            runs.add(run);
        });
        root.put("runs", runs);
        var active = new CompoundTag();
        progress.activeRuns().forEach((id, number) -> active.putLong(id.toString(), number));
        root.put("activeRuns", active);
        var earned = new ListTag();
        for (var value : progress.earned()) {
            var entry = new CompoundTag();
            entry.putString("quest", value.quest().toString());
            entry.putLong("run", value.runNumber());
            entry.putString("definitionHash", value.definitionHash());
            entry.put("value", encodeReward(value.originalValue()));
            earned.add(entry);
        }
        root.put("earned", earned);
        if (progress.tracking() != null) root.putString("tracking", progress.tracking().toString());
        // Runtime samples and server-session replay windows cannot prove current possession after reload.
        return root;
    }

    public DecodeResult decode(CompoundTag tag) {
        if (!tag.contains("schemaVersion", Tag.TAG_INT) || tag.getInt("schemaVersion") != 1) {
            return new DecodeResult(Optional.empty(), tag, "Quest data uses an unsupported schema and has been preserved.");
        }
        try {
            if (!tag.hasUUID("player")) throw new IllegalArgumentException("Missing player identity.");
            long revision = nonnegative(tag, "revision");
            Map<QuestPlayerProgress.RunKey, QuestPlayerProgress.Run> runs = new HashMap<>();
            for (var element : list(tag, "runs", 100_000)) {
                var row = compound(element);
                var key = new QuestPlayerProgress.RunKey(id(row, "quest"), nonnegative(row, "number"));
                if (!row.contains("definitionVersion", Tag.TAG_INT) || row.getInt("definitionVersion") < 1) throw new IllegalArgumentException("Invalid definition version.");
                Map<ResourceLocation, Long> counts = new HashMap<>();
                var values = compound(row.get("counts"));
                if (values.size() > 1024) throw new IllegalArgumentException("Too many objective counts.");
                for (String name : values.getAllKeys()) counts.put(parseId(name), nonnegative(values, name));
                var run = new QuestPlayerProgress.Run(row.getInt("definitionVersion"), hash(row), counts,
                        bool(row, "completed"), nonnegative(row, "completedTick"), bool(row, "archived"));
                if (runs.putIfAbsent(key, run) != null) throw new IllegalArgumentException("Duplicate run identity.");
            }
            for (var key : runs.keySet()) {
                if (key.number() == 0) continue;
                var predecessor = runs.get(new QuestPlayerProgress.RunKey(key.quest(), key.number() - 1));
                if (predecessor == null || !predecessor.completed()) {
                    throw new IllegalArgumentException("Repeat history does not prove prior completion.");
                }
            }
            Map<ResourceLocation, Long> active = new HashMap<>();
            var activeTag = compound(tag.get("activeRuns"));
            if (activeTag.size() > 100_000) throw new IllegalArgumentException("Too many active runs.");
            for (String name : activeTag.getAllKeys()) {
                var id = parseId(name);
                long number = nonnegative(activeTag, name);
                if (number > 0 && !runs.containsKey(new QuestPlayerProgress.RunKey(id, number))) throw new IllegalArgumentException("Missing active run.");
                active.put(id, number);
            }
            List<QuestPlayerProgress.EarnedReward> earned = new ArrayList<>();
            var seen = new java.util.HashSet<String>();
            for (var element : list(tag, "earned", 100_000)) {
                var row = compound(element);
                var value = decodeReward(compound(row.get("value")));
                var quest = id(row, "quest");
                long number = nonnegative(row, "run");
                if (!seen.add(quest + "/" + number + "/" + value.id())) throw new IllegalArgumentException("Duplicate earned identity.");
                earned.add(new QuestPlayerProgress.EarnedReward(quest, number, value, hash(row)));
            }
            var progress = new QuestPlayerProgress(tag.getUUID("player"), revision, runs, active, earned,
                    tag.contains("tracking") ? id(tag, "tracking") : null, List.of(), QuestPlayerProgress.Observed.empty());
            return new DecodeResult(Optional.of(progress), tag, "Quest progress loaded.");
        } catch (IllegalArgumentException exception) {
            return new DecodeResult(Optional.empty(), tag, "Quest data is malformed and has been preserved.");
        }
    }

    public static CompoundTag encodeReward(RewardDefinition reward) {
        var tag = new CompoundTag();
        tag.putString("id", reward.id().toString());
        switch (reward.value()) {
            case RewardDefinition.Item item -> {
                tag.putString("type", "item");
                tag.putString("item", item.item().toString());
                tag.putInt("count", item.count());
            }
            case RewardDefinition.Experience experience -> {
                tag.putString("type", "xp");
                tag.putInt("amount", experience.amount());
            }
            case RewardDefinition.Lore lore -> {
                tag.putString("type", "lore");
                tag.putString("lore", lore.lore().toString());
            }
        }
        return tag;
    }

    public static RewardDefinition decodeReward(CompoundTag tag) {
        RewardDefinition.Value value = switch (tag.getString("type")) {
            case "item" -> {
                int count = positiveInt(tag, "count", 64);
                yield new RewardDefinition.Item(id(tag, "item"), count);
            }
            case "xp" -> new RewardDefinition.Experience(positiveInt(tag, "amount", 1_000_000));
            case "lore" -> new RewardDefinition.Lore(id(tag, "lore"));
            default -> throw new IllegalArgumentException("Unsupported reward type.");
        };
        return new RewardDefinition(id(tag, "id"), value);
    }

    private static ListTag list(CompoundTag root, String field, int max) {
        if (!(root.get(field) instanceof ListTag list) || list.size() > max) throw new IllegalArgumentException("Invalid list.");
        return list;
    }

    private static CompoundTag compound(Tag tag) {
        if (!(tag instanceof CompoundTag compound)) throw new IllegalArgumentException("Invalid compound.");
        return compound;
    }

    private static long nonnegative(CompoundTag tag, String field) {
        if (!tag.contains(field, Tag.TAG_LONG) || tag.getLong(field) < 0) throw new IllegalArgumentException("Invalid count.");
        return tag.getLong(field);
    }

    private static boolean bool(CompoundTag tag, String field) {
        if (!tag.contains(field, Tag.TAG_BYTE)) throw new IllegalArgumentException("Invalid boolean.");
        return tag.getBoolean(field);
    }

    private static int positiveInt(CompoundTag tag, String field, int max) {
        if (!tag.contains(field, Tag.TAG_INT) || tag.getInt(field) < 1 || tag.getInt(field) > max) throw new IllegalArgumentException("Invalid reward value.");
        return tag.getInt(field);
    }

    private static String hash(CompoundTag tag) {
        String hash = tag.getString("definitionHash");
        if (!hash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid definition hash.");
        return hash;
    }

    private static ResourceLocation id(CompoundTag tag, String field) {
        if (!tag.contains(field, Tag.TAG_STRING)) throw new IllegalArgumentException("Missing ID.");
        return parseId(tag.getString(field));
    }

    private static ResourceLocation parseId(String value) {
        var id = value.length() <= 256 ? ResourceLocation.tryParse(value) : null;
        if (id == null || !value.contains(":")) throw new IllegalArgumentException("Invalid ID.");
        return id;
    }

    public record DecodeResult(Optional<QuestPlayerProgress> progress, CompoundTag original, String message) {
        public DecodeResult {
            original = original.copy();
        }

        @Override
        public CompoundTag original() {
            return original.copy();
        }

        public boolean accepted() {
            return progress.isPresent();
        }
    }
}
