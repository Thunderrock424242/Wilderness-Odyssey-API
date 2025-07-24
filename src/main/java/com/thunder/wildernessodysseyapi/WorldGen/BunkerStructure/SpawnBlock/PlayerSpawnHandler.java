package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;


/**
 * The type Player spawn handler.
 */
@EventBusSubscriber(modid = MOD_ID)
public class PlayerSpawnHandler {

    private static final AtomicInteger spawnIndex = new AtomicInteger(0);
    private static List<BlockPos> spawnBlocks = null;
    private static final String CRYO_TAG = "wo_in_cryo";
    private static final String CRYO_USED_TAG = "wo_cryo_used";
    private static final String CRYO_POS_TAG = "wo_cryo_pos";

    /**
     * On player join.
     *
     * @param event the event
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        ServerLevel world = player.serverLevel();

        CompoundTag tag = player.getPersistentData();
        if (tag.getBoolean(CRYO_USED_TAG)) {
            return;
        }

        // Find spawn blocks if not already done
        if (spawnBlocks == null || spawnBlocks.isEmpty()) {
            spawnBlocks = WorldSpawnHandler.findAllWorldSpawnBlocks(world);
        }

        if (!spawnBlocks.isEmpty()) {
            BlockPos spawnPos = getNextSpawnBlock();
            player.teleportTo(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
            tag.putBoolean(CRYO_TAG, true);
            tag.putBoolean(CRYO_USED_TAG, true);
            tag.putLong(CRYO_POS_TAG, spawnPos.asLong());
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 255, false, false));
            playIntroCinematic(player);
        }
    }

    // Intentionally empty - players should only wake in the cryo tube on their first join

    /**
     * Prevent re-entry into the tube after exiting.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        if (!(event instanceof PlayerTickEvent.Post)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(CRYO_POS_TAG)) return;
        BlockPos spawnPos = BlockPos.of(tag.getLong(CRYO_POS_TAG));

        boolean inCryo = tag.getBoolean(CRYO_TAG);
        if (inCryo) {
            if (!player.blockPosition().equals(spawnPos)) {
                tag.putBoolean(CRYO_TAG, false);
            }
        } else {
            if (player.blockPosition().equals(spawnPos)) {
                player.teleportTo(spawnPos.getX() + 1.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
            }
        }
    }

    /**
     * Play an intro cinematic when the player first awakens.
     * This simply displays a title packet on the client as a placeholder.
     * A proper video or scripted camera sequence can be implemented client-side later.
     */
    private static void playIntroCinematic(ServerPlayer player) {
        ServerGamePacketListenerImpl connection = player.connection;
        connection.send(new ClientboundSetTitlesAnimationPacket(20, 60, 20));
        connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.wildernessodysseyapi.wake_up")));
    }

    private static BlockPos getNextSpawnBlock() {
        int index = spawnIndex.getAndUpdate(i -> (i + 1) % spawnBlocks.size());
        return spawnBlocks.get(index);
    }
}