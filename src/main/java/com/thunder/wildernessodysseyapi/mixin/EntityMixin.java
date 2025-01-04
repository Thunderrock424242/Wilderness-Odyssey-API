package com.thunder.wildernessodysseyapi.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void applyWaveAndTideMotion(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        // Skip updates for entities far from players
        Player nearestPlayer = entity.level.getNearestPlayer(entity, 64); // 64-block radius
        if (nearestPlayer == null) {
            return;
        }

        // Handle boats
        if (entity instanceof Boat boat) {
            applyWaveMotionToBoat(boat);
        }

        // Handle sea creatures
        if (entity.isInWater() && entity instanceof Mob seaCreature) {
            applyTideMotionToSeaCreature(seaCreature);
        }
    }

    private void applyWaveMotionToBoat(Entity boat) {
        float time = System.currentTimeMillis() / 1000.0F;
        float tideHeight = (float) Math.sin(time / 120.0F) * 1.5F; // Tides
        float waveOffset = (float) Math.sin(boat.getX() * 0.1F + time * 2.0F) * 0.2F; // Waves

        // Smoothly interpolate to new position
        float currentY = (float) boat.getY();
        float targetY = currentY + tideHeight + waveOffset;
        boat.setPos(boat.getX(), currentY + (targetY - currentY) * 0.1F, boat.getZ()); // Smooth transition
    }

    private void applyTideMotionToSeaCreature(Entity seaCreature) {
        float time = System.currentTimeMillis() / 1000.0F;
        float tideHeight = (float) Math.sin(time / 120.0F) * 1.5F; // Tides

        // Smoothly interpolate to new position
        float currentY = (float) seaCreature.getY();
        float targetY = (float) (seaCreature.getY() + tideHeight);
        seaCreature.setPos(seaCreature.getX(), currentY + (targetY - currentY) * 0.1F, seaCreature.getZ());
    }
}
