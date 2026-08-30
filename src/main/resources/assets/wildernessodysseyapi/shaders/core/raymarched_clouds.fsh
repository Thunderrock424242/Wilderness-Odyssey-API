#version 150

uniform sampler2D CloudFieldPrevious;
uniform sampler2D CloudFieldCurrent;
uniform vec4 ColorModulator;
uniform float GameTime;
uniform vec2 RenderOrigin;
uniform vec2 WindOffset;
uniform vec3 CameraPosition;
uniform vec4 PreviousNearField;
uniform vec4 CurrentNearField;
uniform vec4 PreviousDistantField;
uniform vec4 CurrentDistantField;
uniform vec2 FieldTextureSize;
uniform float FieldBlend;
uniform float NearRadius;
uniform float DistantRadius;
uniform float DetailStrength;
uniform int RaymarchSteps;
uniform int LightingSteps;
uniform vec3 CloudColor;
uniform vec3 SunDirection;
uniform vec3 LightningPosition;
uniform float LightningIllumination;

in vec3 surfacePosition;
in vec2 localNoisePosition;
in vec4 vertexColor;
in vec3 volumeData;

out vec4 fragColor;

const float MINIMUM_BASE = -16.0;
const float BASE_RANGE = 176.0;
const float MAXIMUM_DEPTH = 128.0;

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

float cellularNoise(vec3 point) {
    vec2 cell = floor(point.xz);
    vec2 local = fract(point.xz);
    float distanceToCell = 1.0;
    for (int z = -1; z <= 1; z++) {
        for (int x = -1; x <= 1; x++) {
            vec2 neighbor = vec2(float(x), float(z));
            vec3 seed = vec3(cell + neighbor, floor(point.y));
            vec2 feature = neighbor + vec2(
                    hash13(seed + 3.1),
                    hash13(seed + 17.7)
            );
            float verticalOffset = abs(fract(point.y + hash13(seed + 29.4)) - 0.5) * 0.45;
            distanceToCell = min(distanceToCell, length(vec3(feature - local, verticalOffset)));
        }
    }
    return 1.0 - clamp(distanceToCell, 0.0, 1.0);
}

float fractalNoise(vec3 point) {
    float result = valueNoise(point) * 0.52;
    result += valueNoise(point * 2.03 + 11.7) * 0.28;
    result += valueNoise(point * 4.07 - 7.2) * 0.14;
    result += cellularNoise(point * 1.48 + 5.3) * 0.06;
    return result;
}

vec4 sampleAtlas(
        sampler2D fieldTexture,
        vec2 worldXZ,
        vec4 fieldInfo,
        bool distant,
        int band,
        bool morphologyPlane
) {
    float dimension = fieldInfo.w;
    if (dimension < 1.0) {
        return vec4(0.0);
    }
    vec2 grid = (worldXZ - fieldInfo.xy) / fieldInfo.z;
    if (grid.x < -0.5 || grid.y < -0.5
            || grid.x > dimension - 0.5 || grid.y > dimension - 0.5) {
        return vec4(0.0);
    }
    float nearRows = CurrentNearField.w * 4.0;
    float fieldRows = nearRows + CurrentDistantField.w * 4.0;
    float rowBase = distant ? nearRows + float(band) * dimension : float(band) * dimension;
    if (morphologyPlane) {
        rowBase += fieldRows;
    }
    vec2 uv = vec2(
            (grid.x + 0.5) / FieldTextureSize.x,
            (rowBase + grid.y + 0.5) / FieldTextureSize.y
    );
    return texture(fieldTexture, uv);
}

vec4 sampleField(vec2 worldXZ, bool distant, int band) {
    vec4 previousInfo = distant ? PreviousDistantField : PreviousNearField;
    vec4 currentInfo = distant ? CurrentDistantField : CurrentNearField;
    vec4 previous = sampleAtlas(
            CloudFieldPrevious, worldXZ, previousInfo, distant, band, false
    );
    vec4 current = sampleAtlas(
            CloudFieldCurrent, worldXZ, currentInfo, distant, band, false
    );
    return mix(previous, current, clamp(FieldBlend, 0.0, 1.0));
}

vec4 sampleMorphology(vec2 worldXZ, bool distant, int band) {
    vec4 previousInfo = distant ? PreviousDistantField : PreviousNearField;
    vec4 currentInfo = distant ? CurrentDistantField : CurrentNearField;
    vec4 previous = sampleAtlas(
            CloudFieldPrevious, worldXZ, previousInfo, distant, band, true
    );
    vec4 current = sampleAtlas(
            CloudFieldCurrent, worldXZ, currentInfo, distant, band, true
    );
    return mix(previous, current, clamp(FieldBlend, 0.0, 1.0));
}

vec2 intersectBox(vec3 origin, vec3 direction, vec3 boundsMinimum, vec3 boundsMaximum) {
    vec3 safeSign = sign(direction);
    safeSign.x = abs(direction.x) > 0.00001 ? safeSign.x : 1.0;
    safeSign.y = abs(direction.y) > 0.00001 ? safeSign.y : 1.0;
    safeSign.z = abs(direction.z) > 0.00001 ? safeSign.z : 1.0;
    vec3 inverseDirection = safeSign / max(abs(direction), vec3(0.00001));
    vec3 nearPlane = (boundsMinimum - origin) * inverseDirection;
    vec3 farPlane = (boundsMaximum - origin) * inverseDirection;
    vec3 minimumPlane = min(nearPlane, farPlane);
    vec3 maximumPlane = max(nearPlane, farPlane);
    float entry = max(max(minimumPlane.x, minimumPlane.y), minimumPlane.z);
    float exit = min(min(maximumPlane.x, maximumPlane.y), maximumPlane.z);
    return vec2(entry, exit);
}

// These padded slabs match CloudFieldAtlasModel. Limiting each genus band to
// its plausible altitude keeps the primary samples close enough together to
// resolve the underside instead of exposing slices through a 304-block box.
vec2 bandBounds(int band) {
    if (band == 0) {
        return vec2(-16.0, 48.0);
    }
    if (band == 1) {
        return vec2(16.0, 96.0);
    }
    if (band == 2) {
        return vec2(56.0, 160.0);
    }
    return vec2(-16.0, 144.0);
}

// Interleaved gradient noise is deterministic in screen space. It breaks up
// coherent ray slices without changing every frame and making clouds shimmer.
float stableRayJitter(vec2 pixel, int band, bool distant) {
    vec2 offset = vec2(float(band) * 19.19, distant ? 47.73 : 0.0);
    return fract(52.9829189 * fract(dot(
            floor(pixel) + offset,
            vec2(0.06711056, 0.00583715)
    )));
}

float verticalProfile(float vertical, float wispy, float layered, float cellular, float tower) {
    float sineProfile = max(0.0, sin(clamp(vertical, 0.0, 1.0) * 3.14159265));
    float wispyShape = pow(sineProfile, 0.18);
    float sheetShape = smoothstep(0.01, 0.12, vertical)
            * (1.0 - smoothstep(0.86, 0.99, vertical));
    float cellularShape = pow(sineProfile, 0.44);
    float towerShape = pow(sineProfile, 0.28) * (0.82 + vertical * 0.24);
    return wispyShape * wispy + sheetShape * layered
            + cellularShape * cellular + towerShape * tower;
}

float densityAt(vec3 localPoint, bool distant, int band, out float stormAmount) {
    vec2 worldXZ = localPoint.xz + RenderOrigin;
    vec4 field = sampleField(worldXZ, distant, band);
    float coverage = clamp(field.r, 0.0, 1.0);
    float base = field.g * BASE_RANGE + MINIMUM_BASE;
    float depth = max(1.0, field.b * MAXIMUM_DEPTH);
    stormAmount = field.a;
    if (coverage < 0.004) {
        return 0.0;
    }

    float cameraDistance = length(localPoint.xz - CameraPosition.xz);
    float nearFade = 1.0 - smoothstep(NearRadius - 72.0, NearRadius, cameraDistance);
    float distantFade = smoothstep(NearRadius - 96.0, NearRadius + 72.0, cameraDistance)
            * (1.0 - smoothstep(DistantRadius - 160.0, DistantRadius, cameraDistance));
    float fieldFade = distant ? distantFade : nearFade;
    if (fieldFade < 0.001) {
        return 0.0;
    }

    float vertical = (localPoint.y - base) / depth;
    if (vertical <= 0.0 || vertical >= 1.0) {
        return 0.0;
    }
    vec4 morphology = sampleMorphology(worldXZ, distant, band);
    float wispy = morphology.r;
    float layered = morphology.g;
    float cellular = morphology.b;
    float tower = morphology.a;
    float weightSum = wispy + layered + cellular + tower;
    if (weightSum < 0.001) {
        wispy = smoothstep(46.0, 78.0, base) * (1.0 - smoothstep(30.0, 64.0, depth));
        tower = smoothstep(28.0, 72.0, depth);
        layered = (1.0 - tower) * (1.0 - wispy) * smoothstep(0.48, 0.82, coverage);
        cellular = max(0.0, 1.0 - wispy - tower - layered);
        weightSum = wispy + layered + cellular + tower;
    }
    weightSum = max(0.001, weightSum);
    wispy /= weightSum;
    tower /= weightSum;
    layered /= weightSum;
    cellular /= weightSum;

    // Every procedural scale translates with the synchronized cloud wind.
    // Different rates model shear while retaining one coherent storm mass.
    vec2 macroWindWorld = (worldXZ - WindOffset * 0.78) / 64.0;
    vec2 mediumWindWorld = (worldXZ - WindOffset) / 64.0;
    vec2 fineWindWorld = (worldXZ - WindOffset * 1.16) / 64.0;
    vec2 macroWorld = vec2(macroWindWorld.x * 0.32 + macroWindWorld.y * 0.10,
            macroWindWorld.y * 2.65) * wispy
            + macroWindWorld * 0.58 * layered
            + macroWindWorld * 1.08 * cellular
            + macroWindWorld * (0.76 + vertical * 0.62) * tower;
    vec2 mediumWorld = vec2(mediumWindWorld.x * 0.32 + mediumWindWorld.y * 0.10,
            mediumWindWorld.y * 2.65) * wispy
            + mediumWindWorld * 0.58 * layered
            + mediumWindWorld * 1.08 * cellular
            + mediumWindWorld * (0.76 + vertical * 0.62) * tower;
    vec2 fineWorld = vec2(fineWindWorld.x * 0.32 + fineWindWorld.y * 0.10,
            fineWindWorld.y * 2.65) * wispy
            + fineWindWorld * 0.58 * layered
            + fineWindWorld * 1.08 * cellular
            + fineWindWorld * (0.76 + vertical * 0.62) * tower;

    // A slow world-scale mask rounds the outer silhouette independently from
    // the finer erosion, removing atlas-cell-shaped edges.
    float broadShape = valueNoise(vec3(macroWorld * (64.0 / 210.0),
            float(band) * 1.7 - GameTime * 0.0008));
    float mediumShape = fractalNoise(vec3(mediumWorld * 0.88,
            vertical * (3.2 + tower * 3.8) - GameTime * 0.0022));
    float fineShape = fractalNoise(vec3(fineWorld * 1.92 + 13.0,
            vertical * 7.4 + GameTime * 0.0015));
    float detail = mix(mediumShape, mediumShape * 0.76 + fineShape * 0.24,
            clamp(DetailStrength, 0.0, 1.0));
    float shape = verticalProfile(vertical, wispy, layered, cellular, tower);
    float threshold = 0.73 - coverage * 0.36
            + (1.0 - shape) * 0.26
            + (0.52 - broadShape) * 0.18;
    float edgeWidth = mix(0.12, 0.075, clamp(DetailStrength, 0.0, 1.0));
    float density = smoothstep(threshold - edgeWidth, threshold + edgeWidth, detail);

    return density * coverage * fieldFade;
}

float lightTransmittance(vec3 point, bool distant, int band, vec3 sunDirection) {
    float opticalDepth = 0.0;
    float storm = 0.0;
    float stepLength = distant ? 18.0 : 12.0;
    for (int index = 0; index < 6; index++) {
        if (index >= LightingSteps) {
            break;
        }
        vec3 samplePoint = point + sunDirection * stepLength * (float(index) + 1.0);
        opticalDepth += densityAt(samplePoint, distant, band, storm) * 0.23;
    }
    return exp(-opticalDepth);
}

void main() {
    int fieldLayer = int(floor(clamp(vertexColor.a, 0.0, 0.9999) * 8.0));
    bool distant = fieldLayer >= 4;
    int band = distant ? fieldLayer - 4 : fieldLayer;
    float horizontalRadius = distant ? DistantRadius : NearRadius;
    vec2 verticalBounds = bandBounds(band);
    vec3 boundsMinimum = vec3(-horizontalRadius, verticalBounds.x, -horizontalRadius);
    vec3 boundsMaximum = vec3(horizontalRadius, verticalBounds.y, horizontalRadius);
    vec3 rayDirection = normalize(surfacePosition - CameraPosition);
    vec2 intersection = intersectBox(CameraPosition, rayDirection, boundsMinimum, boundsMaximum);
    if (intersection.y <= max(intersection.x, 0.0)) {
        discard;
    }

    bool cameraInside = all(greaterThanEqual(CameraPosition, boundsMinimum))
            && all(lessThanEqual(CameraPosition, boundsMaximum));
    float carrierDistance = cameraInside ? intersection.y : max(intersection.x, 0.0);
    float surfaceDistance = length(surfacePosition - CameraPosition);
    float carrierTolerance = max(1.25, carrierDistance * 0.0015);
    if (abs(surfaceDistance - carrierDistance) > carrierTolerance) {
        discard;
    }

    float entry = max(intersection.x, 0.0);
    float exit = intersection.y;
    float rayLength = max(0.0, exit - entry);
    // Keep nearby samples on a stable physical interval whenever the bounded
    // quality budget permits it. Only long, grazing rays widen their spacing.
    float minimumStepLength = distant ? 8.0 : 4.0;
    float stepLength = max(
            minimumStepLength,
            rayLength / float(max(RaymarchSteps, 1))
    );
    float jitter = 0.325 + stableRayJitter(gl_FragCoord.xy, band, distant) * 0.35;
    float transmittance = 1.0;
    float accumulated = 0.0;
    float lightAccumulation = 0.0;
    float stormAccumulation = 0.0;
    float lightningAccumulation = 0.0;
    float cachedSunlight = 1.0;
    vec3 sun = normalize(SunDirection);
    for (int sampleIndex = 0; sampleIndex < 64; sampleIndex++) {
        if (sampleIndex >= RaymarchSteps || transmittance < 0.025) {
            break;
        }
        float sampleDistance = entry + stepLength * (float(sampleIndex) + jitter);
        if (sampleDistance >= exit) {
            break;
        }
        vec3 point = CameraPosition + rayDirection * sampleDistance;
        float storm = 0.0;
        float density = densityAt(point, distant, band, storm);
        if (density <= 0.001) {
            continue;
        }
        // Beer-Lambert extinction makes opacity depend on world distance, not
        // on the configured sample count or the carrier face hit by this ray.
        float extinction = distant ? 0.020 : 0.035;
        float sampleAlpha = 1.0 - exp(-density * stepLength * extinction);
        float contribution = transmittance * sampleAlpha;
        // Reuse one bounded sunlight probe for four neighboring primary
        // samples. This preserves self-shadowing without multiplying the
        // default ray budget by every lighting step.
        if (sampleIndex % 4 == 0) {
            cachedSunlight = lightTransmittance(point, distant, band, sun);
        }
        accumulated += contribution;
        lightAccumulation += contribution * cachedSunlight;
        stormAccumulation += contribution * storm;
        float lightningDistance = distance(point, LightningPosition);
        float localLightning = LightningIllumination
                * pow(max(0.0, 1.0 - lightningDistance / 360.0), 2.0);
        lightningAccumulation += contribution * localLightning;
        transmittance *= 1.0 - sampleAlpha;
    }
    if (accumulated < 0.012) {
        discard;
    }

    float meanLight = lightAccumulation / max(accumulated, 0.001);
    float meanStorm = stormAccumulation / max(accumulated, 0.001);
    float meanLightning = lightningAccumulation / max(accumulated, 0.001);
    float forwardScatter = pow(max(0.0, dot(rayDirection, sun)), 8.0) * (1.0 - meanStorm);
    float daylight = 0.62 + max(0.0, sun.y) * 0.28;
    vec3 color = CloudColor * daylight * (0.48 + meanLight * 0.52);
    color += vec3(0.13, 0.14, 0.16) * forwardScatter * (1.0 - transmittance);
    color *= 1.0 - meanStorm * 0.30;
    color += vec3(0.48, 0.62, 0.82) * meanLightning * (0.55 + meanStorm * 0.45);
    float distanceFade = distant ? 0.84 : 1.0;
    fragColor = vec4(color, min(0.985, accumulated * distanceFade)) * ColorModulator;
}
