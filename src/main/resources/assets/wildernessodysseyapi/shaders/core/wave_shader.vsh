#version 150
in vec4 Position;
in vec2 UV0;
in vec4 Color;

uniform float waveTime;
uniform float tideOffset;
out vec2 vUV;
out vec4 vColor;
out vec3 worldPos;

void main() {
    vec4 pos = Position;
    // apply tide
    pos.y += tideOffset;
    // apply simple wave layers
    float w1 = sin(pos.x * 0.1 + waveTime * 0.05) * 0.5;
    float w2 = sin(pos.z * 0.15 + waveTime * 0.08) * 0.3;
    float w3 = sin((pos.x + pos.z) * 0.2 + waveTime * 0.1) * 0.2;
    pos.y += w1 + w2 + w3;

    worldPos = pos.xyz;
    vUV = UV0;
    vColor = Color;
    gl_Position = gl_ModelViewProjectionMatrix * pos;
}
