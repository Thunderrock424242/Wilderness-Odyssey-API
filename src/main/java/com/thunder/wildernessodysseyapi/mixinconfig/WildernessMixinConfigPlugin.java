package com.thunder.wildernessodysseyapi.mixinconfig;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Prevents optional integrations from asking Mixin to resolve absent classes.
 *
 * <p>{@code @Pseudo} remains a defensive fallback, while this resource-only
 * check skips WorldEdit and renderer bridges when their exact targets are not
 * installed. No optional class is initialized during bootstrap.</p>
 */
public final class WildernessMixinConfigPlugin implements IMixinConfigPlugin {

    private static final String MIXIN_PACKAGE = "com.thunder.wildernessodysseyapi.mixin.";
    private static final String EMBEDDIUM_MIXIN = "EmbeddiumWaterRenderMixin";
    private static final List<String> EMBEDDIUM_MIXINS = List.of(
            EMBEDDIUM_MIXIN,
            "EmbeddiumRenderSectionCoordinatesMixin",
            "EmbeddiumChunkBuildOutputHandoffMixin",
            "EmbeddiumChunkMeshingHandoffMixin",
            "EmbeddiumSectionUploadHandoffMixin"
    );
    private static final String EMBEDDIUM_TARGET =
            "org.embeddedt.embeddium.impl.render.chunk.compile.pipeline.FluidRenderer";
    private static final String WORLDEDIT_TARGET =
            "com.sk89q.worldedit.function.operation.ForwardExtentCopy";

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
            case MIXIN_PACKAGE + "ForwardExtentCopyMixin" -> WORLDEDIT_TARGET;
            case MIXIN_PACKAGE + "IrisWaterMaterialBridgeMixin" ->
                    "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings";
            case MIXIN_PACKAGE + "LegacyIrisWaterMaterialBridgeMixin" ->
                    "net.coderbot.iris.block_rendering.BlockRenderingSettings";
            case MIXIN_PACKAGE + "EmbeddiumWaterRenderMixin" ->
                    EMBEDDIUM_TARGET;
            case MIXIN_PACKAGE + "EmbeddiumRenderSectionCoordinatesMixin" ->
                    "org.embeddedt.embeddium.impl.render.chunk.RenderSection";
            case MIXIN_PACKAGE + "EmbeddiumChunkBuildOutputHandoffMixin" ->
                    "org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput";
            case MIXIN_PACKAGE + "EmbeddiumChunkMeshingHandoffMixin" ->
                    "org.embeddedt.embeddium.impl.render.chunk.compile.tasks.ChunkBuilderMeshingTask";
            case MIXIN_PACKAGE + "EmbeddiumSectionUploadHandoffMixin" ->
                    "org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager";
            case MIXIN_PACKAGE + "SodiumFluidRenderMixin" ->
                    "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer";
            case MIXIN_PACKAGE + "SodiumBlockOcclusionCacheMixin" ->
                    "net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockOcclusionCache";
            case MIXIN_PACKAGE + "SodiumRenderSectionCoordinatesMixin" ->
                    "net.caffeinemc.mods.sodium.client.render.chunk.RenderSection";
            case MIXIN_PACKAGE + "SodiumChunkBuildOutputHandoffMixin" ->
                    "net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput";
            case MIXIN_PACKAGE + "SodiumChunkMeshingHandoffMixin" ->
                    "net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask";
            case MIXIN_PACKAGE + "SodiumSectionUploadHandoffMixin" ->
                    "net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager";
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
        return discoverOptionalMixins(WildernessMixinConfigPlugin::classResourceExists);
    }

    /**
     * Discovers optional mixins that must not be parsed when their targets are absent.
     *
     * <p>Embeddium's legacy target is added through the plugin instead of the
     * static client list because Mixin reads a statically listed pseudo target
     * early enough to emit a ClassNotFound warning even when
     * {@link #shouldApplyMixin(String, String)} later vetoes it.</p>
     */
    static List<String> discoverOptionalMixins(Predicate<String> classExists) {
        return classExists.test(EMBEDDIUM_TARGET)
                ? EMBEDDIUM_MIXINS
                : List.of();
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
