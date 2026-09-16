package com.thunder.wildernessodysseyapi.playtest;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Loopback-only endpoint; tests never send feedback or linking requests to Discord. */
public final class LocalWebhook implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    public final CountDownLatch received = new CountDownLatch(1);
    public final CountDownLatch release = new CountDownLatch(1);
    public volatile String request;
    public volatile int status;

    public LocalWebhook(int status, String body, boolean delayed) throws IOException {
        this.status = status;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try (exchange) {
                request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                received.countDown();
                if (delayed) {
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                int responseStatus = this.status;
                exchange.sendResponseHeaders(responseStatus, responseStatus == 204 ? -1 : bytes.length);
                if (responseStatus != 204) {
                    exchange.getResponseBody().write(bytes);
                }
            }
        });
        server.start();
    }

    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @Override
    public void close() {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
    }
}