package com.thunder.wildernessodysseyapi.weather.client.precipitation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import com.thunder.wildernessodysseyapi.weather.client.precipitation.PrecipitationImpactModel.ImpactSurface;
import com.thunder.wildernessodysseyapi.weather.config.WeatherRenderingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Draws small muted rings where synchronized precipitation reaches a surface.
 *
 * <p>The impacts contain no glowing particle texture. They are spawned only
 * after the precipitation mesh rendered recently, preventing the splash-only
 * state that made rain appear invisible.</p>
 */
public final class WeatherImpactRenderer {

    private static final int SEGMENTS = 10;
    private static final Deque<Impact> IMPACTS = new ArrayDeque<>();
    private static ClientLevel activeLevel;

    private WeatherImpactRenderer() {
    }

    /** Adds one bounded cosmetic impact to the active client level. */
    public static void spawn(
            ClientLevel level,
            double x,
            double y,
            double z,
            ImpactSurface surface,
            float intensity
    ) {
        if (level == null || surface == null) {
            return;
        }
        prepareLevel(level);
        int maximum = WeatherRenderingConfig.settings().maximumPrecipitationImpacts();
        while (IMPACTS.size() >= maximum) {
            IMPACTS.removeFirst();
        }
        IMPACTS.addLast(new Impact(x, y + 0.012, z, level.getGameTime(), surface,
                Math.max(0.0F, Math.min(1.0F, intensity))));
    }

    /** Renders impacts after translucent terrain so water rings remain visible. */
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || !ClientWeatherCoordinator.controls(level)) {
            clear();
            return;
        }
        prepareLevel(level);
        if (IMPACTS.isEmpty()) {
            return;
        }

        float renderTick = level.getGameTime()
                + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        IMPACTS.removeIf(impact -> renderTick - impact.startTick
                >= PrecipitationImpactModel.lifetimeTicks(impact.surface));
        if (IMPACTS.isEmpty()) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        var buffers = minecraft.renderBuffers().bufferSource();
        var renderType = WeatherImpactRenderTypes.impacts();
        VertexConsumer vertices = buffers.getBuffer(renderType);
        PoseStack poses = event.getPoseStack();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poses.last().pose();
        for (Impact impact : IMPACTS) {
            float lifetime = PrecipitationImpactModel.lifetimeTicks(impact.surface);
            float progress = Math.max(0.0F, Math.min(1.0F,
                    (renderTick - impact.startTick) / lifetime));
            float radius = PrecipitationImpactModel.radius(progress, impact.surface, impact.intensity);
            float alpha = PrecipitationImpactModel.alpha(progress, impact.surface, impact.intensity);
            emitRing(vertices, matrix, impact, radius, alpha);
        }
        poses.popPose();
        buffers.endBatch(renderType);
    }

    /** Releases all client-only impacts during a level or ownership handoff. */
    public static void clear() {
        IMPACTS.clear();
        activeLevel = null;
    }

    /** Returns the current bounded impact count for weather diagnostics. */
    public static int activeImpactCount() {
        return IMPACTS.size();
    }

    private static void prepareLevel(ClientLevel level) {
        if (activeLevel == level) {
            return;
        }
        IMPACTS.clear();
        activeLevel = level;
    }

    private static void emitRing(
            VertexConsumer vertices,
            Matrix4f matrix,
            Impact impact,
            float radius,
            float alpha
    ) {
        float inner = radius * (impact.surface == ImpactSurface.WATER ? 0.78F : 0.68F);
        int red = impact.surface == ImpactSurface.HAIL ? 157 : 104;
        int green = impact.surface == ImpactSurface.HAIL ? 174 : 142;
        int blue = impact.surface == ImpactSurface.HAIL ? 186 : 160;
        int alphaByte = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double angle0 = Math.PI * 2.0 * segment / SEGMENTS;
            double angle1 = Math.PI * 2.0 * (segment + 1) / SEGMENTS;
            float x0 = (float) Math.cos(angle0);
            float z0 = (float) Math.sin(angle0);
            float x1 = (float) Math.cos(angle1);
            float z1 = (float) Math.sin(angle1);
            add(vertices, matrix, impact.x + x0 * radius, impact.y, impact.z + z0 * radius,
                    red, green, blue, 0);
            add(vertices, matrix, impact.x + x0 * inner, impact.y, impact.z + z0 * inner,
                    red, green, blue, alphaByte);
            add(vertices, matrix, impact.x + x1 * inner, impact.y, impact.z + z1 * inner,
                    red, green, blue, alphaByte);
            add(vertices, matrix, impact.x + x1 * radius, impact.y, impact.z + z1 * radius,
                    red, green, blue, 0);
        }
    }

    private static void add(
            VertexConsumer vertices,
            Matrix4f matrix,
            double x,
            double y,
            double z,
            int red,
            int green,
            int blue,
            int alpha
    ) {
        vertices.addVertex(matrix, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, alpha);
    }

    private record Impact(
            double x,
            double y,
            double z,
            long startTick,
            ImpactSurface surface,
            float intensity
    ) {
    }
}
