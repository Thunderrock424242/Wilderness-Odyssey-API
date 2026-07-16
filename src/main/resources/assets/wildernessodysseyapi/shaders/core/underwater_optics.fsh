#version 150

uniform sampler2D Sampler0;
uniform sampler2D SceneColor;
uniform sampler2D SceneDepth;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float Submersion;
uniform float Clarity;
uniform float SeaState;
uniform float CausticStrength;
uniform float DistortionStrength;
uniform vec3 WaterFogColor;
uniform vec2 ScreenSize;
uniform float SceneCaptureValid;
uniform float CameraDepth;
uniform float VisibilityBlocks;
uniform vec2 DepthRange;
uniform vec3 AbsorptionCoefficients;

in vec2 texCoord0;

out vec4 fragColor;

float linearizeDepth(float depth) {
    float nearPlane = max(0.001, DepthRange.x);
    float farPlane = max(nearPlane + 1.0, DepthRange.y);
    float clipDepth = depth * 2.0 - 1.0;
    return (2.0 * nearPlane * farPlane)
        / max(0.0001, farPlane + nearPlane - clipDepth * (farPlane - nearPlane));
}

float depthDiscontinuity(vec2 uv, float centerDepth) {
    vec2 texel = 1.0 / max(ScreenSize, vec2(1.0));
    float dx = abs(texture(SceneDepth,
        clamp(uv + vec2(texel.x, 0.0), vec2(0.0), vec2(1.0))).r - centerDepth);
    float dy = abs(texture(SceneDepth,
        clamp(uv + vec2(0.0, texel.y), vec2(0.0), vec2(1.0))).r - centerDepth);
    return max(dx, dy);
}

void main() {
    float submersion = clamp(Submersion, 0.0, 1.0);
    float clarity = clamp(Clarity, 0.0, 1.0);
    float sea = clamp(SeaState, 0.0, 1.0);
    vec2 wave = vec2(
        sin(texCoord0.y * 7.0 + GameTime * (0.65 + sea * 0.85)),
        cos(texCoord0.x * 6.0 - GameTime * (0.52 + sea * 0.72))
    );
    vec2 distortedUv = texCoord0 + wave * DistortionStrength;
    vec4 overlay = texture(Sampler0, distortedUv);

    // Two moving interference fields approximate focused surface caustics.
    // They intentionally fade with depth/clarity on the CPU-provided uniform.
    float causticA = sin((distortedUv.x + distortedUv.y) * 18.0 + GameTime * 1.8);
    float causticB = cos((distortedUv.x - distortedUv.y) * 15.0 - GameTime * 1.35);
    float caustic = pow(clamp(1.0 - abs(causticA + causticB) * 0.52, 0.0, 1.0), 5.0);
    caustic *= clamp(CausticStrength, 0.0, 1.0);

    float shaft = pow(clamp(1.0 - abs(fract(distortedUv.x * 0.42 + GameTime * 0.015) - 0.5) * 2.0,
        0.0, 1.0), 5.0) * CausticStrength;
    if (SceneCaptureValid > 0.5) {
        vec2 screenUv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
        float centerDepth = texture(SceneDepth, screenUv).r;
        bool validDepth = centerDepth > 0.00001 && centerDepth < 0.99998;

        float edgeDistance = min(min(screenUv.x, 1.0 - screenUv.x),
            min(screenUv.y, 1.0 - screenUv.y));
        float edgeFade = smoothstep(0.006, 0.045, edgeDistance);
        float silhouetteFade = validDepth
            ? 1.0 - smoothstep(0.0015, 0.012,
                depthDiscontinuity(screenUv, centerDepth))
            : 0.0;
        vec2 sceneOffset = wave * DistortionStrength * edgeFade * silhouetteFade;
        vec2 refractedUv = clamp(screenUv + sceneOffset, vec2(0.002), vec2(0.998));
        float refractedDepth = texture(SceneDepth, refractedUv).r;
        if (!validDepth || abs(refractedDepth - centerDepth) > 0.008) {
            refractedUv = screenUv;
        }

        vec3 sceneColor = texture(SceneColor, refractedUv).rgb;
        float travelDistance = validDepth
            ? clamp(linearizeDepth(centerDepth), 0.0, VisibilityBlocks)
            : VisibilityBlocks;
        vec3 transmission = exp(-max(AbsorptionCoefficients, vec3(0.0001))
            * travelDistance);

        // The captured scene is already fogged and lightmapped. This pass
        // performs one bounded spectral grade, then adds physically motivated
        // ambient in-scattering so nearby terrain stays readable in daylight.
        vec3 mediumRadiance = max(WaterFogColor, vec3(0.018, 0.095, 0.190));
        float scatterDistance = 1.0 - exp(-(0.032 + (1.0 - clarity) * 0.045)
            * travelDistance);
        float depthExposure = smoothstep(0.5, 12.0, max(0.0, CameraDepth));
        float scatterStrength = mix(0.58, 0.78, clarity)
            * mix(0.86, 1.0, depthExposure);
        vec3 color = sceneColor * transmission
            + mediumRadiance * scatterDistance * scatterStrength;
        color += vec3(0.20, 0.34, 0.40) * caustic * 0.085;
        color += vec3(0.10, 0.18, 0.23) * shaft * 0.045;
        color += (overlay.rgb - vec3(0.5)) * 0.010 * clarity;
        color *= ColorModulator.rgb;

        // An opaque scene replacement avoids applying transmission through
        // source-over blending a second time. Submersion performs the smooth
        // above-water-to-underwater transition inside the completed color.
        fragColor = vec4(mix(sceneColor, max(color, vec3(0.0)), submersion), 1.0);
        return;
    }

    // Missing captures remain safe: retain the lightweight vanilla-texture
    // overlay instead of treating an absent/unloaded scene as filled water.
    vec3 fallbackColor = mix(overlay.rgb, WaterFogColor,
        0.24 + (1.0 - clarity) * 0.14);
    fallbackColor = max(fallbackColor,
        WaterFogColor * 0.72 + vec3(0.018, 0.032, 0.042));
    fallbackColor += vec3(0.22, 0.37, 0.44) * caustic * 0.38;
    fallbackColor += vec3(0.12, 0.21, 0.26) * shaft * 0.14;
    fallbackColor *= ColorModulator.rgb;

    float fallbackAlpha = (0.024 + (1.0 - clarity) * 0.042
        + caustic * 0.014 + shaft * 0.008) * submersion * ColorModulator.a;
    fragColor = vec4(fallbackColor, clamp(fallbackAlpha, 0.0, 0.10));
}
