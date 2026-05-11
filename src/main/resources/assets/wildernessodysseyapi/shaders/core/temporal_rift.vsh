#version 150

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec4 ColorModulator;
uniform float GameTime;

out vec4 vertexColor;
out vec3 localPos;
out float facing;

void main() {
    vec3 warped = Position;
    float wobble = sin((Position.y + GameTime * 90.0) * 8.0 + Position.x * 5.0) * 0.025;
    warped.x += Normal.z * wobble;
    warped.z -= Normal.x * wobble;

    gl_Position = ProjMat * ModelViewMat * vec4(warped, 1.0);
    localPos = Position;
    facing = abs(Normal.y) * 0.25 + 0.75;
    vertexColor = Color * ColorModulator;
}
