package com.thunder.wildernessodysseyapi.ocean.events;

import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.ocean.rendering.WaveRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;

public class WaterSystem {

    private static ShaderInstance waveShader;
    private static final ResourceLocation WAVE_SHADER_LOCATION = ResourceLocation.tryParse("wildernessodysseyapi:shaders/core/wave_shader.json");

    // GPU-driven uniforms (Handled in the shader)
    private static float time = 0.0f;

    /**
     * Initializes water system, including wave rendering.
     */
    public static void initialize() {
        WaveRenderer.initializeShader(); // Load wave shader
    }

    /**
     * Updates wave animation time.
     */
    public static void update(float deltaTime) {
        time += deltaTime * 0.1f; // Slow wave animation
    }

    /**
     * Applies wave motion to boats while keeping sea creatures unaffected.
     */
    public static void applyWaveMotion(Level world, Boat boat) {
        if (boat == null || world == null) return;

        // Calculate wave height based on GPU-driven wave data
        double waveHeight = calculateWaveHeight(boat.getX(), boat.getZ());

        // Apply only to boats (not affecting fish, squid, etc.)
        boat.setDeltaMovement(boat.getDeltaMovement().x, waveHeight - boat.getY(), boat.getDeltaMovement().z);
    }

    /**
     * Calculates the wave height at a specific location (For boats ONLY).
     */
    private static double calculateWaveHeight(double x, double z) {
        return WaveRenderer.getWaveHeightAt(x, z); // Uses optimized shader-driven method
    }

    /**
     * Binds shader and updates time uniform.
     */
    public static void bindWaveShader() {
        if (waveShader == null) {
            waveShader = Minecraft.getInstance().gameRenderer.getShaderManager().getShader(WAVE_SHADER_LOCATION);
        }

        if (waveShader != null) {
            RenderSystem.setShader(() -> waveShader);
            waveShader.safeGetUniform("time").set(time); // Only updating time now (Rest is in shader)
        }
    }
}
