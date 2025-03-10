package com.thunder.wildernessodysseyapi.tpdim;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

public class TPProcedure {

    public static void execute(Entity entity, ServerLevel targetDimension, double x, double y, double z) {
        if (entity == null || targetDimension == null) {
            return;
        }

        // Check if the entity is already in the correct dimension
        if (entity.level() == targetDimension) {
            entity.teleportTo(x + 0.5, y, z + 0.5);
        } else {
            entity.changeDimension(targetDimension, (entityToMove, destinationWorld, direction) -> {
                entityToMove.teleportTo(x + 0.5, y, z + 0.5);
                return entityToMove;
            });
        }
    }
}