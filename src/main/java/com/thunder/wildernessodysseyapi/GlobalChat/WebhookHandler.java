package com.thunder.wildernessodysseyapi.GlobalChat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebhookHandler {
    private static final Logger logger = LoggerFactory.getLogger(WebhookHandler.class);

    public void sendWebhook(String url, String payload) {
        logger.info("Sending webhook to URL: {}", url);

        try {
            // Simulate HTTP request
            boolean success = performHttpRequest(url, payload);
            if (success) {
                logger.info("Webhook successfully sent to {}", url);
            } else {
                logger.warn("Webhook failed to send to {}", url);
            }
        } catch (Exception e) {
            logger.error("Exception while sending webhook to {}: {}", url, e.getMessage(), e);
        }
    }

    private boolean performHttpRequest(String url, String payload) throws Exception {
        // Simulate HTTP call (replace with real implementation)
        if (url.contains("fail")) throw new RuntimeException("Simulated failure");
        return true;
    }
}
