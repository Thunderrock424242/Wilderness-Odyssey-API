/**
 * Odyssey Performance Core (OPC): shared performance policy, scheduling, workers, data pipelines,
 * diagnostics and optional native-save observation. Feature gameplay remains with its owner.
 *
 * <p>Start with {@link com.thunder.wildernessodysseyapi.opc.core.OdysseyPerformanceCore} for policy,
 * {@link com.thunder.wildernessodysseyapi.opc.async.AsyncTaskManager} for shared CPU/IO execution,
 * and {@link com.thunder.wildernessodysseyapi.opc.dataengine.DataEngine} for feature data updates.
 * GPU and loading client code stays beneath {@code opc.diagnostics.*.client}; common callers
 * must not load it. The native-save adapter is isolated in {@code opc.saving}, not a chunk governor.</p>
 *
 * <p>Configuration keys, resource identifiers and server/client lifecycle ownership are unchanged
 * by package organization. See {@code docs/performance/opc-packages.md} for the map and API migration.</p>
 */
package com.thunder.wildernessodysseyapi.opc;
