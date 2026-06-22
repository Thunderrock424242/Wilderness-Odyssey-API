#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out ivec2 lightCoord;
out vec3 viewPosition;
out vec3 viewNormal;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    viewPosition = view.xyz;
    viewNormal = normalize(mat3(ModelViewMat) * Normal);
    vertexDistance = length(view.xyz);
    vertexColor = Color;
    texCoord0 = UV0 + vec2(
        sin(GameTime * 0.35 + Position.z * 0.18),
        cos(GameTime * 0.27 + Position.x * 0.16)
    ) * 0.0035;
    lightCoord = UV2;
    gl_Position = ProjMat * view;
}
