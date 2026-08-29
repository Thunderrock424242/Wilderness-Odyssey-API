package com.thunder.wildernessodysseyapi.rendering;

/**
 * Shared transient quality ceiling for expensive client rendering effects.
 *
 * <p>Feature configs remain the player's source of truth. This value can only
 * reduce work while adaptive quality is enabled; it never enables an effect
 * the player disabled and is never written back to configuration.</p>
 */
public enum RenderingQuality {
    LOW,
    MEDIUM,
    HIGH,
    CINEMATIC;

    /** Returns whether this tier includes work assigned to {@code required}. */
    public boolean allows(RenderingQuality required) {
        return required != null && ordinal() >= required.ordinal();
    }

    /** Returns {@code preferred} clamped to the inclusive shared bounds. */
    public static RenderingQuality clamp(
            RenderingQuality preferred,
            RenderingQuality minimum,
            RenderingQuality maximum
    ) {
        RenderingQuality safeMinimum = minimum == null ? LOW : minimum;
        RenderingQuality safeMaximum = maximum == null ? CINEMATIC : maximum;
        if (safeMinimum.ordinal() > safeMaximum.ordinal()) {
            RenderingQuality swap = safeMinimum;
            safeMinimum = safeMaximum;
            safeMaximum = swap;
        }
        RenderingQuality safePreferred = preferred == null ? safeMaximum : preferred;
        if (safePreferred.ordinal() < safeMinimum.ordinal()) {
            return safeMinimum;
        }
        return safePreferred.ordinal() > safeMaximum.ordinal() ? safeMaximum : safePreferred;
    }
}
