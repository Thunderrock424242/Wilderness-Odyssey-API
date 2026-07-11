package com.thunder.wildernessodysseyapi.watersystem.water.compat;

/**
 * Lifecycle contract for an isolated vanilla or optional-mod water adapter.
 *
 * <p>Adapters translate external behavior into {@code WaterServices} queries or
 * controlled mutations. They must not own water storage or duplicate surface,
 * depth, current, or body calculations.</p>
 */
public interface WaterCompatibilityAdapter {

    /** Stable lowercase identifier used by diagnostics and configuration. */
    String id();

    /** Current documented support level for the external feature. */
    CompatibilityLevel compatibilityLevel();

    /** Returns false when an optional dependency is not installed. */
    boolean isAvailable();

    /** Registers the adapter's events or other runtime hooks exactly once. */
    void initialize();
}
