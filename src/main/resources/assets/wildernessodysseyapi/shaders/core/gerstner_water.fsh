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
uniform float GameTime;
uniform float SeaState;
uniform vec2 WindDirection;
uniform vec2 ScreenSize;
uniform vec4 Weather;
uniform vec4 EnvironmentColor;
uniform vec4 OpticalQuality;
uniform vec3 AbsorptionCoefficients;
uniform float SceneCaptureValid;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 lightColor;
in vec3 viewPosition;
in vec3 viewNormal;
in vec3 worldPosition;
in vec3 worldNormal;
in vec3 celestialDirection;
in float celestialDaylight;
in vec3 waterBodyBlend;
in float surfaceContinuity;

out vec4 fragColor;

float waveLayer(vec2 position, vec2 direction, float frequency, float speed) {
    return sin(dot(position, direction) * frequency + GameTime * speed);
}

vec3 proceduralWorldNormal(vec2 position, float sea) {
    vec2 wind = normalize(WindDirection + vec2(0.0001, 0.0));
    vec2 crossWind = vec2(-wind.y, wind.x);
    vec2 diagonal = normalize(wind * 0.73 + crossWind * 0.61);
    // Low-frequency domain warping prevents the small normal layers from
    // resolving into a repeating checkerboard when viewed across many chunks.
    vec2 warp = vec2(
        sin(dot(position, vec2(0.071, 0.113)) + GameTime * 0.19),
        cos(dot(position, vec2(-0.097, 0.059)) - GameTime * 0.16)
    ) * (0.42 + sea * 0.48);
    vec2 warped = position + warp;
    float longRipple = waveLayer(warped, wind, 1.37, 0.51 + sea * 1.07);
    float crossRipple = waveLayer(warped, crossWind, 2.73, -0.39 - sea * 0.83);
    float capillary = waveLayer(warped, diagonal, 6.91, 1.63 + sea * 2.91);
    float glassRipple = waveLayer(warped + wind * GameTime * 0.11,
        normalize(wind * 0.58 - crossWind * 0.81), 13.73, 2.31 + sea * 4.07);
    vec2 gradient = wind * (longRipple * 0.016 + capillary * 0.008)
        + crossWind * (crossRipple * 0.012)
        + diagonal * (capillary * 0.007 + glassRipple * 0.005);
    return normalize(vec3(gradient.x, 1.0, gradient.y));
}

vec3 reconstructViewPosition(vec2 uv, float depth) {
    vec4 clip = vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 view = InverseProjMat * clip;
    return view.xyz / max(abs(view.w), 0.00001);
}

float depthDiscontinuity(vec2 uv, float centerDepth) {
    vec2 texel = 1.0 / max(ScreenSize, vec2(1.0));
    float dx = abs(texture(SceneDepth, clamp(uv + vec2(texel.x, 0.0), 0.0, 1.0)).r - centerDepth);
    float dy = abs(texture(SceneDepth, clamp(uv + vec2(0.0, texel.y), 0.0, 1.0)).r - centerDepth);
    return max(dx, dy);
}

vec3 environmentReflection(float fresnel, float sea) {
    float rain = clamp(Weather.x, 0.0, 1.0);
    float thunder = clamp(Weather.y, 0.0, 1.0);
    vec3 nightSky = vec3(0.045, 0.075, 0.145);
    vec3 sky = mix(nightSky, EnvironmentColor.rgb, celestialDaylight);
    sky *= mix(1.0, 0.62, rain) * mix(1.0, 0.70, thunder);
    vec3 horizon = mix(vec3(0.16, 0.23, 0.34), sky, 0.70);
    return mix(horizon, sky, clamp(fresnel + sea * 0.10, 0.0, 1.0));
}

vec4 traceScreenReflection(vec3 origin, vec3 direction, int stepCount, float maxDistance) {
    if (stepCount <= 0 || maxDistance <= 0.0 || SceneCaptureValid < 0.5) {
        return vec4(0.0);
    }
    float stride = maxDistance / float(stepCount);
    vec3 ray = origin + direction * 0.12;
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
        float tolerance = 0.16 + stride * 0.65;
        if (separation >= -0.06 && separation <= tolerance) {
            return vec4(texture(SceneColor, uv).rgb, 1.0);
        }
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
    vec3 microWorldNormal = proceduralWorldNormal(worldPosition.xz, sea);
    float continuousSurface = smoothstep(0.10, 0.70, surfaceContinuity);
    vec3 combinedWorldNormal = normalize(mix(baseWorldNormal, microWorldNormal,
        (0.30 + sea * 0.18) * continuousSurface));
    vec3 normal = normalize(mat3(ModelViewMat) * combinedWorldNormal);
    vec3 viewDirection = normalize(-viewPosition);
    float facing = clamp(dot(normal, viewDirection), 0.0, 1.0);
    float fresnel = 0.0204 + (1.0 - 0.0204) * pow(1.0 - facing, 5.0);

    vec2 screenUv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
    float sceneDepth = SceneCaptureValid > 0.5 ? texture(SceneDepth, screenUv).r : 1.0;
    bool validDepth = SceneCaptureValid > 0.5 && sceneDepth < 0.99998 && sceneDepth > gl_FragCoord.z + 0.00001;
    vec3 sceneView = validDepth ? reconstructViewPosition(screenUv, sceneDepth) : viewPosition;
    float thickness = validDepth
        ? clamp(length(sceneView) - length(viewPosition), 0.0, 64.0)
        : clamp(vertexColor.a * 3.0, 0.35, 3.0);

    float edgeDistance = min(min(screenUv.x, 1.0 - screenUv.x), min(screenUv.y, 1.0 - screenUv.y));
    float screenEdgeFade = smoothstep(0.004, 0.045, edgeDistance);
    float silhouetteFade = validDepth
        ? 1.0 - smoothstep(0.0015, 0.012, depthDiscontinuity(screenUv, sceneDepth))
        : 0.0;
    float depthFade = smoothstep(0.05, 1.75, thickness);
    float distortionScale = OpticalQuality.y * (0.004 + sea * 0.006)
        * screenEdgeFade * silhouetteFade * depthFade;
    vec2 refractedUv = clamp(screenUv + normal.xy * distortionScale, vec2(0.002), vec2(0.998));
    float refractedDepth = SceneCaptureValid > 0.5 ? texture(SceneDepth, refractedUv).r : 1.0;
    if (refractedDepth <= gl_FragCoord.z + 0.00001) {
        refractedUv = screenUv;
    }

    vec3 sceneColor = SceneCaptureValid > 0.5
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
    vec3 effectiveAbsorption = max(AbsorptionCoefficients * bodyAbsorption * biomeAbsorption,
        vec3(0.0001));
    vec3 transmission = exp(-effectiveAbsorption * thickness);
    vec3 deepBodyColor = vec3(0.010, 0.145, 0.310) * waterBodyBlend.x
        + vec3(0.025, 0.205, 0.235) * waterBodyBlend.y
        + vec3(0.020, 0.175, 0.260) * waterBodyBlend.z;
    vec3 absorbedColor = mix(vertexColor.rgb, deepBodyColor, smoothstep(2.0, 24.0, thickness));
    vec3 transmittedColor = sceneColor * transmission + absorbedColor * (vec3(1.0) - transmission);

    vec3 reflectedColor = environmentReflection(fresnel, sea);
    int ssrSteps = int(clamp(OpticalQuality.z, 0.0, 24.0));
    vec3 incident = normalize(viewPosition);
    vec3 reflectedRay = normalize(reflect(incident, normal));
    vec4 ssr = traceScreenReflection(viewPosition, reflectedRay, ssrSteps, OpticalQuality.w);
    reflectedColor = mix(reflectedColor, ssr.rgb, ssr.a * (0.58 + fresnel * 0.32));

    float slope = clamp(1.0 - combinedWorldNormal.y, 0.0, 1.0);
    float slopeFoam = smoothstep(0.025, 0.14 + sea * 0.04, slope);
    vec3 halfDirection = normalize(celestialDirection + viewDirection);
    float celestialSpecular = pow(max(dot(normal, halfDirection), 0.0), mix(120.0, 54.0, sea));
    vec3 celestialColor = mix(vec3(0.42, 0.52, 0.75), vec3(1.00, 0.88, 0.67), celestialDaylight);

    vec3 color = mix(transmittedColor, reflectedColor, fresnel);
    color = mix(color, vec3(0.84, 0.94, 1.0), slopeFoam * (0.08 + sea * 0.18));
    color += celestialColor * celestialSpecular * (0.24 + sea * 0.25);
    // Captured terrain is already lightmapped. Applying the water lightmap to
    // the completed scene-color composite a second time made the seafloor much
    // darker than the surrounding world, especially under overhangs.
    vec3 waterLighting = max(lightColor, vec3(0.62));
    waterLighting = mix(waterLighting, vec3(1.0), SceneCaptureValid * 0.82);
    color *= waterLighting * ColorModulator.rgb;

    float mediumOpacity = 1.0 - dot(transmission, vec3(0.2126, 0.7152, 0.0722));
    float alpha = clamp(
        fresnel + (1.0 - fresnel) * (0.10 + mediumOpacity * 0.82),
        0.10,
        0.96
    ) * ColorModulator.a;
    float loadedFrontier = smoothstep(0.08, 0.55, surfaceContinuity);
    color = mix(environmentReflection(fresnel, sea), color, loadedFrontier);
    alpha *= mix(0.18, 1.0, loadedFrontier);
    float fogRange = max(0.001, FogEnd - FogStart);
    float fogFactor = clamp((vertexDistance - FogStart) / fogRange, 0.0, 1.0);
    fragColor = vec4(mix(color, FogColor.rgb, fogFactor), alpha);
}
