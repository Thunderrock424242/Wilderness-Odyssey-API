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
     * <p>
     * The cryo tube model uses a texture with transparent pixels for its glass
     * portion.  Registering the block with a translucent render layer ensures
     * those pixels render with alpha blending while the rest of the model
     * remains unaffected.
     *
     * @param event the client setup event
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Allow both cutout and translucent layers. The base tube renders on
        // cutout to avoid z-fighting when tubes touch, while the emissive
        // overlay (texture slot #1 in the model) uses the translucent sheet so
        // it can glow without being affected by lightmaps.
        event.enqueueWork(() ->
                ItemBlockRenderTypes.setRenderLayer(
                        CryoTubeBlock.CRYO_TUBE.get(),
                        renderType -> renderType == RenderType.cutout() || renderType == RenderType.translucent()
                )
        );
    }
}
