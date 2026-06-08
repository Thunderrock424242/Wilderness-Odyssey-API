package com.thunder.wildernessodysseyapi.telemetry;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared HTTP utilities for telemetry with retry/backoff.
 */
public final class TelemetryHttp {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private TelemetryHttp() {
    }

    public static HttpResponse<String> sendWithRetry(HttpRequest request, int maxRetries, Duration baseDelay,
                                                     Duration maxDelay) throws Exception {
        int attempt = 0;
        Exception lastException = null;
        HttpResponse<String> lastResponse = null;

        while (attempt <= maxRetries) {
            try {
                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                lastResponse = response;
                if (response.statusCode() / 100 == 2) {
                    return response;
                }
            } catch (Exception ex) {
                lastException = ex;
            }

            if (attempt == maxRetries) {
                break;
            }
            long delayMillis = calculateDelayMillis(baseDelay, maxDelay, attempt);
            Thread.sleep(delayMillis);
            attempt++;
        }

        if (lastException != null) {
            throw lastException;
        }
        return lastResponse;
    }

    private static long calculateDelayMillis(Duration baseDelay, Duration maxDelay, int attempt) {
        long baseMillis = Math.max(1L, baseDelay.toMillis());
        long maxMillis = Math.max(baseMillis, maxDelay.toMillis());
        long expDelay = Math.min(maxMillis, baseMillis * (1L << Math.min(attempt, 10)));
        double jitter = ThreadLocalRandom.current().nextDouble(0.5, 1.5);
        long delay = (long) (expDelay * jitter);
        return Math.max(1L, Math.min(delay, maxMillis));
    }
}
