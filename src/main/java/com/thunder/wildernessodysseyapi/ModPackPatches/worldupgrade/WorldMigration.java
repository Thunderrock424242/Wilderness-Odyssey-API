package com.thunder.wildernessodysseyapi.ModPackPatches.worldupgrade;

/**
 * Defines one idempotent world migration step.
 */
public interface WorldMigration {
    String id();

    int fromVersion();

    int toVersion();

    /**
     * Applies migration logic to one chunk context.
     *
     * @return true when migration completed successfully.
     */
    boolean apply(MigrationContext context);
}
