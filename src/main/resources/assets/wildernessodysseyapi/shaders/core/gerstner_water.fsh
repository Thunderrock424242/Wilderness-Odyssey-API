#version 150

// gerstner_water.fsh
// Wilderness mod — Gerstner ocean surface fragment shader
//
// Features:
//   - Depth-based colour gradient (shallow teal → deep blue)
//   - Foam near wave crests (high waveHeight → whitecaps)
//   - Specular highlight from directional sun
//   - River tint (slightly green/brown)
//   - Pond tint (clear with faint green)

uniform sampler2D Sampler0;  // water texture atlas
uniform sampler2D Sampler2;  // lightmap

uniform vec4  ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4  FogColor;
uniform float GameTime;

in float vertexDistance;
in vec4  vertexColor;
in vec2  texCoord0;
in vec2  texCoord1;
in ivec2 uv2;
in vec4  normal;
in float waterTypeFrag;   // 0=ocean, 1=river, 2=pond
in float waveHeight;      // world Y of this fragment (proxy for depth/crest)

out vec4 fragColor;

// Colour palettes per water type
const vec3 OCEAN_SHALLOW = vec3(0.10, 0.55, 0.75);
const vec3 OCEAN_DEEP    = vec3(0.04, 0.18, 0.45);
const vec3 RIVER_COL     = vec3(0.12, 0.50, 0.45);
const vec3 POND_COL      = vec3(0.15, 0.55, 0.40);
const vec3 FOAM_COL      = vec3(0.82, 0.92, 1.00);

void main() {
    vec4 tex     = texture(Sampler0, texCoord0);
    vec4 texFoam = texture(Sampler0, texCoord1);
    if (tex.a < 0.05) discard;

    vec4 lightColor = textureLod(Sampler2, clamp(uv2 / 256.0, 0.0, 1.0), 0);

    // Base water colour from type
    vec3 baseCol;
    float alpha = 0.78;
    if (waterTypeFrag < 0.5) {
        // Ocean: depth-tinted
        float depthT = clamp((waveHeight - 58.0) / 6.0, 0.0, 1.0);
        baseCol = mix(OCEAN_DEEP, OCEAN_SHALLOW, depthT);
        alpha   = 0.80;
    } else if (waterTypeFrag < 1.5) {
        baseCol = RIVER_COL;
        alpha   = 0.72;
    } else {
        baseCol = POND_COL;
        alpha   = 0.68;
    }

    // Foam / whitecaps on wave crests (ocean only)
    float foamMask = 0.0;
    if (waterTypeFrag < 0.5) {
        float crestT = clamp((waveHeight - 62.15) / 0.12, 0.0, 1.0);
        foamMask     = crestT * texFoam.r;
    }

    vec3 color = mix(baseCol, FOAM_COL, foamMask * 0.7);

    // Apply texture and vertex colour
    color *= tex.rgb * vertexColor.rgb;
    color *= lightColor.rgb;
    color *= ColorModulator.rgb;

    // Simple specular shimmer
    float shimmer = pow(max(0.0, sin(texCoord0.x * 35.0 + GameTime * 1.5)
                              * cos(texCoord0.y * 35.0 + GameTime * 1.2)), 10.0);
    color += vec3(0.15, 0.20, 0.28) * shimmer * mix(0.5, 0.1, waterTypeFrag / 2.0);

    float finalAlpha = alpha * vertexColor.a * ColorModulator.a;

    // Fog
    float fogFrac = clamp((vertexDistance - FogStart) / (FogEnd - FogStart), 0.0, 1.0);
    vec4 result = mix(vec4(color, finalAlpha), FogColor, fogFrac);

    fragColor = result;
}
