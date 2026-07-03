#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;
uniform float SeaState;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 lightColor;
in vec3 viewPosition;
in vec3 viewNormal;
in vec3 celestialDirection;
in float celestialDaylight;

out vec4 fragColor;

void main() {
    vec4 waterTexture = texture(Sampler0, texCoord0);
    if (waterTexture.a < 0.03) {
        discard;
    }

    vec3 normal = normalize(viewNormal);
    float sea = clamp(SeaState, 0.0, 1.0);
    vec3 viewDirection = normalize(-viewPosition);
    float facing = clamp(dot(normal, viewDirection), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - facing, 5.0);
    float slopeFoam = smoothstep(0.93 - sea * 0.04, 0.985, 1.0 - normal.y + 0.93);
    float microShimmer = pow(max(0.0,
        sin(texCoord0.x * 42.0 + GameTime * (1.0 + sea * 2.2))
        * cos(texCoord0.y * 39.0 + GameTime * (0.8 + sea * 1.8))), 12.0);
    vec3 halfDirection = normalize(celestialDirection + viewDirection);
    float celestialSpecular = pow(max(dot(normal, halfDirection), 0.0), 88.0);
    vec3 celestialColor = mix(
        vec3(0.38, 0.50, 0.72),
        vec3(1.00, 0.88, 0.68),
        celestialDaylight
    );

    vec3 color = mix(vertexColor.rgb, vertexColor.rgb * waterTexture.rgb, 0.16);
    color = mix(color, vec3(0.80, 0.91, 0.98), fresnel * 0.34 + slopeFoam * (0.10 + sea * 0.18));
    color += vec3(0.20, 0.24, 0.31) * microShimmer * (0.55 + sea * 0.90);
    color += celestialColor * celestialSpecular * (0.20 + sea * 0.14);
    color *= max(lightColor, vec3(0.48));
    color = max(color, vertexColor.rgb * 0.42);
    color *= ColorModulator.rgb;

    float textureAlpha = mix(0.90, waterTexture.a, 0.30);
    float alpha = clamp(vertexColor.a * textureAlpha * ColorModulator.a + fresnel * 0.08, 0.0, 0.98);
    float fogRange = max(0.001, FogEnd - FogStart);
    float fogFactor = clamp((vertexDistance - FogStart) / fogRange, 0.0, 1.0);
    fragColor = mix(vec4(color, alpha), FogColor, fogFactor);
}
