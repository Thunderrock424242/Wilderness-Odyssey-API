package com.thunder.wildernessodysseyapi.ocean.events;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;

import java.io.IOException;

/**
 * Responsible for loading our custom wave shader (GPU‐driven), animating its "time" uniform,
 * and applying vertical wave motion to boats (but not to sea creatures).
 */
public class WaterSystem {

    // Where to find our JSON that points to .vsh + .fsh under assets/wildernessodysseyapi/shaders/core/
    private static final ResourceLocation WAVE_SHADER_LOCATION =
            ResourceLocation.tryParse("wildernessodysseyapi:shaders/core/wave_shader.json");

    // The in‐memory ShaderInstance, once loaded.
    private static ShaderInstance waveShader = null;

    // A single "time" uniform—everything else (amplitudes, frequencies, etc.) is baked into the GLSL.
    private static float time = 0.0f;

    /**
     * Call during client‐setup: load (and compile) our wave shader. Minecraft will
     * look for "wave_shader.vsh" and "wave_shader.fsh" under
     * "assets/wildernessodysseyapi/shaders/core/" because our JSON points there.
     */
    public static void initialize() {
        try {
            waveShader = new ShaderInstance(
                    Minecraft.getInstance().getResourceManager(),
                    WAVE_SHADER_LOCATION,
                    DefaultVertexFormat.POSITION_TEX
            );
        } catch (IOException e) {
            System.err.println("Failed to load wave shader: " + e.getMessage());
            waveShader = null;
        }
    }

    public static void applyWaveForces(Entity entity) {
        if (!(entity instanceof Boat boat)) {
            return;
        }
    }

    /**
     * Each client tick, advance our "time" uniform so the waves animate.
     * @param deltaTime how many seconds have passed since the last call
     */
    public static void update(float deltaTime) {
        time += deltaTime * 0.1f; // slower progression on GPU
    }

    /**
     * Before drawing any water‐related geometry, bind the shader and upload its "time" uniform.
     * (The rest of the wave parameters live inside the .json + GLSL.)
     */
    public static void bindWaveShader() {
        if (waveShader == null) return;

        // Directly set our loaded ShaderInstance—no need to fetch from gameRenderer.
        RenderSystem.setShader(() -> waveShader);
        waveShader.safeGetUniform("time").set(time);
    }

    /**
     * Apply vertical wave motion only to boats. Sea creatures (fish, squid, etc.) are unaffected.
     *
     * @param world The level (unused here, but might be helpful if you add region‐specific logic later).
     * @param boat  The boat entity to "jiggle" up/down.
     */
    public static void applyWaveMotion(Level world, Boat boat) {
        if (boat == null) return;

        double x = boat.getX();
        double z = boat.getZ();
        double currentY = boat.getY();

        // Compute a three‐layer sine‐based wave height at (x,z).
        double waveHeight = getWaveHeightAt(x, z);

        // Set the boat’s vertical velocity so it “rides” the wave.
        boat.setDeltaMovement(
                boat.getDeltaMovement().x,
                (waveHeight - currentY),
                boat.getDeltaMovement().z
        );
    }

    /**
     * Matches exactly the three‐layer sine logic from our GLSL (.vsh/.fsh):
     *   layer1 = sin(x * 0.1 + time * 0.05) * 0.5
     *   layer2 = sin(z * 0.15 + time * 0.08) * 0.3
     *   layer3 = sin((x+z) * 0.2 + time * 0.1) * 0.2
     *
     * If you tweak amplitudes/frequencies/speeds in the shader JSON, keep this in sync.
     */
    public static double getWaveHeightAt(double x, double z) {
        double layer1 = Math.sin(x * 0.1 + time * 0.05) * 0.5;
        double layer2 = Math.sin(z * 0.15 + time * 0.08) * 0.3;
        double layer3 = Math.sin((x + z) * 0.2 + time * 0.1) * 0.2;
        return layer1 + layer2 + layer3;
    }
}
