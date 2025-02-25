#version 150

uniform sampler2D waterTexture;
uniform sampler2D underwaterTexture;
uniform float time;
uniform float waterDepth;
uniform vec3 lightDir;
uniform vec3 viewDir;

in vec3 worldPos;
in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec2 flowUV = texCoord + vec2(sin(time + worldPos.x * 0.2) * 0.02, cos(time + worldPos.z * 0.2) * 0.02);
    vec4 baseColor = texture(waterTexture, flowUV);

    float depthFactor = smoothstep(0.0, waterDepth, worldPos.y);
    vec4 deepColor = vec4(0.0, 0.1, 0.3, 1.0);
    vec4 shallowColor = vec4(0.3, 0.6, 0.9, 1.0);
    vec4 depthColor = mix(deepColor, shallowColor, depthFactor);

    float foamIntensity = smoothstep(0.6, 0.8, abs(sin(worldPos.y * 5.0)));
    vec4 foamColor = vec4(1.0, 1.0, 1.0, foamIntensity);

    vec3 normal = normalize(vec3(dFdx(worldPos.y), 1.0, dFdz(worldPos.y)));
    vec3 reflection = reflect(-lightDir, normal);
    float specular = pow(max(dot(reflection, viewDir), 0.0), 32.0);
    vec4 highlightColor = vec4(1.0, 1.0, 0.8, specular);

    vec4 finalColor = mix(baseColor, depthColor, 0.5);
    finalColor = mix(finalColor, foamColor, foamIntensity);
    finalColor = mix(finalColor, highlightColor, specular);

    fragColor = finalColor;
}
