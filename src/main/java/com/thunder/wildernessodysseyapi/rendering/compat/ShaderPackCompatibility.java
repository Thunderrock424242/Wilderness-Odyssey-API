package com.thunder.wildernessodysseyapi.rendering.compat;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import net.neoforged.fml.ModList;

/** Shared, cached optional integration boundary for active Iris/Oculus shader packs. */
public final class ShaderPackCompatibility {

    private static final IrisShaderPackProbe PROBE = new IrisShaderPackProbe(
            ShaderPackCompatibility.class.getClassLoader(),
            "net.irisshaders.iris.api.v0.IrisApi",
            "net.coderbot.iris.api.v0.IrisApi"
    );
    private static boolean queryFailureLogged;
    private static volatile long sampledFrame = Long.MIN_VALUE;
    private static volatile Status statusForFrame = Status.NOT_INSTALLED;

    private ShaderPackCompatibility() {
    }

    /** Distinguishes native rendering from active or uncertain external ownership. */
    public enum Status {
        NOT_INSTALLED,
        DISABLED,
        ACTIVE,
        UNKNOWN;

        /** Uncertain optional integrations conservatively retain pack ownership. */
        public boolean ownsWorldEffects() {
            return this == ACTIVE || this == UNKNOWN;
        }
    }

    /**
     * Returns whether a shader pack should remain authoritative for world effects.
     * Unknown installed APIs fail closed to Minecraft's tagged/vanilla paths.
     */
    public static boolean isExternalShaderPackActive() {
        return status().ownsWorldEffects();
    }

    /** Returns the ownership sampled for all water and weather consumers this frame. */
    public static Status status() {
        if (sampledFrame != Long.MIN_VALUE) {
            return statusForFrame;
        }
        return queryStatus();
    }

    /** Samples optional shader ownership once for all rendering paths in one frame. */
    public static void sampleFrame(long frameIndex) {
        if (sampledFrame == frameIndex) {
            return;
        }
        synchronized (ShaderPackCompatibility.class) {
            if (sampledFrame == frameIndex) {
                return;
            }
            statusForFrame = queryStatus();
            sampledFrame = frameIndex;
        }
    }

    private static synchronized Status queryStatus() {
        ModList mods = ModList.get();
        if (mods == null) {
            return Status.UNKNOWN;
        }
        Status status = PROBE.sample(mods.isLoaded("iris") || mods.isLoaded("oculus"));
        if (status == Status.UNKNOWN && !queryFailureLogged) {
            ModConstants.LOGGER.warn(
                    "Unable to query the active Iris/Oculus shader pack; preserving compatibility rendering",
                    PROBE.failure()
            );
            queryFailureLogged = true;
        }
        return status;
    }
}
