package com.thunder.wildernessodysseyapi.entity.client;

import com.thunder.wildernessodysseyapi.entity.RiftbornEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders Riftborn with Minecraft's stable zombie model and texture contract.
 *
 * <p>Riftborn currently have no dedicated texture asset. Reusing the vanilla
 * zombie skin keeps the registered hostile fully renderable while allowing a
 * feature-specific texture to replace this location later.</p>
 */
public final class RiftbornRenderer extends MobRenderer<RiftbornEntity, HumanoidModel<RiftbornEntity>> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");

    /**
     * Creates the client renderer during NeoForge entity-renderer registration.
     *
     * @param context client model and renderer context
     */
    public RiftbornRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(RiftbornEntity entity) {
        return TEXTURE;
    }
}
