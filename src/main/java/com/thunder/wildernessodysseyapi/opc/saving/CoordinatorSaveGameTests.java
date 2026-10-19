package com.thunder.wildernessodysseyapi.opc.saving;

import com.thunder.wildernessodysseyapi.opc.diagnostics.PerformanceDiagnosticsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkDataEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Isolated transformed native-save proof, including block-entity data and a controlled serialization-event failure. */
@GameTestHolder("wildernessodysseyapi_coordinator_tests")
@PrefixGameTestTemplate(false)
public final class CoordinatorSaveGameTests {
    private CoordinatorSaveGameTests() { }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void nativeSaveRetainsChestDataAndFailureLeavesDirtyForNormalRetry(GameTestHelper helper) {
        var level = helper.getLevel();
        var server = level.getServer();
        helper.assertTrue(!PerformanceDiagnosticsConfig.values().enabled(), "The isolated run must preserve the disabled master default");
        BlockPos position = helper.absolutePos(new BlockPos(0, 1, 0));
        level.setBlockAndUpdate(position, Blocks.CHEST.defaultBlockState());
        var chest = (ChestBlockEntity) level.getBlockEntity(position);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 3));
        var chunk = level.getChunkAt(position);
        helper.assertTrue(level.getChunkSource().chunkMap instanceof NativeChunkSaveAccess, "Native accessor must transform");
        SavingCoordinator.start(server, true, true, 128, 0, 0, "isolated GameTest adapter");
        chunk.setUnsaved(false);
        // Vanilla uses a direct inherited-field assignment here, not setUnsaved.
        level.setBlockAndUpdate(position.below(), Blocks.STONE.defaultBlockState());
        helper.assertTrue(SavingCoordinator.metrics().getOrDefault("saveObservedDirty", 0L) > 0, "Direct vanilla block mutation must be tracked");
        var access = (NativeChunkSaveAccess) level.getChunkSource().chunkMap;
        var holder = access.wildernessodysseyapi$visibleChunk(chunk.getPos().toLong());
        helper.assertTrue(holder != null && holder.currentlyLoading == null, "Fixture must expose a loaded full chunk");
        holder.increaseGenerationRefCount();
        try {
            helper.assertTrue(!holder.isReadyForSaving(), "Generation reference must hold the native save barrier");
            SavingCoordinator.drain(server, 10_000_000, () -> true);
            helper.assertTrue(SavingCoordinator.metrics().get("savePendingWrites") == 0,
                    "A loaded chunk with pending generation dependencies must not be optionally serialized");
            helper.assertTrue(chunk.isUnsaved(), "Deferred chunk must keep native dirty state");
        } finally {
            holder.decreaseGenerationRefCount();
        }
        SavingCoordinator.drain(server, 10_000_000, () -> true);
        helper.assertTrue(SavingCoordinator.metrics().getOrDefault("savePendingWrites", 0L) == 1, "Native write completion must be observed");
        helper.assertTrue(SavingCoordinator.metrics().getOrDefault("saveDrainEnabled", 0L) == 1, "Native hook must preserve successful submission");
        SavingCoordinator.stopping(server);
        helper.assertTrue(chunk.isUnsaved(), "Unprocessed optional write must be dirty before native final save");
        SavingCoordinator.clear();
        level.getChunkSource().save(true);
        AtomicReference<CompletableFuture<Optional<CompoundTag>>> read = new AtomicReference<>(level.getChunkSource().chunkMap.read(chunk.getPos()));
        helper.startSequence()
                .thenWaitUntil(() -> assertStoredChest(helper, read.get(), position, 3))
                .thenExecute(() -> {
                    SavingCoordinator.start(server, true, true, 128, 0, 0, "isolated forced failure");
                    chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
                    chunk.setUnsaved(true);
                    Consumer<ChunkDataEvent.Save> failOnce = event -> {
                        if (event.getLevel() == level && event.getChunk().getPos().equals(chunk.getPos()))
                            throw new IllegalStateException("Expected coordinator GameTest save failure");
                    };
                    NeoForge.EVENT_BUS.addListener(failOnce);
                    try { SavingCoordinator.drain(server, 10_000_000, () -> true); }
                    finally { NeoForge.EVENT_BUS.unregister(failOnce); }
                    helper.assertTrue(chunk.isUnsaved(), "Failed optional save must retain native dirty state");
                    helper.assertTrue(SavingCoordinator.metrics().get("saveDrainEnabled") == 0, "Failure must disable later optional saves");
                    SavingCoordinator.poll(server);
                    SavingCoordinator.clear();
                    level.getChunkSource().save(true);
                    read.set(level.getChunkSource().chunkMap.read(chunk.getPos()));
                })
                .thenWaitUntil(() -> assertStoredChest(helper, read.get(), position, 5))
                .thenSucceed();
    }

    private static void assertStoredChest(GameTestHelper helper, CompletableFuture<Optional<CompoundTag>> read,
                                          BlockPos position, int count) {
        helper.assertTrue(read.isDone() && !read.isCompletedExceptionally(), "Native asynchronous stored-data read must finish");
        CompoundTag chunk = read.getNow(Optional.empty()).orElseThrow();
        var entities = chunk.getList("block_entities", Tag.TAG_COMPOUND);
        for (int index = 0; index < entities.size(); index++) {
            CompoundTag entity = entities.getCompound(index);
            if (entity.getInt("x") == position.getX() && entity.getInt("y") == position.getY() && entity.getInt("z") == position.getZ()) {
                var items = entity.getList("Items", Tag.TAG_COMPOUND);
                helper.assertTrue(items.size() == 1 && items.getCompound(0).getString("id").equals("minecraft:diamond")
                        && items.getCompound(0).getInt("count") == count, "Normal serialized chest inventory must survive native write/retry");
                return;
            }
        }
        helper.fail("Stored chunk did not contain the chest block entity");
    }
}
