#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 localNoisePosition;
out vec4 vertexColor;
out vec3 columnData;
out vec3 localPosition;

void main() {
    localNoisePosition = UV0;
    vertexColor = Color;
    columnData = Normal;
    localPosition = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
