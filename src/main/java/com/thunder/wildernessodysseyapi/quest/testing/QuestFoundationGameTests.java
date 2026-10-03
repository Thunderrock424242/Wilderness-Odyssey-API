package com.thunder.wildernessodysseyapi.quest.testing;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.thunder.wildernessodysseyapi.lorebook.LoreBookManager;
import com.thunder.wildernessodysseyapi.quest.definition.QuestDefinitionCodec;
import com.thunder.wildernessodysseyapi.quest.definition.QuestSourceDocument;
import com.thunder.wildernessodysseyapi.quest.definition.RewardDefinition;
import com.thunder.wildernessodysseyapi.quest.integration.LoreQuestBridge;
import com.thunder.wildernessodysseyapi.quest.integration.QuestAvailability;
import com.thunder.wildernessodysseyapi.quest.objective.QuestEvent;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgressStore;
import com.thunder.wildernessodysseyapi.quest.reward.QuestRewardDelivery;
import com.thunder.wildernessodysseyapi.quest.reward.QuestRewardLedger;
import com.thunder.wildernessodysseyapi.quest.runtime.QuestObjectiveEngine;
import com.thunder.wildernessodysseyapi.quest.runtime.QuestObjectiveEvents;
import com.thunder.wildernessodysseyapi.quest.runtime.QuestServerEvents;
import com.thunder.wildernessodysseyapi.quest.world.QuestWorldState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Loaded-world adapter contracts. Fake players do not prove real login, network or save atomicity. */
@GameTestHolder("wildernessodysseyapi_quest_tests")
@PrefixGameTestTemplate(false)
public final class QuestFoundationGameTests {
    private QuestFoundationGameTests() { }

    @GameTest(template = "empty")
    public static void actualPossessionDimensionAndLoreCompleteOptionalCampaign(GameTestHelper helper) {
        var player = player(helper, UUID.randomUUID());
        player.getInventory().items.set(0, new ItemStack(Items.OAK_LOG, 2));
        player.getInventory().offhand.set(0, new ItemStack(Items.OAK_LOG, 2));
        String lore = LoreBookManager.config().books().getFirst().id();
        LoreBookManager.markCollected(player, lore);
        var sources = List.of(
                source(QuestSourceDocument.Kind.CAMPAIGN, "campaign", """
                        {"schemaVersion":1,"definitionVersion":1,"title":"Adapter test","chapters":["wildernessodysseyapi:quest_test_chapter"]}
                        """),
                source(QuestSourceDocument.Kind.CHAPTER, "chapter", """
                        {"schemaVersion":1,"definitionVersion":1,"title":"Test","campaign":"wildernessodysseyapi:quest_test_campaign","quests":["wildernessodysseyapi:quest_test_quest"]}
                        """),
                source(QuestSourceDocument.Kind.QUEST, "quest", """
                        {"schemaVersion":1,"definitionVersion":1,"chapter":"wildernessodysseyapi:quest_test_chapter","title":"Optional test",
                        "icon":"minecraft:compass","optional":true,"objectives":[
                        {"id":"wildernessodysseyapi:quest_test_inventory","type":"possession","items":["minecraft:oak_log"],"tags":["minecraft:logs"],"goal":4},
                        {"id":"wildernessodysseyapi:quest_test_dimension","type":"dimension","dimension":"minecraft:overworld"},
                        {"id":"wildernessodysseyapi:quest_test_lore","type":"lore","lore":"wildernessodysseyapi:%s"}],
                        "rewards":[{"id":"wildernessodysseyapi:quest_test_reward","type":"xp","amount":5}]}
                        """.formatted(lore)));
        var report = new QuestDefinitionCodec().decode(sources, QuestAvailability.capture(player.getServer().registryAccess(), 1));
        helper.assertTrue(report.accepted(), "Actual registry/tag/lore capture must accept the test campaign: " + report.findings());
        var engine = QuestObjectiveEngine.compile(report.snapshot().orElseThrow());
        var progress = QuestPlayerProgress.empty(player.getUUID());
        var world = QuestWorldState.empty(UUID.randomUUID());
        UUID session = UUID.randomUUID();
        for (var evidence : List.<QuestEvent.Evidence>of(new QuestEvent.Possession(QuestObjectiveEvents.captureInventory(player)),
                new QuestEvent.Dimension(player.level().dimension().location()), new QuestEvent.Lore(LoreQuestBridge.map(lore).orElseThrow()))) {
            progress = engine.apply(progress, world, new QuestEvent(session, UUID.randomUUID(), player.getUUID(), player.level().getGameTime(), evidence)).player();
        }
        helper.assertTrue(progress.completed(id("quest")), "Real carried slots, dimension and committed lore must complete the campaign");
        helper.assertTrue(progress.count(id("quest"), id("inventory")) == 4 && progress.earned().size() == 1,
                "Overlapping item/tag selectors must count each stack once and earn once");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void registeredCloneEventPreservesDeathAndEndReturnState(GameTestHelper helper) {
        UUID owner = UUID.randomUUID(); var original = player(helper, owner);
        var state = new QuestPlayerProgress(owner, 3,
                Map.of(new QuestPlayerProgress.RunKey(id("quest"), 0), new QuestPlayerProgress.Run(1, "a".repeat(64), Map.of(id("inventory"), 4L), true, 10, false)),
                Map.of(), List.of(), id("quest"), List.of(), QuestPlayerProgress.Observed.empty());
        new QuestPlayerProgressStore().save(original, state);
        for (boolean death : List.of(true, false)) {
            var replacement = player(helper, owner); replacement.getPersistentData().putString("quest_test_unrelated", "keep");
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, original, death));
            var restored = new QuestPlayerProgressStore().load(replacement);
            helper.assertTrue(restored.completed(id("quest")) && restored.revision() == 3 && restored.count(id("quest"), id("inventory")) == 4,
                    "Registered clone adapter must retain completion without adding counts");
            helper.assertTrue(replacement.getPersistentData().getString("quest_test_unrelated").equals("keep"), "Clone must preserve unrelated replacement NBT");
            replacement.getPersistentData().getCompound(QuestPlayerProgressStore.ROOT_TAG).putInt("schemaVersion", 999);
            helper.assertTrue(new QuestPlayerProgressStore().load(original).revision() == 3, "Clone roots must not share mutable NBT");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void typedRewardsRespectFullInventoryAndGrantXpAndLore(GameTestHelper helper) {
        var player = player(helper, UUID.randomUUID()); var delivery = new QuestRewardDelivery();
        var item = new RewardDefinition(id("item_reward"), new RewardDefinition.Item(ResourceLocation.parse("minecraft:compass"), 1));
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) player.getInventory().items.set(slot, new ItemStack(Items.STONE, 64));
        helper.assertTrue(!delivery.canDeliver(player, item) && delivery.deliver(player, item) == QuestRewardLedger.DeliveryOutcome.NOT_APPLIED,
                "Full inventory must retain the entitlement without applying an item effect");
        player.getInventory().items.set(0, ItemStack.EMPTY);
        helper.assertTrue(delivery.deliver(player, item) == QuestRewardLedger.DeliveryOutcome.DELIVERED && player.getInventory().getItem(0).is(Items.COMPASS),
                "Typed delivery must insert into available carried inventory");
        int before = player.totalExperience;
        helper.assertTrue(delivery.deliver(player, new RewardDefinition(id("xp_reward"), new RewardDefinition.Experience(5))) == QuestRewardLedger.DeliveryOutcome.DELIVERED
                && player.totalExperience == before + 5, "Typed XP must change the real player experience");
        String lore = LoreBookManager.config().books().getFirst().id();
        var unlock = new RewardDefinition(id("lore_reward"), new RewardDefinition.Lore(LoreQuestBridge.map(lore).orElseThrow()));
        helper.assertTrue(delivery.deliver(player, unlock) == QuestRewardLedger.DeliveryOutcome.DELIVERED && LoreBookManager.hasCollected(player, lore),
                "Typed lore must commit through the existing lore owner");
        delivery.deliver(player, unlock);
        helper.assertTrue(LoreBookManager.getCollected(player).size() == 1, "Repeated lore unlock must remain idempotent");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void dedicatedStartupRestoresStoreAndRegistersCommands(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.succeedWhen(() -> {
            var session = QuestServerEvents.session(server).orElseThrow();
            helper.assertTrue(session.runtime().isPresent() && session.ledger().isPresent(), "Mandatory tick must finish asynchronous quest storage startup");
            boolean disabled = Boolean.getBoolean("wildernessodysseyapi.questTests.disabledWorkers");
            helper.assertTrue(com.thunder.wildernessodysseyapi.async.AsyncTaskManager.getConfigValues().enabled() != disabled
                    && com.thunder.wildernessodysseyapi.dataengine.config.DataEngineConfig.values().enabled() != disabled,
                    "The run must actually load its intended shared-worker/DataEngine settings");
            helper.assertTrue(session.ioWorker().startsWith("WO-Quest-IO-") == disabled, "Opening must use the intended actual I/O worker");
            var quest = server.getCommands().getDispatcher().getRoot().getChild("wo").getChild("quest");
            helper.assertTrue(quest != null && quest.getChild("status").canUse(server.createCommandSourceStack().withPermission(0)), "Quest status must register for level zero");
            helper.assertTrue(!quest.getChild("publish").canUse(server.createCommandSourceStack().withPermission(2))
                    && quest.getChild("publish").canUse(server.createCommandSourceStack().withPermission(3)), "Publication must require the configured publisher level");
            helper.assertTrue(!quest.getChild("receipts").canUse(server.createCommandSourceStack().withPermission(3)), "Private receipt diagnostics require level four");
        });
    }

    private static FakePlayer player(GameTestHelper helper, UUID owner) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(owner, "quest-test"));
        player.setGameMode(GameType.SURVIVAL); return player;
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void workshopRestoresDraftAndRegistersPermissionAwareEditor(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        helper.succeedWhen(() -> {
            var workshop = QuestServerEvents.session(server).orElseThrow().workshop().orElseThrow();
            helper.assertTrue(workshop.ready(), "Workshop draft must finish durable restore through mandatory ticks");
            var editor = server.getCommands().getDispatcher().getRoot().getChild("wo").getChild("quest").getChild("editor");
            helper.assertTrue(editor != null && !editor.canUse(server.createCommandSourceStack().withPermission(1))
                    && editor.canUse(server.createCommandSourceStack().withPermission(2)), "Native editor must require configured edit permission");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void resourceReloadRevalidatesCandidateAfterTagsAreBound(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        long before = QuestServerEvents.session(server).orElseThrow().availabilityGeneration();
        var reloaded = server.reloadResources(server.getPackRepository().getSelectedIds());
        helper.succeedWhen(() -> {
            helper.assertTrue(reloaded.isDone() && !reloaded.isCompletedExceptionally(), "Normal server resource reload must finish");
            helper.assertTrue(QuestServerEvents.session(server).orElseThrow().availabilityGeneration() > before,
                    "A reload must deliver a new availability capture through the post-binding tag event");
        });
    }
    private static ResourceLocation id(String suffix) { return ResourceLocation.parse("wildernessodysseyapi:quest_test_" + suffix); }
    private static QuestSourceDocument source(QuestSourceDocument.Kind kind, String suffix, String json) {
        return new QuestSourceDocument(kind, id(suffix), JsonParser.parseString(json).getAsJsonObject(), "gametest");
    }
}
