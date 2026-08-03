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
    private static final Matrix4f CAPTURED_INVERSE_PROJECTION = new Matrix4f();
    private static final Matrix4f CAPTURED_VIEW_TO_WORLD = new Matrix4f();
    private static long capturedOpticalFrameKey = Long.MIN_VALUE;
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
        capturedOpticalFrameKey = Long.MIN_VALUE;
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

        oceanShader.safeGetUniform("SeaState").set(seaState);
        oceanShader.safeGetUniform("WindDirection").set(windDirectionX, windDirectionZ);
        oceanShader.safeGetUniform("DayTime").set(dayTime);

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            oceanShader.safeGetUniform("SceneCaptureValid").set(0.0f);
            capturedOpticalFrameKey = Long.MIN_VALUE;
            WaterRenderDiagnostics.setSceneCaptureAvailable(false);
            return;
        }

        float partialTick = minecraft.gameRenderer.getMainCamera().getPartialTickTime();
        uploadStableTime(oceanShader, minecraft.level.getGameTime(), partialTick);
        uploadSurfaceAnimationPhases(
                oceanShader,
                minecraft.level.getGameTime(),
                partialTick,
                seaState,
                windDirectionX,
                windDirectionZ
        );
        long frameKey = sceneFrameKey(
                minecraft.level,
                minecraft.level.getGameTime(),
                partialTick
        );
        Matrix4f inverseProjection = new Matrix4f(RenderSystem.getProjectionMatrix()).invert();
        Matrix4f viewToWorld = new Matrix4f().rotation(
                minecraft.gameRenderer.getMainCamera().rotation()
        );
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
            // Preserve the exact camera transform that produced this depth
            // texture. GUI and hand-overlay hooks run with different matrices.
            CAPTURED_INVERSE_PROJECTION.set(inverseProjection);
            CAPTURED_VIEW_TO_WORLD.set(viewToWorld);
            capturedOpticalFrameKey = frameKey;
            WaterRenderDiagnostics.setSceneCaptureAvailable(true);
        } else {
            oceanShader.safeGetUniform("SceneCaptureValid").set(0.0f);
            capturedOpticalFrameKey = Long.MIN_VALUE;
            WaterRenderDiagnostics.setSceneCaptureAvailable(false);
        }

        oceanShader.safeGetUniform("InverseProjMat").set(inverseProjection);
        var cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();

        float rain;
        float thunder;
        float frozen;
        if (ClientWeatherCoordinator.controls(minecraft.level)) {
            // Water remains read-only from weather's perspective; only these
            // client shader uniforms consume the local immutable snapshot.
            WeatherSample localWeather = ClientWeatherCoordinator.sampleAt(
                    minecraft.level,
                    cameraPosition
            );
            rain = (float) localWeather.precipitationIntensity();
            thunder = ClientWeatherCoordinator.thunderContribution(localWeather);
            frozen = (float) localWeather.surface().frozenFraction();
        } else {
            rain = minecraft.level.getRainLevel(partialTick);
            thunder = minecraft.level.getThunderLevel(partialTick);
            frozen = 0.0f;
        }
        float skyBrightness = Math.max(0.12f, 1.0f - rain * 0.32f - thunder * 0.38f);
        oceanShader.safeGetUniform("Weather").set(rain, thunder, skyBrightness, frozen);
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
        oceanShader.safeGetUniform("SurfaceOpacityStrength").set(
                WaterRenderingConfig.surfaceOpacityStrength()
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
                configuredWaveLimit(WaterBodyClassifier.WaterType.OCEAN),
                minecraft.level.getGameTime(),
                partialTick
        );
        updateWaveUniforms(
                "River",
                GerstnerWaveProfile.RIVER,
                configuredWaveLimit(WaterBodyClassifier.WaterType.RIVER),
                minecraft.level.getGameTime(),
                partialTick
        );
        updateWaveUniforms(
                "Pond",
                GerstnerWaveProfile.POND,
                configuredWaveLimit(WaterBodyClassifier.WaterType.POND),
                minecraft.level.getGameTime(),
                partialTick
        );
        updateImpulseUniforms(
                minecraft.level,
                minecraft.level.getGameTime() + (double) partialTick,
                cameraPosition.x,
                cameraPosition.z
        );
    }

    // Inactive components upload zero amplitude so GPU quality limits match
    // boat/entity CPU sampling without branching in the vertex shader.
    private static void updateWaveUniforms(
            String prefix,
            GerstnerWaveProfile profile,
            int waveLimit,
            long gameTime,
            float partialTick
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
                    profile.phaseOffset[profileIndex] - stableAnimationPhase(
                            gameTime,
                            partialTick,
                            profile.angularFrequency[profileIndex]
                    ),
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
            double sampleTick,
            double cameraX,
            double cameraZ
    ) {
        oceanShader.safeGetUniform("ImpulseChunkIndex").set(
                (float) Math.floor(cameraX / 16.0),
                (float) Math.floor(cameraZ / 16.0)
        );
        int count = WaterSurfaceDisplacement.writeGpuImpulses(
                level,
                sampleTick,
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
        underwaterShader.safeGetUniform("Submersion").set(optics.immersionBlend());
        underwaterShader.safeGetUniform("Clarity").set(optics.clarity());
        underwaterShader.safeGetUniform("CausticStrength").set(optics.causticStrength());
        underwaterShader.safeGetUniform("DistortionStrength").set(optics.distortionStrength());
        underwaterShader.safeGetUniform("EffectQuality").set(
                (float) WaterRenderingConfig.waterQuality().ordinal()
        );
        underwaterShader.safeGetUniform("WaterFogColor").set(
                optics.fogRed(),
                optics.fogGreen(),
                optics.fogBlue()
        );

        // The hand and GUI passes clear world depth before either underwater
        // overlay hook executes. Reuse only the capture made at the translucent
        // water stage; copying here would create a second full-resolution blit
        // whose depth texture cannot reconstruct the terrain scene.
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = minecraft.gameRenderer.getMainCamera().getPartialTickTime();
        long gameTime = minecraft.level == null ? 0L : minecraft.level.getGameTime();
        uploadUnderwaterAnimationPhases(
                underwaterShader,
                gameTime,
                partialTick,
                state.seaState()
        );
        WaterSceneCapture.Capture capture = null;
        if (minecraft.level != null) {
            long frameKey = sceneFrameKey(
                    minecraft.level,
                    minecraft.level.getGameTime(),
                    partialTick
            );
            if (capturedOpticalFrameKey == frameKey) {
                try {
                    capture = WaterSceneCapture.getIfCurrent(frameKey);
                } catch (RuntimeException exception) {
                    if (!sceneCaptureFailureLogged) {
                        ModConstants.LOGGER.warn(
                                "Unable to reuse the water-stage scene for underwater optics; using texture fallback",
                                exception
                        );
                        sceneCaptureFailureLogged = true;
                    }
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
            underwaterShader.safeGetUniform("InverseProjMat").set(
                    CAPTURED_INVERSE_PROJECTION
            );
            underwaterShader.safeGetUniform("ViewToWorldMat").set(
                    CAPTURED_VIEW_TO_WORLD
            );
            underwaterShader.safeGetUniform("SceneCaptureValid").set(1.0f);
        } else {
            underwaterShader.safeGetUniform("ScreenSize").set(1.0f, 1.0f);
            underwaterShader.safeGetUniform("SceneCaptureValid").set(0.0f);
        }

        var cameraPosition = minecraft.gameRenderer.getMainCamera().getPosition();
        underwaterShader.safeGetUniform("CameraAnchor").set(
                wrappedHorizontalAnchor(cameraPosition.x),
                (float) cameraPosition.y,
                wrappedHorizontalAnchor(cameraPosition.z)
        );
        underwaterShader.safeGetUniform("CameraDepth").set(
                Math.max(0.0f, state.depthBelowSurface())
        );
        underwaterShader.safeGetUniform("VisibilityBlocks").set(
                Math.max(6.0f, optics.visibilityBlocks())
        );

        float sunAngle = minecraft.level == null ? 0.0f : minecraft.level.getSunAngle(partialTick);
        boolean skyLight = minecraft.level != null && minecraft.level.dimensionType().hasSkyLight();
        underwaterShader.safeGetUniform("SunDirection").set(
                skyLight ? -(float) Math.sin(sunAngle) : 0.0f,
                skyLight ? (float) Math.cos(sunAngle) : -1.0f,
                0.0f
        );

        // The CPU optical model and fragment shader now share exactly the same
        // bounded medium coefficients for ray-distance transmission.
        UnderwaterOpticsModel.AbsorptionCoefficients absorption =
                UnderwaterOpticsModel.absorptionForClarity(optics.clarity());
        underwaterShader.safeGetUniform("AbsorptionCoefficients").set(
                absorption.red(),
                absorption.green(),
                absorption.blue()
        );
        underwaterShader.safeGetUniform("ScatteringCoefficient").set(
                UnderwaterOpticsModel.scatteringForClarity(optics.clarity())
        );
    }

    private static long sceneFrameKey(Object level, long gameTime, float partialTick) {
        long levelKey = Integer.toUnsignedLong(System.identityHashCode(level));
        return (levelKey * 0x9E37_79B9L)
                ^ (gameTime << 32)
                ^ (Float.floatToRawIntBits(partialTick) & 0xFFFF_FFFFL);
    }

    // Encodes the exact long tick as base-1024 digits. Shader phase functions
    // reduce each digit before accumulation, so animation retains partial-tick
    // motion without multiplying a huge imprecise float time.
    private static void uploadStableTime(
            ShaderInstance shader,
            long gameTime,
            float partialTick
    ) {
        long ticks = Math.max(0L, gameTime);
        float fraction = Math.max(0.0f, Math.min(1.0f, partialTick));
        shader.safeGetUniform("TimeFrameLow").set(
                (ticks & 1023L) + fraction,
                (float) ((ticks >>> 10) & 1023L),
                (float) ((ticks >>> 20) & 1023L),
                (float) ((ticks >>> 30) & 1023L)
        );
        shader.safeGetUniform("TimeFrameHigh").set(
                (float) ((ticks >>> 40) & 1023L),
                (float) ((ticks >>> 50) & 1023L),
                (float) ((ticks >>> 60) & 7L)
        );
    }

    // Fullscreen underwater work consumes pre-reduced phases. Performing the
    // long-tick modular arithmetic once on the CPU avoids dozens of modulo
    // operations for every screen pixel while retaining exact tick motion.
    private static void uploadUnderwaterAnimationPhases(
            ShaderInstance shader,
            long gameTime,
            float partialTick,
            float seaState
    ) {
        double sea = Math.max(0.0, Math.min(1.0, seaState));
        double speed = 0.58 + sea * 0.82;
        shader.safeGetUniform("DistortionPhases").set(
                stableAnimationPhase(gameTime, partialTick, speed),
                stableAnimationPhase(gameTime, partialTick, -speed * 0.83),
                stableAnimationPhase(gameTime, partialTick, speed * 1.34),
                stableAnimationPhase(gameTime, partialTick, -speed * 1.12)
        );
        shader.safeGetUniform("CausticPhases0").set(
                stableAnimationPhase(gameTime, partialTick, 0.42),
                stableAnimationPhase(gameTime, partialTick, -0.37),
                stableAnimationPhase(gameTime, partialTick, 1.16),
                stableAnimationPhase(gameTime, partialTick, -0.93)
        );
        shader.safeGetUniform("CausticPhases1").set(
                stableAnimationPhase(gameTime, partialTick, 0.71),
                stableAnimationPhase(gameTime, partialTick, 0.035),
                stableAnimationPhase(gameTime, partialTick, -0.028)
        );
        shader.safeGetUniform("FallbackPhases").set(
                stableAnimationPhase(gameTime, partialTick, 0.65 + sea * 0.85),
                stableAnimationPhase(gameTime, partialTick, -(0.52 + sea * 0.72))
        );
    }

    private static void uploadSurfaceAnimationPhases(
            ShaderInstance shader,
            long gameTime,
            float partialTick,
            float seaState,
            float windDirectionX,
            float windDirectionZ
    ) {
        double sea = Math.max(0.0, Math.min(1.0, seaState));
        double windX = windDirectionX + 0.0001;
        double windZ = windDirectionZ;
        double windLength = Math.hypot(windX, windZ);
        if (windLength <= 1.0e-8) {
            windX = 1.0;
            windZ = 0.0;
        } else {
            windX /= windLength;
            windZ /= windLength;
        }
        double crossX = -windZ;
        double crossZ = windX;
        double glassX = windX * 0.58 - crossX * 0.81;
        double glassZ = windZ * 0.58 - crossZ * 0.81;
        double glassLength = Math.hypot(glassX, glassZ);
        glassX /= glassLength;
        glassZ /= glassLength;
        double glassAdvection = (windX * glassX + windZ * glassZ) * 13.73 * 0.11;

        shader.safeGetUniform("SurfaceAnimationPhases0").set(
                stableAnimationPhase(gameTime, partialTick, 0.19),
                stableAnimationPhase(gameTime, partialTick, -0.16),
                stableAnimationPhase(gameTime, partialTick, 0.51 + sea * 1.07),
                stableAnimationPhase(gameTime, partialTick, -0.39 - sea * 0.83)
        );
        shader.safeGetUniform("SurfaceAnimationPhases1").set(
                stableAnimationPhase(gameTime, partialTick, 1.63 + sea * 2.91),
                stableAnimationPhase(
                        gameTime, partialTick, 2.31 + sea * 4.07 + glassAdvection),
                stableAnimationPhase(gameTime, partialTick, 1.15),
                stableAnimationPhase(gameTime, partialTick, 0.55)
        );
    }

    static float stableAnimationPhase(
            long gameTime,
            float partialTick,
            double radiansPerSecond
    ) {
        long remainingTicks = Math.max(0L, gameTime);
        double phaseStep = Math.IEEEremainder(radiansPerSecond / 20.0, Math.PI * 2.0);
        double phase = Math.IEEEremainder(
                Math.max(0.0f, Math.min(1.0f, partialTick)) * phaseStep,
                Math.PI * 2.0
        );
        for (int digit = 0; digit < 7; digit++) {
            phase = Math.IEEEremainder(
                    phase + (remainingTicks & 1023L) * phaseStep,
                    Math.PI * 2.0
            );
            remainingTicks >>>= 10;
            phaseStep = Math.IEEEremainder(phaseStep * 1024.0, Math.PI * 2.0);
        }
        return (float) phase;
    }

    private static float wrappedHorizontalAnchor(double coordinate) {
        double period = 4096.0;
        double wrapped = coordinate % period;
        return (float) (wrapped < 0.0 ? wrapped + period : wrapped);
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
