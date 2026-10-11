package com.thunder.wildernessodysseyapi.temporalrift.hybrid;

import com.mojang.authlib.GameProfile;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.temporalrift.TemporalRiftSavedData;
import com.thunder.wildernessodysseyapi.temporalrift.blockentity.RiftCoreBlockEntity;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlocks;
import com.thunder.wildernessodysseyapi.worldgen.structure.rift.RiftRockPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.UUID;

/** Loaded-world contracts. Fake-player packets do not prove real client or multiplayer behavior. */
@GameTestHolder("wildernessodysseyapi_rift_tests")
@PrefixGameTestTemplate(false)
public final class RiftHybridGameTests {
    private RiftHybridGameTests() { }

    @GameTest(template = "rift_test_clear")
    public static void legacyTagsAndHybridStateRoundTrip(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(16, 5, 16));
        var state = TemporalRiftBlocks.RIFT_CORE.get().defaultBlockState();
        var core = new RiftCoreBlockEntity(anchor, state);
        CompoundTag legacy = new CompoundTag();
        legacy.putFloat("renderScale", 0.6F);
        core.loadWithComponents(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(!core.isHybrid() && core.getVisualMode() == RiftCoreBlockEntity.VisualMode.OVERWORLD_SINKHOLE
                && Math.abs(core.getRenderScale() - 0.6F) < 0.001F, "Old tags must retain the legacy mode and scale");
        var savedLegacy = TemporalRiftSavedData.FACTORY.deserializer().apply(legacy, helper.getLevel().registryAccess());
        helper.assertTrue(!savedLegacy.isHybridRift(), "Missing saved-data discriminator must retain legacy cleanup");
        savedLegacy.setHybridRift(true);
        var savedHybrid = TemporalRiftSavedData.FACTORY.deserializer().apply(
                savedLegacy.save(new CompoundTag(), helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(savedHybrid.isHybridRift(), "Hybrid lifecycle discriminator must survive save and restart");
        core.setVisualMode(RiftCoreBlockEntity.VisualMode.ANOMALY_RING);
        core.setRiftState(RiftState.STABILIZED);
        core.setFacing(Direction.WEST);
        core.markCompositionPlaced();
        core.setManagedCloseTick(12345);
        var restored = new RiftCoreBlockEntity(anchor, state);
        restored.loadWithComponents(core.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(restored.isHybrid() && restored.getRiftState() == RiftState.STABILIZED
                && restored.getFacing() == Direction.WEST && restored.isCompositionPlaced()
                && restored.getManagedCloseTick() == 12345, "Hybrid state must survive normal block-entity NBT serialization");
        helper.succeed();
    }

    @GameTest(template = "rift_test_clear")
    public static void actualTemplatesProvideCollisionAndPreserveEdits(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(16, 5, 16));
        var level = helper.getLevel();
        var result = RiftSites.create(level, anchor, Direction.EAST, true);
        helper.assertTrue(result.core() != null && result.core().isCompositionPlaced(), "Real templates must place: " + result.message());
        helper.assertTrue(level.getBlockState(anchor).getCollisionShape(level, anchor).isEmpty(), "Ring anchor must remain collision-free");
        BlockPos rock = null;
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-12, -4, -12), anchor.offset(12, 10, 12))) {
            if (pos.equals(anchor) || level.getBlockState(pos).isAir()) continue;
            count++;
            if (rock == null && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) rock = pos.immutable();
        }
        helper.assertTrue(count > 40 && rock != null, "Composition must contain actual collidable terrain");
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-1, 1, -1), anchor.offset(1, 4, 1))) {
            helper.assertTrue(level.getBlockState(pos).isAir(), "Composition must keep the central aperture clear");
        }
        level.setBlock(rock, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        var repeat = RiftRockPlacement.place(level, result.core());
        helper.assertTrue(!repeat.placed() && level.getBlockState(rock).is(Blocks.GOLD_BLOCK), "Repeated placement must preserve player edits");
        level.removeBlock(anchor, false);
        helper.assertTrue(level.getBlockState(rock).is(Blocks.GOLD_BLOCK), "Closing the ring must preserve terrain and edits");
        helper.succeed();
    }

    @GameTest(template = "rift_test_clear")
    public static void obstructionDeclinesBeforeAnyRockWrites(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(16, 5, 16));
        var level = helper.getLevel();
        var result = RiftSites.create(level, anchor, Direction.NORTH, false);
        helper.assertTrue(result.core() != null, "Ring must place in the clear test volume");
        var dimensions = new ArrayList<RiftCompositionPlan.Variant>();
        for (String name : RiftRockPlacement.VARIANTS) {
            var template = level.getStructureManager().get(ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, name)).orElseThrow();
            var size = template.getSize();
            dimensions.add(new RiftCompositionPlan.Variant(name, size.getX(), size.getY(), size.getZ()));
        }
        var plan = RiftCompositionPlan.plan(level.getSeed() ^ anchor.asLong() ^ 0x5A16E4B3L,
                RiftConfig.FORMATION_DIAMETER.get(), RiftConfig.ROCK_COUNT.get(), RiftConfig.ROCK_SPACING.get(), dimensions);
        helper.assertTrue(!plan.isEmpty(), "Test composition must have candidates");
        var box = plan.getLast().box();
        BlockPos obstruction = anchor.offset(box.minX(), box.minY(), box.minZ());
        level.setBlock(obstruction, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
        var placement = RiftRockPlacement.place(level, result.core());
        helper.assertTrue(!placement.placed() && !result.core().isCompositionPlaced(), "Occupied final footprint must decline the whole placement");
        int occupied = 0;
        for (BlockPos pos : BlockPos.betweenClosed(anchor.offset(-12, -4, -12), anchor.offset(12, 10, 12))) {
            if (!pos.equals(anchor) && !level.getBlockState(pos).isAir()) occupied++;
        }
        helper.assertTrue(occupied == 1 && level.getBlockState(obstruction).is(Blocks.GOLD_BLOCK), "Preflight must leave all other footprints untouched");
        helper.succeed();
    }

    @GameTest(template = "rift_test_clear")
    public static void unstableImpactIsSurvivableAndCooldownPreventsRepeatedForce(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(new BlockPos(16, 5, 16));
        var level = helper.getLevel();
        var result = RiftSites.create(level, anchor, Direction.NORTH, false);
        helper.assertTrue(result.core() != null, "Ring must place in the clear test volume");
        var profile = new GameProfile(UUID.randomUUID(), "rift-test");
        var player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        player.connection = new FakePlayer(level, profile).connection;
        player.setGameMode(GameType.SURVIVAL);
        // Advance the real server-player ticker past spawn protection; FakePlayer.tick() deliberately does nothing.
        for (int i = 0; i < 61; i++) player.tick();
        player.setHealth(3);
        Vec3 center = RiftTrigger.center(anchor);
        player.setPos(center.x, center.y - 0.5, center.z);
        player.setDeltaMovement(0, 0, 15);
        long tick = level.getGameTime();
        RiftServerController.enter(level, result.core(), player, center.add(0, 0, -8), tick);
        helper.assertTrue(player.isAlive() && Math.abs(player.getHealth() - 1) < 0.001F,
                "High-speed impact must apply real damage and leave exactly one health point in this unarmored test");
        Vec3 rejected = player.getDeltaMovement();
        helper.assertTrue(rejected.z < 0 && rejected.length() < 1, "Entry must replace forward momentum with bounded rejection");
        player.setDeltaMovement(Vec3.ZERO);
        RiftServerController.enter(level, result.core(), player, center.add(0, 0, -8), tick + 1);
        helper.assertTrue(player.getDeltaMovement().equals(Vec3.ZERO), "Entry cooldown must prevent another rejection");
        result.core().setRiftState(RiftState.STABILIZED);
        player.setDeltaMovement(0, 0, 1);
        RiftServerController.enter(level, result.core(), player, center.add(0, 0, -8), tick + 40);
        helper.assertTrue(player.getDeltaMovement().equals(new Vec3(0, 0, 1)), "Stabilized state must not apply unstable rejection or invent a destination");
        helper.succeed();
    }
}
