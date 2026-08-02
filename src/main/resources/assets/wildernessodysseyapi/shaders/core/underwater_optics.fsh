#version 150

uniform sampler2D Sampler0;
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform vec4 ColorModulator;
uniform vec4 DistortionPhases;
uniform vec4 CausticPhases0;
uniform vec3 CausticPhases1;
uniform vec2 FallbackPhases;
uniform float Submersion;
uniform float Clarity;
uniform float CausticStrength;
uniform float DistortionStrength;
uniform float EffectQuality;
uniform vec3 WaterFogColor;
uniform vec2 ScreenSize;
uniform float SceneCaptureValid;
uniform mat4 InverseProjMat;
uniform mat4 ViewToWorldMat;
uniform vec3 CameraAnchor;
uniform vec3 SunDirection;
uniform float CameraDepth;
uniform float VisibilityBlocks;
uniform vec3 AbsorptionCoefficients;
uniform float ScatteringCoefficient;

in vec2 texCoord0;

out vec4 fragColor;

const float PI = 3.141592653589793;
const float WORLD_REPEAT = 4096.0;
const float PERIODIC_PHASE = (2.0 * PI) / WORLD_REPEAT;

vec3 reconstructViewPosition(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = InverseProjMat * clip;
    return view.xyz / max(abs(view.w), 0.00001);
}

vec3 viewToWorldDirection(vec3 viewDirection) {
    return normalize((ViewToWorldMat * vec4(viewDirection, 0.0)).xyz);
}

bool containsSceneGeometry(float depth) {
    return depth > 0.00001 && depth < 0.99998;
}

float sceneDistance(vec2 uv, float depth) {
    return containsSceneGeometry(depth)
        ? length(reconstructViewPosition(uv, depth))
        : max(6.0, VisibilityBlocks);
}

float depthDiscontinuity(vec2 uv, float centerDistance) {
    vec2 texel = 1.0 / max(ScreenSize, vec2(1.0));
    vec2 xUv = clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0));
    vec2 yUv = clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0));
    float xDistance = sceneDistance(xUv, texture(SceneDepth, xUv).r);
    float yDistance = sceneDistance(yUv, texture(SceneDepth, yUv).r);
    float scale = max(1.0, centerDistance);
    return max(abs(xDistance - centerDistance), abs(yDistance - centerDistance)) / scale;
}

// Integer cycle vectors make every field exactly periodic across the wrapped
// camera anchor. Caustics therefore remain stable even near the world border.
float periodicWave(vec2 worldPosition, vec2 cycles, float phase) {
    return sin(dot(worldPosition, cycles) * PERIODIC_PHASE + phase);
}

vec2 projectedLightPlane(vec3 worldPosition, vec3 lightDirection) {
    float safeVertical = max(0.18, abs(lightDirection.y));
    return worldPosition.xz - lightDirection.xz * (worldPosition.y / safeVertical);
}

vec2 anchoredSurfaceDistortion(vec3 worldRay) {
    float surfaceDistance = max(0.02, CameraDepth) / max(0.08, worldRay.y);
    surfaceDistance = clamp(surfaceDistance, 0.0, 32.0);
    vec2 surfacePosition = CameraAnchor.xz + worldRay.xz * surfaceDistance;
    float broad = periodicWave(surfacePosition, vec2(431.0, 197.0), DistortionPhases.x);
    float cross = periodicWave(surfacePosition, vec2(-263.0, 509.0), DistortionPhases.y);
    return vec2(
        periodicWave(surfacePosition, vec2(1511.0, 827.0), DistortionPhases.z + cross * 0.38),
        periodicWave(surfacePosition, vec2(-1093.0, 1741.0), DistortionPhases.w + broad * 0.34)
    );
}

float worldCausticField(vec3 worldPosition, vec3 lightDirection) {
    vec2 projected = projectedLightPlane(worldPosition, lightDirection);
    float warpA = periodicWave(projected, vec2(223.0, 431.0), CausticPhases0.x);
    float warpB = periodicWave(projected, vec2(-379.0, 157.0), CausticPhases0.y);
    float focusA = periodicWave(
        projected,
        vec2(3691.0, 2179.0),
        CausticPhases0.z + warpB * 0.46
    );
    float focusB = periodicWave(
        projected,
        vec2(-2477.0, 3907.0),
        CausticPhases0.w + warpA * 0.42
    );
    float focusC = periodicWave(
        projected,
        vec2(4513.0, -1489.0),
        CausticPhases1.x + (warpA - warpB) * 0.24
    );
    float convergence = abs(focusA + focusB * 0.82 + focusC * 0.34);
    return pow(clamp(1.0 - convergence * 0.47, 0.0, 1.0), 4.5);
}

float shaftField(vec3 worldPosition, vec3 lightDirection) {
    vec2 projected = projectedLightPlane(worldPosition, lightDirection);
    float broadA = 0.5 + 0.5 * periodicWave(
        projected,
        vec2(173.0, 79.0),
        CausticPhases1.y
    );
    float broadB = 0.5 + 0.5 * periodicWave(
        projected,
        vec2(-83.0, 211.0),
        CausticPhases1.z
    );
    return clamp(pow(broadA, 7.0) * 0.72 + pow(broadB, 9.0) * 0.48, 0.0, 1.0);
}

float integrateSunShafts(vec3 worldRay, float travelDistance,
                         vec3 lightDirection, int sampleCount) {
    float boundedTravel = clamp(travelDistance, 0.0, max(6.0, VisibilityBlocks));
    float integrated = 0.0;
    for (int index = 0; index < 4; index++) {
        if (index >= sampleCount) {
            break;
        }
        float sampleFraction = (float(index) + 0.5) / float(sampleCount);
        vec3 samplePosition = CameraAnchor + worldRay * (boundedTravel * sampleFraction);
        integrated += shaftField(samplePosition, lightDirection)
            * (1.0 - sampleFraction * 0.28);
    }
    integrated /= float(max(sampleCount, 1));

    float lookingTowardLight = pow(clamp(dot(worldRay, lightDirection), 0.0, 1.0), 4.0);
    float scatteringBuild = 1.0 - exp(-max(0.0001, ScatteringCoefficient) * boundedTravel);
    float cameraDepthFade = exp(-max(0.0, CameraDepth) * 0.035);
    return integrated * mix(0.28, 1.0, lookingTowardLight)
        * scatteringBuild * cameraDepthFade;
}

vec3 reconstructedSceneNormal(vec3 worldPosition, vec3 worldRay) {
    vec3 normal = cross(dFdx(worldPosition), dFdy(worldPosition));
    if (dot(normal, normal) < 0.000001) {
        return vec3(0.0, 1.0, 0.0);
    }
    normal = normalize(normal);
    return dot(normal, -worldRay) < 0.0 ? -normal : normal;
}

void main() {
    float submersion = clamp(Submersion, 0.0, 1.0);
    float clarity = clamp(Clarity, 0.0, 1.0);
    float effectQuality = clamp(EffectQuality, 0.0, 3.0);

    if (SceneCaptureValid > 0.5) {
        vec2 screenUv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
        vec3 farView = reconstructViewPosition(screenUv, 0.99998);
        vec3 viewRay = normalize(farView);
        vec3 worldRay = viewToWorldDirection(viewRay);
        vec3 lightDirection = normalize(SunDirection);
        float sunlight = smoothstep(0.015, 0.18, lightDirection.y);

        float centerDepth = texture(SceneDepth, screenUv).r;
        bool centerContainsGeometry = containsSceneGeometry(centerDepth);
        float centerDistance = sceneDistance(screenUv, centerDepth);

        float edgeDistance = min(min(screenUv.x, 1.0 - screenUv.x),
            min(screenUv.y, 1.0 - screenUv.y));
        float edgeFade = smoothstep(0.006, 0.045, edgeDistance);
        float silhouetteFade = centerContainsGeometry
            ? 1.0 - smoothstep(0.035, 0.18, depthDiscontinuity(screenUv, centerDistance))
            : 1.0;
        vec2 wave = DistortionStrength > 0.0001
            ? anchoredSurfaceDistortion(worldRay)
            : vec2(0.0);
        vec2 sceneOffset = wave * DistortionStrength * edgeFade * silhouetteFade;
        vec2 refractedUv = clamp(screenUv + sceneOffset, vec2(0.002), vec2(0.998));
        float refractedDepth = texture(SceneDepth, refractedUv).r;
        bool refractedContainsGeometry = containsSceneGeometry(refractedDepth);

        // Never bend a foreground silhouette into the background. Geometry may
        // refract within its own bounded distance, while sky remains free to
        // shimmer against other sky pixels.
        if (centerContainsGeometry != refractedContainsGeometry) {
            refractedUv = screenUv;
            refractedDepth = centerDepth;
            refractedContainsGeometry = centerContainsGeometry;
        } else if (centerContainsGeometry) {
            float refractedDistance = sceneDistance(refractedUv, refractedDepth);
            float distanceTolerance = max(0.65, centerDistance * 0.08);
            if (abs(refractedDistance - centerDistance) > distanceTolerance) {
                refractedUv = screenUv;
                refractedDepth = centerDepth;
                refractedContainsGeometry = true;
            }
        }

        vec3 sceneColor = texture(SceneColor, refractedUv).rgb;
        vec3 refractedViewPosition = refractedContainsGeometry
            ? reconstructViewPosition(refractedUv, refractedDepth)
            : viewRay * max(6.0, VisibilityBlocks);
        float geometricDistance = refractedContainsGeometry
            ? length(refractedViewPosition)
            : max(6.0, VisibilityBlocks);

        // Rays looking upward leave the medium at the animated surface. Other
        // rays remain in water until terrain or the configured visibility cap.
        float distanceToSurface = worldRay.y > 0.025
            ? max(0.02, CameraDepth) / worldRay.y
            : max(6.0, VisibilityBlocks);
        float travelDistance = clamp(
            min(geometricDistance, distanceToSurface),
            0.0,
            max(6.0, VisibilityBlocks)
        );

        vec3 absorption = max(AbsorptionCoefficients, vec3(0.0001));
        vec3 transmission = exp(-absorption * travelDistance);
        vec3 mediumRadiance = max(WaterFogColor, vec3(0.018, 0.095, 0.190));
        vec3 lostRadiance = vec3(1.0) - transmission;
        float scatterDistance = 1.0 - exp(-max(0.0001, ScatteringCoefficient) * travelDistance);
        vec3 color = sceneColor * transmission
            + mediumRadiance * lostRadiance * mix(0.58, 0.76, clarity);
        color = mix(color, mediumRadiance, scatterDistance * 0.14);

        float opticalEffect = clamp(CausticStrength, 0.0, 1.0) * sunlight;
        float caustic = 0.0;
        float shaft = 0.0;
        // Uniform-coherent gates prevent night, disabled, and low-quality
        // frames from paying for caustic derivatives or shaft integration.
        if (opticalEffect > 0.0001 && effectQuality >= 1.0) {
            bool receiverBeforeSurface = worldRay.y <= 0.025
                || geometricDistance <= distanceToSurface + 0.15;
            if (refractedContainsGeometry && receiverBeforeSurface) {
                vec3 worldPosition = CameraAnchor
                    + (ViewToWorldMat * vec4(refractedViewPosition, 0.0)).xyz;
                vec3 sceneNormal = reconstructedSceneNormal(worldPosition, worldRay);
                float receiverDepth = max(0.0,
                    CameraAnchor.y + CameraDepth - worldPosition.y);
                float submergedReceiver = smoothstep(-0.05, 0.25,
                    CameraAnchor.y + CameraDepth - worldPosition.y);
                float lightIncidence = pow(clamp(
                    dot(sceneNormal, lightDirection), 0.0, 1.0), 0.55);
                caustic = worldCausticField(worldPosition, lightDirection)
                    * opticalEffect
                    * lightIncidence
                    * submergedReceiver
                    * exp(-receiverDepth * 0.075)
                    * silhouetteFade;
            }
            if (effectQuality >= 2.0) {
                int shaftSamples = effectQuality >= 3.0 ? 4 : 2;
                shaft = integrateSunShafts(
                    worldRay, travelDistance, lightDirection, shaftSamples)
                    * opticalEffect;
            }
        }

        color += vec3(0.24, 0.39, 0.43) * caustic * 0.13;
        color += vec3(0.10, 0.19, 0.24) * shaft * 0.11;
        color *= ColorModulator.rgb;

        // This is a completed scene replacement, not a translucent tint. The
        // transition is resolved here so source-over blending cannot apply the
        // medium transmission a second time.
        fragColor = vec4(mix(sceneColor, max(color, vec3(0.0)), submersion), 1.0);
        return;
    }

    // No same-frame water-stage capture means no trustworthy terrain depth or
    // world reconstruction. Keep a restrained texture fallback instead of
    // inventing screen-space caustics that swim when the camera moves.
    vec2 fallbackWave = vec2(
        sin(texCoord0.y * 7.0 + FallbackPhases.x),
        cos(texCoord0.x * 6.0 + FallbackPhases.y)
    );
    vec4 overlay = texture(Sampler0, texCoord0 + fallbackWave * DistortionStrength);
    vec3 fallbackColor = mix(overlay.rgb, WaterFogColor,
        0.24 + (1.0 - clarity) * 0.14);
    fallbackColor = max(fallbackColor,
        WaterFogColor * 0.72 + vec3(0.018, 0.032, 0.042));
    fallbackColor *= ColorModulator.rgb;

    float fallbackAlpha = (0.024 + (1.0 - clarity) * 0.042)
        * submersion * ColorModulator.a;
    fragColor = vec4(fallbackColor, clamp(fallbackAlpha, 0.0, 0.08));
}
