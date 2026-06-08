package com.thunder.wildernessodysseyapi.client.entity;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.entity.RiftboundWraithEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class RiftboundWraithModel extends GeoModel<RiftboundWraithEntity> {
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "geo/riftbound_wraith.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "textures/entity/riftbound_wraith.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "animations/riftbound_wraith.animation.json");

    @Override
    public ResourceLocation getModelResource(RiftboundWraithEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RiftboundWraithEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RiftboundWraithEntity animatable) {
        return ANIMATION;
    }
}
