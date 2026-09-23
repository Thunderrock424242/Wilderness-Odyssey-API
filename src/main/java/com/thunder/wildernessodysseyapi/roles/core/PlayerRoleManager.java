package com.thunder.wildernessodysseyapi.roles.core;

import com.thunder.wildernessodysseyapi.roles.api.PlayerRole;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages role assignments for players.
 */
public class PlayerRoleManager {
    private static final Map<UUID, PlayerRole> roleMap = new HashMap<>();

    /**
     * Assigns a {@link PlayerRole} to the specified player.
     *
     * @param uuid the player's UUID
     * @param role the role to assign
     */
    public static void assignRole(UUID uuid, PlayerRole role) {
        roleMap.put(uuid, role);
    }

    /**
     * Retrieves the role of the given player.
     *
     * @param uuid the player's UUID
     * @return the assigned role or {@code PlayerRole.NONE}
     */
    public static PlayerRole getRole(UUID uuid) {
        return roleMap.getOrDefault(uuid, PlayerRole.NONE);
    }
}
