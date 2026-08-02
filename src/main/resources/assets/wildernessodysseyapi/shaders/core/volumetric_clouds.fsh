#version 150

uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec2 WorldOrigin;
uniform vec2 WindOffset;
uniform float DetailStrength;
uniform vec3 SunDirection;

in vec2 localNoisePosition;
in vec4 vertexColor;
in vec3 columnData;

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
    float result = valueNoise(point) * 0.58;
    result += valueNoise(point * 2.03 + 11.7) * 0.28;
    result += valueNoise(point * 4.09 - 7.2) * 0.14;
    return result;
}

void main() {
    float packedLayer = min(clamp(columnData.x, 0.0, 1.0) * 4.0, 3.999);
    float band = floor(packedLayer);
    float layer = clamp((fract(packedLayer) - 0.08) / 0.84, 0.0, 1.0);
    float coverage = clamp(columnData.y, 0.0, 1.0);
    float packedShape = min(clamp(columnData.z, 0.0, 1.0) * 4.0, 3.999);
    float morphology = floor(packedShape);
    float storm = clamp((fract(packedShape) - 0.10) / 0.80, 0.0, 1.0);
    vec2 wind = WindOffset + vec2(GameTime * 0.006, GameTime * 0.002);
    vec2 world = localNoisePosition + WorldOrigin + wind;

    // Meteorological families use distinct spatial scales: stretched high
    // wisps, broad sheets, cauliflower cells, and vertically varied towers.
    vec2 shapedWorld = world;
    shapedWorld = mix(shapedWorld, vec2(world.x * 0.34 + world.y * 0.08, world.y * 2.45),
            1.0 - step(0.5, morphology));
    shapedWorld = mix(shapedWorld, world * 0.62, step(0.5, morphology) * (1.0 - step(1.5, morphology)));
    shapedWorld = mix(shapedWorld, world * 1.18, step(1.5, morphology) * (1.0 - step(2.5, morphology)));
    shapedWorld = mix(shapedWorld, world * (0.82 + layer * 0.58), step(2.5, morphology));

    float verticalFrequency = mix(2.2, 5.2, step(1.5, morphology));
    verticalFrequency = mix(verticalFrequency, 7.0, step(2.5, morphology));
    float detail = cloudNoise(vec3(shapedWorld * 1.32,
            layer * verticalFrequency - GameTime * (0.004 + band * 0.0015)));
    detail = mix(0.58, detail, clamp(DetailStrength, 0.0, 1.0));

    float cellularShape = pow(max(0.0, sin(layer * 3.14159265)), 0.42);
    float sheetShape = smoothstep(0.02, 0.18, layer) * (1.0 - smoothstep(0.82, 0.98, layer));
    float wispyShape = pow(max(0.0, sin(layer * 3.14159265)), 0.22);
    float towerShape = pow(max(0.0, sin(layer * 3.14159265)), 0.30)
            * (0.80 + detail * 0.30);
    float verticalShape = wispyShape;
    verticalShape = mix(verticalShape, sheetShape, step(0.5, morphology));
    verticalShape = mix(verticalShape, cellularShape, step(1.5, morphology));
    verticalShape = mix(verticalShape, towerShape, step(2.5, morphology));

    float familyThreshold = 0.03 * (1.0 - step(0.5, morphology));
    familyThreshold -= 0.05 * (step(0.5, morphology) * (1.0 - step(1.5, morphology)));
    familyThreshold += 0.02 * step(2.5, morphology);
    float altitudeThinning = band == 2.0 ? 0.035 : 0.0;
    float threshold = 0.72 + familyThreshold + altitudeThinning
            - coverage * 0.40 + (1.0 - verticalShape) * 0.24;
    float density = smoothstep(threshold - 0.10, threshold + 0.10, detail);
    if (density < 0.015) {
        discard;
    }

    vec3 sun = normalize(SunDirection);
    float daylight = 0.72 + max(0.0, sun.y) * 0.22;
    float silverEdge = smoothstep(threshold - 0.03, threshold + 0.12, detail)
            * (1.0 - storm) * (0.65 + band * 0.10);
    vec3 color = vertexColor.rgb * daylight;
    color += vec3(0.10, 0.11, 0.13) * silverEdge * max(0.0, sun.y);
    color *= 1.0 - storm * (0.20 + (1.0 - layer) * 0.20);

    float alpha = vertexColor.a * density * (0.72 + verticalShape * 0.28);
    fragColor = vec4(color, alpha) * ColorModulator;
}
