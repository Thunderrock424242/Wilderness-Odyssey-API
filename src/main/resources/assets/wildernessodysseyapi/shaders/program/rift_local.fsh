#version 150
uniform sampler2D DiffuseSampler;
uniform float Intensity;
uniform float Pulse;
uniform float Critical;
uniform float Time;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 p = texCoord - 0.5;
    float radius = length(p);
    float strength = clamp(Intensity, 0.0, 1.0);
    float pulse = clamp(Pulse, 0.0, 1.0);
    vec2 warp = p * sin(radius * 19.0 - Time * 3.0) * strength * pulse * 0.004;
    vec2 uv = clamp(texCoord + warp, vec2(0.002), vec2(0.998));
    vec2 chroma = vec2(strength * pulse * 0.0014, 0.0);
    vec3 base = texture(DiffuseSampler, uv).rgb;
    vec3 color = vec3(texture(DiffuseSampler, clamp(uv + chroma, 0.0, 1.0)).r, base.g,
                      texture(DiffuseSampler, clamp(uv - chroma, 0.0, 1.0)).b);
    // Small spatial echo during pulses; no historical frame retention or constant blur.
    vec3 echoColor = texture(DiffuseSampler, clamp(uv - warp * 3.0, 0.0, 1.0)).rgb;
    color = mix(color, echoColor, strength * pulse * 0.06);
    color = mix(color, color * vec3(0.94 + Critical * 0.035, 0.99, 1.04), strength * 0.16);
    float vignette = smoothstep(0.26, 0.70, radius) * strength * (0.035 + pulse * Critical * 0.09);
    fragColor = vec4(color * (1.0 - vignette), 1.0);
}
