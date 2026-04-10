package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.thunder.wildernessodysseyapi.watersystem.water.mesh.FluidMesh;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHConstants;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulationManager;
import com.thunder.wildernessodysseyapi.watersystem.water.sph.SPHSimulator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FluidRenderer
 *
 * Runs every render frame:
 *   1. For each active SPHSimulator, rebuild its FluidMesh
 *   2. Upload the mesh vertices to a BufferBuilder
 *   3. Draw the translucent water mesh
 *   4. Draw droplets as small sphere-like quads
 *
 * Renders during AFTER_TRANSLUCENT_BLOCKS so it composites
 * correctly with vanilla translucent geometry.
 */
@EventBusSubscriber(modid = "wilderness", bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class FluidRenderer {

    // One FluidMesh per active simulator
    private static final Map<SPHSimulator, FluidMesh> meshMap = new ConcurrentHashMap<>();

    // Water colour: deep blue-grey with high translucency
    private static final float R = 0.15f, G = 0.40f, B = 0.65f, A = 0.82f;

    // Droplet colour — slightly lighter
    private static final float DR = 0.25f, DG = 0.55f, DB = 0.80f, DA = 0.75f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        List<SPHSimulator> active = new ArrayList<>(SPHSimulationManager.get().getActive());
        if (active.isEmpty()) {
            meshMap.clear();
            return;
        }

        // Remove meshes for dead sims
        meshMap.keySet().retainAll(active);

        PoseStack poseStack = event.getPoseStack();
        var camera = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix())
                .mul(poseStack.last().pose());

        for (SPHSimulator sim : active) {
            FluidMesh mesh = meshMap.computeIfAbsent(sim, FluidMesh::new);
            mesh.rebuild();

            if (mesh.hasGeometry()) {
                drawFluidMesh(mesh, poseStack);
            }

            drawDroplets(sim, poseStack);
        }

        poseStack.popPose();
    }

    // -------------------------------------------------------------------------
    // Draw the marching-cubes mesh
    // -------------------------------------------------------------------------

    private static void drawFluidMesh(FluidMesh mesh, PoseStack poseStack) {
        float[] data = mesh.meshData;
        if (data == null || data.length < 18) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false); // don't write depth for translucent fluid

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLES,
                                        DefaultVertexFormat.POSITION_COLOR_NORMAL);

        Matrix4f mat = poseStack.last().pose();

        for (int i = 0; i + 17 < data.length; i += 18) {
            for (int v = 0; v < 3; v++) {
                int base = i + v * 6;
                float px = data[base],   py = data[base+1], pz = data[base+2];
                float nx = data[base+3], ny = data[base+4], nz = data[base+5];

                // Simple diffuse lighting from above
                float diffuse = Math.max(0.3f, ny * 0.7f + 0.3f);

                buf.addVertex(mat, px, py, pz)
                   .setColor(R * diffuse, G * diffuse, B * diffuse, A)
                   .setNormal(nx, ny, nz);
            }
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    // -------------------------------------------------------------------------
    // Draw droplets as billboarded quads
    // -------------------------------------------------------------------------

    private static void drawDroplets(SPHSimulator sim, PoseStack poseStack) {
        var droplets = sim.particles.stream()
                .filter(p -> p.isDroplet)
                .toList();

        if (droplets.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS,
                                        DefaultVertexFormat.POSITION_COLOR_NORMAL);
        Matrix4f mat = poseStack.last().pose();

        float s = 0.055f; // droplet half-size

        for (var p : droplets) {
            float x = p.position.x, y = p.position.y, z = p.position.z;
            float life = p.dropletLife / (float) SPHConstants.DROPLET_LIFETIME;
            float alpha = DA * life;

            // Billboarded quad (axis-aligned for simplicity)
            buf.addVertex(mat, x-s, y-s, z).setColor(DR, DG, DB, alpha).setNormal(0,0,1);
            buf.addVertex(mat, x+s, y-s, z).setColor(DR, DG, DB, alpha).setNormal(0,0,1);
            buf.addVertex(mat, x+s, y+s, z).setColor(DR, DG, DB, alpha).setNormal(0,0,1);
            buf.addVertex(mat, x-s, y+s, z).setColor(DR, DG, DB, alpha).setNormal(0,0,1);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }
}
