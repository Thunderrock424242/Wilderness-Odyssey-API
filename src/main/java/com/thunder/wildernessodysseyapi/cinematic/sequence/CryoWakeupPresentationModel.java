package com.thunder.wildernessodysseyapi.cinematic.sequence;

import com.thunder.wildernessodysseyapi.cinematic.client.CinematicCameraShake;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Pure, allocation-free curves used by the cryo client presentation and focused tests. */
public final class CryoWakeupPresentationModel {
    private CryoWakeupPresentationModel() {
    }

    /** Returns the normalized eyelid opening amount for the active stage. */
    public static float eyeOpenAmount(ResourceLocation stage, float progress) {
        float t = smooth(progress);
        if (stage.equals(CryoWakeupSequence.BLACK_SCREEN)
                || stage.equals(CryoWakeupSequence.MACHINERY_HUM)
                || stage.equals(CryoWakeupSequence.HEARTBEAT)
                || stage.equals(CryoWakeupSequence.EYES_CLOSED)) {
            return 0.0F;
        }
        if (stage.equals(CryoWakeupSequence.EYES_PARTIAL)) {
            float pulse = progress < 0.55F
                    ? smooth(progress / 0.55F)
                    : 1.0F - smooth((progress - 0.55F) / 0.45F);
            return 0.24F * pulse;
        }
        if (stage.equals(CryoWakeupSequence.EYES_REOPENING)) {
            return 0.62F * t;
        }
        if (stage.equals(CryoWakeupSequence.LIGHTS_FLICKER)) {
            return Mth.lerp(t, 0.62F, 0.68F);
        }
        if (stage.equals(CryoWakeupSequence.WARNING_STARTED)) {
            return Mth.lerp(t, 0.68F, 0.74F);
        }
        if (stage.equals(CryoWakeupSequence.WARNING_LIGHTS)) {
            return Mth.lerp(t, 0.74F, 0.79F);
        }
        if (stage.equals(CryoWakeupSequence.ALARM_BEEPS)) {
            float blink = triangularBlink(progress, 0.34F, 0.12F);
            return Mth.clamp(0.80F - blink * 0.43F, 0.34F, 0.82F);
        }
        if (stage.equals(CryoWakeupSequence.RELEASE_STARTED)) {
            return Mth.lerp(t, 0.80F, 0.86F);
        }
        if (stage.equals(CryoWakeupSequence.LOCKS_DISENGAGED)
                || stage.equals(CryoWakeupSequence.MIST_RELEASE)) {
            return Mth.lerp(t, 0.86F, 0.91F);
        }
        if (stage.equals(CryoWakeupSequence.CRYO_OPENING)) {
            return Mth.lerp(t, 0.91F, 0.98F);
        }
        return 1.0F;
    }

    /** Subtle yaw offset relative to the server-supplied tube facing. */
    public static float yawOffset(ResourceLocation stage, float progress, float time) {
        float breathing = Mth.sin(time * 0.075F) * 0.35F;
        if (stage.equals(CryoWakeupSequence.CAMERA_TURN)) {
            return Mth.lerp(smooth(progress), -1.5F, 8.0F) + breathing;
        }
        if (stage.equals(CryoWakeupSequence.CRYO_OPEN)) {
            return 8.0F + breathing;
        }
        if (stage.equals(CryoWakeupSequence.LIGHTS_STABLE)
                || stage.equals(CryoWakeupSequence.CAMERA_RELEASE)) {
            return Mth.lerp(smooth(progress), 8.0F, 2.0F) + breathing;
        }
        return -1.5F + breathing;
    }

    /** Slight reclined pitch that rises as the pod opens. */
    public static float pitchOffset(ResourceLocation stage, float progress, float time) {
        float breathing = Mth.sin(time * 0.10F + 0.8F) * 0.22F;
        if (stage.equals(CryoWakeupSequence.CAMERA_TURN)) {
            return Mth.lerp(smooth(progress), 6.0F, -3.5F) + breathing;
        }
        if (stage.equals(CryoWakeupSequence.CRYO_OPEN)) {
            return -3.5F + breathing;
        }
        if (stage.equals(CryoWakeupSequence.LIGHTS_STABLE)
                || stage.equals(CryoWakeupSequence.CAMERA_RELEASE)) {
            return Mth.lerp(smooth(progress), -3.5F, 0.0F) + breathing;
        }
        return 6.0F + breathing;
    }

    /** Roll combines the initial tube tilt with bounded mechanical shake. */
    public static float rollOffset(ResourceLocation stage, float progress, float time) {
        float baseRoll = -1.35F;
        if (stage.equals(CryoWakeupSequence.CAMERA_TURN)
                || stage.equals(CryoWakeupSequence.CRYO_OPEN)
                || stage.equals(CryoWakeupSequence.LIGHTS_STABLE)
                || stage.equals(CryoWakeupSequence.CAMERA_RELEASE)) {
            baseRoll = Mth.lerp(smooth(progress), -1.35F, 0.0F);
        }
        return baseRoll + shake(stage, progress, time);
    }

    /** Red warning-light wash; the flicker is presentation-only and deterministic. */
    public static float warningAlpha(ResourceLocation stage, float progress, float time) {
        if (stage.equals(CryoWakeupSequence.WARNING_LIGHTS)
                || stage.equals(CryoWakeupSequence.ALARM_BEEPS)
                || stage.equals(CryoWakeupSequence.RELEASE_STARTED)
                || stage.equals(CryoWakeupSequence.LOCKS_DISENGAGED)
                || stage.equals(CryoWakeupSequence.MIST_RELEASE)
                || stage.equals(CryoWakeupSequence.CRYO_OPENING)) {
            return 0.055F + Math.abs(Mth.sin(time * 0.42F)) * 0.075F;
        }
        if (stage.equals(CryoWakeupSequence.LIGHTS_FLICKER)) {
            return Math.abs(Mth.sin(time * 1.7F)) * 0.035F;
        }
        if (stage.equals(CryoWakeupSequence.LIGHTS_STABLE)) {
            return (1.0F - smooth(progress)) * 0.045F;
        }
        return 0.0F;
    }

    private static float shake(ResourceLocation stage, float progress, float time) {
        float intensity;
        if (stage.equals(CryoWakeupSequence.MIST_RELEASE)) {
            intensity = 0.22F;
        } else if (stage.equals(CryoWakeupSequence.CRYO_OPENING)) {
            intensity = 0.55F;
        } else if (stage.equals(CryoWakeupSequence.CAMERA_TURN)) {
            intensity = 0.16F;
        } else {
            return 0.0F;
        }
        return CinematicCameraShake.sample(
                time,
                intensity,
                progress,
                CinematicCameraShake.Falloff.SMOOTH
        );
    }

    private static float triangularBlink(float value, float center, float halfWidth) {
        return Mth.clamp(1.0F - Math.abs(value - center) / halfWidth, 0.0F, 1.0F);
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
