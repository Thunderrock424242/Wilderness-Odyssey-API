#version 150

uniform float time;  // Time uniform for animation
uniform vec3 waveAmplitudes;  // Amplitude of each wave
uniform vec3 waveFrequencies; // Frequency of each wave
uniform vec3 waveSpeeds;      // Speed of each wave

in vec3 Position;  // Vertex position input
in vec2 UV0;       // Texture coordinates input

out vec3 worldPos; // Pass transformed position to fragment shader
out vec2 texCoord; // Pass texture coordinates

void main() {
    vec4 pos = vec4(Position, 1.0);

    // Apply multiple waves
    float wave1 = sin(pos.x * waveFrequencies.x + time * waveSpeeds.x) * waveAmplitudes.x;
    float wave2 = sin(pos.z * waveFrequencies.y + time * waveSpeeds.y) * waveAmplitudes.y;
    float wave3 = sin((pos.x + pos.z) * waveFrequencies.z + time * waveSpeeds.z) * waveAmplitudes.z;

    // Combine waves
    float totalWaveHeight = wave1 + wave2 + wave3;
    pos.y += totalWaveHeight;

    worldPos = pos.xyz;
    texCoord = UV0;
    gl_Position = gl_ModelViewProjectionMatrix * pos;
}
