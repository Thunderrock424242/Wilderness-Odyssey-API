package com.thunder.wildernessodysseyapi.loading;

/**
 * Implemented by the narrow server mixin. Only the client thread requests
 * cancellation; native server cleanup retains ownership of worlds and saves.
 */
public interface WorldLoadCancellation {
    void wildernessodysseyapi$cancelWorldLoad();
}
