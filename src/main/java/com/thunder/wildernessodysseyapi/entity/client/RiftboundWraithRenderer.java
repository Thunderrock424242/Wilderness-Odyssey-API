package com.thunder.wildernessodysseyapi.entity.client;

import com.thunder.wildernessodysseyapi.entity.RiftboundWraithEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.util.Color;

public class RiftboundWraithRenderer extends GeoEntityRenderer<RiftboundWraithEntity> {
    public RiftboundWraithRenderer(EntityRendererProvider.Context context) {
        super(context, new RiftboundWraithModel());
        this.shadowRadius = 0.35F;
        withScale(0.85F);
    }

    @Override
    public RenderType getRenderType(RiftboundWraithEntity animatable,
                                    ResourceLocation texture,
                                    MultiBufferSource bufferSource,
                                    float partialTick) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public Color getRenderColor(RiftboundWraithEntity animatable, float partialTick, int packedLight) {
        float alpha = switch (animatable.getWraithState()) {
            case RiftboundWraithEntity.STATE_LISTENING -> 0.58F;
            case RiftboundWraithEntity.STATE_HUNTING -> 0.82F;
            case RiftboundWraithEntity.STATE_GRASPING -> 0.95F;
            default -> 0.36F;
        };
        return Color.ofRGBA(0.74F, 0.62F, 1.0F, alpha);
    }
}
