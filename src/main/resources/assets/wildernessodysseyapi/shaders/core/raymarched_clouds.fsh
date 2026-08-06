#version 150

uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec2 WorldOrigin;
uniform vec2 WindOffset;
uniform vec3 CameraPosition;
uniform float DetailStrength;
uniform int RaymarchSteps;
uniform vec3 SunDirection;

in vec3 surfacePosition;
in vec2 localNoisePosition;
in vec4 vertexColor;
in vec3 volumeData;

out vec4 fragColor;

float hash13(vec3 point) {
    point = fract(point * 0.1031);
    point += dot(point, point.yzx + 33.33);
    return fract((point.x + point.y) * point.z);
}

float valueNoise(vec3 point) {
    vec3 cell = floor(point);
    vec3 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    float n000 = hash13(cell);
    float n100 = hash13(cell + vec3(1.0, 0.0, 0.0));
    float n010 = hash13(cell + vec3(0.0, 1.0, 0.0));
    float n110 = hash13(cell + vec3(1.0, 1.0, 0.0));
    float n001 = hash13(cell + vec3(0.0, 0.0, 1.0));
    float n101 = hash13(cell + vec3(1.0, 0.0, 1.0));
    float n011 = hash13(cell + vec3(0.0, 1.0, 1.0));
    float n111 = hash13(cell + vec3(1.0, 1.0, 1.0));
    float x00 = mix(n000, n100, local.x);
    float x10 = mix(n010, n110, local.x);
    float x01 = mix(n001, n101, local.x);
    float x11 = mix(n011, n111, local.x);
    return mix(mix(x00, x10, local.y), mix(x01, x11, local.y), local.z);
}

float cloudNoise(vec3 point) {
    float result = valueNoise(point) * 0.56;
    result += valueNoise(point * 2.03 + 11.7) * 0.29;
    result += valueNoise(point * 4.07 - 7.2) * 0.15;
    return result;
}

float densityAt(vec3 point, float base, float depth, float coverage, float morphology) {
    float vertical = clamp((point.y - base) / max(depth, 0.01), 0.0, 1.0);
    float rounded = pow(max(0.0, sin(vertical * 3.14159265)),
            mix(0.24, 0.48, step(0.5, morphology)));
    vec2 world = point.xz * (1.0 / 64.0) + WorldOrigin + WindOffset;
    world += vec2(GameTime * 0.006, GameTime * 0.002);
    vec2 shaped = world;
    shaped = mix(shaped, vec2(world.x * 0.34 + world.y * 0.08, world.y * 2.45),
            1.0 - step(0.5, morphology));
    shaped = mix(shaped, world * 0.68,
            step(0.5, morphology) * (1.0 - step(1.5, morphology)));
    shaped = mix(shaped, world * 1.16,
            step(1.5, morphology) * (1.0 - step(2.5, morphology)));
    shaped = mix(shaped, world * (0.86 + vertical * 0.48), step(2.5, morphology));
    float detail = cloudNoise(vec3(shaped * 1.30,
            vertical * mix(3.2, 6.4, step(1.5, morphology)) - GameTime * 0.004));
    detail = mix(0.60, detail, clamp(DetailStrength, 0.0, 1.0));
    float threshold = 0.71 - coverage * 0.34 + (1.0 - rounded) * 0.27;
    return smoothstep(threshold - 0.085, threshold + 0.085, detail);
}

void main() {
    float signedDepth = volumeData.x;
    float depth = max(1.0, abs(signedDepth) * 128.0);
    bool topFace = signedDepth > 0.0;
    float base = topFace ? surfacePosition.y - depth : surfacePosition.y;
    float middle = base + depth * 0.5;

    // Only the camera-facing shell starts a ray. This removes the visible
    // stack of internal horizontal planes that the former volume path exposed.
    if ((CameraPosition.y < middle && topFace)
            || (CameraPosition.y >= middle && !topFace)) {
        discard;
    }

    float coverage = clamp(vertexColor.a, 0.0, 1.0);
    float packedShape = min(clamp(volumeData.z, 0.0, 1.0) * 4.0, 3.999);
    float morphology = floor(packedShape);
    float storm = clamp((fract(packedShape) - 0.10) / 0.80, 0.0, 1.0);
    vec3 rayDirection = normalize(surfacePosition - CameraPosition);
    float stepLength = depth / float(max(RaymarchSteps, 1))
            / max(abs(rayDirection.y), 0.24);
    float transmittance = 1.0;
    float accumulated = 0.0;
    float lightAccumulation = 0.0;
    for (int sampleIndex = 0; sampleIndex < 64; sampleIndex++) {
        if (sampleIndex >= RaymarchSteps || transmittance < 0.025) {
            break;
        }
        vec3 point = surfacePosition + rayDirection * stepLength * (float(sampleIndex) + 0.5);
        float vertical = (point.y - base) / depth;
        if (vertical < 0.0 || vertical > 1.0) {
            break;
        }
        float density = densityAt(point, base, depth, coverage, morphology);
        float sampleAlpha = density * (0.075 + coverage * 0.095);
        float contribution = transmittance * sampleAlpha;
        accumulated += contribution;
        lightAccumulation += contribution * (0.64 + vertical * 0.36);
        transmittance *= 1.0 - sampleAlpha;
    }
    accumulated = min(accumulated, coverage * 0.98);
    if (accumulated < 0.018) {
        discard;
    }

    float meanLight = lightAccumulation / max(accumulated, 0.001);
    float daylight = 0.68 + max(0.0, normalize(SunDirection).y) * 0.25;
    vec3 color = vertexColor.rgb * daylight * meanLight;
    color += vec3(0.07, 0.075, 0.085) * (1.0 - storm) * (1.0 - transmittance);
    color *= 1.0 - storm * 0.28;
    fragColor = vec4(color, accumulated) * ColorModulator;
}
