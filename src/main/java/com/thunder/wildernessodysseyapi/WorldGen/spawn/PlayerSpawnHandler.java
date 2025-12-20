package com.thunder.wildernessodysseyapi.WorldGen.spawn;

import com.thunder.wildernessodysseyapi.WorldGen.blocks.CryoTubeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collections;
import java.util.List;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Handles assigning players to cryo tubes when they first join the world.
 */
@EventBusSubscriber(modid = MOD_ID)
public class PlayerSpawnHandler {

    private static final String CRYO_ASSIGNED_TAG = "wo_cryo_assigned";
    private static final String CRYO_POS_TAG = "wo_cryo_pos";

    private static List<BlockPos> spawnBlocks = Collections.emptyList();

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tryAssignSpawn(player);
        }
    }

    /**
     * Retry assignment while waiting for cryo tubes to be discovered.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            tryAssignSpawn(player);
        }
    }

    public static void setSpawnBlocks(List<BlockPos> blocks) {
        spawnBlocks = blocks == null ? Collections.emptyList() : List.copyOf(blocks);
    }

    private static void tryAssignSpawn(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData();

        if (tag.getBoolean(CRYO_ASSIGNED_TAG) || spawnBlocks.isEmpty()) {
            return;
        }

        BlockPos spawnPos = selectSpawn(player, tag);
        if (spawnPos == null) {
            return;
        }

        teleportPlayer(player, spawnPos);
        playIntroCinematic(player);
        tag.putBoolean(CRYO_ASSIGNED_TAG, true);
        tag.putLong(CRYO_POS_TAG, spawnPos.asLong());
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 255, false, false));
    }

    private static BlockPos selectSpawn(ServerPlayer player, CompoundTag tag) {
        if (tag.contains(CRYO_POS_TAG)) {
            BlockPos stored = BlockPos.of(tag.getLong(CRYO_POS_TAG));
            if (isCryoTube(player, stored)) {
                return stored;
            }
            tag.remove(CRYO_POS_TAG);
        }

        RandomSource random = player.serverLevel().getRandom();
        return spawnBlocks.get(random.nextInt(spawnBlocks.size()));
    }

    private static boolean isCryoTube(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().getBlockState(pos).is(CryoTubeBlock.CRYO_TUBE.get());
    }

    private static void teleportPlayer(ServerPlayer player, BlockPos spawnPos) {
        player.teleportTo(player.serverLevel(),
                spawnPos.getX() + 0.5,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5,
                player.getYRot(),
                player.getXRot());
    }

    /**
     * Play an intro cinematic when the player first awakens.
     */
    private static void playIntroCinematic(ServerPlayer player) {
        ServerGamePacketListenerImpl connection = player.connection;
        connection.send(new ClientboundSetTitlesAnimationPacket(20, 60, 20));
        connection.send(new ClientboundSetTitleTextPacket(Component.translatable("message.wildernessodysseyapi.wake_up")));
    }
}
