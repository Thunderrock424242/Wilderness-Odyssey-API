package com.thunder.wildernessodysseyapi.mixin.bridge;

/**
 * Bridge contract used by mixins to synchronize structure-block corner cache lifecycle hooks
 * without relying on reflective invocation.
 */
public interface StructureBlockCornerCacheBridge {

    void wildernessodysseyapi$bridge$syncCornerCache();

    void wildernessodysseyapi$bridge$removeCornerFromCache();
}
