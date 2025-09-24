package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;


/**
 * The type Player spawn handler.
 */
@EventBusSubscriber(modid = MOD_ID)
public class PlayerSpawnHandler {

    private static final AtomicInteger spawnIndex = new AtomicInteger(0);
    private static List<BlockPos> spawnBlocks = Collections.emptyList();
    private static final String CRYO_TAG = "wo_in_cryo";
    private static final String CRYO_USED_TAG = "wo_cryo_used";
    private static final String CRYO_POS_TAG = "wo_cryo_pos";
    private static final String CRYO_DELAY_TAG = "wo_spawn_delay";

    /**
     * On player join.
     *
     * @param event the event
     */
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();

        CompoundTag tag = player.getPersistentData();
        if (tag.getBoolean(CRYO_USED_TAG)) {
            return;
        }

        if (spawnBlocks.isEmpty()) {
            return;
        }

        tag.putInt(CRYO_DELAY_TAG, 200); // 10 second delay (20 ticks * 10)
        playIntroCinematic(player);
    }

    /**
     * Handle delayed teleportation and prevent re-entry into the tube after exiting.
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        CompoundTag tag = player.getPersistentData();

        if (tag.contains(CRYO_DELAY_TAG)) {
            int delay = tag.getInt(CRYO_DELAY_TAG) - 1;
            if (delay > 0) {
                tag.putInt(CRYO_DELAY_TAG, delay);
                return;
            }
            tag.remove(CRYO_DELAY_TAG);

            if (!spawnBlocks.isEmpty()) {
                BlockPos spawnPos = getNextSpawnBlock();
                player.teleportTo(player.serverLevel(),
                        spawnPos.getX() + 0.5,
                        spawnPos.getY(),
                        spawnPos.getZ() + 0.5,
                        player.getYRot(),
                        player.getXRot());
                tag.putBoolean(CRYO_TAG, true);
                tag.putBoolean(CRYO_USED_TAG, true);
                tag.putLong(CRYO_POS_TAG, spawnPos.asLong());
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 255, false, false));
            }
            return;
        }

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
        int size = spawnBlocks.size();
        int index = spawnIndex.getAndUpdate(i -> i + 1 >= size ? 0 : i + 1);
        return spawnBlocks.get(index);
    }

    public static void setSpawnBlocks(List<BlockPos> blocks) {
        spawnBlocks = blocks == null ? Collections.emptyList() : blocks;
        spawnIndex.set(0);
    }

}
