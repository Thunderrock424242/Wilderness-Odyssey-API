package com.thunder.wildernessodysseyapi.quest.runtime;

import com.thunder.wildernessodysseyapi.quest.config.QuestConfig;
import com.thunder.wildernessodysseyapi.quest.definition.QuestReloadListener;
import com.thunder.wildernessodysseyapi.quest.integration.QuestAvailability;
import com.thunder.wildernessodysseyapi.quest.objective.QuestEvent;
import com.thunder.wildernessodysseyapi.quest.validation.QuestValidationReport;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Narrow facade registered through the existing server lifecycle owner; gameplay hooks are registered separately once. */
public final class QuestServerEvents {
    public record Reload(QuestAvailability availability, QuestValidationReport report) { }
    private static final AtomicLong GENERATION = new AtomicLong();
    private static volatile Reload initialCandidate;
    private static volatile QuestServerSession current;
    private record PendingReload(QuestServerSession owner, QuestReloadListener.Prepared prepared) { }
    private static volatile PendingReload pendingReload;
    private QuestServerEvents() { }

    public static QuestReloadListener reloadListener(RegistryAccess access) {
        var captured = current;
        return new QuestReloadListener(prepared -> pendingReload = new PendingReload(captured, prepared));
    }
    public static void tagsUpdated(net.neoforged.neoforge.event.TagsUpdatedEvent event) {
        if (event.getUpdateCause() != net.neoforged.neoforge.event.TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) return;
        var pending = pendingReload; pendingReload = null;
        if (pending == null) return;
        if (pending.owner() == null) initialCandidate = validate(pending.prepared(), event.getRegistryAccess());
        else pending.owner().execute(() -> {
            if (current == pending.owner()) pending.owner().candidate(validate(pending.prepared(), event.getRegistryAccess()));
        });
    }
    private static Reload validate(QuestReloadListener.Prepared prepared, RegistryAccess access) {
        var lookup = QuestAvailability.capture(access, GENERATION.incrementAndGet());
        return new Reload(lookup, prepared.validate(lookup));
    }
    public static void start(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Quest startup requires the server thread.");
        if (current != null) throw new IllegalStateException("A quest server session is already active.");
        if (!QuestConfig.values().enabled()) return;
        var candidate = initialCandidate; initialCandidate = null;
        var lookup = QuestAvailability.capture(server.registryAccess(), candidate == null ? GENERATION.incrementAndGet() : candidate.availability().generation());
        current = new QuestServerSession(server, lookup, candidate); current.open();
    }
    public static Optional<QuestServerSession> session(MinecraftServer server) {
        var value = current; return value == null || value.server() != server || value.closed() ? Optional.empty() : Optional.of(value);
    }
    public static Optional<QuestRuntime> runtime(MinecraftServer server) { return session(server).flatMap(QuestServerSession::runtime); }
    public static void tick(MinecraftServer server) { session(server).ifPresent(QuestServerSession::tick); }
    public static void onConfigurationReload(MinecraftServer server) { session(server).ifPresent(QuestServerSession::refreshPlayers); }
    public static void stop(MinecraftServer server) {
        var value = current;
        if (value != null && value.server() == server) { value.close(); current = null; }
        initialCandidate = null; pendingReload = null;
    }
    public static void observe(ServerPlayer player, QuestEvent.Evidence evidence) {
        session(player.getServer()).ifPresent(value -> value.observe(player, evidence));
    }
    public static void login(ServerPlayer player) { session(player.getServer()).ifPresent(value -> value.login(player)); }
    public static void logout(ServerPlayer player) { session(player.getServer()).ifPresent(value -> value.forget(player)); }
    public static void forget(ServerPlayer player) { logout(player); }
}
