#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float Submersion;
uniform float Clarity;
uniform float SeaState;
uniform float CausticStrength;
uniform float DistortionStrength;
uniform vec3 WaterFogColor;

in vec2 texCoord0;

out vec4 fragColor;

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
    // Preserve the scene carried by the vanilla overlay instead of replacing
    // most of it with a dark fog swatch. Fog distance remains responsible for
    // large-scale attenuation while this pass supplies tint and caustics.
    vec3 color = mix(overlay.rgb, WaterFogColor, 0.24 + (1.0 - clarity) * 0.14);
    color = max(color, WaterFogColor * 0.72 + vec3(0.018, 0.032, 0.042));
    color += vec3(0.22, 0.37, 0.44) * caustic * 0.38;
    color += vec3(0.12, 0.21, 0.26) * shaft * 0.14;
    color *= ColorModulator.rgb;

    float alpha = (0.024 + (1.0 - clarity) * 0.042 + caustic * 0.014 + shaft * 0.008)
        * submersion * ColorModulator.a;
    fragColor = vec4(color, clamp(alpha, 0.0, 0.10));
}
