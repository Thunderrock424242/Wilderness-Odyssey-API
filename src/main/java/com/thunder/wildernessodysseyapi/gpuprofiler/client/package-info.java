/**
 * Legacy OpenGL GPU profiling implementation for Minecraft 1.21.1.
 *
 * <p>The profiler's reports remain useful, but its memory probes, timestamp
 * queries, state inspection, and allocation hooks depend on OpenGL. A future
 * profiler must consume supported backend telemetry instead.</p>
 *
 * @deprecated Replace with backend-provided diagnostics in the Vulkan-targeted version.
 */
@Deprecated(forRemoval = true)
package com.thunder.wildernessodysseyapi.gpuprofiler.client;
