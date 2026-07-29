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
uniform float WindSpeed;
uniform vec4 SpectrumState;
uniform float TideOffset;
uniform float GpuWaveStrength;
uniform float ImpulseCount;
uniform vec4 ImpulsePosition0;
uniform vec4 ImpulsePosition1;
uniform vec4 ImpulsePosition2;
uniform vec4 ImpulsePosition3;
uniform vec4 ImpulsePosition4;
uniform vec4 ImpulsePosition5;
uniform vec4 ImpulsePosition6;
uniform vec4 ImpulsePosition7;
uniform vec4 ImpulseShape0;
uniform vec4 ImpulseShape1;
uniform vec4 ImpulseShape2;
uniform vec4 ImpulseShape3;
uniform vec4 ImpulseShape4;
uniform vec4 ImpulseShape5;
uniform vec4 ImpulseShape6;
uniform vec4 ImpulseShape7;
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
out float surfaceContinuity;
out vec2 localCurrent;
out float shoreFactor;
out float depthFactor;
out float disturbanceStrength;
out vec3 celestialDirection;
out float celestialDaylight;

vec2 normalizedOr(vec2 direction, vec2 fallbackDirection) {
    float directionLength = length(direction);
    return directionLength > 0.000001 ? direction / directionLength : fallbackDirection;
}

float decodeSignedPayload(float channelByte) {
    float code = mod(channelByte, 8.0);
    if (code < 0.5 || code > 6.5) {
        return 0.0;
    }
    float level = code <= 3.5 ? code : code - 3.0;
    float signValue = code <= 3.5 ? 1.0 : -1.0;
    float magnitude = level < 1.5 ? 0.10 : (level < 2.5 ? 0.45 : 1.50);
    return signValue * magnitude;
}

float decodeUnitPayload(float channelByte) {
    return mod(channelByte, 8.0) / 7.0;
}

float displayChannel(float channelByte) {
    return min(255.0, floor(channelByte / 8.0) * 8.0 + 4.0) / 255.0;
}

void accumulateWave(vec2 worldXZ, vec4 parameters, vec4 shape,
                    float spectrumBlend, float bodyWeight,
                    inout float height, inout vec2 horizontalDisplacement,
                    inout vec3 tangentXDelta, inout vec3 tangentZDelta) {
    vec2 baseDirection = normalizedOr(parameters.xy, vec2(1.0, 0.0));
    vec2 wind = normalizedOr(WindDirection, vec2(1.0, 0.0));
    float directionBlend = SpectrumState.z * (0.35 + shape.w * 0.65) * spectrumBlend;
    vec2 direction = normalizedOr(mix(baseDirection, wind, directionBlend), baseDirection);
    float spectrumEnergy = mix(SpectrumState.x, SpectrumState.y, shape.w);
    float energy = mix(1.0, spectrumEnergy, spectrumBlend);
    float amplitude = shape.x * energy * bodyWeight;
    float phase = parameters.z * dot(worldXZ, direction)
        - parameters.w * GameTime + shape.y;
    float sine = sin(phase);
    float cosine = cos(phase);
    float horizontalScale = shape.z * amplitude;
    height += amplitude * sine;
    horizontalDisplacement += horizontalScale * direction * cosine;

    // These are the same analytic tangent terms evaluated by
    // GerstnerWaveProfile.sampleAt on the CPU.
    float horizontalDerivative = horizontalScale * parameters.z * sine;
    float verticalDerivative = amplitude * parameters.z * cosine;
    tangentXDelta += vec3(
        -horizontalDerivative * direction.x * direction.x,
        verticalDerivative * direction.x,
        -horizontalDerivative * direction.x * direction.y
    );
    tangentZDelta += vec3(
        -horizontalDerivative * direction.x * direction.y,
        verticalDerivative * direction.y,
        -horizontalDerivative * direction.y * direction.y
    );
}

void accumulateImpulse(vec2 worldXZ, vec4 impulse, vec4 shape,
                       inout float height, inout vec2 gradient, inout float activity) {
    if (shape.w < 0.5 || abs(impulse.w) < 0.000001) {
        return;
    }
    vec2 delta = worldXZ - impulse.xy;
    float centerWidth = max(shape.x, 0.001);
    float ringWidth = max(shape.y, 0.001);
    float influenceRadius = max(centerWidth * 3.0, impulse.z + ringWidth * 3.0);
    float distanceSquared = dot(delta, delta);
    if (distanceSquared > influenceRadius * influenceRadius) {
        return;
    }
    float distance = max(sqrt(distanceSquared), 0.0001);
    float centerPosition = distance / centerWidth;
    float ringPosition = (distance - impulse.z) / ringWidth;
    float center = exp(-(centerPosition * centerPosition));
    float ring = exp(-(ringPosition * ringPosition));
    float localHeight = -impulse.w * center + impulse.w * shape.z * ring;

    float centerDerivative = impulse.w * center * (2.0 * distance / (centerWidth * centerWidth));
    float ringDerivative = -impulse.w * shape.z * ring
        * (2.0 * (distance - impulse.z) / (ringWidth * ringWidth));
    height += localHeight;
    gradient += (centerDerivative + ringDerivative) * delta / distance;
    activity += abs(localHeight) * 4.0 + length(gradient) * 0.20;
}

void main() {
    float sea = clamp(SeaState, 0.0, 1.0);
    vec2 worldXZ = Position.xz;
    vec4 encodedColor = floor(Color * 255.0 + 0.5);
    localCurrent = vec2(
        decodeSignedPayload(encodedColor.r),
        decodeSignedPayload(encodedColor.g)
    );
    shoreFactor = decodeUnitPayload(encodedColor.b);
    depthFactor = decodeUnitPayload(encodedColor.a);
    vertexColor = vec4(
        displayChannel(encodedColor.r),
        displayChannel(encodedColor.g),
        displayChannel(encodedColor.b),
        displayChannel(encodedColor.a)
    );
    vec3 bodyBlend = max(Normal, vec3(0.0));
    surfaceContinuity = clamp(bodyBlend.x + bodyBlend.y + bodyBlend.z, 0.0, 1.0);
    bodyBlend /= max(surfaceContinuity, 0.0001);
    waterBodyBlend = bodyBlend;
    float gpuHeight = 0.0;
    vec2 horizontalDisplacement = vec2(0.0);
    vec3 tangentXDelta = vec3(0.0);
    vec3 tangentZDelta = vec3(0.0);
    accumulateWave(worldXZ, OceanWaveParam0, OceanWaveShape0, 1.0, bodyBlend.x,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(worldXZ, OceanWaveParam1, OceanWaveShape1, 1.0, bodyBlend.x,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(worldXZ, OceanWaveParam2, OceanWaveShape2, 1.0, bodyBlend.x,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(worldXZ, OceanWaveParam3, OceanWaveShape3, 1.0, bodyBlend.x,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(worldXZ, RiverWaveParam0, RiverWaveShape0, 0.0, bodyBlend.y,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(worldXZ, RiverWaveParam1, RiverWaveShape1, 0.0, bodyBlend.y,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(worldXZ, RiverWaveParam2, RiverWaveShape2, 0.0, bodyBlend.y,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(worldXZ, RiverWaveParam3, RiverWaveShape3, 0.0, bodyBlend.y,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(worldXZ, PondWaveParam0, PondWaveShape0, 0.0, bodyBlend.z,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(worldXZ, PondWaveParam1, PondWaveShape1, 0.0, bodyBlend.z,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(worldXZ, PondWaveParam2, PondWaveShape2, 0.0, bodyBlend.z,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(worldXZ, PondWaveParam3, PondWaveShape3, 0.0, bodyBlend.z,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    float impulseHeight = 0.0;
    vec2 impulseGradient = vec2(0.0);
    float impulseActivity = 0.0;
    if (ImpulseCount > 0.5) {
        accumulateImpulse(worldXZ, ImpulsePosition0, ImpulseShape0,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 1.5) {
        accumulateImpulse(worldXZ, ImpulsePosition1, ImpulseShape1,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 2.5) {
        accumulateImpulse(worldXZ, ImpulsePosition2, ImpulseShape2,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 3.5) {
        accumulateImpulse(worldXZ, ImpulsePosition3, ImpulseShape3,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 4.5) {
        accumulateImpulse(worldXZ, ImpulsePosition4, ImpulseShape4,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 5.5) {
        accumulateImpulse(worldXZ, ImpulsePosition5, ImpulseShape5,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 6.5) {
        accumulateImpulse(worldXZ, ImpulsePosition6, ImpulseShape6,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 7.5) {
        accumulateImpulse(worldXZ, ImpulsePosition7, ImpulseShape7,
            impulseHeight, impulseGradient, impulseActivity);
    }
    impulseHeight = clamp(impulseHeight, -0.25, 0.25);
    impulseGradient = clamp(impulseGradient, vec2(-1.25), vec2(1.25));
    disturbanceStrength = clamp(impulseActivity, 0.0, 1.0);

    vec3 displacedPosition = Position;
    // Flatten displacement continuously at dry or unloaded boundaries. This
    // makes the stable custom mesh meet the safe fluid fallback without a
    // raised, visibly flat perimeter ring or cracks between ownership modes.
    float continuityWave = smoothstep(0.18, 0.92, surfaceContinuity);
    float shoreHorizontalTaper = 1.0 - smoothstep(0.18, 0.88, shoreFactor);
    float horizontalTaper = continuityWave * shoreHorizontalTaper;
    displacedPosition.xz += horizontalDisplacement * GpuWaveStrength * horizontalTaper;
    displacedPosition.y += (gpuHeight * GpuWaveStrength + impulseHeight) * continuityWave
        + TideOffset * bodyBlend.x * continuityWave;
    vec3 tangentX = vec3(
        1.0 + tangentXDelta.x * GpuWaveStrength * horizontalTaper,
        tangentXDelta.y * GpuWaveStrength * continuityWave
            + impulseGradient.x * continuityWave,
        tangentXDelta.z * GpuWaveStrength * horizontalTaper
    );
    vec3 tangentZ = vec3(
        tangentZDelta.x * GpuWaveStrength * horizontalTaper,
        tangentZDelta.y * GpuWaveStrength * continuityWave
            + impulseGradient.y * continuityWave,
        1.0 + tangentZDelta.z * GpuWaveStrength * horizontalTaper
    );
    vec3 analyticNormal = cross(tangentZ, tangentX);
    vec3 combinedNormal = length(analyticNormal) > 0.00001
        ? normalize(analyticNormal)
        : vec3(0.0, 1.0, 0.0);

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
    vec2 wind = normalize(WindDirection + vec2(0.0001, 0.0));
    float windPhase = dot(worldXZ, wind) * 0.16
        + GameTime * (0.24 + WindSpeed * 0.055);
    vec2 windRipple = wind * sin(windPhase) * (0.0015 + sea * 0.0035);
    vec2 currentRipple = normalizedOr(localCurrent, vec2(0.0))
        * sin(dot(worldXZ, normalizedOr(localCurrent, wind)) * 0.55
            - GameTime * (0.7 + length(localCurrent) * 1.4))
        * min(0.0045, length(localCurrent) * 0.0025);
    texCoord0 = UV0 + windRipple + vec2(
        sin(GameTime * 0.35 + worldXZ.y * 0.18),
        cos(GameTime * 0.27 + worldXZ.x * 0.16)
    ) * 0.0035 + currentRipple;
    lightColor = minecraft_sample_lightmap(Sampler2, UV2).rgb;
    gl_Position = ProjMat * view;
}
