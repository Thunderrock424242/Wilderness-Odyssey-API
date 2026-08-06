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
uniform vec2 ChunkOrigin;
uniform float DayTime;
uniform float SeaState;
uniform vec2 WindDirection;
uniform float WindSpeed;
uniform vec4 SpectrumState;
uniform float RegionalSeaStateEnabled;
uniform mat4 RegionalSeaStateCorners;
uniform mat4 RegionalSpectrumCorners;
uniform vec4 Weather;
uniform float TideOffset;
uniform float GpuWaveStrength;
uniform float ImpulseCount;
uniform vec2 ImpulseChunkIndex;
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
out vec3 worldNormal;
out vec2 phaseLocalXZ;
flat out vec2 phaseChunkIndex;
out vec3 waterBodyBlend;
out float surfaceContinuity;
out vec2 localCurrent;
out float shoreFactor;
out float depthFactor;
out float disturbanceStrength;
out vec3 celestialDirection;
out float celestialDaylight;
out float regionalSeaState;
out vec2 regionalWindDirection;
out float regionalWindSpeed;
out vec4 regionalSpectrumState;

vec2 normalizedOr(vec2 direction, vec2 fallbackDirection) {
    float directionLength = length(direction);
    return directionLength > 0.000001 ? direction / directionLength : fallbackDirection;
}

const float TWO_PI = 6.28318530718;
const float PHASE_CHUNK_SPAN = 16.0;
const float PHASE_COARSE_CHUNKS = 1024.0;
vec4 bilinearCornerState(mat4 corners, vec2 blend) {
    vec4 north = mix(corners[0], corners[3], blend.x);
    vec4 south = mix(corners[1], corners[2], blend.x);
    return mix(north, south, blend.y);
}

// Snapshot chunks share exact world-space corner samples. Bilinear evaluation
// therefore returns identical displacement/material state on both copies of a
// chunk edge while keeping camera motion out of the environmental field.
void resolveRegionalOceanState(vec2 localXZ, out vec4 state, out vec4 spectrum) {
    if (RegionalSeaStateEnabled < 0.5) {
        state = vec4(SeaState, WindDirection, WindSpeed);
        spectrum = SpectrumState;
        return;
    }
    vec2 blend = clamp(localXZ / PHASE_CHUNK_SPAN, vec2(0.0), vec2(1.0));
    state = bilinearCornerState(RegionalSeaStateCorners, blend);
    spectrum = bilinearCornerState(RegionalSpectrumCorners, blend);
}

// Evaluates a linear world-space phase without first adding a fractional local
// vertex to a multi-million-block world coordinate. Splitting the integral
// chunk origin preserves sub-block waves near the world edge.
float stableLinearPhase(vec2 localXZ, vec2 coefficient) {
    // Canonicalize boundary vertices before any phase math. A west-chunk
    // vertex at local x=16 then has the exact same operands as the east-chunk
    // copy at local x=0, including at Minecraft's world border.
    vec2 localChunkOffset = floor(localXZ / PHASE_CHUNK_SPAN);
    vec2 canonicalLocal = localXZ - localChunkOffset * PHASE_CHUNK_SPAN;
    vec2 chunkIndex = ChunkOrigin / PHASE_CHUNK_SPAN + localChunkOffset;
    vec2 coarseIndex = floor(chunkIndex / PHASE_COARSE_CHUNKS);
    vec2 fineIndex = chunkIndex - coarseIndex * PHASE_COARSE_CHUNKS;
    vec2 coarseStep = mod(
        coefficient * (PHASE_CHUNK_SPAN * PHASE_COARSE_CHUNKS),
        vec2(TWO_PI)
    );
    vec2 fineCoordinate = fineIndex * PHASE_CHUNK_SPAN + canonicalLocal;
    vec2 axisPhase = mod(coarseIndex * coarseStep + fineCoordinate * coefficient,
        vec2(TWO_PI));
    return mod(axisPhase.x + axisPhase.y, TWO_PI);
}

// River components are authored as a spread around +X. Rotating that local
// basis onto the decoded current preserves the authored spread while making
// crests travel with real synchronized canonical flow where it is available.
vec2 flowRelativeDirection(vec2 authoredDirection, vec2 flowDirection) {
    float flowLength = length(flowDirection);
    if (flowLength <= 0.000001) {
        return authoredDirection;
    }
    vec2 flow = flowDirection / flowLength;
    vec2 flowRight = vec2(-flow.y, flow.x);
    return normalizedOr(flow * authoredDirection.x + flowRight * authoredDirection.y,
        authoredDirection);
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

void accumulateWave(vec2 localXZ, vec4 parameters, vec4 shape,
                    float spectrumBlend, float bodyWeight, vec2 flowDirection,
                    vec2 regionalWind, vec4 regionalSpectrum,
                    inout float height, inout vec2 horizontalDisplacement,
                    inout vec3 tangentXDelta, inout vec3 tangentZDelta) {
    // Disabled quality-tier components and absent body blends avoid all
    // normalization, phase, sine, and cosine work.
    if (bodyWeight <= 0.000001 || shape.x <= 0.000001) {
        return;
    }
    vec2 baseDirection = normalizedOr(parameters.xy, vec2(1.0, 0.0));
    baseDirection = flowRelativeDirection(baseDirection, flowDirection);
    vec2 wind = normalizedOr(regionalWind, vec2(1.0, 0.0));
    vec2 direction = baseDirection;
    float spectrumEnergy = mix(regionalSpectrum.x, regionalSpectrum.y, shape.w);
    float windAlignment = max(0.0, dot(direction, wind));
    float alignedEnergy = 0.55 + windAlignment * 0.90;
    float directionalEnergy = mix(1.0, alignedEnergy, regionalSpectrum.z);
    float energy = mix(1.0, spectrumEnergy * directionalEnergy, spectrumBlend);
    float amplitude = shape.x * energy * bodyWeight;
    float phase = stableLinearPhase(localXZ, parameters.z * direction) + shape.y;
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

void accumulateImpulse(vec2 localXZ, vec4 impulse, vec4 shape,
                       inout float height, inout vec2 gradient, inout float activity) {
    // Shape.w carries a slower foam envelope. This lets the white wake trail
    // remain after the short geometric depression settles without adding a
    // second unbounded buffer or another fragment-stage search.
    if (shape.w <= 0.000001 && abs(impulse.w) < 0.000001) {
        return;
    }
    // Both operands stay close to the camera: chunk indices subtract exactly,
    // while each uploaded wake center is relative to the camera chunk origin.
    vec2 relativeChunkOrigin = (ChunkOrigin / PHASE_CHUNK_SPAN
        - ImpulseChunkIndex) * PHASE_CHUNK_SPAN;
    vec2 delta = localXZ + relativeChunkOrigin - impulse.xy;
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
    float persistentFoam = max(ring, center * 0.30) * shape.w;
    activity += abs(localHeight) * 4.0 + length(gradient) * 0.20 + persistentFoam;
}

void main() {
    vec2 localXZ = Position.xz;
    vec4 frameSeaState;
    vec4 frameSpectrum;
    resolveRegionalOceanState(localXZ, frameSeaState, frameSpectrum);
    float sea = clamp(frameSeaState.x, 0.0, 1.0);
    vec2 frameWind = normalizedOr(frameSeaState.yz, vec2(1.0, 0.0));
    regionalSeaState = sea;
    regionalWindDirection = frameWind;
    regionalWindSpeed = max(0.0, frameSeaState.w);
    regionalSpectrumState = frameSpectrum;
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
    accumulateWave(localXZ, OceanWaveParam0, OceanWaveShape0, 1.0, bodyBlend.x, vec2(0.0),
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(localXZ, OceanWaveParam1, OceanWaveShape1, 1.0, bodyBlend.x, vec2(0.0),
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(localXZ, OceanWaveParam2, OceanWaveShape2, 1.0, bodyBlend.x, vec2(0.0),
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(localXZ, OceanWaveParam3, OceanWaveShape3, 1.0, bodyBlend.x, vec2(0.0),
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(localXZ, RiverWaveParam0, RiverWaveShape0, 0.0, bodyBlend.y, localCurrent,
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(localXZ, RiverWaveParam1, RiverWaveShape1, 0.0, bodyBlend.y, localCurrent,
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(localXZ, RiverWaveParam2, RiverWaveShape2, 0.0, bodyBlend.y, localCurrent,
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(localXZ, RiverWaveParam3, RiverWaveShape3, 0.0, bodyBlend.y, localCurrent,
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(localXZ, PondWaveParam0, PondWaveShape0, 0.0, bodyBlend.z, vec2(0.0),
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(localXZ, PondWaveParam1, PondWaveShape1, 0.0, bodyBlend.z, vec2(0.0),
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(localXZ, PondWaveParam2, PondWaveShape2, 0.0, bodyBlend.z, vec2(0.0),
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    accumulateWave(localXZ, PondWaveParam3, PondWaveShape3, 0.0, bodyBlend.z, vec2(0.0),
        frameWind, frameSpectrum,
        gpuHeight, horizontalDisplacement, tangentXDelta, tangentZDelta);
    float impulseHeight = 0.0;
    vec2 impulseGradient = vec2(0.0);
    float impulseActivity = 0.0;
    if (ImpulseCount > 0.5) {
        accumulateImpulse(localXZ, ImpulsePosition0, ImpulseShape0,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 1.5) {
        accumulateImpulse(localXZ, ImpulsePosition1, ImpulseShape1,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 2.5) {
        accumulateImpulse(localXZ, ImpulsePosition2, ImpulseShape2,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 3.5) {
        accumulateImpulse(localXZ, ImpulsePosition3, ImpulseShape3,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 4.5) {
        accumulateImpulse(localXZ, ImpulsePosition4, ImpulseShape4,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 5.5) {
        accumulateImpulse(localXZ, ImpulsePosition5, ImpulseShape5,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 6.5) {
        accumulateImpulse(localXZ, ImpulsePosition6, ImpulseShape6,
            impulseHeight, impulseGradient, impulseActivity);
    }
    if (ImpulseCount > 7.5) {
        accumulateImpulse(localXZ, ImpulsePosition7, ImpulseShape7,
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
    float frozen = clamp(Weather.w, 0.0, 1.0);
    float waveFreedom = 1.0 - frozen * 0.94;
    float horizontalTaper = continuityWave * shoreHorizontalTaper * waveFreedom;
    disturbanceStrength *= waveFreedom;
    displacedPosition.xz += horizontalDisplacement * GpuWaveStrength * horizontalTaper;
    displacedPosition.y += (gpuHeight * GpuWaveStrength + impulseHeight)
        * continuityWave * waveFreedom
        + TideOffset * bodyBlend.x * continuityWave;
    vec3 tangentX = vec3(
        1.0 + tangentXDelta.x * GpuWaveStrength * horizontalTaper,
        tangentXDelta.y * GpuWaveStrength * continuityWave * waveFreedom
            + impulseGradient.x * continuityWave * waveFreedom,
        tangentXDelta.z * GpuWaveStrength * horizontalTaper
    );
    vec3 tangentZ = vec3(
        tangentZDelta.x * GpuWaveStrength * horizontalTaper,
        tangentZDelta.y * GpuWaveStrength * continuityWave * waveFreedom
            + impulseGradient.y * continuityWave * waveFreedom,
        1.0 + tangentZDelta.z * GpuWaveStrength * horizontalTaper
    );
    vec3 analyticNormal = cross(tangentZ, tangentX);
    vec3 combinedNormal = length(analyticNormal) > 0.00001
        ? normalize(analyticNormal)
        : vec3(0.0, 1.0, 0.0);

    vec4 view = ModelViewMat * vec4(displacedPosition, 1.0);
    viewPosition = view.xyz;
    viewNormal = normalize(mat3(ModelViewMat) * combinedNormal);
    worldNormal = combinedNormal;
    // Material detail is parameterized on the undisturbed world plane. Sea
    // energy can move geometry without dragging the normal phase underneath it.
    phaseLocalXZ = localXZ;
    phaseChunkIndex = ChunkOrigin / PHASE_CHUNK_SPAN;

    float celestialAngle = DayTime * 6.28318530718;
    vec3 sunDirection = normalize(vec3(cos(celestialAngle), sin(celestialAngle), 0.20));
    celestialDaylight = step(0.0, sunDirection.y);
    vec3 activeLightDirection = celestialDaylight > 0.5 ? sunDirection : -sunDirection;
    celestialDirection = normalize(mat3(ModelViewMat) * activeLightDirection);
    vertexDistance = length(view.xyz);
    // The BLOCK format and stock fallback path still receive their ordinary
    // atlas coordinate. Surface motion belongs to world-space geometry and
    // procedural normals; offsetting atlas UVs can cross a sprite boundary and
    // turn transparent padding into block-shaped gaps.
    texCoord0 = UV0;
    lightColor = minecraft_sample_lightmap(Sampler2, UV2).rgb;
    gl_Position = ProjMat * view;
}
