package com.thunder.wildernessodysseyapi.cryo.client;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.cryo.block.CryoTubeBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** GeckoLib resources for the animated cryo tube assembly. */
public final class CryoTubeModel extends GeoModel<CryoTubeBlockEntity> {
    private static final ResourceLocation MODEL = id("geo/cryo_tube.geo.json");
    private static final ResourceLocation TEXTURE = id("textures/block/cryo_tube.png");
    private static final ResourceLocation ANIMATION = id("animations/cryo_tube.animation.json");

    @Override
    public ResourceLocation getModelResource(CryoTubeBlockEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CryoTubeBlockEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CryoTubeBlockEntity animatable) {
        return ANIMATION;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, path);
    }
}
