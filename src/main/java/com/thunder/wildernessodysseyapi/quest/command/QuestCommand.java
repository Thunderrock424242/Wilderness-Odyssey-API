package com.thunder.wildernessodysseyapi.quest.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.thunder.wildernessodysseyapi.quest.config.QuestConfig;
import com.thunder.wildernessodysseyapi.quest.network.QuestPlayerRequestPayload;
import com.thunder.wildernessodysseyapi.quest.network.QuestRequests;
import com.thunder.wildernessodysseyapi.quest.runtime.QuestServerEvents;
import com.thunder.wildernessodysseyapi.quest.runtime.QuestServerSession;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Quest diagnostics and authorized native Workshop opening; no reset or arbitrary grant command exists. */
public final class QuestCommand {
    private QuestCommand() { }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wo").then(Commands.literal("quest")
                .then(Commands.literal("status").executes(context -> status(context.getSource())))
                .then(Commands.literal("editor").requires(source -> permitted(source, QuestOperation.VALIDATE)).executes(context -> editor(context.getSource())))
                .then(Commands.literal("track").then(Commands.argument("quest", ResourceLocationArgument.id()).executes(context -> playerAction(context.getSource(),
                        QuestPlayerRequestPayload.Operation.TRACK, ResourceLocationArgument.getId(context, "quest"), null))))
                .then(Commands.literal("untrack").executes(context -> playerAction(context.getSource(), QuestPlayerRequestPayload.Operation.UNTRACK, null, null)))
                .then(Commands.literal("claim").then(Commands.argument("quest", ResourceLocationArgument.id()).then(Commands.argument("reward", ResourceLocationArgument.id())
                        .executes(context -> playerAction(context.getSource(), QuestPlayerRequestPayload.Operation.CLAIM,
                                ResourceLocationArgument.getId(context, "quest"), ResourceLocationArgument.getId(context, "reward"))))))
                .then(Commands.literal("validate").requires(source -> permitted(source, QuestOperation.VALIDATE)).executes(context -> validate(context.getSource())))
                .then(Commands.literal("publish").requires(source -> permitted(source, QuestOperation.PUBLISH))
                        .then(Commands.argument("candidate_hash", StringArgumentType.word()).then(Commands.argument("base_hash", StringArgumentType.word())
                                .executes(context -> publish(context.getSource(), StringArgumentType.getString(context, "candidate_hash"), StringArgumentType.getString(context, "base_hash"))))))
                .then(Commands.literal("receipts").requires(source -> permitted(source, QuestOperation.RECEIPTS)).executes(context -> receipts(context.getSource())))));
    }
    private static boolean permitted(CommandSourceStack source, QuestOperation operation) {
        int level = 0; for (int next = 1; next <= 4; next++) if (source.hasPermission(next)) level = next;
        return QuestPermissions.allowed(level, operation, QuestConfig.values());
    }
    private static int editor(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var owner = QuestServerEvents.session(source.getServer());
        if (owner.isEmpty() || owner.get().workshop().isEmpty() || !admitted(source, owner.get())) {
            message(source, "Workshop is loading, disabled or rate limited. Try again shortly."); return 0;
        }
        owner.get().workshop().orElseThrow().open(player); return 1;
    }
    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        var owner = QuestServerEvents.session(source.getServer());
        if (owner.isEmpty()) { message(source, "The optional quest system is disabled or unavailable."); return 0; }
        if (source.hasPermission(QuestConfig.values().editLevel())) message(source, owner.get().status());
        else {
            var player = source.getPlayerOrException(); var runtime = owner.get().runtime();
            if (runtime.isEmpty()) { message(source, "Quest state is loading or requires server review."); return 0; }
            var progress = runtime.get().progress(player);
            message(source, "Completed quest runs: " + progress.runs().values().stream().filter(run -> run.completed()).count()
                    + "; tracking: " + (progress.tracking() == null ? "none" : progress.tracking()) + ". Quests are optional.");
        }
        return 1;
    }
    private static int playerAction(CommandSourceStack source, QuestPlayerRequestPayload.Operation operation, ResourceLocation quest, ResourceLocation reward) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var owner = QuestServerEvents.session(source.getServer());
        if (owner.isEmpty() || owner.get().runtime().isEmpty()) { message(source, "Quest state is loading, disabled or requires server review."); return 0; }
        var runtime = owner.get().runtime().orElseThrow();
        var progress = runtime.progress(player);
        QuestRequests.handle(player, new QuestPlayerRequestPayload(UUID.randomUUID(), owner.get().id(), runtime.activeSnapshot().map(snapshot -> snapshot.hash()).orElse(""),
                progress.revision(), operation, quest == null ? "" : quest.toString(), reward == null ? "" : reward.toString(), quest == null ? 0 : progress.currentRun(quest)));
        return 1;
    }
    private static boolean admitted(CommandSourceStack source, QuestServerSession owner) throws CommandSyntaxException {
        return owner.requestGate().admit(source.getPlayerOrException().getUUID(), UUID.randomUUID(), source.getServer().getTickCount(), false, QuestConfig.values());
    }
    private static int validate(CommandSourceStack source) throws CommandSyntaxException {
        if (!permitted(source, QuestOperation.VALIDATE)) return 0;
        var owner = QuestServerEvents.session(source.getServer());
        if (owner.isEmpty() || !admitted(source, owner.get())) { message(source, "Validation is unavailable or rate limited. Try again shortly."); return 0; }
        message(source, owner.get().status());
        var report = owner.get().candidateReport();
        if (report.accepted()) message(source, "Candidate is valid. Publish with its candidate hash and the current active hash (use none when empty).");
        report.findings().stream().limit(32).forEach(finding -> message(source, finding.severity() + " " + finding.contentId() + " " + finding.fieldPath() + ": " + finding.message()));
        return report.accepted() ? 1 : 0;
    }
    private static int publish(CommandSourceStack source, String hash, String base) throws CommandSyntaxException {
        if (!permitted(source, QuestOperation.PUBLISH)) return 0;
        var player = source.getPlayerOrException(); var owner = QuestServerEvents.session(source.getServer());
        if (owner.isEmpty() || owner.get().publications().isEmpty() || !admitted(source, owner.get())) { message(source, "Publication is unavailable or rate limited."); return 0; }
        owner.get().publications().orElseThrow().publish(player, hash, base.equals("none") ? "" : base).whenComplete((result, failure) -> owner.get().execute(() -> {
            var actor = source.getServer().getPlayerList().getPlayer(player.getUUID());
            if (actor != null) actor.sendSystemMessage(Component.literal(failure == null ? result.message() : "Publication failed; existing data is preserved. Check server diagnostics."));
        }));
        message(source, "Publication request received. Its result will be reported after durable confirmation."); return 1;
    }
    private static int receipts(CommandSourceStack source) {
        if (!permitted(source, QuestOperation.RECEIPTS)) return 0;
        var ledger = QuestServerEvents.session(source.getServer()).flatMap(QuestServerSession::ledger);
        if (ledger.isEmpty()) { message(source, "Reward receipts are unavailable; inspect server diagnostics. Existing files are preserved."); return 0; }
        message(source, "Reward identities: " + ledger.get().receipts().size() + "; writes locked: " + ledger.get().locked());
        ledger.get().receipts().values().stream().filter(receipt -> receipt.state() == com.thunder.wildernessodysseyapi.quest.reward.QuestRewardLedger.State.NEEDS_REVIEW)
                .limit(32).forEach(receipt -> message(source, "NEEDS_REVIEW player=" + receipt.key().playerId() + " quest=" + receipt.key().questId()
                        + " run=" + receipt.key().runNumber() + " reward=" + receipt.key().rewardId() + " reservation=" + receipt.reservationId()));
        return 1;
    }
    private static void message(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text), false); }
}
