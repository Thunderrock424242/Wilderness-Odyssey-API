#version 150

#moj_import <light.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float GameTime;
uniform float SeaState;
uniform vec2 WindDirection;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 lightColor;
out vec3 viewPosition;
out vec3 viewNormal;

void main() {
    vec4 view = ModelViewMat * vec4(Position, 1.0);
    viewPosition = view.xyz;
    viewNormal = normalize(mat3(ModelViewMat) * Normal);
    vertexDistance = length(view.xyz);
    vertexColor = Color;
    float sea = clamp(SeaState, 0.0, 1.0);
    vec2 wind = normalize(WindDirection + vec2(0.0001, 0.0));
    float windPhase = dot(Position.xz, wind) * 0.16 + GameTime * (0.30 + sea * 0.90);
    vec2 windRipple = wind * sin(windPhase) * (0.0015 + sea * 0.0035);
    texCoord0 = UV0 + windRipple + vec2(
        sin(GameTime * 0.35 + Position.z * 0.18),
        cos(GameTime * 0.27 + Position.x * 0.16)
    ) * 0.0035;
    // Match vanilla's lightmap path and interpolate sampled color, not packed integers.
    lightColor = minecraft_sample_lightmap(Sampler2, UV2).rgb;
    gl_Position = ProjMat * view;
}
