#version 150

// water_surface.vsh
// Wilderness mod — water surface vertex shader
//
// Applies a UV-scroll for animated water texture and outputs
// normals for lighting. Wave displacement is handled Java-side
// via WaveVertexConsumer; this shader adds secondary ripple detail
// via UV animation.

in vec3 Position;
in vec4 Color;
in vec2 UV0;       // Block atlas UV
in ivec2 UV2;      // Lightmap UV
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int  FogShape;

// GameTime is passed from WaterShaderManager each frame
uniform float GameTime;

out float vertexDistance;
out vec4  vertexColor;
out vec2  texCoord0;
out ivec2 uv2;
out vec4  normal;

void main() {
    // Scroll UV to simulate flowing water surface
    float scrollSpeed = 0.04;
    vec2 scrolledUV = UV0 + vec2(
        sin(GameTime * scrollSpeed + Position.z * 0.5) * 0.003,
        cos(GameTime * scrollSpeed + Position.x * 0.5) * 0.003
    );

    gl_Position    = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexDistance = length((ModelViewMat * vec4(Position, 1.0)).xyz);
    vertexColor    = Color;
    texCoord0      = scrolledUV;
    uv2            = UV2;
    normal         = ProjMat * ModelViewMat * vec4(Normal, 0.0);
}
