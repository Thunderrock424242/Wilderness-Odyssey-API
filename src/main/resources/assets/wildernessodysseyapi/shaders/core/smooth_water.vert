#version 150

uniform float time;

in vec3 Position;
in vec4 Color;
in vec2 UV0;

out vec4 vertexColor;
out vec2 texCoord;

void main() {
    float wave = sin(Position.x * 10.0 + time) * 0.05 + cos(Position.z * 10.0 + time) * 0.05;
    vec3 pos = Position + vec3(0.0, wave, 0.0);
    gl_Position = projectionMatrix * modelViewMatrix * vec4(pos, 1.0);
    texCoord = UV0;
    vertexColor = Color;
}
