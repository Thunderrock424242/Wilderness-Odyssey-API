#version 150

// water_surface.fsh
// Wilderness mod — water surface fragment shader
//
// Applies the water atlas texture, tints it with a blue-green water colour,
// and adds a subtle specular highlight to suggest light reflections.

uniform sampler2D Sampler0;   // Block texture atlas
uniform sampler2D Sampler2;   // Lightmap

uniform vec4  ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4  FogColor;

in float vertexDistance;
in vec4  vertexColor;
in vec2  texCoord0;
in ivec2 uv2;
in vec4  normal;

out vec4 fragColor;

void main() {
    vec4 texColor = texture(Sampler0, texCoord0);
    if (texColor.a < 0.1) discard;

    // Sample lightmap
    vec4 lightColor = textureLod(Sampler2, clamp(uv2 / 256.0, 0.0, 1.0), 0);

    // Base water colour — blue-green tint
    vec4 waterTint = vec4(0.28, 0.65, 0.85, 0.72);

    vec4 color = texColor * vertexColor * lightColor * waterTint * ColorModulator;

    // Subtle specular shimmer based on UV animation phase
    float shimmer = pow(max(0.0, sin(texCoord0.x * 40.0 + texCoord0.y * 40.0)), 8.0);
    color.rgb += vec3(0.12, 0.18, 0.22) * shimmer * 0.4;

    // Apply fog
    float fogFraction = clamp((vertexDistance - FogStart) / (FogEnd - FogStart), 0.0, 1.0);
    color = mix(color, FogColor, fogFraction);

    fragColor = color;
}
