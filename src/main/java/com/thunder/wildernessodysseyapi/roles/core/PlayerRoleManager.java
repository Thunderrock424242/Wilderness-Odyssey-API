package com.thunder.wildernessodysseyapi.roles.core;

import com.thunder.wildernessodysseyapi.roles.api.PlayerRole;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerRoleManager {
    private static final Map<UUID, PlayerRole> roleMap = new HashMap<>();

    public static void assignRole(UUID uuid, PlayerRole role) {
        roleMap.put(uuid, role);
    }

    public static PlayerRole getRole(UUID uuid) {
        return roleMap.getOrDefault(uuid, PlayerRole.NONE);
    }
}