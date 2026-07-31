package com.thunder.wildernessodysseyapi.core;

import com.thunder.wildernessodysseyapi.entity.client.RiftListenerRenderer;
import com.thunder.wildernessodysseyapi.entity.client.RiftMawRenderer;
import com.thunder.wildernessodysseyapi.entity.client.RiftboundWraithRenderer;
import com.thunder.wildernessodysseyapi.meteor.renderer.MeteorRenderer;
import com.thunder.wildernessodysseyapi.temporalrift.client.RiftCoreBlockEntityRenderer;
import com.thunder.wildernessodysseyapi.temporalrift.client.TemporalRiftShaders;
import com.thunder.wildernessodysseyapi.temporalrift.registry.TemporalRiftBlockEntities;
import com.thunder.wildernessodysseyapi.watersystem.water.fluid.WildernessFluidRegistry;
import com.thunder.wildernessodysseyapi.watersystem.water.render.WaterShaders;
import com.thunder.wildernessodysseyapi.weather.client.cloud.VolumetricCloudShaders;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.io.IOException;

@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public class ClientSetup {
    private static final ResourceLocation WATER_STILL =
            ResourceLocation.withDefaultNamespace("block/water_still");
    private static final ResourceLocation WATER_FLOW =
            ResourceLocation.withDefaultNamespace("block/water_flow");
    private static final ResourceLocation WATER_OVERLAY =
            ResourceLocation.withDefaultNamespace("block/water_overlay");
    private static final ResourceLocation UNDERWATER_OVERLAY =
            ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");

    /**
     * Assigns both Wilderness fluid states to Minecraft's translucent chunk pass.
     *
     * <p>The assignment must happen while client loading is active. Keeping the
     * source and flowing variants on the same layer also makes the vanilla fluid
     * mesh a safe fallback until a coordinated custom surface is ready.</p>
     */
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(
                    WildernessFluidRegistry.WILDERNESS_WATER.get(),
                    RenderType.translucent()
            );
            ItemBlockRenderTypes.setRenderLayer(
                    WildernessFluidRegistry.FLOWING_WILDERNESS_WATER.get(),
                    RenderType.translucent()
            );
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFT_LISTENER.get(), RiftListenerRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFT_MAW.get(), RiftMawRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFTBOUND_WRAITH.get(), RiftboundWraithRenderer::new);
        event.registerBlockEntityRenderer(TemporalRiftBlockEntities.RIFT_CORE.get(), RiftCoreBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return WATER_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return WATER_FLOW;
            }

            @Override
            public ResourceLocation getOverlayTexture() {
                return WATER_OVERLAY;
            }

            @Override
            public ResourceLocation getRenderOverlayTexture(net.minecraft.client.Minecraft minecraft) {
                return UNDERWATER_OVERLAY;
            }

            @Override
            public int getTintColor(FluidState state, BlockAndTintGetter getter, BlockPos pos) {
                return 0xFF000000 | BiomeColors.getAverageWaterColor(getter, pos);
            }
        }, WildernessFluidRegistry.WILDERNESS_WATER_TYPE.get());
    }

    @SubscribeEvent
    public static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            TemporalRiftShaders.register(event);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load temporal rift shader", exception);
        }

        try {
            WaterShaders.register(event);
        } catch (IOException exception) {
            // Enhanced water optics are optional. Keeping registration failure
            // non-fatal preserves the standard translucent fallback path.
            ModConstants.LOGGER.warn("Unable to load built-in water shader; using compatibility rendering", exception);
        }

        try {
            VolumetricCloudShaders.register(event);
        } catch (IOException exception) {
            // Cloud volumes are optional; the existing Fast/Fancy voxel paths
            // remain available after a failed resource reload.
            ModConstants.LOGGER.warn("Unable to load volumetric cloud shader; using voxel cloud fallback", exception);
        }
    }
}
