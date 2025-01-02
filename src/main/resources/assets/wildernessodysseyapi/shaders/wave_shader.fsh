#version 120

uniform sampler2D waveHeightMap;
uniform sampler2D foamTexture;
uniform float time;

varying vec3 worldPos;

void main() {
    // Sample wave height
    vec2 texCoord = worldPos.xz * 0.01;
    float waveHeight = texture2D(waveHeightMap, texCoord).r;

    // Foam near the peak of waves
    float foamIntensity = smoothstep(0.8, 1.0, waveHeight);
    vec4 foamColor = texture2D(foamTexture, texCoord) * foamIntensity;

    // Water color blending
    vec4 waterColor = vec4(0.0, 0.3, 0.7, 1.0); // Deep blue water
    waterColor.rgb += waveHeight * 0.1; // Lighten based on wave height

    gl_FragColor = mix(waterColor, foamColor, foamIntensity);
}
