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
    vec3 color = mix(WaterFogColor, overlay.rgb, 0.12 + clarity * 0.16);
    color += vec3(0.20, 0.34, 0.40) * caustic * 0.34;
    color += vec3(0.10, 0.18, 0.22) * shaft * 0.12;
    color *= ColorModulator.rgb;

    float alpha = (0.035 + (1.0 - clarity) * 0.055 + caustic * 0.018 + shaft * 0.010)
        * submersion * ColorModulator.a;
    fragColor = vec4(color, clamp(alpha, 0.0, 0.14));
}
