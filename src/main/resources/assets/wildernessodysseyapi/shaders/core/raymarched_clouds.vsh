#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec3 surfacePosition;
out vec2 localNoisePosition;
out vec4 vertexColor;
out vec3 volumeData;

void main() {
    surfacePosition = Position;
    localNoisePosition = UV0;
    vertexColor = Color;
    volumeData = Normal;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
