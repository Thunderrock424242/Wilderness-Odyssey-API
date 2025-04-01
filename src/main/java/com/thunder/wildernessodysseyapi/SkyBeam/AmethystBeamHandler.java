package com.thunder.wildernessodysseyapi.SkyBeam;

import com.thunder.wildernessodysseyapi.SkyBeam.Effects.BeamEffects;
import com.thunder.wildernessodysseyapi.SkyBeam.Effects.BlockEffects;
import com.thunder.wildernessodysseyapi.SkyBeam.Effects.TreeEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import static com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class AmethystBeamHandler {

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().is(Items.AMETHYST_SHARD)) {
            BlockPos targetPos = event.getPos();
            ServerLevel serverWorld = (ServerLevel) event.getLevel();

            // Summon beacon-like beam effect
            BeamEffects.summonBeamEffect(serverWorld, targetPos);

            // Apply blindness and knockback to entities
            BeamEffects.affectEntities(serverWorld, targetPos);

            // Destroy blocks like TNT and sand
            BlockEffects.destroyBlocks(serverWorld, targetPos);

            // Set fires and spawn magma blocks
            BlockEffects.igniteArea(serverWorld, targetPos);

            // Char trees and remove leaves
            TreeEffects.charTrees(serverWorld, targetPos);
        }
    }
}