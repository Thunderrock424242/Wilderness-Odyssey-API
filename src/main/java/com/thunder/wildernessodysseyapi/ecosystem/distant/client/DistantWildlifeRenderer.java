package com.thunder.wildernessodysseyapi.ecosystem.distant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeForm;
import com.thunder.wildernessodysseyapi.ecosystem.distant.DistantWildlifeTransitionPolicy;
import com.thunder.wildernessodysseyapi.ecosystem.distant.network.DistantWildlifeSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws deterministic depth-tested low-poly silhouettes for abstract groups.
 *
 * <p>The renderer performs one frustum check per group and one batched draw. It
 * does not instantiate entity renderers, run animations, raycast terrain, or
 * ask another LOD mod for internal scene data.</p>
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID, value = Dist.CLIENT)
public final class DistantWildlifeRenderer {

    private DistantWildlifeRenderer() {
    }

    /** Renders after translucent terrain so the active depth buffer provides occlusion. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        try {
            render(event);
        } catch (RuntimeException | LinkageError failure) {
            ClientDistantWildlifeState.disableRendererForSession(failure);
        }
    }

    private static void render(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            ClientDistantWildlifeState.recordRender(null);
            return;
        }
        DistantWildlifeSyncPayload snapshot = ClientDistantWildlifeState.snapshot(minecraft.level);
        if (snapshot == null || snapshot.groups().isEmpty()) {
            ClientDistantWildlifeState.recordRender(null);
            return;
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double clientGameTime = minecraft.level.getGameTime() + partialTick;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack poses = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        RenderType renderType = RenderType.debugFilledBox();
        VertexConsumer vertices = buffers.getBuffer(renderType);
        int visibleGroups = 0;
        int transitionGroups = 0;
        int distantGroups = 0;
        int fadeGroups = 0;
        int frustumCulledGroups = 0;

        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (DistantWildlifeSyncPayload.GroupSnapshot group : snapshot.groups()) {
            Vec3 center = group.positionAt(clientGameTime);
            double distance = center.distanceTo(camera);
            DistantWildlifeTransitionPolicy.LodState lod = DistantWildlifeTransitionPolicy.lodState(
                    distance,
                    snapshot.realEntityDistance(),
                    snapshot.distantWildlifeDistance(),
                    snapshot.transitionBuffer()
            );
            float alpha = DistantWildlifeTransitionPolicy.renderAlpha(
                    distance,
                    snapshot.realEntityDistance(),
                    snapshot.distantWildlifeDistance(),
                    snapshot.transitionBuffer()
            );
            if (alpha <= 0.0F) {
                continue;
            }

            double spread = groupSpread(group.populationEstimate());
            AABB bounds = new AABB(
                    center.x - spread - 3.0, center.y - 3.0, center.z - spread - 3.0,
                    center.x + spread + 3.0, center.y + 8.0, center.z + spread + 3.0
            );
            if (!event.getFrustum().isVisible(bounds)) {
                frustumCulledGroups++;
                continue;
            }

            visibleGroups++;
            switch (lod) {
                case TRANSITION -> transitionGroups++;
                case DISTANT -> distantGroups++;
                case DISTANT_FADE -> fadeGroups++;
                default -> {
                }
            }
            renderGroup(poses, vertices, group, center, distance, clientGameTime, alpha, snapshot);
        }
        poses.popPose();
        buffers.endBatch(renderType);
        ClientDistantWildlifeState.recordRender(new ClientDistantWildlifeState.RenderCounters(
                visibleGroups, transitionGroups, distantGroups, fadeGroups, frustumCulledGroups
        ));
    }

    private static void renderGroup(
            PoseStack poses,
            VertexConsumer vertices,
            DistantWildlifeSyncPayload.GroupSnapshot group,
            Vec3 center,
            double cameraDistance,
            double gameTime,
            float alpha,
            DistantWildlifeSyncPayload snapshot
    ) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(group.species()).orElse(null);
        double width = clamp(type == null ? 0.8 : type.getWidth(), 0.28, 2.6);
        double height = clamp(type == null ? 1.25 : type.getHeight(), 0.35, 3.8);
        double spread = groupSpread(group.populationEstimate());
        boolean extremeLod = cameraDistance > snapshot.realEntityDistance()
                + (snapshot.distantWildlifeDistance() - snapshot.realEntityDistance()) * 0.52;
        float[] color = silhouetteColor(group.species().hashCode(), alpha);

        for (int index = 0; index < group.populationEstimate(); index++) {
            long hash = mix64(group.seed() + index * 0x9E3779B97F4A7C15L);
            double angle = unitHash(hash) * Math.PI * 2.0;
            double radius = Math.sqrt(unitHash(Long.rotateLeft(hash, 19))) * spread;
            double forward = (unitHash(Long.rotateLeft(hash, 37)) - 0.5) * spread * 0.55;
            double offsetX = Math.cos(angle) * radius + group.directionX() * forward;
            double offsetZ = Math.sin(angle) * radius + group.directionZ() * forward;
            double phase = unitHash(Long.rotateLeft(hash, 11)) * Math.PI * 2.0;
            double bob = switch (group.form()) {
                case GROUND -> Math.max(0.0, Math.sin(gameTime * 0.16 + phase)) * 0.035;
                case FLYING -> 2.5 + Math.sin(gameTime * 0.10 + phase) * 0.65;
                case AQUATIC -> Math.sin(gameTime * 0.08 + phase) * 0.22;
            };
            Vec3 animal = center.add(offsetX, bob, offsetZ);
            renderSilhouette(
                    poses, vertices, animal, group.form(),
                    group.directionX(), group.directionZ(), width, height,
                    extremeLod, color
            );
        }
    }

    // A few untextured boxes remain readable at distance and batch into one draw.
    private static void renderSilhouette(
            PoseStack poses,
            VertexConsumer vertices,
            Vec3 position,
            DistantWildlifeForm form,
            double directionX,
            double directionZ,
            double width,
            double height,
            boolean extremeLod,
            float[] color
    ) {
        double bodyBottom = position.y + height * 0.22;
        double bodyTop = position.y + height * 0.72;
        double bodyHalfWidth = width * 0.48;
        double bodyHalfLength = width * (form == DistantWildlifeForm.FLYING ? 0.45 : 0.72);
        box(poses, vertices,
                position.x - bodyHalfWidth, bodyBottom, position.z - bodyHalfLength,
                position.x + bodyHalfWidth, bodyTop, position.z + bodyHalfLength,
                color);

        double headX = position.x + directionX * width * 0.66;
        double headZ = position.z + directionZ * width * 0.66;
        double headRadius = width * 0.27;
        box(poses, vertices,
                headX - headRadius, position.y + height * 0.55, headZ - headRadius,
                headX + headRadius, position.y + height * 0.90, headZ + headRadius,
                color);
        if (extremeLod) {
            return;
        }

        if (form == DistantWildlifeForm.FLYING) {
            double wingSpan = width * 1.35;
            box(poses, vertices,
                    position.x - wingSpan, position.y + height * 0.48, position.z - width * 0.14,
                    position.x + wingSpan, position.y + height * 0.58, position.z + width * 0.14,
                    color);
            return;
        }
        if (form == DistantWildlifeForm.AQUATIC) {
            double tailX = position.x - directionX * width * 0.85;
            double tailZ = position.z - directionZ * width * 0.85;
            box(poses, vertices,
                    tailX - width * 0.22, position.y + height * 0.30, tailZ - width * 0.22,
                    tailX + width * 0.22, position.y + height * 0.66, tailZ + width * 0.22,
                    color);
            return;
        }

        double legRadius = Math.max(0.035, width * 0.09);
        double legTop = position.y + height * 0.30;
        for (int xSign : new int[]{-1, 1}) {
            for (int zSign : new int[]{-1, 1}) {
                double legX = position.x + xSign * width * 0.28;
                double legZ = position.z + zSign * width * 0.38;
                box(poses, vertices,
                        legX - legRadius, position.y, legZ - legRadius,
                        legX + legRadius, legTop, legZ + legRadius,
                        color);
            }
        }
    }

    private static void box(
            PoseStack poses,
            VertexConsumer vertices,
            double minX,
            double minY,
            double minZ,
            double maxX,
            double maxY,
            double maxZ,
            float[] color
    ) {
        LevelRenderer.addChainedFilledBoxVertices(
                poses, vertices,
                minX, minY, minZ, maxX, maxY, maxZ,
                color[0], color[1], color[2], color[3]
        );
    }

    private static double groupSpread(int population) {
        return Math.min(18.0, 1.8 + Math.sqrt(population) * 1.65);
    }

    private static float[] silhouetteColor(int speciesHash, float alpha) {
        long hash = mix64(speciesHash);
        float red = 0.085F + (float) unitHash(hash) * 0.055F;
        float green = 0.090F + (float) unitHash(Long.rotateLeft(hash, 17)) * 0.065F;
        float blue = 0.080F + (float) unitHash(Long.rotateLeft(hash, 33)) * 0.050F;
        return new float[]{red, green, blue, alpha * 0.88F};
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double unitHash(long value) {
        return (mix64(value) >>> 11) * 0x1.0p-53;
    }

    private static long mix64(long value) {
        long mixed = value + 0x9E3779B97F4A7C15L;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
