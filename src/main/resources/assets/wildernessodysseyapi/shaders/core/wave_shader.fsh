#version 150
in vec2 vUV;
in vec4 vColor;
in vec3 worldPos;

uniform sampler2D waterTexture;
uniform sampler2D underwaterTexture;
uniform float waterDepth;
uniform vec3 lightDir;
uniform vec3 viewDir;

out vec4 FragColor;

void main() {
    // base water
    vec4 base = texture(waterTexture, vUV);
    // depth gradient
    float df = clamp(worldPos.y / waterDepth, 0.0,1.0);
    vec4 deep = vec4(0.0,0.1,0.3,1.0),
    shallow = vec4(0.3,0.6,0.9,1.0);
    vec4 depthColor = mix(deep, shallow, df);

    // foam at peaks
    float foam = smoothstep(0.6,0.8, abs(sin(worldPos.y*5.0)));
    vec4 foamColor = vec4(1.0,1.0,1.0,foam);

    // specular
    vec3 N = normalize(vec3(dFdx(worldPos.y),1.0,dFdz(worldPos.y)));
    vec3 R = reflect(-lightDir, N);
    float spec = pow(max(dot(R,viewDir),0.0),32.0);
    vec4 specCol = vec4(1.0,1.0,0.8,spec);

    // combine
    vec4 col = mix(base, depthColor, 0.5);
    col = mix(col, foamColor, foam);
    col = mix(col, specCol, spec);

    FragColor = col;
}
