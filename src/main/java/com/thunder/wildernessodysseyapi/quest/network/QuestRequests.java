package com.thunder.wildernessodysseyapi.quest.network;

import com.thunder.wildernessodysseyapi.quest.command.QuestOperation;
import com.thunder.wildernessodysseyapi.quest.command.QuestPermissions;
import com.thunder.wildernessodysseyapi.quest.config.QuestConfig;
import com.thunder.wildernessodysseyapi.quest.progress.QuestPlayerProgress;
import com.thunder.wildernessodysseyapi.quest.reward.QuestRewardService;
import com.thunder.wildernessodysseyapi.quest.runtime.QuestServerEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** Shared authenticated request policy for commands and wire requests, including bounded admission. */
public final class QuestRequests {
    private QuestRequests() { }
    public static void handle(ServerPlayer player, QuestPlayerRequestPayload request) {
        var session = QuestServerEvents.session(player.getServer());
        if (session.isEmpty()) { player.sendSystemMessage(Component.literal("The optional quest system is disabled or unavailable.")); return; }
        var owner = session.get(); var runtime = owner.runtime();
        if (runtime.isEmpty() || !runtime.get().isCurrent(player)) { result(player, request, owner.id(), "Quest state is still loading or needs server review."); return; }
        boolean view = request.operation() == QuestPlayerRequestPayload.Operation.VIEW || request.operation() == QuestPlayerRequestPayload.Operation.RESYNC;
        if (!owner.requestGate().admit(player.getUUID(), request.request(), player.getServer().getTickCount(), view, QuestConfig.values())) {
            result(player, request, owner.id(), "This request was already received or arrived too quickly. Wait briefly and try again."); return;
        }
        var operation = QuestOperation.valueOf(request.operation() == QuestPlayerRequestPayload.Operation.RESYNC ? "VIEW" : request.operation().name());
        if (!QuestPermissions.mayPerform(player, operation)) { result(player, request, owner.id(), "You cannot perform this quest action with the current server settings."); return; }
        try {
            if (view) { owner.views().sendInitial(player); return; }
            var progress = runtime.get().progress(player);
            String hash = runtime.get().activeSnapshot().map(snapshot -> snapshot.hash()).orElse("");
            if (!owner.id().equals(request.session()) || !hash.equals(request.hash()) || progress.revision() != request.revision()) {
                result(player, request, owner.id(), "Your quest view changed. Refresh it before trying this action again."); return;
            }
            switch (request.operation()) {
                case TRACK, UNTRACK -> {
                    ResourceLocation tracking = request.operation() == QuestPlayerRequestPayload.Operation.UNTRACK ? null : ResourceLocation.parse(request.quest());
                    if (tracking != null && runtime.get().availableSnapshot().filter(snapshot -> QuestViewService.disclosed(snapshot, progress, tracking)).isEmpty()) {
                        result(player, request, owner.id(), "That quest is not currently available to track."); return;
                    }
                    if (!java.util.Objects.equals(tracking, progress.tracking())) runtime.get().replaceProgress(player, new QuestPlayerProgress(progress.playerId(), progress.revision() + 1,
                            progress.runs(), progress.activeRuns(), progress.earned(), tracking, progress.recentOccurrences(), progress.observed()));
                    owner.views().sendDirty(player); result(player, request, owner.id(), tracking == null ? "Quest tracking cleared." : "Quest tracking selected.");
                }
                case CLAIM -> owner.claim(player, ResourceLocation.parse(request.quest()), request.run(), ResourceLocation.parse(request.reward()), request.request())
                        .whenComplete((status, failure) -> owner.execute(() -> {
                            var actor = owner.server().getPlayerList().getPlayer(player.getUUID());
                            if (actor != null && runtime.get().isCurrent(actor)) {
                                result(actor, request, owner.id(), claimMessage(failure == null ? status : QuestRewardService.ClaimResult.NEEDS_REVIEW)); owner.views().sendInitial(actor);
                            }
                        }));
                default -> { }
            }
        } catch (IllegalStateException exception) { result(player, request, owner.id(), "Quest data has been preserved and requires server review."); }
    }
    private static void result(ServerPlayer player, QuestPlayerRequestPayload request, UUID session, String message) {
        PacketDistributor.sendToPlayer(player, new QuestRequestResultPayload(request.request(), session, message));
        player.sendSystemMessage(Component.literal(message));
    }
    public static String claimMessage(QuestRewardService.ClaimResult result) {
        return switch (result) {
            case PENDING -> "Your reward is waiting for durable confirmation. Try again shortly.";
            case DELIVERED -> "Your quest reward was delivered.";
            case INVENTORY_FULL -> "Make room in your inventory, then claim again. Your reward is still waiting.";
            case UNAVAILABLE -> "This reward is not claimable now. It may already be claimed, or the quest system may be unavailable.";
            case NEEDS_REVIEW -> "The reward outcome needs an operator review. Automatic delivery is paused to protect this claim.";
        };
    }
}
