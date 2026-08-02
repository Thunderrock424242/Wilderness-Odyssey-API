package com.thunder.wildernessodysseyapi.mixinconfig;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Prevents optional renderer bridges from asking Mixin to resolve absent classes.
 *
 * <p>{@code @Pseudo} remains a defensive fallback, while this resource-only
 * check removes harmless ClassNotFound startup warnings on clients that have
 * neither Iris/Oculus, Embeddium, nor Sodium installed. No optional class is
 * initialized during bootstrap.</p>
 */
public final class WildernessMixinConfigPlugin implements IMixinConfigPlugin {

    private static final String MIXIN_PACKAGE = "com.thunder.wildernessodysseyapi.mixin.";

    @Override
    public void onLoad(String mixinPackage) {
        // No eager initialization: optional renderer classes stay optional.
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return shouldApplyOptionalMixin(mixinClassName, WildernessMixinConfigPlugin::classResourceExists);
    }

    /** Evaluates one optional mixin without loading its target class. */
    public static boolean shouldApplyOptionalMixin(
            String mixinClassName,
            Predicate<String> classExists
    ) {
        String optionalTarget = optionalTarget(mixinClassName);
        return optionalTarget == null || classExists.test(optionalTarget);
    }

    static String optionalTarget(String mixinClassName) {
        return switch (mixinClassName) {
            case MIXIN_PACKAGE + "IrisWaterMaterialBridgeMixin" ->
                    "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings";
            case MIXIN_PACKAGE + "LegacyIrisWaterMaterialBridgeMixin" ->
                    "net.coderbot.iris.block_rendering.BlockRenderingSettings";
            case MIXIN_PACKAGE + "EmbeddiumWaterRenderMixin" ->
                    "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.FluidRenderer";
            case MIXIN_PACKAGE + "SodiumFluidRenderMixin" ->
                    "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer";
            case MIXIN_PACKAGE + "SodiumBlockOcclusionCacheMixin" ->
                    "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache";
            default -> null;
        };
    }

    private static boolean classResourceExists(String className) {
        String resource = className.replace('.', '/') + ".class";
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null && contextLoader.getResource(resource) != null) {
            return true;
        }
        ClassLoader ownLoader = WildernessMixinConfigPlugin.class.getClassLoader();
        return ownLoader != null && ownLoader.getResource(resource) != null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
    }

    @Override
    public void postApply(
            String targetClassName,
            ClassNode targetClass,
            String mixinClassName,
            IMixinInfo mixinInfo
    ) {
    }
}
