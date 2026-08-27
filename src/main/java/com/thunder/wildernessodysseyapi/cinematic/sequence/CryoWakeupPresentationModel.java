package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.thunder.wildernessodysseyapi.cinematic.client.CinematicCameraShake;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Pure, allocation-free presentation curves for the cryogenic revival. */
public final class CryoWakeupPresentationModel {
    private CryoWakeupPresentationModel() {
    }

    /** Returns whether the stage uses the detached view looking into the tube. */
    public static boolean isExteriorStage(ResourceLocation stage) {
        return CryoWakeupSequence.EXTERIOR_REVEAL.equals(stage)
                || CryoWakeupSequence.MEDICAL_DIAGNOSTIC.equals(stage)
                || CryoWakeupSequence.REVIVAL_PROTOCOL.equals(stage)
                || CryoWakeupSequence.CARDIAC_PACING.equals(stage)
                || CryoWakeupSequence.SUSPENSION_DRAIN.equals(stage);
    }

    /** Returns whether the stage uses the lower, occupant-relative first-person camera. */
    public static boolean isFirstPersonStage(ResourceLocation stage) {
        return CryoWakeupSequence.BLACKOUT_TRANSITION.equals(stage)
                || CryoWakeupSequence.EYES_REOPENING.equals(stage)
                || CryoWakeupSequence.MASK_RELEASE.equals(stage)
                || CryoWakeupSequence.CRYO_OPENING.equals(stage)
                || CryoWakeupSequence.BALANCE_CHECK.equals(stage);
    }

    /** Normalized eyelid opening. Exterior stages do not use this mask. */
    public static float eyeOpenAmount(ResourceLocation stage, float progress) {
        float t = smooth(progress);
        if (CryoWakeupSequence.BLACK_SCREEN.equals(stage)
                || CryoWakeupSequence.BLACKOUT_TRANSITION.equals(stage)) {
            return 0.0F;
        }
        if (CryoWakeupSequence.EYES_REOPENING.equals(stage)) {
            float blink = triangularBlink(progress, 0.58F, 0.09F) * 0.26F;
            return Mth.clamp(0.86F * t - blink, 0.0F, 0.86F);
        }
        if (CryoWakeupSequence.MASK_RELEASE.equals(stage)) {
            return Mth.lerp(t, 0.86F, 0.94F);
        }
        if (CryoWakeupSequence.CRYO_OPENING.equals(stage)) {
            return Mth.lerp(t, 0.94F, 1.0F);
        }
        if (CryoWakeupSequence.BALANCE_CHECK.equals(stage)
                || CryoWakeupSequence.RECOVERY_WALK.equals(stage)) {
            return 1.0F;
        }
        return 0.0F;
    }

    /** Lateral orbit, in blocks, for the detached exterior camera. */
    public static float exteriorOrbitOffset(ResourceLocation stage, float progress) {
        float t = smooth(progress);
        if (CryoWakeupSequence.EXTERIOR_REVEAL.equals(stage)) {
            return Mth.lerp(t, -0.72F, -0.22F);
        }
        if (CryoWakeupSequence.MEDICAL_DIAGNOSTIC.equals(stage)) {
            return Mth.lerp(t, -0.22F, 0.20F);
        }
        if (CryoWakeupSequence.REVIVAL_PROTOCOL.equals(stage)) {
            return Mth.lerp(t, 0.20F, 0.48F);
        }
        if (CryoWakeupSequence.CARDIAC_PACING.equals(stage)) {
            return Mth.lerp(t, 0.48F, 0.26F);
        }
        if (CryoWakeupSequence.SUSPENSION_DRAIN.equals(stage)) {
            return Mth.lerp(t, 0.26F, 0.0F);
        }
        return 0.0F;
    }

    /** Subtle occupant-view yaw motion relative to the tube facing. */
    public static float yawOffset(ResourceLocation stage, float progress, float time) {
        if (CryoWakeupSequence.RECOVERY_WALK.equals(stage)) {
            return 0.0F;
        }
        float drift = Mth.sin(time * 0.055F) * 0.42F;
        if (CryoWakeupSequence.BALANCE_CHECK.equals(stage)) {
            return Mth.lerp(smooth(progress), -2.4F, 0.0F) + drift;
        }
        return -2.4F + drift;
    }

    /** Reclined first-person pitch that levels out before movement returns. */
    public static float pitchOffset(ResourceLocation stage, float progress, float time) {
        if (CryoWakeupSequence.RECOVERY_WALK.equals(stage)) {
            return 0.0F;
        }
        float breathing = Mth.sin(time * 0.085F + 0.9F) * 0.30F;
        if (CryoWakeupSequence.EYES_REOPENING.equals(stage)) {
            return Mth.lerp(smooth(progress), 7.5F, 4.0F) + breathing;
        }
        if (CryoWakeupSequence.MASK_RELEASE.equals(stage)) {
            return Mth.lerp(smooth(progress), 4.0F, 2.2F) + breathing;
        }
        if (CryoWakeupSequence.CRYO_OPENING.equals(stage)) {
            return Mth.lerp(smooth(progress), 2.2F, 0.8F) + breathing;
        }
        if (CryoWakeupSequence.BALANCE_CHECK.equals(stage)) {
            return Mth.lerp(smooth(progress), 0.8F, 0.0F) + breathing;
        }
        return 7.5F + breathing;
    }

    /** Roll combines an initial recline with bounded mechanical and cardiac jolts. */
    public static float rollOffset(ResourceLocation stage, float progress, float time) {
        float base = -1.8F;
        if (CryoWakeupSequence.CRYO_OPENING.equals(stage)
                || CryoWakeupSequence.BALANCE_CHECK.equals(stage)) {
            base = Mth.lerp(smooth(progress), -1.8F, 0.0F);
        }
        return base + shake(stage, progress, time);
    }

    /** Red diagnostic wash used while the failing medical system is active. */
    public static float warningAlpha(ResourceLocation stage, float progress, float time) {
        if (CryoWakeupSequence.MEDICAL_DIAGNOSTIC.equals(stage)
                || CryoWakeupSequence.REVIVAL_PROTOCOL.equals(stage)) {
            return 0.035F + Math.abs(Mth.sin(time * 0.24F)) * 0.045F;
        }
        if (CryoWakeupSequence.CARDIAC_PACING.equals(stage)) {
            return 0.075F + Math.abs(Mth.sin(time * 0.82F)) * 0.095F;
        }
        if (CryoWakeupSequence.SUSPENSION_DRAIN.equals(stage)) {
            return (1.0F - smooth(progress)) * 0.08F;
        }
        return 0.0F;
    }

    /** Blue-green fluid wash, reduced as the contaminated suspension medium drains. */
    public static float suspensionAlpha(ResourceLocation stage, float progress) {
        if (CryoWakeupSequence.SUSPENSION_DRAIN.equals(stage)) {
            return Mth.lerp(smooth(progress), 0.105F, 0.0F);
        }
        return isExteriorStage(stage) ? 0.105F : 0.0F;
    }

    /** Relative visual-disorientation strength used to select accessibility-aware blur. */
    public static float blurStrength(ResourceLocation stage, float progress) {
        if (CryoWakeupSequence.EYES_REOPENING.equals(stage)) {
            return Mth.lerp(smooth(progress), 1.0F, 0.66F);
        }
        if (CryoWakeupSequence.MASK_RELEASE.equals(stage)) {
            return Mth.lerp(smooth(progress), 0.66F, 0.46F);
        }
        if (CryoWakeupSequence.CRYO_OPENING.equals(stage)) {
            return Mth.lerp(smooth(progress), 0.46F, 0.28F);
        }
        if (CryoWakeupSequence.BALANCE_CHECK.equals(stage)) {
            return Mth.lerp(smooth(progress), 0.28F, 0.14F);
        }
        if (CryoWakeupSequence.RECOVERY_WALK.equals(stage)) {
            return Mth.lerp(smooth(progress), 0.14F, 0.0F);
        }
        return 0.0F;
    }

    private static float shake(ResourceLocation stage, float progress, float time) {
        float intensity;
        if (CryoWakeupSequence.CARDIAC_PACING.equals(stage)) {
            intensity = 0.85F;
        } else if (CryoWakeupSequence.CRYO_OPENING.equals(stage)) {
            intensity = 0.42F;
        } else if (CryoWakeupSequence.BALANCE_CHECK.equals(stage)) {
            intensity = 0.18F;
        } else {
            return 0.0F;
        }
        return CinematicCameraShake.sample(time, intensity, progress, CinematicCameraShake.Falloff.SMOOTH);
    }

    private static float triangularBlink(float value, float center, float halfWidth) {
        return Mth.clamp(1.0F - Math.abs(value - center) / halfWidth, 0.0F, 1.0F);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
