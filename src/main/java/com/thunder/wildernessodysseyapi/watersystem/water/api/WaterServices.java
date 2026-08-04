package com.thunder.wildernessodysseyapi.watersystem.water.api;

import com.thunder.wildernessodysseyapi.watersystem.water.authority.AuthorityWaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.authority.AuthorityWaterBuoyancyProvider;

/**
 * Stable entry point for the active custom-water query and physics services.
 *
 * <p>The implementation remains private to the water subsystem so future
 * storage migrations do not force compatibility adapters to change.</p>
 */
public final class WaterServices {

    /** Public compatibility/API contract version for optional integrations. */
    public static final int API_VERSION = 2;

    private static final WaterAccess ACCESS = new AuthorityWaterAccess();
    private static final WaterBuoyancyProvider BUOYANCY = new AuthorityWaterBuoyancyProvider(ACCESS);

    private WaterServices() {
    }

    /** Returns the process-wide facade that resolves state per supplied level. */
    public static WaterAccess access() {
        return ACCESS;
    }

    /** Returns reusable buoyancy logic backed by the same authority facade. */
    public static WaterBuoyancyProvider buoyancy() {
        return BUOYANCY;
    }

    /** Returns the public water API contract version supported by this build. */
    public static int apiVersion() {
        return API_VERSION;
    }
}
