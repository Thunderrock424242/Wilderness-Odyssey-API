package com.thunder.wildernessodysseyapi.telemetry;

import com.thunder.wildernessodysseyapi.playtest.PlaytestWebhookClient;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/** Worker-only HTTP retry/backoff with bounded response memory and interruptible deadlines. */
public final class TelemetryHttp {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();

    private TelemetryHttp() {
    }

    /** Call only on an I/O worker; interruption aborts all retries during shutdown. */
    public static HttpResponse<String> sendWithRetry(HttpRequest request, int maxRetries, Duration baseDelay,
                                                     Duration maxDelay) throws Exception {
        Exception lastException = null;
        HttpResponse<String> lastResponse = null;
        int retries = Math.clamp(maxRetries, 0, 10);
        for (int attempt = 0; attempt <= retries; attempt++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Telemetry worker interrupted");
            }
            var response = HTTP_CLIENT.sendAsync(request, PlaytestWebhookClient.responseBodyHandler());
            try {
                long timeoutMillis = Math.clamp(request.timeout().orElse(Duration.ofSeconds(10)).toMillis(), 1, 60_000);
                lastResponse = response.get(timeoutMillis, TimeUnit.MILLISECONDS);
                lastException = null;
                int status = lastResponse.statusCode();
                if (status / 100 == 2 || (status >= 400 && status < 500 && status != 429)) {
                    return lastResponse;
                }
            } catch (InterruptedException interrupted) {
                response.cancel(true);
                Thread.currentThread().interrupt();
                throw interrupted;
            } catch (Exception failure) {
                response.cancel(true);
                lastException = failure;
            }
            if (attempt < retries) {
                Thread.sleep(calculateDelayMillis(baseDelay, maxDelay, attempt));
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        return lastResponse;
    }

    private static long calculateDelayMillis(Duration baseDelay, Duration maxDelay, int attempt) {
        long baseMillis = Math.clamp(baseDelay.toMillis(), 1L, 10_000L);
        long maxMillis = Math.clamp(maxDelay.toMillis(), baseMillis, 60_000L);
        long exponential = Math.min(maxMillis, baseMillis * (1L << Math.min(attempt, 10)));
        return Math.clamp((long) (exponential * ThreadLocalRandom.current().nextDouble(0.5, 1.5)), 1L, maxMillis);
    }
}