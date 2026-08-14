package com.thunder.wildernessodysseyapi.developmentstudio.inspection.provider;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspection;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspectionLine;
import com.thunder.wildernessodysseyapi.developmentstudio.inspection.StudioInspectionProvider;
import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.ecosystem.data.SpeciesBehaviorProfileManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Exposes real identity, health, movement, target, and navigation state for entities. */
public final class EntityStudioInspectionProvider implements StudioInspectionProvider<Entity> {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            ModConstants.MOD_ID, "entity"
    );

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public Class<Entity> targetType() {
        return Entity.class;
    }

    @Override
    public StudioInspection inspect(ServerPlayer player, Entity entity) {
        List<StudioInspectionLine> lines = new ArrayList<>();
        lines.add(new StudioInspectionLine("Registry ID", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()));
        lines.add(new StudioInspectionLine("UUID", entity.getUUID().toString()));
        lines.add(new StudioInspectionLine("Entity ID", Integer.toString(entity.getId())));
        lines.add(new StudioInspectionLine("Position", formatVector(entity.position())));
        lines.add(new StudioInspectionLine("Velocity", formatVector(entity.getDeltaMovement())));
        lines.add(new StudioInspectionLine("On ground", Boolean.toString(entity.onGround())));

        if (entity instanceof LivingEntity living) {
            lines.add(new StudioInspectionLine("Health", String.format(
                    Locale.ROOT, "%.2f / %.2f", living.getHealth(), living.getMaxHealth())));
        }
        if (entity instanceof Mob mob) {
            Entity target = mob.getTarget();
            lines.add(new StudioInspectionLine("Current target", target == null
                    ? "none"
                    : BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()) + " " + target.getUUID()));
            lines.add(new StudioInspectionLine("Navigation", mob.getNavigation().isDone() ? "idle" : "active"));
            lines.add(new StudioInspectionLine("No AI", Boolean.toString(mob.isNoAi())));
        }
        if (entity instanceof PathfinderMob animal) {
            SpeciesBehaviorProfileManager.profileFor(animal).ifPresent(profile -> {
                lines.add(new StudioInspectionLine("Ecosystem profile", profile.id().toString()));
                animal.getExistingData(ModAttachments.ANIMAL_NEEDS).ifPresentOrElse(needs -> {
                    lines.add(new StudioInspectionLine("Ecosystem behavior", needs.behavior().toString()));
                    lines.add(new StudioInspectionLine("Ecosystem needs", String.format(
                            Locale.ROOT,
                            "thirst=%.3f hunger=%.3f rest=%.3f social=%.3f safety=%.3f",
                            needs.thirst(), needs.hunger(), needs.rest(), needs.social(), needs.safetyConcern()
                    )));
                    lines.add(new StudioInspectionLine("Behavior target", needs.behaviorTarget() == null
                            ? "none" : needs.behaviorTarget().toShortString()));
                }, () -> lines.add(new StudioInspectionLine(
                        "Ecosystem state", "profile loaded; needs attachment not initialized"
                )));
            });
        }
        return new StudioInspection(ID, "Entity Inspector", lines);
    }

    private static String formatVector(Vec3 vector) {
        return String.format(Locale.ROOT, "%.3f, %.3f, %.3f", vector.x, vector.y, vector.z);
    }
}
