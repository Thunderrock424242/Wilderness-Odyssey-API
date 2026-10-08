package com.thunder.wildernessodysseyapi.meteor.worldgen;

import com.thunder.wildernessodysseyapi.environment.api.EnvironmentServices;
import com.thunder.wildernessodysseyapi.environment.network.EnvironmentSyncPayload;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteServices;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteSource;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** Loaded-server handoff and public environmental composition, without terrain scans. */
@GameTestHolder("wildernessodysseyapi_environment_tests")
@PrefixGameTestTemplate(false)
public final class MeteorEnvironmentGameTests {
    private MeteorEnvironmentGameTests() {
    }

    @GameTest(template = "empty", batch = "meteor_environment")
    public static void promotedCraterStartReachesTheServerSavedIndex(GameTestHelper helper) {
        var level = helper.getLevel();
        var chunk = level.getChunkAt(helper.absolutePos(new BlockPos(1, 1, 1)));
        var structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(
                ResourceLocation.fromNamespaceAndPath("wildernessodysseyapi", "meteor_crater"));
        helper.assertTrue(structure instanceof MeteorCraterStructure, "Crater structure must be registered");
        // A distinct height identifies this fixture without changing terrain or
        // colliding with an already indexed natural center in the arena chunk.
        BlockPos center = new BlockPos(chunk.getPos().getMiddleBlockX(), level.getMaxBuildHeight() - 2,
                chunk.getPos().getMiddleBlockZ());
        MeteorCraterPlan plan = new MeteorCraterPlan(center, 60, 12, 8, 20, 11L);
        var previousStarts = new HashMap<>(chunk.getAllStarts());
        var starts = new HashMap<>(previousStarts);
        starts.put(structure, new StructureStart(structure, chunk.getPos(), 0,
                new PiecesContainer(List.of(new MeteorCraterPiece(plan, level.getMinBuildHeight(),
                        level.getMaxBuildHeight() - 1)))));
        try {
            chunk.setAllStarts(starts);
            MeteorCraterEvents.onChunkLoad(new ChunkEvent.Load(chunk, false));
        } finally {
            chunk.setAllStarts(previousStarts);
        }
        helper.runAfterDelay(5L, () -> {
            boolean indexed = MeteorSavedData.get(level).getMeteors().stream()
                    .anyMatch(record -> record.center().equals(center)
                            && record.source() == MeteorSiteSource.WORLDGEN && record.craterRadius() == 60);
            helper.assertTrue(indexed, "Promoted natural crater was not handed to the server's saved index");
            int size = MeteorSavedData.get(level).getMeteors().size();
            MeteorSiteServices.recordGeneratedSite(level, center, 60);
            helper.assertTrue(MeteorSavedData.get(level).getMeteors().size() == size,
                    "Repeated chunk promotion duplicated a saved site");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "meteor_environment")
    public static void regionalRadiationAndPacketUseTheStrongestOverlappingSite(GameTestHelper helper) {
        var level = helper.getLevel();
        // Querying a distant region must not load it; the public authorities
        // compose saved sites and cached owner samples independently of terrain.
        BlockPos query = new BlockPos(1_000_008, 80, 1_000_008);
        helper.assertTrue(!level.hasChunkAt(query), "Fixture region must start unloaded");
        var data = MeteorSavedData.get(level);
        var nearest = data.addMeteor(query.offset(10, 0, 0), 5, level.getGameTime(), 1.0,
                MeteorSiteSource.WORLDGEN);
        data.addMeteor(query.offset(30, 0, 0), 125, level.getGameTime(), 1.0, MeteorSiteSource.WORLDGEN);
        EnvironmentServices.invalidate(level, query, 512);
        var snapshot = EnvironmentServices.query().sample(level, query);
        helper.assertTrue(snapshot.meteorSite().id().equals(nearest.id()), "Nearest-site metadata changed");
        helper.assertTrue(snapshot.meteorSite().radiation() == 0.0, "Small nearest site should have no exposure here");
        helper.assertTrue(Math.abs(snapshot.radiation() - 0.84) < 0.0001,
                "Regional exposure ignored the stronger overlapping site");
        var player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "radiation-fixture"), ClientInformation.createDefault());
        helper.assertTrue(Math.abs(EnvironmentSyncPayload.from(player, snapshot)
                        .radiation() - 0.84) < 0.0001,
                "Synchronized exposure differs from the authoritative region");
        helper.assertTrue(!level.hasChunkAt(query), "Environmental composition forced a distant chunk load");
        helper.succeed();
    }
}
