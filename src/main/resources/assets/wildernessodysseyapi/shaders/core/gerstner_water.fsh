#version 150

uniform sampler2D Sampler0;
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 InverseProjMat;
uniform vec2 ScreenSize;
uniform vec4 Weather;
uniform vec4 EnvironmentColor;
uniform vec4 OpticalQuality;
uniform vec4 SurfaceAnimationPhases0;
uniform vec4 SurfaceAnimationPhases1;
uniform float SurfaceOpacityStrength;
uniform vec3 AbsorptionCoefficients;
uniform float SceneCaptureValid;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 lightColor;
in vec3 viewPosition;
in vec3 viewNormal;
in vec3 worldNormal;
in vec2 phaseLocalXZ;
flat in vec2 phaseChunkIndex;
in vec3 celestialDirection;
in float celestialDaylight;
in vec3 waterBodyBlend;
in float surfaceContinuity;
in vec2 localCurrent;
in float shoreFactor;
in float depthFactor;
in float disturbanceStrength;
in float waveSlope;
in float crestCompression;
in float regionalSeaState;
in vec2 regionalWindDirection;
in float regionalWindSpeed;
in vec4 regionalSpectrumState;

out vec4 fragColor;

const float TWO_PI = 6.28318530718;
const float PHASE_CHUNK_SPAN = 16.0;
const float PHASE_COARSE_CHUNKS = 1024.0;
const vec2 DETAIL_PRIMARY_DIRECTION = vec2(0.857493, 0.514496);
const vec2 DETAIL_CROSS_DIRECTION = vec2(-0.514496, 0.857493);
const vec2 DETAIL_DIAGONAL_DIRECTION = vec2(0.312230, 0.950007);
const vec2 DETAIL_GLASS_DIRECTION = vec2(0.914354, -0.404916);
const vec2 SHORE_BREAK_DIRECTION = vec2(0.780869, 0.624695);
const vec2 SHORE_SHEAR_DIRECTION = vec2(-0.624695, 0.780869);

// Mirrors the vertex-stage origin split so fragment detail retains sub-block
// phase precision near Minecraft's world border instead of quantizing against
// a multi-million-block interpolated world position.
float stableWorldPhase(vec2 coefficient) {
    // Mesh-edge interpolation can land exactly on the neighboring chunk.
    // Canonicalizing it keeps fragment detail continuous across both ordinary
    // and coarse phase-chunk boundaries.
    vec2 localChunkOffset = floor(phaseLocalXZ / PHASE_CHUNK_SPAN);
    vec2 canonicalLocal = phaseLocalXZ - localChunkOffset * PHASE_CHUNK_SPAN;
    vec2 canonicalChunkIndex = phaseChunkIndex + localChunkOffset;
    vec2 coarseIndex = floor(canonicalChunkIndex / PHASE_COARSE_CHUNKS);
    vec2 fineIndex = canonicalChunkIndex - coarseIndex * PHASE_COARSE_CHUNKS;
    vec2 coarseStep = mod(
        coefficient * (PHASE_CHUNK_SPAN * PHASE_COARSE_CHUNKS),
        vec2(TWO_PI)
    );
    vec2 fineCoordinate = fineIndex * PHASE_CHUNK_SPAN + canonicalLocal;
    vec2 axisPhase = mod(coarseIndex * coarseStep + fineCoordinate * coefficient,
        vec2(TWO_PI));
    return mod(axisPhase.x + axisPhase.y, TWO_PI);
}

float animatedStablePhase(vec2 coefficient, float basePhase) {
    return mod(stableWorldPhase(coefficient) + basePhase, TWO_PI);
}

float phaseBandLimit(vec2 coefficient) {
    // Analytic waves have no texture mip chain. Fade only frequencies whose
    // world-space footprint spans more than a pixel to prevent motion shimmer.
    vec2 pixelSpan = abs(dFdx(phaseLocalXZ)) + abs(dFdy(phaseLocalXZ));
    float radiansPerPixel = dot(pixelSpan, abs(coefficient));
    return 1.0 - smoothstep(0.65, 1.75, radiansPerPixel);
}

float stableWaveLayer(vec2 localOffset, vec2 direction,
                      float frequency, float basePhase) {
    vec2 coefficient = direction * frequency;
    return sin(mod(animatedStablePhase(coefficient, basePhase)
        + dot(localOffset, coefficient), TWO_PI)) * phaseBandLimit(coefficient);
}

vec2 normalizedOr(vec2 direction, vec2 fallbackDirection) {
    float directionLength = length(direction);
    return directionLength > 0.000001 ? direction / directionLength : fallbackDirection;
}

float phaseStableDirectionalWeight(vec2 carrier, vec2 driver, float influence) {
    float alignment = max(0.0, dot(carrier, driver));
    return mix(1.0, 0.58 + alignment * 0.84, clamp(influence, 0.0, 1.0));
}

vec3 proceduralWorldNormal(vec2 current, float sea) {
    vec2 wind = normalizedOr(regionalWindDirection, DETAIL_PRIMARY_DIRECTION);
    float windEnergy = clamp(regionalWindSpeed / 20.0, 0.0, 1.0);
    float windDetailScale = mix(0.88, 1.14, windEnergy);
    float currentStrength = clamp(length(current) / 1.5, 0.0, 1.0);
    vec2 currentDirection = normalizedOr(current, wind);
    vec2 driver = normalizedOr(mix(wind, currentDirection, currentStrength), wind);
    float directionalInfluence = max(regionalSpectrumState.z, currentStrength);
    float primaryWeight = phaseStableDirectionalWeight(
        DETAIL_PRIMARY_DIRECTION, driver, directionalInfluence);
    float crossWeight = phaseStableDirectionalWeight(
        DETAIL_CROSS_DIRECTION, driver, directionalInfluence);
    float diagonalWeight = phaseStableDirectionalWeight(
        DETAIL_DIAGONAL_DIRECTION, driver, directionalInfluence);
    float glassWeight = phaseStableDirectionalWeight(
        DETAIL_GLASS_DIRECTION, driver, directionalInfluence);
    float quality = clamp(OpticalQuality.x, 0.0, 3.0);
    if (quality < 0.5) {
        float lowRipple = sin(animatedStablePhase(
            DETAIL_PRIMARY_DIRECTION * 1.37, SurfaceAnimationPhases0.z));
        vec2 lowGradient = DETAIL_PRIMARY_DIRECTION * lowRipple * 0.014
            * primaryWeight * windDetailScale;
        return normalize(vec3(lowGradient.x, 1.0, lowGradient.y));
    }
    // Static world-space domain warping breaks repetition without cyclically
    // accelerating the temporal phases at a stationary camera.
    vec2 warp = vec2(
        sin(stableWorldPhase(vec2(0.071, 0.113)) + 1.37),
        cos(stableWorldPhase(vec2(-0.097, 0.059)) - 0.91)
    ) * 0.66;
    float detailEnergy = (0.78 + sea * 0.44) * windDetailScale;
    float longRipple = stableWaveLayer(warp, DETAIL_PRIMARY_DIRECTION,
        1.37, SurfaceAnimationPhases0.z);
    float crossRipple = stableWaveLayer(warp, DETAIL_CROSS_DIRECTION,
        2.73, SurfaceAnimationPhases0.w);
    vec2 gradient = (DETAIL_PRIMARY_DIRECTION * longRipple * 0.016 * primaryWeight
        + DETAIL_CROSS_DIRECTION * crossRipple * 0.012 * crossWeight) * detailEnergy;
    if (quality >= 2.0) {
        float capillary = stableWaveLayer(warp, DETAIL_DIAGONAL_DIRECTION,
            6.91, SurfaceAnimationPhases1.x);
        gradient += (DETAIL_PRIMARY_DIRECTION * capillary * 0.008 * primaryWeight
            + DETAIL_DIAGONAL_DIRECTION * capillary * 0.007 * diagonalWeight)
            * detailEnergy;

        // Rain is represented as two bounded capillary bands rather than one
        // SPH particle or persistent event per drop.
        float rainEnergy = clamp(Weather.x, 0.0, 1.0);
        float rainRippleA = stableWaveLayer(warp, DETAIL_GLASS_DIRECTION,
            17.3, SurfaceAnimationPhases1.y);
        float rainRippleB = stableWaveLayer(-warp, DETAIL_CROSS_DIRECTION,
            23.7, SurfaceAnimationPhases0.w);
        gradient += (DETAIL_GLASS_DIRECTION * rainRippleA
            + DETAIL_CROSS_DIRECTION * rainRippleB) * rainEnergy * 0.006;
    }
    if (quality >= 3.0) {
        float glassFrequency = 13.73;
        float glassRipple = stableWaveLayer(warp, DETAIL_GLASS_DIRECTION,
            glassFrequency, SurfaceAnimationPhases1.y);
        gradient += DETAIL_GLASS_DIRECTION * glassRipple * 0.005 * glassWeight
            * detailEnergy;
    }
    return normalize(vec3(gradient.x, 1.0, gradient.y));
}

vec3 reconstructViewPosition(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = InverseProjMat * clip;
    return view.xyz / max(abs(view.w), 0.00001);
}

float depthDiscontinuity(vec2 uv, float centerDepth) {
    vec2 texel = 1.0 / max(ScreenSize, vec2(1.0));
    float xDepth = texture(SceneDepth,
        clamp(uv + vec2(texel.x, 0.0), 0.0, 1.0)).r;
    float yDepth = texture(SceneDepth,
        clamp(uv + vec2(0.0, texel.y), 0.0, 1.0)).r;
    if (centerDepth >= 0.99998 || xDepth >= 0.99998 || yDepth >= 0.99998) {
        return centerDepth >= 0.99998 && xDepth >= 0.99998 && yDepth >= 0.99998
            ? 0.0
            : 1.0;
    }
    float centerDistance = max(1.0, -reconstructViewPosition(uv, centerDepth).z);
    float xDistance = -reconstructViewPosition(
        clamp(uv + vec2(texel.x, 0.0), 0.0, 1.0), xDepth).z;
    float yDistance = -reconstructViewPosition(
        clamp(uv + vec2(0.0, texel.y), 0.0, 1.0), yDepth).z;
    float dx = abs(xDistance - centerDistance) / centerDistance;
    float dy = abs(yDistance - centerDistance) / centerDistance;
    return max(dx, dy);
}

float screenEdgeConfidence(vec2 uv) {
    float edgeDistance = min(min(uv.x, 1.0 - uv.x), min(uv.y, 1.0 - uv.y));
    return smoothstep(0.003, 0.055, edgeDistance);
}

vec3 environmentReflection(vec3 reflectionDirection, float fresnel, float sea) {
    float rain = clamp(Weather.x, 0.0, 1.0);
    float thunder = clamp(Weather.y, 0.0, 1.0);
    float frozen = clamp(Weather.w, 0.0, 1.0);
    float overcast = clamp(rain * 0.74 + thunder * 0.55, 0.0, 1.0);
    vec3 nightSky = vec3(0.045, 0.075, 0.145);
    vec3 clearSky = mix(nightSky, max(EnvironmentColor.rgb, vec3(0.0)), celestialDaylight);
    float skyLuminance = dot(clearSky, vec3(0.2126, 0.7152, 0.0722));
    vec3 stormSky = mix(clearSky, vec3(skyLuminance), 0.48)
        * mix(1.0, 0.78, thunder);
    // Minecraft's sampled sky color already contains rain dimming. Apply only
    // the missing desaturation/storm response here instead of darkening twice.
    vec3 sky = mix(clearSky, stormSky, overcast);
    vec3 horizon = mix(max(FogColor.rgb, vec3(0.025)), sky, 0.62);
    float skyWeight = smoothstep(0.025, 0.62, fresnel + sea * 0.08);

    // A compact analytic sun/moon lobe gives the fallback reflection a stable
    // directional highlight even when screen-space tracing cannot see the sky.
    float lightAlignment = max(dot(normalize(reflectionDirection),
        normalize(celestialDirection)), 0.0);
    float opticalRoughness = clamp(
        sea * 0.62 + rain * 0.48 + thunder * 0.22 + frozen * 0.38,
        0.0,
        1.0
    );
    float lightDisc = pow(lightAlignment, mix(720.0, 76.0, opticalRoughness));
    float lightBloom = pow(lightAlignment, mix(46.0, 18.0, opticalRoughness)) * 0.10;
    vec3 celestialColor = mix(vec3(0.42, 0.54, 0.82),
        vec3(1.00, 0.86, 0.62), celestialDaylight);
    float weatherVisibility = 1.0 - clamp(rain * 0.68 + thunder * 0.52, 0.0, 0.94);
    return max(mix(horizon, sky, skyWeight)
        + celestialColor * (lightDisc + lightBloom) * weatherVisibility, vec3(0.0));
}

vec4 traceScreenReflection(vec3 origin, vec3 direction, int stepCount, float maxDistance) {
    if (stepCount <= 0 || maxDistance <= 0.0 || SceneCaptureValid < 0.5) {
        return vec4(0.0);
    }
    float stride = maxDistance / float(stepCount);
    vec3 ray = origin + direction * 0.10;
    vec3 previousRay = ray;
    float previousSeparation = 0.0;
    bool hasFrontSample = false;
    for (int stepIndex = 0; stepIndex < 24; stepIndex++) {
        if (stepIndex >= stepCount) {
            break;
        }
        ray += direction * stride;
        vec4 projected = ProjMat * vec4(ray, 1.0);
        if (projected.w <= 0.0001) {
            break;
        }
        vec2 uv = projected.xy / projected.w * 0.5 + 0.5;
        if (any(lessThan(uv, vec2(0.002))) || any(greaterThan(uv, vec2(0.998)))) {
            break;
        }
        float sampledDepth = texture(SceneDepth, uv).r;
        if (sampledDepth >= 0.99998) {
            continue;
        }
        vec3 sampledView = reconstructViewPosition(uv, sampledDepth);
        float separation = (-ray.z) - (-sampledView.z);
        float crossingTolerance = 0.12 + stride * max(abs(direction.z), 0.18) * 1.35;
        if (separation < 0.0) {
            previousRay = ray;
            previousSeparation = separation;
            hasFrontSample = true;
            continue;
        }
        if (!hasFrontSample || separation > crossingTolerance
                || previousSeparation < -crossingTolerance) {
            hasFrontSample = false;
            continue;
        }

        // Four fixed refinement steps keep the trace bounded on GLSL 150 while
        // removing most of the coarse popping from the configurable march.
        vec3 frontRay = previousRay;
        vec3 backRay = ray;
        vec2 hitUv = uv;
        float hitDepth = sampledDepth;
        float hitSeparation = separation;
        for (int refinementIndex = 0; refinementIndex < 4; refinementIndex++) {
            vec3 midpoint = (frontRay + backRay) * 0.5;
            vec4 midpointProjected = ProjMat * vec4(midpoint, 1.0);
            if (midpointProjected.w <= 0.0001) {
                break;
            }
            vec2 midpointUv = midpointProjected.xy / midpointProjected.w * 0.5 + 0.5;
            if (any(lessThan(midpointUv, vec2(0.002)))
                    || any(greaterThan(midpointUv, vec2(0.998)))) {
                break;
            }
            float midpointDepth = texture(SceneDepth, midpointUv).r;
            if (midpointDepth >= 0.99998) {
                frontRay = midpoint;
                continue;
            }
            vec3 midpointView = reconstructViewPosition(midpointUv, midpointDepth);
            float midpointSeparation = (-midpoint.z) - (-midpointView.z);
            if (midpointSeparation >= 0.0) {
                backRay = midpoint;
                hitUv = midpointUv;
                hitDepth = midpointDepth;
                hitSeparation = midpointSeparation;
            } else {
                frontRay = midpoint;
            }
        }

        float traveled = length(backRay - origin);
        float residualConfidence = 1.0 - smoothstep(0.025, crossingTolerance,
            abs(hitSeparation));
        float continuityConfidence = 1.0 - smoothstep(0.008, 0.10,
            depthDiscontinuity(hitUv, hitDepth));
        float distanceConfidence = 1.0 - smoothstep(0.72, 1.0,
            traveled / max(maxDistance, 0.001));
        float directionConfidence = smoothstep(0.035, 0.24, abs(direction.z));
        float hitConfidence = screenEdgeConfidence(hitUv)
            * residualConfidence * continuityConfidence
            * distanceConfidence * directionConfidence;
        return vec4(texture(SceneColor, hitUv).rgb, clamp(hitConfidence, 0.0, 1.0));
    }
    return vec4(0.0);
}

void main() {
    float sea = clamp(regionalSeaState, 0.0, 1.0);
    float frozen = clamp(Weather.w, 0.0, 1.0);
    vec3 baseWorldNormal = normalize(worldNormal);
    vec3 microWorldNormal = proceduralWorldNormal(localCurrent, sea);
    float continuousSurface = smoothstep(0.10, 0.70, surfaceContinuity);
    vec3 combinedWorldNormal = normalize(mix(baseWorldNormal, microWorldNormal,
        (0.30 + sea * 0.18) * continuousSurface * (1.0 - frozen * 0.90)));
    vec3 normal = normalize(mat3(ModelViewMat) * combinedWorldNormal);
    vec3 viewDirection = normalize(-viewPosition);
    float facing = clamp(dot(normal, viewDirection), 0.0, 1.0);
    float fresnel = 0.0204 + (1.0 - 0.0204) * pow(1.0 - facing, 5.0);

    vec2 screenUv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
    bool capturedScene = SceneCaptureValid > 0.5;
    float sceneDepth = capturedScene ? texture(SceneDepth, screenUv).r : 1.0;
    bool sceneHasDepth = capturedScene && sceneDepth < 0.99998;
    vec3 sampledSceneView = sceneHasDepth
        ? reconstructViewPosition(screenUv, sceneDepth)
        : viewPosition;
    float surfaceViewDepth = max(0.0, -viewPosition.z);
    float sceneViewDepth = max(0.0, -sampledSceneView.z);

    // The custom pass can target a separate translucent framebuffer whose
    // hardware depth does not always retain every opaque terrain sample. The
    // captured scene is the authoritative visibility source: reject a wave
    // behind foreground terrain before it can replace that terrain's color.
    // A small view-space tolerance treats nearly coincident fallback water as
    // the same surface and scales gently with distance to avoid grazing-angle
    // shimmer without letting full terrain blocks leak through.
    float depthTolerance = max(0.015, surfaceViewDepth * 0.0005);
    if (sceneHasDepth && sceneViewDepth + depthTolerance < surfaceViewDepth) {
        discard;
    }

    bool validDepth = sceneHasDepth
        && sceneViewDepth > surfaceViewDepth + depthTolerance;
    vec3 sceneView = validDepth ? sampledSceneView : viewPosition;
    vec3 incidentRay = normalize(viewPosition);
    float centerThickness = validDepth
        ? clamp(dot(sceneView - viewPosition, incidentRay), 0.0, 64.0)
        : clamp(vertexColor.a * 3.0, 0.35, 3.0);
    float thickness = centerThickness;

    float screenEdgeFade = screenEdgeConfidence(screenUv);
    float silhouetteFade = validDepth
        ? 1.0 - smoothstep(0.008, 0.10, depthDiscontinuity(screenUv, sceneDepth))
        : 0.0;
    float depthFade = smoothstep(0.05, 1.75, centerThickness);
    vec2 refractedUv = screenUv;
    vec3 refractedRay = incidentRay;

    if (validDepth && OpticalQuality.y > 0.0001) {
        // Snell's law bends the view ray at the air/water interface. The
        // configured strength scales the bend itself, so the sampled UV and
        // Beer-Lambert travel distance remain part of the same optical ray.
        const float AIR_TO_WATER_ETA = 1.0 / 1.333;
        vec3 interfaceNormal = faceforward(normal, incidentRay, normal);
        vec3 snellRay = refract(incidentRay, interfaceNormal, AIR_TO_WATER_ETA);
        if (dot(snellRay, snellRay) > 0.000001) {
            // Strength controls the approach to the physical Snell ray. Values
            // above one must not extrapolate past that ray into non-physical bends.
            float bendStrength = clamp(OpticalQuality.y, 0.0, 1.0)
                * screenEdgeFade * silhouetteFade * depthFade;
            refractedRay = normalize(incidentRay + (snellRay - incidentRay) * bendStrength);

            // Start at the center-depth plane, then perform one bounded
            // correction against the depth sampled by the refracted screen ray.
            float zDenominator = refractedRay.z;
            float travelGuess = abs(zDenominator) > 0.0001
                ? (sceneView.z - viewPosition.z) / zDenominator
                : centerThickness;
            travelGuess = clamp(travelGuess, 0.05, 64.0);
            vec3 refractedEndpoint = viewPosition + refractedRay * travelGuess;
            vec4 refractedProjection = ProjMat * vec4(refractedEndpoint, 1.0);
            if (refractedProjection.w > 0.0001) {
                vec2 candidateUv = refractedProjection.xy / refractedProjection.w * 0.5 + 0.5;
                if (all(greaterThan(candidateUv, vec2(0.002)))
                        && all(lessThan(candidateUv, vec2(0.998)))) {
                    float candidateDepth = texture(SceneDepth, candidateUv).r;
                    if (candidateDepth < 0.99998) {
                        vec3 candidateView = reconstructViewPosition(candidateUv, candidateDepth);
                        vec3 candidateOffset = candidateView - viewPosition;
                        float candidateTravel = dot(candidateOffset, refractedRay);
                        float candidateMiss = length(
                            candidateOffset - refractedRay * candidateTravel);
                        bool candidateBehindSurface = -candidateView.z > -viewPosition.z + 0.001;
                        float candidateMissLimit = max(0.08, candidateTravel * 0.018);
                        if (candidateBehindSurface && candidateTravel > 0.02
                                && candidateMiss <= candidateMissLimit) {
                            vec3 correctedEndpoint = viewPosition + refractedRay
                                * clamp(candidateTravel, 0.02, 64.0);
                            vec4 correctedProjection = ProjMat * vec4(correctedEndpoint, 1.0);
                            if (correctedProjection.w > 0.0001) {
                                vec2 correctedUv = correctedProjection.xy
                                    / correctedProjection.w * 0.5 + 0.5;
                                if (all(greaterThan(correctedUv, vec2(0.002)))
                                        && all(lessThan(correctedUv, vec2(0.998)))) {
                                    float correctedDepth = texture(SceneDepth, correctedUv).r;
                                    if (correctedDepth < 0.99998) {
                                        vec3 correctedView = reconstructViewPosition(
                                            correctedUv, correctedDepth);
                                        vec3 correctedOffset = correctedView - viewPosition;
                                        float correctedTravel = dot(correctedOffset, refractedRay);
                                        float correctedMiss = length(
                                            correctedOffset - refractedRay * correctedTravel);
                                        if (-correctedView.z > -viewPosition.z + 0.001
                                                && correctedTravel > 0.02
                                                && correctedMiss <= max(
                                                    0.08, correctedTravel * 0.018)) {
                                            candidateUv = correctedUv;
                                            candidateTravel = correctedTravel;
                                        }
                                    }
                                }
                            }
                            refractedUv = candidateUv;
                            thickness = clamp(candidateTravel, 0.0, 64.0);
                        }
                    }
                }
            }
        }
    }

    vec3 sceneColor = capturedScene
        ? texture(SceneColor, refractedUv).rgb
        : vertexColor.rgb;
    vec3 oceanAbsorption = vec3(1.0, 0.92, 0.86);
    vec3 riverAbsorption = vec3(0.82, 1.10, 1.34);
    vec3 lakeAbsorption = vec3(0.91, 1.02, 1.18);
    vec3 bodyAbsorption = oceanAbsorption * waterBodyBlend.x
        + riverAbsorption * waterBodyBlend.y
        + lakeAbsorption * waterBodyBlend.z;
    vec3 biomeAbsorption = mix(vec3(1.0),
        clamp(vec3(1.35) - vertexColor.rgb, vec3(0.65), vec3(1.55)), 0.32);
    float opacityControl = clamp((SurfaceOpacityStrength - 0.5) / 1.5, 0.0, 1.0);
    float opticalDensityScale = mix(0.70, 1.30, opacityControl);
    vec3 effectiveAbsorption = max(
        AbsorptionCoefficients * bodyAbsorption * biomeAbsorption * opticalDensityScale,
        vec3(0.0001)
    );
    vec3 transmission = exp(-effectiveAbsorption * thickness);
    vec3 deepBodyColor = vec3(0.010, 0.145, 0.310) * waterBodyBlend.x
        + vec3(0.025, 0.205, 0.235) * waterBodyBlend.y
        + vec3(0.020, 0.175, 0.260) * waterBodyBlend.z;
    vec3 absorbedColor = mix(
        vertexColor.rgb, deepBodyColor, smoothstep(2.0, 24.0, thickness));

    vec3 reflectedRay = normalize(reflect(incidentRay, normal));
    vec3 reflectedColor = environmentReflection(reflectedRay, fresnel, sea);
    int ssrSteps = int(clamp(OpticalQuality.z, 0.0, 24.0));
    // Trace just above physical water F0. Reflected energy is still multiplied
    // by Fresnel below, so this improves scene identity without turning a
    // face-on clear surface into a mirror.
    if (ssrSteps > 0 && capturedScene && fresnel > 0.0205) {
        vec4 ssr = traceScreenReflection(
            viewPosition, reflectedRay, ssrSteps, OpticalQuality.w);
        reflectedColor = mix(
            reflectedColor, ssr.rgb, ssr.a * (0.66 + fresnel * 0.30));
    }

    float slope = clamp(1.0 - combinedWorldNormal.y, 0.0, 1.0);
    float normalizedWaveSlope = clamp(waveSlope / 2.0, 0.0, 1.0);
    float breakingEnergy = clamp(
        regionalSpectrumState.w * 0.70
        + sea * 0.45
        + clamp(regionalWindSpeed / 16.0, 0.0, 1.0) * 0.30,
        0.0,
        1.0
    );
    // A tilted calm surface is still clear water. Broad slope foam is admitted
    // only once synchronized wind/sea energy approaches breaking conditions.
    float rawSlopeFoam = smoothstep(0.025, 0.14 + sea * 0.04, slope);
    float slopeFoam = rawSlopeFoam * smoothstep(0.16, 0.70, breakingEnergy);
    // Compressed, steep crests can whiten in swell without requiring a storm.
    // Keep broad slope foam weather-gated so flat calm water remains clear.
    float crestFoam = smoothstep(0.08, 0.48, crestCompression)
        * (0.40 + 0.60 * smoothstep(0.12, 0.65, breakingEnergy))
        * smoothstep(0.025, 0.24, normalizedWaveSlope);
    float foamPattern = smoothstep(-0.55, 0.60,
        sin(stableWorldPhase(vec2(2.73, 1.91)) - SurfaceAnimationPhases1.z)
        * cos(stableWorldPhase(vec2(-1.37, 3.17)) + SurfaceAnimationPhases1.w));
    crestFoam *= 0.48 + foamPattern * 0.52;
    float currentSpeed = length(localCurrent);
    float shallowWater = 1.0 - clamp(depthFactor, 0.0, 1.0);
    // Shore proximity is a deterministic client snapshot-boundary/depth
    // approximation. regionalSpectrumState.w is the synchronized server breaking cue;
    // this does not claim to synchronize the separate shallow-water grid.
    float shoreBreaker = shoreFactor * shallowWater * 0.12;
    float currentShear = 0.0;
    if (shoreFactor > 0.001 && OpticalQuality.x >= 1.0) {
        float breakerPhase = 0.5 + 0.5 * sin(
            stableWorldPhase(SHORE_BREAK_DIRECTION * 1.82)
            - SurfaceAnimationPhases1.z
        );
        shoreBreaker = shoreFactor
            * (0.22 + regionalSpectrumState.w * 0.78)
            * smoothstep(0.34, 0.82, breakerPhase + shallowWater * 0.28);
        if (OpticalQuality.x >= 2.0 && currentSpeed > 0.001) {
            currentShear = shoreFactor
                * clamp(currentSpeed / 1.6, 0.0, 1.0)
                * smoothstep(0.48, 0.92, 0.5 + 0.5 * sin(
                    stableWorldPhase(SHORE_SHEAR_DIRECTION * 3.4)
                    + SurfaceAnimationPhases1.w
                ));
        }
    }
    float impulseFoam = disturbanceStrength * (0.34 + sea * 0.26);
    float foam = clamp(max(max(slopeFoam, crestFoam), max(shoreBreaker, currentShear))
        + impulseFoam, 0.0, 1.0)
        * (1.0 - frozen * 0.88);
    vec3 halfVector = celestialDirection + viewDirection;
    vec3 halfDirection = dot(halfVector, halfVector) > 0.000001
        ? normalize(halfVector)
        : normal;
    float celestialSpecular = pow(max(dot(normal, halfDirection), 0.0), mix(120.0, 54.0, sea));
    vec3 celestialColor = mix(vec3(0.42, 0.52, 0.75), vec3(1.00, 0.88, 0.67), celestialDaylight);

    // Partition energy before normalizing the material response. Reflection
    // owns Fresnel energy once, while in-water scattering owns only the light
    // actually removed from transmission. This keeps clear water reflective
    // instead of accidentally reducing normal-incidence highlights to F^2.
    vec3 waterLighting = max(lightColor, vec3(0.62));
    vec3 mediumColor = absorbedColor * waterLighting * ColorModulator.rgb;
    vec3 reflectedRadiance = (
        reflectedColor
        + celestialColor * celestialSpecular * (0.24 + sea * 0.25)
    ) * ColorModulator.rgb;
    vec3 waterMaterialWeight = vec3(1.0) - transmission * (1.0 - fresnel);
    vec3 waterMaterialNumerator =
        mediumColor * (vec3(1.0) - transmission) * (1.0 - fresnel)
        + reflectedRadiance * fresnel;

    // Foam is a real, optically thick surface layer. It replaces a bounded
    // fraction of both transmitted scene light and the underlying water rather
    // than merely tinting a low-opacity material contribution.
    float foamCoverage = clamp(foam * (0.58 + sea * 0.24), 0.0, 0.86);
    vec3 foamColor = vec3(0.84, 0.94, 1.0) * waterLighting * ColorModulator.rgb;
    vec3 materialWeight = waterMaterialWeight * (1.0 - foamCoverage)
        + vec3(foamCoverage);
    vec3 materialNumerator = waterMaterialNumerator * (1.0 - foamCoverage)
        + foamColor * foamCoverage;
    vec3 materialColor = materialNumerator / max(materialWeight, vec3(0.0001));
    float iceCoverage = smoothstep(0.34, 0.88, frozen);
    vec3 iceColor = vec3(0.72, 0.86, 0.94) * waterLighting * ColorModulator.rgb;
    materialColor = mix(materialColor, iceColor, iceCoverage * 0.82);
    materialWeight = mix(materialWeight, vec3(0.94), iceCoverage * 0.86);

    float fogRange = max(0.001, FogEnd - FogStart);
    float fogFactor = clamp((vertexDistance - FogStart) / fogRange, 0.0, 1.0);
    vec3 foggedMaterial = mix(materialColor, FogColor.rgb, fogFactor);
    vec3 transmittedScene = sceneColor * transmission * (1.0 - fresnel)
        * (1.0 - foamCoverage);
    transmittedScene *= 1.0 - iceCoverage * 0.88;
    vec3 color = transmittedScene + foggedMaterial * materialWeight;

    float mediumOpacity = 1.0 - dot(transmission, vec3(0.2126, 0.7152, 0.0722));
    float fallbackAlpha = clamp(
        fresnel + (1.0 - fresnel) * (0.10 + mediumOpacity * 0.82),
        0.10,
        0.96
    ) * ColorModulator.a * mix(0.78, 1.28, opacityControl);
    fallbackAlpha = min(0.98, fallbackAlpha + foam * 0.08);
    fallbackAlpha = min(0.995, fallbackAlpha + iceCoverage * 0.72);
    float loadedFrontier = smoothstep(0.08, 0.55, surfaceContinuity);

    if (capturedScene) {
        // Refraction/transmission already incorporate the captured background.
        // Resolve frontier coverage inside this completed color and replace the
        // framebuffer pixel opaquely, avoiding a second alpha composition.
        vec3 capturedBackground = texture(SceneColor, screenUv).rgb;
        float captureCoverage = clamp(loadedFrontier * ColorModulator.a, 0.0, 1.0);
        vec3 completedColor = mix(capturedBackground, color, captureCoverage);
        fragColor = vec4(completedColor, 1.0);
    } else {
        // Resource reloads and unsupported scene capture still retain a safe
        // conventional translucent path. The stock water sprite may color this
        // fallback, but its alpha never owns custom-surface geometry coverage.
        vec3 fallbackTexture = texture(Sampler0, texCoord0).rgb * vertexColor.rgb;
        vec3 fallbackEnvironment = environmentReflection(reflectedRay, fresnel, sea);
        vec3 fallbackColor = mix(fallbackEnvironment, color, loadedFrontier);
        fallbackColor = mix(fallbackTexture, fallbackColor, loadedFrontier);
        fallbackColor = mix(fallbackColor, FogColor.rgb, fogFactor);
        fallbackAlpha *= mix(0.18, 1.0, loadedFrontier);
        fragColor = vec4(fallbackColor, fallbackAlpha);
    }
}
