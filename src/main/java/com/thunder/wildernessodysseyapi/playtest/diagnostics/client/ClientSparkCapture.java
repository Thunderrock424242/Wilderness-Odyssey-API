package com.thunder.wildernessodysseyapi.playtest.diagnostics.client;

import com.thunder.wildernessodysseyapi.playtest.diagnostics.DiagnosticCollector;
import net.neoforged.neoforge.client.ClientCommandHandler;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Optional adapter for the installed NeoForge Spark generation. Internal APIs are checked at runtime.
 * Uses an independently owned, time-limited Java sampler; never replaces or cancels Spark's active sampler.
 * Export is raw Spark protobuf, matching Spark's save-to-file path, with no public-host upload.
 */
final class ClientSparkCapture implements DiagnosticCollector.SparkCapture {
    private volatile Object platform;
    private volatile Object threadDumper;
    private Object sampler;
    private volatile String status = "unavailable";

    /** Called on the Minecraft client thread; avoids SparkProvider's ambiguous server/client singleton. */
    void discover() {
        if (platform != null) return;
        try {
            var dispatcher = ClientCommandHandler.getDispatcher();
            var node = dispatcher == null ? null : dispatcher.getRoot().getChild("sparkc");
            Object command = node == null ? null : node.getCommand();
            if (command == null || !command.getClass().getName().equals("me.lucko.spark.neoforge.plugin.NeoForgeClientSparkPlugin")) return;
            Field field = command.getClass().getSuperclass().getDeclaredField("platform");
            field.setAccessible(true);
            threadDumper = command.getClass().getMethod("getDefaultThreadDumper").invoke(command);
            platform = field.get(command);
            if (platform != null) status = "idle";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) { status = "unavailable"; }
    }

    @Override public synchronized void start() {
        if (sampler != null || platform == null) return;
        try {
            Object container = platform.getClass().getMethod("getSamplerContainer").invoke(platform);
            if (container.getClass().getMethod("getActiveSampler").invoke(container) != null) { status = "busy"; return; }
            ClassLoader loader = platform.getClass().getClassLoader();
            Class<?> builderType = Class.forName("me.lucko.spark.common.sampler.SamplerBuilder", true, loader);
            Object builder = builderType.getConstructor().newInstance();
            builderType.getMethod("forceJavaSampler", boolean.class).invoke(builder, true);
            builderType.getMethod("samplingInterval", double.class).invoke(builder, 10.0);
            Class<?> dumperType = Class.forName("me.lucko.spark.common.sampler.ThreadDumper", true, loader);
            builderType.getMethod("threadDumper", dumperType).invoke(builder, threadDumper);
            builderType.getMethod("completeAfter", long.class, TimeUnit.class).invoke(builder, 120L, TimeUnit.SECONDS);
            sampler = builderType.getMethod("start", platform.getClass()).invoke(builder, platform);
            status = sampler == null ? "failed" : "recording";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) { status = "unavailable"; }
    }

    @Override public synchronized byte[] finish() {
        Object owned = sampler;
        sampler = null;
        if (owned == null) return null;
        try {
            ClassLoader loader = platform.getClass().getClassLoader();
            Class<?> samplerType = Class.forName("me.lucko.spark.common.sampler.Sampler", true, loader);
            samplerType.getMethod("stop", boolean.class).invoke(owned, false);
            Class<?> propsType = Class.forName("me.lucko.spark.common.sampler.Sampler$ExportProps", true, loader);
            Object props = propsType.getConstructor().newInstance();
            Class<?> strategy = Class.forName("me.lucko.spark.common.sampler.java.MergeStrategy", true, loader);
            propsType.getMethod("mergeStrategy", strategy).invoke(props, strategy.getField("SAME_METHOD").get(null));
            // Avoid embedding local JAR paths or player identity through a class-source lookup.
            Object noLookup = Class.forName("me.lucko.spark.common.sampler.source.ClassSourceLookup", true, loader).getField("NO_OP").get(null);
            propsType.getMethod("classSourceLookup", Supplier.class).invoke(props, (Supplier<Object>) () -> noLookup);
            Object data = samplerType.getMethod("toProto", platform.getClass(), propsType).invoke(owned, platform, props);
            // Spark normally adds process/config metadata to its protobuf. Retain sampled stacks and
            // mod versions, but omit creator, JVM/system statistics and arbitrary server config strings.
            Object metadata = data.getClass().getMethod("getMetadata").invoke(data);
            Object metadataBuilder = metadata.getClass().getMethod("toBuilder").invoke(metadata);
            for (String method : new String[]{"clearCreator", "clearServerConfigurations", "clearExtraPlatformMetadata",
                    "clearSystemStatistics", "clearPlatformStatistics"}) metadataBuilder.getClass().getMethod(method).invoke(metadataBuilder);
            Object cleanMetadata = metadataBuilder.getClass().getMethod("build").invoke(metadataBuilder);
            Object dataBuilder = data.getClass().getMethod("toBuilder").invoke(data);
            dataBuilder.getClass().getMethod("setMetadata", metadata.getClass()).invoke(dataBuilder, cleanMetadata);
            data = dataBuilder.getClass().getMethod("build").invoke(dataBuilder);
            int size = (int) data.getClass().getMethod("getSerializedSize").invoke(data);
            if (size > 1024 * 1024) { status = "failed"; return null; }
            byte[] bytes = (byte[]) data.getClass().getMethod("toByteArray").invoke(data);
            status = "saved";
            return bytes;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) {
            cancelOwned(owned);
            status = "failed";
            return null;
        }
    }

    @Override public synchronized void cancel() {
        Object owned = sampler;
        sampler = null;
        if (owned != null) { cancelOwned(owned); status = "idle"; }
    }

    private void cancelOwned(Object owned) {
        try {
            Class.forName("me.lucko.spark.common.sampler.Sampler", true, owned.getClass().getClassLoader())
                    .getMethod("stop", boolean.class).invoke(owned, true);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unsupported) { status = "failed"; }
    }

    @Override public String status() { return status; }
}
