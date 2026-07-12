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
uniform float DayTime;
uniform float SeaState;
uniform vec2 WindDirection;
uniform float TideOffset;
uniform float GpuWaveStrength;
uniform vec4 OceanWaveParam0;
uniform vec4 OceanWaveParam1;
uniform vec4 OceanWaveParam2;
uniform vec4 OceanWaveParam3;
uniform vec4 OceanWaveShape0;
uniform vec4 OceanWaveShape1;
uniform vec4 OceanWaveShape2;
uniform vec4 OceanWaveShape3;
uniform vec4 RiverWaveParam0;
uniform vec4 RiverWaveParam1;
uniform vec4 RiverWaveParam2;
uniform vec4 RiverWaveParam3;
uniform vec4 RiverWaveShape0;
uniform vec4 RiverWaveShape1;
uniform vec4 RiverWaveShape2;
uniform vec4 RiverWaveShape3;
uniform vec4 PondWaveParam0;
uniform vec4 PondWaveParam1;
uniform vec4 PondWaveParam2;
uniform vec4 PondWaveParam3;
uniform vec4 PondWaveShape0;
uniform vec4 PondWaveShape1;
uniform vec4 PondWaveShape2;
uniform vec4 PondWaveShape3;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 lightColor;
out vec3 viewPosition;
out vec3 viewNormal;
out vec3 worldPosition;
out vec3 worldNormal;
out vec3 waterBodyBlend;
out vec3 celestialDirection;
out float celestialDaylight;

vec2 normalizedOr(vec2 direction, vec2 fallbackDirection) {
    float directionLength = length(direction);
    return directionLength > 0.000001 ? direction / directionLength : fallbackDirection;
}

void accumulateWave(vec2 worldXZ, vec4 parameters, vec4 shape, float sea,
                    inout float height, inout vec2 gradient) {
    vec2 baseDirection = normalizedOr(parameters.xy, vec2(1.0, 0.0));
    vec2 wind = normalizedOr(WindDirection, vec2(1.0, 0.0));
    float directionBlend = clamp(sea * (0.12 + shape.w * 0.42), 0.0, 0.62);
    vec2 direction = normalizedOr(mix(baseDirection, wind, directionBlend), baseDirection);
    float energy = mix(0.92 + sea * 0.16, 0.86 + sea * 0.48, shape.w);
    float amplitude = shape.x * energy;
    float phase = parameters.z * dot(worldXZ, direction)
        - parameters.w * GameTime + shape.y;
    height += amplitude * sin(phase);
    gradient += amplitude * parameters.z * cos(phase) * direction;
}

void main() {
    float sea = clamp(SeaState, 0.0, 1.0);
    vec2 worldXZ = Position.xz;
    vec3 bodyBlend = max(Normal, vec3(0.0));
    bodyBlend /= max(bodyBlend.x + bodyBlend.y + bodyBlend.z, 0.0001);
    waterBodyBlend = bodyBlend;
    float oceanHeight = 0.0;
    vec2 oceanGradient = vec2(0.0);
    accumulateWave(worldXZ, OceanWaveParam0, OceanWaveShape0, sea, oceanHeight, oceanGradient);
    accumulateWave(worldXZ, OceanWaveParam1, OceanWaveShape1, sea, oceanHeight, oceanGradient);
    accumulateWave(worldXZ, OceanWaveParam2, OceanWaveShape2, sea, oceanHeight, oceanGradient);
    accumulateWave(worldXZ, OceanWaveParam3, OceanWaveShape3, sea, oceanHeight, oceanGradient);

    float riverHeight = 0.0;
    vec2 riverGradient = vec2(0.0);
    accumulateWave(worldXZ, RiverWaveParam0, RiverWaveShape0, sea, riverHeight, riverGradient);
    accumulateWave(worldXZ, RiverWaveParam1, RiverWaveShape1, sea, riverHeight, riverGradient);
    accumulateWave(worldXZ, RiverWaveParam2, RiverWaveShape2, sea, riverHeight, riverGradient);
    accumulateWave(worldXZ, RiverWaveParam3, RiverWaveShape3, sea, riverHeight, riverGradient);

    float pondHeight = 0.0;
    vec2 pondGradient = vec2(0.0);
    accumulateWave(worldXZ, PondWaveParam0, PondWaveShape0, sea, pondHeight, pondGradient);
    accumulateWave(worldXZ, PondWaveParam1, PondWaveShape1, sea, pondHeight, pondGradient);
    accumulateWave(worldXZ, PondWaveParam2, PondWaveShape2, sea, pondHeight, pondGradient);
    accumulateWave(worldXZ, PondWaveParam3, PondWaveShape3, sea, pondHeight, pondGradient);

    float gpuHeight = dot(bodyBlend, vec3(oceanHeight, riverHeight, pondHeight));
    vec2 gpuGradient = oceanGradient * bodyBlend.x
        + riverGradient * bodyBlend.y
        + pondGradient * bodyBlend.z;

    vec3 displacedPosition = Position;
    displacedPosition.y += gpuHeight * GpuWaveStrength
        + TideOffset * bodyBlend.x;
    vec3 gpuNormal = normalize(vec3(
        -gpuGradient.x * GpuWaveStrength,
        1.0,
        -gpuGradient.y * GpuWaveStrength
    ));
    vec3 combinedNormal = gpuNormal;

    vec4 view = ModelViewMat * vec4(displacedPosition, 1.0);
    viewPosition = view.xyz;
    viewNormal = normalize(mat3(ModelViewMat) * combinedNormal);
    worldPosition = displacedPosition;
    worldNormal = combinedNormal;

    float celestialAngle = DayTime * 6.28318530718;
    vec3 sunDirection = normalize(vec3(cos(celestialAngle), sin(celestialAngle), 0.20));
    celestialDaylight = step(0.0, sunDirection.y);
    vec3 activeLightDirection = celestialDaylight > 0.5 ? sunDirection : -sunDirection;
    celestialDirection = normalize(mat3(ModelViewMat) * activeLightDirection);
    vertexDistance = length(view.xyz);
    vertexColor = Color;

    vec2 wind = normalize(WindDirection + vec2(0.0001, 0.0));
    float windPhase = dot(worldXZ, wind) * 0.16 + GameTime * (0.30 + sea * 0.90);
    vec2 windRipple = wind * sin(windPhase) * (0.0015 + sea * 0.0035);
    texCoord0 = UV0 + windRipple + vec2(
        sin(GameTime * 0.35 + worldXZ.y * 0.18),
        cos(GameTime * 0.27 + worldXZ.x * 0.16)
    ) * 0.0035;
    lightColor = minecraft_sample_lightmap(Sampler2, UV2).rgb;
    gl_Position = ProjMat * view;
}
