#version 150

// gerstner_water.vsh
// Wilderness mod — Gerstner ocean surface shader
//
// Performs secondary micro-wave UV animation on top of the
// Java-side Gerstner vertex displacement. The vertex shader
// receives GameTime and WaterType (0=ocean, 1=river, 2=pond)
// to scale effects correctly per water body.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int  FogShape;
uniform float GameTime;
uniform float WaterType;    // 0=ocean, 1=river, 2=pond
uniform float TideOffset;   // current tide height offset

out float vertexDistance;
out vec4  vertexColor;
out vec2  texCoord0;
out vec2  texCoord1;   // second UV layer for foam
out ivec2 uv2;
out vec4  normal;
out float waterTypeFrag;
out float waveHeight;

void main() {
    // UV scroll speed scales with water type
    float scrollSpeed = mix(0.08, 0.02, WaterType / 2.0);
    float crossSpeed  = scrollSpeed * 0.6;

    vec2 scroll1 = vec2(
        sin(GameTime * scrollSpeed + Position.z * 0.3) * 0.004,
        cos(GameTime * scrollSpeed * 0.7 + Position.x * 0.3) * 0.004
    );
    vec2 scroll2 = vec2(
        cos(GameTime * crossSpeed + Position.x * 0.5) * 0.003,
        sin(GameTime * crossSpeed * 0.8 + Position.z * 0.4) * 0.003
    );

    texCoord0    = UV0 + scroll1;
    texCoord1    = UV0 * 3.0 + scroll2;   // tighter foam UV
    waveHeight   = Position.y;
    waterTypeFrag = WaterType;

    gl_Position    = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = length((ModelViewMat * vec4(Position, 1.0)).xyz);
    vertexColor    = Color;
    uv2            = UV2;
    normal         = ProjMat * ModelViewMat * vec4(Normal, 0.0);
}
