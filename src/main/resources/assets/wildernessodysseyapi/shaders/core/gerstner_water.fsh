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
uniform vec4 TimeFrameLow;
uniform vec3 TimeFrameHigh;
uniform float SeaState;
uniform vec2 WindDirection;
uniform vec4 SpectrumState;
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

out vec4 fragColor;

const float TWO_PI = 6.28318530718;
const float PHASE_CHUNK_SPAN = 16.0;
const float PHASE_COARSE_CHUNKS = 1024.0;

float stableTimePhase(float radiansPerSecond) {
    float phaseStep = mod(radiansPerSecond / 20.0, TWO_PI);
    float phase = mod(TimeFrameLow.x * phaseStep, TWO_PI);
    phaseStep = mod(phaseStep * 1024.0, TWO_PI);
    phase = mod(phase + TimeFrameLow.y * phaseStep, TWO_PI);
    phaseStep = mod(phaseStep * 1024.0, TWO_PI);
    phase = mod(phase + TimeFrameLow.z * phaseStep, TWO_PI);
    phaseStep = mod(phaseStep * 1024.0, TWO_PI);
    phase = mod(phase + TimeFrameLow.w * phaseStep, TWO_PI);
    phaseStep = mod(phaseStep * 1024.0, TWO_PI);
    phase = mod(phase + TimeFrameHigh.x * phaseStep, TWO_PI);
    phaseStep = mod(phaseStep * 1024.0, TWO_PI);
    phase = mod(phase + TimeFrameHigh.y * phaseStep, TWO_PI);
    phaseStep = mod(phaseStep * 1024.0, TWO_PI);
    return mod(phase + TimeFrameHigh.z * phaseStep, TWO_PI);
}

// Mirrors the vertex-stage origin split so fragment detail retains sub-block
// phase precision near Minecraft's world border instead of quantizing against
// a multi-million-block interpolated world position.
float stableWorldPhase(vec2 coefficient) {
    // Displacement can carry an interpolated point beyond its source chunk.
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

float currentAdvectionPhase(vec2 coefficient, vec2 current) {
    return dot(current, current) > 0.000001
        ? stableTimePhase(-dot(current, coefficient) * 1.35)
        : 0.0;
}

float animatedStablePhase(vec2 coefficient, vec2 current, float basePhase) {
    return mod(stableWorldPhase(coefficient) + basePhase
        + currentAdvectionPhase(coefficient, current), TWO_PI);
}

float stableWaveLayer(vec2 localOffset, vec2 current, vec2 direction,
                      float frequency, float basePhase) {
    vec2 coefficient = direction * frequency;
    return sin(mod(animatedStablePhase(coefficient, current, basePhase)
        + dot(localOffset, coefficient), TWO_PI));
}

vec2 normalizedOr(vec2 direction, vec2 fallbackDirection) {
    float directionLength = length(direction);
    return directionLength > 0.000001 ? direction / directionLength : fallbackDirection;
}

vec3 proceduralWorldNormal(vec2 current, float sea) {
    vec2 wind = normalize(WindDirection + vec2(0.0001, 0.0));
    vec2 crossWind = vec2(-wind.y, wind.x);
    vec2 diagonal = normalize(wind * 0.73 + crossWind * 0.61);
    float quality = clamp(OpticalQuality.x, 0.0, 3.0);
    if (quality < 0.5) {
        float lowRipple = sin(animatedStablePhase(wind * 1.37, current,
            SurfaceAnimationPhases0.z));
        vec2 lowGradient = wind * lowRipple * 0.014;
        return normalize(vec3(lowGradient.x, 1.0, lowGradient.y));
    }
    // Low-frequency domain warping prevents the small normal layers from
    // resolving into a repeating checkerboard. Canonical current is folded
    // into phase velocity so it remains visible without rebuilding geometry.
    vec2 warp = vec2(
        sin(animatedStablePhase(
            vec2(0.071, 0.113), current, SurfaceAnimationPhases0.x)),
        cos(animatedStablePhase(
            vec2(-0.097, 0.059), current, SurfaceAnimationPhases0.y))
    ) * (0.42 + sea * 0.48);
    float longRipple = stableWaveLayer(warp, current, wind,
        1.37, SurfaceAnimationPhases0.z);
    float crossRipple = stableWaveLayer(warp, current, crossWind,
        2.73, SurfaceAnimationPhases0.w);
    vec2 gradient = wind * longRipple * 0.016
        + crossWind * crossRipple * 0.012;
    if (quality >= 2.0) {
        float capillary = stableWaveLayer(warp, current, diagonal,
            6.91, SurfaceAnimationPhases1.x);
        gradient += wind * capillary * 0.008 + diagonal * capillary * 0.007;
    }
    if (quality >= 3.0) {
        vec2 glassDirection = normalize(wind * 0.58 - crossWind * 0.81);
        float glassFrequency = 13.73;
        float glassRipple = stableWaveLayer(warp, current, glassDirection,
            glassFrequency, SurfaceAnimationPhases1.y);
        gradient += diagonal * glassRipple * 0.005;
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
    float opticalRoughness = clamp(sea * 0.62 + rain * 0.48 + thunder * 0.22, 0.0, 1.0);
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
    vec4 waterTexture = texture(Sampler0, texCoord0);
    if (waterTexture.a < 0.03) {
        discard;
    }

    float sea = clamp(SeaState, 0.0, 1.0);
    vec3 baseWorldNormal = normalize(worldNormal);
    vec3 microWorldNormal = proceduralWorldNormal(localCurrent, sea);
    float continuousSurface = smoothstep(0.10, 0.70, surfaceContinuity);
    vec3 combinedWorldNormal = normalize(mix(baseWorldNormal, microWorldNormal,
        (0.30 + sea * 0.18) * continuousSurface));
    vec3 normal = normalize(mat3(ModelViewMat) * combinedWorldNormal);
    vec3 viewDirection = normalize(-viewPosition);
    float facing = clamp(dot(normal, viewDirection), 0.0, 1.0);
    float fresnel = 0.0204 + (1.0 - 0.0204) * pow(1.0 - facing, 5.0);

    vec2 screenUv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
    bool capturedScene = SceneCaptureValid > 0.5;
    float sceneDepth = capturedScene ? texture(SceneDepth, screenUv).r : 1.0;
    bool validDepth = capturedScene && sceneDepth < 0.99998
        && sceneDepth > gl_FragCoord.z + 0.00001;
    vec3 sceneView = validDepth ? reconstructViewPosition(screenUv, sceneDepth) : viewPosition;
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
    if (ssrSteps > 0 && capturedScene && fresnel > 0.024) {
        vec4 ssr = traceScreenReflection(
            viewPosition, reflectedRay, ssrSteps, OpticalQuality.w);
        reflectedColor = mix(
            reflectedColor, ssr.rgb, ssr.a * (0.58 + fresnel * 0.32));
    }

    float slope = clamp(1.0 - combinedWorldNormal.y, 0.0, 1.0);
    float slopeFoam = smoothstep(0.025, 0.14 + sea * 0.04, slope);
    float currentSpeed = length(localCurrent);
    vec2 foamDirection = normalizedOr(localCurrent,
        normalizedOr(WindDirection, vec2(1.0, 0.0)));
    float shallowWater = 1.0 - clamp(depthFactor, 0.0, 1.0);
    // Shore proximity is a deterministic client snapshot-boundary/depth
    // approximation. SpectrumState.w is the synchronized server breaking cue;
    // this does not claim to synchronize the separate shallow-water grid.
    float shoreBreaker = shoreFactor * shallowWater * 0.12;
    float currentShear = 0.0;
    if (shoreFactor > 0.001 && OpticalQuality.x >= 1.0) {
        float breakerPhase = 0.5 + 0.5 * sin(
            stableWorldPhase(foamDirection * (1.65 + currentSpeed * 0.35))
            - SurfaceAnimationPhases1.z
            - (currentSpeed > 0.001 ? stableTimePhase(currentSpeed * 2.4) : 0.0)
        );
        shoreBreaker = shoreFactor
            * (0.22 + SpectrumState.w * 0.78)
            * smoothstep(0.34, 0.82, breakerPhase + shallowWater * 0.28);
        if (OpticalQuality.x >= 2.0 && currentSpeed > 0.001) {
            currentShear = shoreFactor
                * clamp(currentSpeed / 1.6, 0.0, 1.0)
                * smoothstep(0.48, 0.92, 0.5 + 0.5 * sin(
                    stableWorldPhase(vec2(-foamDirection.y, foamDirection.x) * 3.4)
                    + SurfaceAnimationPhases1.w
                ));
        }
    }
    float impulseFoam = disturbanceStrength * (0.34 + sea * 0.26);
    float foam = clamp(max(slopeFoam, max(shoreBreaker, currentShear)) + impulseFoam, 0.0, 1.0);
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
    float foamCoverage = clamp(foam * (0.16 + sea * 0.30), 0.0, 0.72);
    vec3 foamColor = vec3(0.84, 0.94, 1.0) * waterLighting * ColorModulator.rgb;
    vec3 materialWeight = waterMaterialWeight * (1.0 - foamCoverage)
        + vec3(foamCoverage);
    vec3 materialNumerator = waterMaterialNumerator * (1.0 - foamCoverage)
        + foamColor * foamCoverage;
    vec3 materialColor = materialNumerator / max(materialWeight, vec3(0.0001));

    float fogRange = max(0.001, FogEnd - FogStart);
    float fogFactor = clamp((vertexDistance - FogStart) / fogRange, 0.0, 1.0);
    vec3 foggedMaterial = mix(materialColor, FogColor.rgb, fogFactor);
    vec3 transmittedScene = sceneColor * transmission * (1.0 - fresnel)
        * (1.0 - foamCoverage);
    vec3 color = transmittedScene + foggedMaterial * materialWeight;

    float mediumOpacity = 1.0 - dot(transmission, vec3(0.2126, 0.7152, 0.0722));
    float fallbackAlpha = clamp(
        fresnel + (1.0 - fresnel) * (0.10 + mediumOpacity * 0.82),
        0.10,
        0.96
    ) * ColorModulator.a * mix(0.78, 1.28, opacityControl);
    fallbackAlpha = min(0.98, fallbackAlpha + foam * 0.08);
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
        // conventional translucent path instead of sampling invalid textures.
        vec3 fallbackEnvironment = environmentReflection(reflectedRay, fresnel, sea);
        vec3 fallbackColor = mix(fallbackEnvironment, color, loadedFrontier);
        fallbackColor = mix(fallbackColor, FogColor.rgb, fogFactor);
        fallbackAlpha *= mix(0.18, 1.0, loadedFrontier);
        fragColor = vec4(fallbackColor, fallbackAlpha);
    }
}
