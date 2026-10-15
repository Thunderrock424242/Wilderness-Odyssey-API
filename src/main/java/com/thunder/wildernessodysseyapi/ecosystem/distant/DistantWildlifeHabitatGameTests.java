package com.thunder.wildernessodysseyapi.ecosystem.distant;

import com.thunder.wildernessodysseyapi.ecosystem.config.EcosystemConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;

/** Exercises the real final materialization path against loaded biome habitat. */
@GameTestHolder("wildernessodysseyapi_environment_tests")
@PrefixGameTestTemplate(false)
public final class DistantWildlifeHabitatGameTests {
    private DistantWildlifeHabitatGameTests() {
    }

    @GameTest(template = "water_flow", batch = "ecosystem_habitat")
    public static void finalMaterializationRejectsLoadedUnsuitableHabitat(GameTestHelper helper) throws Exception {
        ServerLevel level = helper.getLevel();
        BlockPos anchor = helper.absolutePos(new BlockPos(8, 2, 8));
        for (int x = 0; x <= 15; x++) {
            for (int z = 0; z <= 15; z++) {
                BlockPos ground = helper.absolutePos(new BlockPos(x, 1, z));
                level.setBlock(ground, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(ground.above(2), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        helper.assertTrue(level.getBiome(anchor).value().getMobSettings().getMobs(EntityType.WOLF.getCategory())
                        .unwrap().stream().noneMatch(entry -> entry.type == EntityType.WOLF),
                "The fixture biome must exclude natural wolf habitat");
        DistantWildlifeSavedData ledger = new DistantWildlifeSavedData();
        helper.assertTrue(ledger.absorb(ResourceLocation.withDefaultNamespace("wolf"), Vec3.atBottomCenterOf(anchor),
                        1.0, 0.0, 0.4, 42L, level.getGameTime(), DistantWildlifeForm.GROUND,
                        false, true, 4, 4), "Could not establish an abstract wolf");
        DistantWildlifeGroup group = ledger.groups().getFirst();
        Method materialize = DistantWildlifeManager.class.getDeclaredMethod("tryMaterialize", ServerLevel.class,
                DistantWildlifeGroup.class, Vec3.class, double.class,
                EcosystemConfig.DistantWildlifeSettings.class, int.class);
        materialize.setAccessible(true);
        Mob spawned = (Mob) materialize.invoke(DistantWildlifeManager.get(), level, group,
                Vec3.atBottomCenterOf(anchor), 0.0, EcosystemConfig.distantWildlifeSettings(), 0);
        try {
            helper.assertTrue(spawned == null,
                    "Final materialization inserted wildlife outside its biome habitat");
            helper.assertTrue(ledger.representedAnimals() == 1,
                    "Rejected habitat must retain abstract population");
            helper.succeed();
        } finally {
            if (spawned != null) {
                spawned.discard();
            }
        }
    }
}
