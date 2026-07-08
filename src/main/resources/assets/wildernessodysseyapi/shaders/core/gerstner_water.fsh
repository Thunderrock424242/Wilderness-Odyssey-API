#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform mat4 ModelViewMat;
uniform float GameTime;
uniform float SeaState;
uniform vec2 WindDirection;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 lightColor;
in vec3 viewPosition;
in vec3 viewNormal;
in vec3 worldPosition;
in vec3 worldNormal;
in vec3 celestialDirection;
in float celestialDaylight;

out vec4 fragColor;

float waveLayer(vec2 position, vec2 direction, float frequency, float speed) {
    return sin(dot(position, direction) * frequency + GameTime * speed);
}

vec3 proceduralWorldNormal(vec2 position, float sea) {
    vec2 wind = normalize(WindDirection + vec2(0.0001, 0.0));
    vec2 crossWind = vec2(-wind.y, wind.x);
    vec2 diagonal = normalize(wind + crossWind * 0.45);

    float longRipple = waveLayer(position, wind, 1.65, 0.55 + sea * 1.15);
    float crossRipple = waveLayer(position, crossWind, 3.35, -0.42 - sea * 0.90);
    float capillary = waveLayer(position, diagonal, 9.25, 1.80 + sea * 3.20);
    float glassRipple = waveLayer(position + wind * GameTime * 0.12, normalize(wind - crossWind * 0.60), 15.0, 2.60 + sea * 4.40);

    vec2 gradient = wind * (longRipple * 0.018 + capillary * 0.010)
        + crossWind * (crossRipple * 0.014)
        + diagonal * (glassRipple * 0.006);
    return normalize(vec3(gradient.x, 1.0, gradient.y));
}

void main() {
    vec4 waterTexture = texture(Sampler0, texCoord0);
    if (waterTexture.a < 0.03) {
        discard;
    }

    float sea = clamp(SeaState, 0.0, 1.0);
    vec3 baseWorldNormal = normalize(worldNormal);
    vec3 microWorldNormal = proceduralWorldNormal(worldPosition.xz, sea);
    vec3 combinedWorldNormal = normalize(mix(baseWorldNormal, microWorldNormal, 0.30 + sea * 0.18));
    vec3 normal = normalize(mat3(ModelViewMat) * combinedWorldNormal);
    vec3 viewDirection = normalize(-viewPosition);
    float facing = clamp(dot(normal, viewDirection), 0.0, 1.0);
    float fresnel = 0.02 + 0.98 * pow(1.0 - facing, 5.0);
    float slope = clamp(1.0 - combinedWorldNormal.y, 0.0, 1.0);
    float slopeFoam = smoothstep(0.018, 0.115 + sea * 0.035, slope);
    float microShimmer = pow(max(0.0,
        sin(worldPosition.x * 7.5 + worldPosition.z * 3.2 + GameTime * (2.0 + sea * 3.4))
        * cos(worldPosition.z * 8.3 - worldPosition.x * 2.7 + GameTime * (1.4 + sea * 2.6))), 10.0);
    vec3 halfDirection = normalize(celestialDirection + viewDirection);
    float celestialSpecular = pow(max(dot(normal, halfDirection), 0.0), mix(112.0, 58.0, sea));
    vec3 celestialColor = mix(
        vec3(0.38, 0.50, 0.72),
        vec3(1.00, 0.88, 0.68),
        celestialDaylight
    );

    // The Minecraft water atlas is only a transparency guard here. Letting its
    // pixels tint the custom surface makes the replacement ocean look like
    // vanilla tiled water instead of one continuous optical medium.
    vec3 bodyColor = vertexColor.rgb;
    vec3 deepOpticalBlue = mix(vec3(0.015, 0.155, 0.285), vec3(0.035, 0.235, 0.405), celestialDaylight);
    vec3 skyReflection = mix(vec3(0.12, 0.18, 0.29), vec3(0.60, 0.78, 0.93), celestialDaylight);
    vec3 color = mix(bodyColor, deepOpticalBlue, 0.22);
    color = mix(color, skyReflection, fresnel * (0.44 + sea * 0.16));
    color = mix(color, vec3(0.88, 0.96, 1.0), slopeFoam * (0.14 + sea * 0.26));
    color += vec3(0.44, 0.57, 0.72) * microShimmer * (0.24 + sea * 0.62);
    color += celestialColor * celestialSpecular * (0.34 + sea * 0.24);
    color *= max(lightColor, vec3(0.56));
    color = max(color, bodyColor * 0.52);
    color *= ColorModulator.rgb;

    float alpha = clamp(
        vertexColor.a * ColorModulator.a
            + fresnel * 0.13
            + slopeFoam * 0.045,
        0.0,
        0.985
    );
    float fogRange = max(0.001, FogEnd - FogStart);
    float fogFactor = clamp((vertexDistance - FogStart) / fogRange, 0.0, 1.0);
    fragColor = mix(vec4(color, alpha), FogColor, fogFactor);
}
