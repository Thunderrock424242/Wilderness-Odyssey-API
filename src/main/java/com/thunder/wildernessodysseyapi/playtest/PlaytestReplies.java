package com.thunder.wildernessodysseyapi.playtest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** Hands completed network results to the owning server executor before any player access. */
public final class PlaytestReplies {
    private PlaytestReplies() {
    }

    /** Returns a future covering both HTTP completion and the server-thread reply. */
    public static CompletableFuture<Void> deliver(CompletableFuture<PlaytestWebhookClient.Result> submission,
                                                  Executor server, Consumer<PlaytestWebhookClient.Result> reply) {
        return submission.handle((result, failure) -> failure == null && result != null
                        ? result : PlaytestWebhookClient.Result.UNAVAILABLE)
                .thenAcceptAsync(reply, server);
    }
}