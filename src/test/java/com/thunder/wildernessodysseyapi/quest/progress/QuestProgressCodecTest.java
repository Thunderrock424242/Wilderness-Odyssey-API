package com.thunder.wildernessodysseyapi.quest.progress;

import com.thunder.wildernessodysseyapi.quest.QuestFixtures;
import com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class QuestProgressCodecTest {
    static QuestPlayerProgress progress() {
        var quest = QuestFixtures.id("test:first");
        var objective = QuestFixtures.id("test:supplies");
        var earned = new QuestPlayerProgress.EarnedReward(quest, 0,
                new RewardDefinition(QuestFixtures.id("test:reward"), new RewardDefinition.Item(QuestFixtures.id("minecraft:compass"), 1)), "a".repeat(64));
        return new QuestPlayerProgress(UUID.randomUUID(), 12,
                Map.of(new QuestPlayerProgress.RunKey(quest, 0), new QuestPlayerProgress.Run(1, "a".repeat(64), Map.of(objective, 4L), true, 100, true),
                        new QuestPlayerProgress.RunKey(quest, 1), new QuestPlayerProgress.Run(2, "b".repeat(64), Map.of(objective, 2L), false, 0, false)),
                Map.of(quest, 1L), List.of(earned), quest, List.of(), QuestPlayerProgress.Observed.empty());
    }

    @Test
    void roundTripsHistoricalRunsArchivesTrackingAndOriginalRewardValues() {
        var original = progress();
        var codec = new QuestProgressCodec();
        var result = codec.decode(codec.encode(original));
        assertTrue(result.accepted(), result.message());
        var restored = result.progress().orElseThrow();
        assertEquals(original.playerId(), restored.playerId());
        assertEquals(12, restored.revision());
        assertEquals(original.runs(), restored.runs());
        assertEquals(original.activeRuns(), restored.activeRuns());
        assertEquals(original.earned(), restored.earned());
        assertEquals(original.tracking(), restored.tracking());
    }

    @Test
    void futureAndMalformedSchemasPreserveTheirOriginalTagInsteadOfProducingEmptyProgress() {
        var codec = new QuestProgressCodec();
        var tag = codec.encode(progress());
        tag.putInt("schemaVersion", 99);
        tag.putString("futureField", "keep this");
        var result = codec.decode(tag);
        assertFalse(result.accepted());
        assertTrue(result.progress().isEmpty());
        assertEquals(tag, result.original());
        result.original().remove("futureField");
        assertEquals("keep this", result.original().getString("futureField"));
        tag.putInt("schemaVersion", 1);
        tag.putString("runs", "wrong type");
        assertFalse(codec.decode(tag).accepted());
    }

    @Test
    void cloneCopiesOnlyTheQuestRootAndNeverMergesCounts() {
        var codec = new QuestProgressCodec();
        var original = new CompoundTag();
        var replacement = new CompoundTag();
        original.put(QuestPlayerProgressStore.ROOT_TAG, codec.encode(progress()));
        original.putString("unrelatedAether", "old entity");
        replacement.putString("unrelatedAether", "new entity");
        QuestPlayerProgressStore.copyRoot(original, replacement);
        var copied = replacement.getCompound(QuestPlayerProgressStore.ROOT_TAG).copy();
        QuestPlayerProgressStore.copyRoot(original, replacement);
        assertEquals(copied, replacement.getCompound(QuestPlayerProgressStore.ROOT_TAG));
        assertEquals("new entity", replacement.getString("unrelatedAether"));
        original.getCompound(QuestPlayerProgressStore.ROOT_TAG).putLong("revision", 99);
        assertEquals(12, replacement.getCompound(QuestPlayerProgressStore.ROOT_TAG).getLong("revision"));
    }

    @Test
    void savesCannotOverwriteFutureDataOrUseAnotherPlayersProgress() {
        var progress = progress();
        var persistent = new CompoundTag();
        var future = new QuestProgressCodec().encode(progress);
        future.putInt("schemaVersion", 99);
        persistent.put(QuestPlayerProgressStore.ROOT_TAG, future);
        assertThrows(IllegalStateException.class, () -> QuestPlayerProgressStore.replaceRoot(persistent, progress.playerId(), progress));
        assertEquals(future, persistent.getCompound(QuestPlayerProgressStore.ROOT_TAG));
        assertThrows(IllegalArgumentException.class, () -> QuestPlayerProgressStore.replaceRoot(new CompoundTag(), UUID.randomUUID(), progress));
    }

    @Test
    void aRepeatRunWithoutItsCompletedPredecessorCannotUnlockPrerequisites() {
        var codec = new QuestProgressCodec();
        var tag = codec.encode(progress());
        tag.getList("runs", net.minecraft.nbt.Tag.TAG_COMPOUND).removeIf(row -> ((CompoundTag) row).getLong("number") == 0);
        assertFalse(codec.decode(tag).accepted());
    }
}
