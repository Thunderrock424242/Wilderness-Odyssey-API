/**
 * Wilderness Odyssey Adaptive GPU Engine (WAGE): shared render-frame contracts,
 * backend capabilities, optional adaptive quality, workload timing and GPU policy.
 *
 * <p>The existing {@link com.thunder.wildernessodysseyapi.wage.client.WildernessRenderingFramework}
 * owns the client frame lifecycle. Minecraft retains render-pass and resource ownership;
 * weather and water retain their own rendering and gameplay. Shared JVM sampling stays
 * in OPC rather than being duplicated here.</p>
 *
 * <p>Client frame subscribers, diagnostics, backend discovery and OpenGL implementations
 * require the client/render thread. Pure contracts and configuration remain usable by
 * common config assembly without initializing those client owners. GPU policy remains
 * disabled by default. Package relocation preserves config keys and resource identifiers;
 * downstream Java callers must update imports and recompile.</p>
 *
 * <p>See {@code docs/performance/wage-packages.md} for the package map and migration.</p>
 */
package com.thunder.wildernessodysseyapi.wage;
