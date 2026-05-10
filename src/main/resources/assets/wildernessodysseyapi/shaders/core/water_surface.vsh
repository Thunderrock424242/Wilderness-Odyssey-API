#version 150

// Simple shader for the generated SPH water mesh.

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float vertexDistance;
out vec4 vertexColor;
out vec4 normal;

void main() {
    vec4 viewPosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPosition;
    vertexDistance = length(viewPosition.xyz);
    vertexColor = Color;
    normal = vec4(normalize(Normal), 0.0);
}
