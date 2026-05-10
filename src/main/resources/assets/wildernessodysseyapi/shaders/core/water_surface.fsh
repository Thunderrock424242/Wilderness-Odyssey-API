#version 150

// Simple lit tint for the generated SPH water mesh.

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 normal;

out vec4 fragColor;

void main() {
    float diffuse = clamp(normal.y * 0.6 + 0.4, 0.35, 1.0);
    vec4 color = vertexColor * vec4(diffuse, diffuse, diffuse, 1.0) * ColorModulator;

    float fogRange = max(FogEnd - FogStart, 0.0001);
    float fogFraction = clamp((vertexDistance - FogStart) / fogRange, 0.0, 1.0);
    fragColor = mix(color, FogColor, fogFraction);
}
