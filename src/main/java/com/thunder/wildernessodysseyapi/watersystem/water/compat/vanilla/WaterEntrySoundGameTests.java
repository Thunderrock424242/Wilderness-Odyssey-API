package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the actual native water-entry sound hook on the logical server. */
@GameTestHolder("wildernessodysseyapi_water_safety_tests")
@PrefixGameTestTemplate(false)
public final class WaterEntrySoundGameTests {

    private WaterEntrySoundGameTests() {
    }

    @GameTest(template = "water_flow")
    public static void shallowEntityEntryDoesNotPlaySplash(GameTestHelper helper) {
        Zombie entity = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 8, 4));
        helper.assertTrue(entrySounds(helper, entity, 2.99f) == 0,
                "An entry below three blocks played an impact splash");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void threeBlockEntityEntryPlaysOneSplash(GameTestHelper helper) {
        Zombie entity = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(4, 8, 4));
        helper.assertTrue(entrySounds(helper, entity, 3.0f) == 1,
                "A three-block fall did not play exactly one native entry splash");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void fishNeverCountAsEntrySplashImpacts(GameTestHelper helper) {
        Cod entity = helper.spawnWithNoFreeWill(EntityType.COD, new BlockPos(4, 8, 4));
        helper.assertTrue(entrySounds(helper, entity, 8.0f) == 0,
                "A fish played an entity entry splash");
        helper.succeed();
    }

    @GameTest(template = "water_flow")
    public static void fallingItemPlaysOneSplash(GameTestHelper helper) {
        BlockPos dry = helper.absolutePos(new BlockPos(4, 8, 4));
        ItemEntity entity = new ItemEntity(helper.getLevel(), dry.getX() + 0.5,
                dry.getY(), dry.getZ() + 0.5, new ItemStack(Items.STONE));
        helper.getLevel().addFreshEntity(entity);
        helper.assertTrue(entrySounds(helper, entity, 4.0f) == 1,
                "A falling object did not play exactly one native entry splash");
        helper.succeed();
    }

    private static int entrySounds(GameTestHelper helper, Entity entity, float fallDistance) {
        BlockPos water = helper.absolutePos(new BlockPos(4, 4, 4));
        var chunk = helper.getLevel().getChunkAt(water);
        var original = chunk.getBlockState(water);
        chunk.setBlockState(water, Blocks.WATER.defaultBlockState(), false);
        entity.setNoGravity(true);
        // Exercise the native contact transition without unrelated AI or
        // hydrodynamic movement between the controlled dry and wet positions.
        entity.baseTick();
        entity.setPos(water.getX() + 0.5, water.getY() + 0.05, water.getZ() + 0.5);
        entity.setDeltaMovement(0.0, -0.4, 0.0);
        entity.fallDistance = fallDistance;
        SoundProbe probe = new SoundProbe(entity);
        NeoForge.EVENT_BUS.register(probe);
        try {
            entity.baseTick();
            return probe.splashes;
        } finally {
            NeoForge.EVENT_BUS.unregister(probe);
            entity.discard();
            chunk.setBlockState(water, original, false);
        }
    }

    /** Counts only this entity's native splash emission, leaving the event unchanged. */
    public static final class SoundProbe {
        private final Entity entity;
        private int splashes;

        private SoundProbe(Entity entity) {
            this.entity = entity;
        }

        @SubscribeEvent
        public void onSound(PlayLevelSoundEvent.AtPosition event) {
            if (event.getLevel() == entity.level() && event.getSound() != null
                    && event.getPosition().distanceToSqr(entity.position()) < 1.0
                    && (event.getSound().value() == SoundEvents.GENERIC_SPLASH
                    || event.getSound().value() == SoundEvents.HOSTILE_SPLASH
                    || event.getSound().value() == SoundEvents.PLAYER_SPLASH
                    || event.getSound().value() == SoundEvents.PLAYER_SPLASH_HIGH_SPEED)) {
                splashes++;
            }
        }
    }
}
