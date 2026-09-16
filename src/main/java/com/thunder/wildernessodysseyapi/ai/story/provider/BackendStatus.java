package com.thunder.wildernessodysseyapi.ai.story.provider;

import com.thunder.wildernessodysseyapi.ai.story.AIBackendConfig;

/** Last observed gateway status; reading this snapshot never performs network I/O. */
public record BackendStatus(boolean enabled, AIBackendConfig.Mode mode, String endpoint,
                            boolean reachable, boolean modelReady, String model,
                            long latencyMs, String error) {
}
