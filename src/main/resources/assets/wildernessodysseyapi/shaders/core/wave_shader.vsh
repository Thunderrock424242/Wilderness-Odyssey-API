#version 150

uniform float time;                // Time uniform for animation
uniform vec3 waveAmplitudes;       // Amplitudes for the three wave layers
uniform vec3 waveFrequencies;      // Frequencies for the three wave layers
uniform vec3 waveSpeeds;           // Speeds for the three wave layers

in vec3 Position;                  // Vertex position
in vec2 UV0;                       // Vertex UV coordinates

out vec3 worldPos;                 // Pass world position to fragment shader
out vec2 vUV;                      // Pass UV coordinates to fragment shader

void main() {
    vec4 pos = vec4(Position, 1.0); // Original vertex position

    // Calculate wave displacements
    float wave1 = sin(pos.x * waveFrequencies.x + time * waveSpeeds.x) * waveAmplitudes.x; // Long, slow wave
    float wave2 = sin(pos.z * waveFrequencies.y + time * waveSpeeds.y) * waveAmplitudes.y; // Medium wave
    float wave3 = sin((pos.x + pos.z) * waveFrequencies.z + time * waveSpeeds.z) * waveAmplitudes.z; // Small ripples

    // Combine wave layers
    float totalWaveHeight = wave1 + wave2 + wave3;

    // Apply wave height to the Y-coordinate
    pos.y += totalWaveHeight;

    // Pass transformed position and UV coordinates to fragment shader
    worldPos = pos.xyz;
    vUV = UV0;

    // Apply model-view-projection transformation
    gl_Position = gl_ModelViewProjectionMatrix * pos;
}
