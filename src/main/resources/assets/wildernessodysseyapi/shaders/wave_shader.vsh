#version 120

uniform float time; // Time uniform for animation
varying vec3 worldPos; // Pass world position to fragment shader

void main() {
    vec4 pos = gl_Vertex; // Original vertex position

    // Multiple wave layers for complex motion
    float wave1 = sin(pos.x * 0.1 + time * 0.05) * 0.5; // Long, slow wave
    float wave2 = sin(pos.z * 0.15 + time * 0.08) * 0.3; // Medium wave
    float wave3 = sin((pos.x + pos.z) * 0.2 + time * 0.1) * 0.2; // Small ripples

    // Combine wave layers
    float totalWaveHeight = wave1 + wave2 + wave3;

    // Apply wave height to Y coordinate
    pos.y += totalWaveHeight;

    worldPos = pos.xyz; // Pass transformed position to fragment shader
    gl_Position = gl_ModelViewProjectionMatrix * pos; // Apply transformation
}
