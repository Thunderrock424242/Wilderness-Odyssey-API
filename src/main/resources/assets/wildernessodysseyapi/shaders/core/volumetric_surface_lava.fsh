#version 150

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

uniform float GameTime;

void main() {
    float flow = 0.5 + 0.5 * sin((texCoord.x * 18.0) + (GameTime * 0.9));
    float flow2 = 0.5 + 0.5 * sin((texCoord.y * 22.0) - (GameTime * 1.1));
    float pattern = clamp((flow * 0.6 + flow2 * 0.4), 0.0, 1.0);

    vec3 cool = vec3(0.34, 0.08, 0.02);
    vec3 hot = vec3(1.0, 0.55, 0.06);
    vec3 lavaColor = mix(cool, hot, pattern);

    float rim = pow(1.0 - clamp(abs(texCoord.x - 0.5) * 2.0, 0.0, 1.0), 1.8);
    vec3 emissive = lavaColor + vec3(0.15, 0.05, 0.0) * rim;

    fragColor = vec4(emissive, vertexColor.a);
}
