#version 150

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

void main() {
    fragColor = vertexColor;
}
