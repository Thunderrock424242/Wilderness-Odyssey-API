package com.thunder.wildernessodysseyapi.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

public class PurpleStormMonsterEntity extends Zombie {

    public PurpleStormMonsterEntity(EntityType<? extends Zombie> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    public static boolean checkPurpleStormSpawnRules(EntityType<PurpleStormMonsterEntity> type,
                                                     ServerLevelAccessor level,
                                                     MobSpawnType reason,
                                                     BlockPos pos,
                                                     RandomSource random) {
        return Monster.isDarkEnoughToSpawn(level, pos, random)
                && checkMobSpawnRules(type, level, reason, pos, random)
                && level.getLevel().isRaining();
    }

    @Override
    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        // Intentionally no default equipment for a clean vanilla-compatible spawn profile.
    }
}
