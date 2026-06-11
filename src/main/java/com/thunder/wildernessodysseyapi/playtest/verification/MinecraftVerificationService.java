package com.thunder.wildernessodysseyapi.playtest.verification;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public final class MinecraftVerificationService {
    private static final VerificationApiClient API_CLIENT = new VerificationApiClient();
    private static final LinkedMinecraftAccountStore STORE = new LinkedMinecraftAccountStore();

    private MinecraftVerificationService() {
    }

    public static CompletableFuture<VerificationOutcome> verifyCode(String code, String minecraftUuid, String minecraftName) {
        MinecraftVerificationConfig.Values config = MinecraftVerificationConfig.values();
        if (!config.enabled()) {
            return CompletableFuture.completedFuture(VerificationOutcome.failure("Minecraft verification is disabled in the client config."));
        }

        String apiBaseUrl = VerificationApiClient.normalizeBaseUrl(config.apiBaseUrl());
        if (apiBaseUrl.isBlank()) {
            return CompletableFuture.completedFuture(VerificationOutcome.failure(
                    "Verification API is not configured. Set verification.apiBaseUrl in the playtest client config."
            ));
        }

        String normalizedCode = code == null ? "" : code.trim();
        if (normalizedCode.isBlank()) {
            return CompletableFuture.completedFuture(VerificationOutcome.failure("Enter the code from /minecraft link in Discord."));
        }

        Duration timeout = Duration.ofSeconds(config.requestTimeoutSeconds());
        return CompletableFuture.supplyAsync(() -> {
            VerificationApiClient.VerificationResponse response = API_CLIENT.verify(
                    apiBaseUrl,
                    timeout,
                    normalizedCode,
                    minecraftUuid,
                    minecraftName
            );
            if (!response.ok()) {
                return VerificationOutcome.failure(response.failureMessage());
            }

            LinkedMinecraftAccount account = new LinkedMinecraftAccount(
                    response.discordUserId(),
                    response.minecraftUuid(),
                    response.minecraftName(),
                    Instant.now().toString(),
                    apiBaseUrl
            );

            if (!config.rememberLinkedAccount()) {
                STORE.clear();
                return VerificationOutcome.success(account, false, false);
            }

            boolean stored = STORE.save(account);
            return VerificationOutcome.success(account, true, stored);
        });
    }

    public static Optional<LinkedMinecraftAccount> linkedAccount() {
        return STORE.load();
    }

    public static boolean clearLinkedAccount() {
        return STORE.clear();
    }

    public static String storePath() {
        return STORE.path().toString();
    }

    public record VerificationOutcome(
            boolean ok,
            LinkedMinecraftAccount account,
            boolean rememberEnabled,
            boolean stored,
            String failureMessage
    ) {
        static VerificationOutcome success(LinkedMinecraftAccount account, boolean rememberEnabled, boolean stored) {
            return new VerificationOutcome(true, account, rememberEnabled, stored, "");
        }

        static VerificationOutcome failure(String failureMessage) {
            return new VerificationOutcome(false, null, false, false, failureMessage);
        }
    }
}
