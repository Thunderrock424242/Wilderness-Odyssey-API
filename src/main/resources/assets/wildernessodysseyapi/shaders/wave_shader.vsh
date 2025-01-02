#version 120

uniform float time;
uniform sampler2D waveHeightMap;

varying vec3 worldPos;

void main() {
    // Extract wave height from the heightmap
    vec2 texCoord = gl_Vertex.xy * 0.01; // Scale for heightmap lookup
    float waveHeight = texture2D(waveHeightMap, texCoord).r;

    // Modify the vertex position with the wave height
    vec3 pos = gl_Vertex.xyz;
    pos.y += waveHeight * sin(time + pos.x * 0.05 + pos.z * 0.05);

    worldPos = pos;
    gl_Position = gl_ModelViewProjectionMatrix * vec4(pos, 1.0);
}
