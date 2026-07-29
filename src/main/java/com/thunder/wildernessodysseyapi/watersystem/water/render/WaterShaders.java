package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.watersystem.ocean.OceanSeaState;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaterBodyClassifier;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.client.ClientWeatherCoordinator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;
import org.joml.Matrix4f;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * Owns the optional built-in ocean shader and its frame uniforms.
 *
 * <p>An active Iris/Oculus shader pack stays on the tagged-fluid path so the
 * pack remains the final authority over water shading. Installing either mod
 * without enabling a pack still permits the built-in GPU-wave renderer.</p>
 */
public final class WaterShaders {

    private static ShaderInstance oceanShader;
    private static ShaderInstance underwaterShader;
    private static boolean sceneCaptureFailureLogged;
    private static volatile boolean externalShaderApiResolved;
    private static Method externalShaderApiGetInstance;
    private static Method externalShaderApiIsPackInUse;
    private static boolean externalShaderApiFailureLogged;
    private static final float[] GPU_IMPULSE_DATA =
            new float[WaterSurfaceDisplacement.MAX_GPU_IMPULSES
                    * WaterSurfaceDisplacement.GPU_IMPULSE_STRIDE];

    private WaterShaders() {
    }

    /** Registers the core ocean shader during the client shader event. */
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "gerstner_water"),
                        DefaultVertexFormat.BLOCK
                ),
                WaterShaders::acceptOceanShader
        );
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "underwater_optics"),
                        DefaultVertexFormat.POSITION_TEX
                ),
                WaterShaders::acceptUnderwaterShader
        );
    }

    // A failed GLSL link can still produce a ShaderInstance. Reject it here so
    // the per-frame ocean pass can keep drawing the Wilderness mesh with the
    // stock translucent program instead of retrying an invalid shader.
    private static void acceptOceanShader(ShaderInstance shader) {
        if (!isLinked(shader)) {
            ModConstants.LOGGER.error("Ocean shader failed to link; drawing the Wilderness ocean mesh with the stock translucent shader");
            oceanShader = null;
            return;
        }
        oceanShader = shader;
        sceneCaptureFailureLogged = false;
    }

    // Underwater optics are optional for the same reason as the surface shader:
    // a bad resource reload must not prevent the client from reaching the menu.
    private static void acceptUnderwaterShader(ShaderInstance shader) {
        if (!isLinked(shader)) {
            ModConstants.LOGGER.error("Underwater shader failed to link; drawing the Wilderness underwater overlay with the stock texture shader");
            underwaterShader = null;
            return;
        }
        underwaterShader = shader;
    }

    /** Returns the program used by the custom Wilderness ocean RenderType. */
    public static ShaderInstance getOceanShader() {
        return oceanShader != null ? oceanShader : GameRenderer.getRendertypeTranslucentShader();
    }

    /** Returns whether the built-in shader should own ocean pixels this frame. */
    public static boolean shouldUseCoreShader() {
        if (!WaterRenderingConfig.ENABLE_WATER_CORE_SHADER.get() || oceanShader == null) {
            return false;
        }
        return !externalShaderPackOwnsWater();
    }

    /** Returns whether the built-in underwater overlay should own this frame. */
    public static boolean shouldUseUnderwaterShader() {
        return WaterRenderingConfig.ENABLE_UNDERWATER_CAUSTICS.get()
                && underwaterShader != null
                && !externalShaderPackOwnsWater();
    }

    /** Returns the program used by the Wilderness underwater overlay. */
    public static ShaderInstance getUnderwaterShader() {
        return underwaterShader != null ? underwaterShader : GameRenderer.getPositionTexShader();
    }

    /** Updates uniforms consumed by the built-in optical water pass. */
    public static void updateOceanUniforms(
            float timeSeconds,
            OceanSeaState.Sample seaState,
            float dayTime
    ) {
        OceanSeaState.Sample safeState = seaState == null ? OceanSeaState.CALM : seaState;
        updateOceanUniforms(
                timeSeconds,
                safeState.strength(),
                safeState.windDirectionX(),
                safeState.windDirectionZ(),
                dayTime
        );
        if (oceanShader != null) {
            updateSpectrumUniforms(
                    safeState.spectrum(),
                    safeState.windSpeed(),
                    safeState.breakingStrength()
            );
        }
    }

    /**
     * Updates legacy scalar sea-state callers that do not retain the complete spectrum.
     *
     * <p>The active coordinator uses the complete-state overload above. Dormant
     * surface implementations retain this entry point for binary compatibility.</p>
     */
    public static void updateOceanUniforms(
            float timeSeconds,
            float seaState,
            float windDirectionX,
            float windDirectionZ,
            float dayTime
    ) {
        if (oceanShader == null) {
            return;
        }

        oceanShader.safeGetUniform("GameTime").set(timeSeconds);
        oceanShader.safeGetUniform("SeaState").set(seaState);
        oceanShader.safeGetUniform("WindDirection").set(windDirectionX, windDirectionZ);
        oceanShader.safeGetUniform("DayTime").set(dayTime);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            oceanShader.safeGetUniform("SceneCaptureValid").set(0.0f);
            return;
        }

        float partialTick = minecraft.gameRenderer.getMainCamera().getPartialTickTime();
        long frameKey = (minecraft.level.getGameTime() << 32)
                ^ (Float.floatToRawIntBits(timeSeconds) & 0xFFFFFFFFL);
        WaterSceneCapture.Capture capture = null;
        try {
            capture = WaterSceneCapture.capture(frameKey);
        } catch (RuntimeException exception) {
            if (!sceneCaptureFailureLogged) {
                ModConstants.LOGGER.warn("Unable to capture scene color/depth for optical water; using environment reflection", exception);
                sceneCaptureFailureLogged = true;
            }
        }

        if (capture != null && capture.available()) {
            oceanShader.setSampler("SceneColor", capture.colorTextureId());
            oceanShader.setSampler("SceneDepth", capture.depthTextureId());
            oceanShader.safeGetUniform("ScreenSize").set((float) capture.width(), (float) capture.height());
            oceanShader.safeGetUniform("SceneCaptureValid").set(1.0f);
        } else {
            oceanShader.safeGetUniform("SceneCaptureValid").set(0.0f);
        }

        Matrix4f inverseProjection = new Matrix4f(RenderSystem.getProjectionMatrix()).invert();
        oceanShader.safeGetUniform("InverseProjMat").set(inverseProjection);
        var cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();

        float rain;
        float thunder;
        if (ClientWeatherCoordinator.controls(minecraft.level)) {
            // Water remains read-only from weather's perspective; only these
            // client shader uniforms consume the local immutable snapshot.
            WeatherSample localWeather = ClientWeatherCoordinator.sampleAt(
                    minecraft.level,
                    cameraPosition
            );
            rain = (float) localWeather.precipitationIntensity();
            thunder = ClientWeatherCoordinator.thunderContribution(localWeather);
        } else {
            rain = minecraft.level.getRainLevel(partialTick);
            thunder = minecraft.level.getThunderLevel(partialTick);
        }
        float skyBrightness = Math.max(0.12f, 1.0f - rain * 0.32f - thunder * 0.38f);
        oceanShader.safeGetUniform("Weather").set(rain, thunder, skyBrightness, 0.0f);
        var sky = minecraft.level.getSkyColor(cameraPosition, partialTick);
        oceanShader.safeGetUniform("EnvironmentColor").set(
                (float) sky.x,
                (float) sky.y,
                (float) sky.z,
                skyBrightness
        );

        oceanShader.safeGetUniform("OpticalQuality").set(
                (float) WaterRenderingConfig.waterQuality().ordinal(),
                WaterRenderingConfig.refractionStrength(),
                (float) WaterRenderingConfig.screenSpaceReflectionSteps(),
                WaterRenderingConfig.screenSpaceReflectionDistance()
        );
        float absorption = WaterRenderingConfig.surfaceAbsorptionStrength();
        // Keep enough spectral separation for blue-water depth while allowing
        // sunlit terrain to remain readable through several blocks of water.
        // The previous coefficients compounded with the cinematic config and
        // effectively attenuated the already-lit scene twice.
        oceanShader.safeGetUniform("AbsorptionCoefficients").set(
                0.082f * absorption,
                0.030f * absorption,
                0.014f * absorption
        );
        oceanShader.safeGetUniform("TideOffset").set(
                TideSystem.getTideOffset(minecraft.level) * 0.18f
        );
        oceanShader.safeGetUniform("GpuWaveStrength").set(
                WaterSurfaceEquation.LEGACY_GPU_COMPLEMENT_SCALE
        );
        WaveSpectrumState approximateSpectrum = new WaveSpectrumState(
                0.72f + seaState * 1.02f,
                0.50f + seaState * 1.85f,
                windDirectionX,
                windDirectionZ,
                0.10f + seaState * 0.72f
        );
        updateSpectrumUniforms(
                approximateSpectrum,
                2.0f + seaState * 13.0f,
                smoothStep(0.22f, 0.92f, seaState)
        );
        updateWaveUniforms(
                "Ocean",
                GerstnerWaveProfile.OCEAN,
                configuredWaveLimit(WaterBodyClassifier.WaterType.OCEAN)
        );
        updateWaveUniforms(
                "River",
                GerstnerWaveProfile.RIVER,
                configuredWaveLimit(WaterBodyClassifier.WaterType.RIVER)
        );
        updateWaveUniforms(
                "Pond",
                GerstnerWaveProfile.POND,
                configuredWaveLimit(WaterBodyClassifier.WaterType.POND)
        );
        updateImpulseUniforms(minecraft.level, timeSeconds, cameraPosition.x, cameraPosition.z);
    }

    // Inactive components upload zero amplitude so GPU quality limits match
    // boat/entity CPU sampling without branching in the vertex shader.
    private static void updateWaveUniforms(
            String prefix,
            GerstnerWaveProfile profile,
            int waveLimit
    ) {
        for (int index = 0; index < 4; index++) {
            boolean active = index < profile.waveCount && index < waveLimit;
            int profileIndex = active ? index : 0;
            float componentBlend = profile.waveCount <= 1
                    ? 0.0f
                    : profileIndex / (float) (profile.waveCount - 1);
            oceanShader.safeGetUniform(prefix + "WaveParam" + index).set(
                    profile.dirX[profileIndex],
                    profile.dirZ[profileIndex],
                    profile.waveNumber[profileIndex],
                    profile.angularFrequency[profileIndex]
            );
            oceanShader.safeGetUniform(prefix + "WaveShape" + index).set(
                    active ? profile.amplitude[profileIndex] : 0.0f,
                    profile.phaseOffset[profileIndex],
                    profile.steepness[profileIndex],
                    componentBlend
            );
        }
    }

    private static int configuredWaveLimit(WaterBodyClassifier.WaterType type) {
        return WaterRenderingConfig.ENABLE_GERSTNER_WAVES.get()
                ? WaterRenderingConfig.waveTrainLimit(type)
                : 0;
    }

    private static void updateSpectrumUniforms(
            WaveSpectrumState spectrum,
            float windSpeed,
            float breakingStrength
    ) {
        oceanShader.safeGetUniform("SpectrumState").set(
                spectrum.swellScale(),
                spectrum.chopScale(),
                spectrum.directionBlend(),
                Math.max(0.0f, Math.min(1.0f, breakingStrength))
        );
        oceanShader.safeGetUniform("WindSpeed").set(Math.max(0.0f, windSpeed));
    }

    private static void updateImpulseUniforms(
            net.minecraft.world.level.Level level,
            float timeSeconds,
            double cameraX,
            double cameraZ
    ) {
        int count = WaterSurfaceDisplacement.writeGpuImpulses(
                level,
                timeSeconds * 20.0f,
                cameraX,
                cameraZ,
                GPU_IMPULSE_DATA
        );
        oceanShader.safeGetUniform("ImpulseCount").set((float) count);
        for (int index = 0; index < WaterSurfaceDisplacement.MAX_GPU_IMPULSES; index++) {
            int offset = index * WaterSurfaceDisplacement.GPU_IMPULSE_STRIDE;
            oceanShader.safeGetUniform("ImpulsePosition" + index).set(
                    GPU_IMPULSE_DATA[offset],
                    GPU_IMPULSE_DATA[offset + 1],
                    GPU_IMPULSE_DATA[offset + 2],
                    GPU_IMPULSE_DATA[offset + 3]
            );
            oceanShader.safeGetUniform("ImpulseShape" + index).set(
                    GPU_IMPULSE_DATA[offset + 4],
                    GPU_IMPULSE_DATA[offset + 5],
                    GPU_IMPULSE_DATA[offset + 6],
                    GPU_IMPULSE_DATA[offset + 7]
            );
        }
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = Math.max(0.0f, Math.min(1.0f, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    /** Enables full GPU displacement for stable snapshot meshes with flat CPU topology. */
    public static void prepareSnapshotMeshPass() {
        if (oceanShader != null) {
            oceanShader.safeGetUniform("GpuWaveStrength").set(1.0f);
        }
    }

    /** Updates the bounded uniforms consumed by the underwater overlay. */
    public static void updateUnderwaterUniforms(
            float timeSeconds,
            ClientWaterImmersion.ImmersionState state
    ) {
        if (underwaterShader == null) {
            return;
        }
        UnderwaterOpticsModel.OpticalProperties optics = state.optics();
        underwaterShader.safeGetUniform("GameTime").set(timeSeconds);
        underwaterShader.safeGetUniform("Submersion").set(optics.immersionBlend());
        underwaterShader.safeGetUniform("Clarity").set(optics.clarity());
        underwaterShader.safeGetUniform("SeaState").set(state.seaState());
        underwaterShader.safeGetUniform("CausticStrength").set(optics.causticStrength());
        underwaterShader.safeGetUniform("DistortionStrength").set(optics.distortionStrength());
        underwaterShader.safeGetUniform("WaterFogColor").set(
                optics.fogRed(),
                optics.fogGreen(),
                optics.fogBlue()
        );

        // The underwater pass runs after the level has been drawn. Capture it
        // once at this stage so every underwater optical operation samples an
        // isolated scene instead of feeding the framebuffer back into itself.
        Minecraft minecraft = Minecraft.getInstance();
        WaterSceneCapture.Capture capture = null;
        if (minecraft.level != null) {
            long frameKey = ((minecraft.level.getGameTime() << 32)
                    ^ (Float.floatToRawIntBits(timeSeconds) & 0xFFFFFFFFL))
                    ^ 0x4000_0000_0000_0000L;
            try {
                capture = WaterSceneCapture.capture(frameKey);
            } catch (RuntimeException exception) {
                if (!sceneCaptureFailureLogged) {
                    ModConstants.LOGGER.warn(
                            "Unable to capture the completed scene for underwater optics; using texture fallback",
                            exception
                    );
                    sceneCaptureFailureLogged = true;
                }
            }
        }

        if (capture != null && capture.available()) {
            underwaterShader.setSampler("SceneColor", capture.colorTextureId());
            underwaterShader.setSampler("SceneDepth", capture.depthTextureId());
            underwaterShader.safeGetUniform("ScreenSize").set(
                    (float) capture.width(),
                    (float) capture.height()
            );
            underwaterShader.safeGetUniform("SceneCaptureValid").set(1.0f);
        } else {
            underwaterShader.safeGetUniform("ScreenSize").set(1.0f, 1.0f);
            underwaterShader.safeGetUniform("SceneCaptureValid").set(0.0f);
        }

        float turbidity = 1.0f - optics.clarity();
        underwaterShader.safeGetUniform("CameraDepth").set(
                Math.max(0.0f, state.depthBelowSurface())
        );
        underwaterShader.safeGetUniform("VisibilityBlocks").set(
                Math.max(6.0f, optics.visibilityBlocks())
        );
        underwaterShader.safeGetUniform("DepthRange").set(
                0.05f,
                Math.max(16.0f, minecraft.gameRenderer.getDepthFar())
        );
        // The world fog pass already handles long-distance extinction. These
        // bounded coefficients grade the captured scene once and supply local
        // Beer-Lambert color separation without darkening its lightmap twice.
        underwaterShader.safeGetUniform("AbsorptionCoefficients").set(
                0.018f + turbidity * 0.018f,
                0.007f + turbidity * 0.010f,
                0.0035f + turbidity * 0.006f
        );
    }

    /**
     * Returns whether an active Iris/Oculus pack should retain the ordinary tagged-fluid path.
     *
     * <p>The snapshot mesh relies on the built-in vertex program for continuous
     * wave displacement. Drawing it through stock translucent under a shader
     * pack would produce a flat duplicate surface and hide the fluid geometry
     * the pack expects to shade.</p>
     */
    public static boolean externalShaderPackOwnsWater() {
        ModList mods = ModList.get();
        if (!mods.isLoaded("iris") && !mods.isLoaded("oculus")) {
            return false;
        }
        resolveExternalShaderApi();
        if (externalShaderApiGetInstance == null || externalShaderApiIsPackInUse == null) {
            // An installed renderer with an unknown API is safer on its tagged
            // fluid path than behind an un-displaced duplicate snapshot mesh.
            return true;
        }
        try {
            Object api = externalShaderApiGetInstance.invoke(null);
            return Boolean.TRUE.equals(externalShaderApiIsPackInUse.invoke(api));
        } catch (ReflectiveOperationException | RuntimeException exception) {
            if (!externalShaderApiFailureLogged) {
                ModConstants.LOGGER.warn(
                        "Unable to query the active Iris/Oculus shader pack; preserving tagged-fluid water fallback",
                        exception
                );
                externalShaderApiFailureLogged = true;
            }
            return true;
        }
    }

    private static void resolveExternalShaderApi() {
        if (externalShaderApiResolved) {
            return;
        }
        synchronized (WaterShaders.class) {
            if (externalShaderApiResolved) {
                return;
            }
            for (String className : new String[] {
                    "net.irisshaders.iris.api.v0.IrisApi",
                    "net.coderbot.iris.api.v0.IrisApi"
            }) {
                try {
                    Class<?> apiClass = Class.forName(className, false, WaterShaders.class.getClassLoader());
                    Method getInstance = apiClass.getMethod("getInstance");
                    Method isPackInUse = apiClass.getMethod("isShaderPackInUse");
                    externalShaderApiGetInstance = getInstance;
                    externalShaderApiIsPackInUse = isPackInUse;
                    break;
                } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                    // Try the next API package used by modern/legacy Iris and Oculus.
                }
            }
            externalShaderApiResolved = true;
        }
    }

    private static boolean isLinked(ShaderInstance shader) {
        int programId = shader.getId();
        return GL20.glIsProgram(programId)
                && GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) != 0;
    }
}
