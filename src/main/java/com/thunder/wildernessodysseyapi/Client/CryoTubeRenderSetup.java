package com.thunder.wildernessodysseyapi.Client;

import com.thunder.wildernessodysseyapi.WorldGen.blocks.CryoTubeBlock;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Registers client side rendering for the cryo tube block.
 */
@EventBusSubscriber(modid = MOD_ID, value = Dist.CLIENT)
public class CryoTubeRenderSetup {

    /**
     * Configure render layers on client setup.
     *
     * @param event the client setup event
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        ItemBlockRenderTypes.setRenderLayer(CryoTubeBlock.CRYO_TUBE_BLOCK.get(), RenderType.cutout());
    }
}

