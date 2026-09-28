package com.thunder.aether.server.config;

import com.thunder.aether.server.security.GatewaySecurityConfig;
import java.util.Map;

/** Operator-only commissioning gates; no remote operation may enable or modify them. */
public record ActivationConfig(boolean hostingVerified, boolean capacityVerified, boolean inferenceEnabled,
        String verifiedModel) {
    public static ActivationConfig load(Map<?, ?> values) {
        return new ActivationConfig(GatewaySecurityConfig.flag(values, "hosting_verified"),
                GatewaySecurityConfig.flag(values, "capacity_verified"),
                GatewaySecurityConfig.flag(values, "inference_enabled"),
                GatewaySecurityConfig.text(values, "verified_model", ""));
    }

    /** Capacity approval applies only to the commissioned model. */
    public boolean permits(String model) {
        return hostingVerified && capacityVerified && inferenceEnabled && model.equals(verifiedModel);
    }

    public static ActivationConfig disabled() { return load(Map.of()); }
}
