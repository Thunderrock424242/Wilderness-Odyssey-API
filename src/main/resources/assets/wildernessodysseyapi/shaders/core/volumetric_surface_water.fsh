#version 150

in vec4 vertexColor;
in vec2 texCoord;

out vec4 fragColor;

uniform float GameTime;

void main() {
    float fresnel = pow(1.0 - clamp(abs(texCoord.y - 0.5) * 2.0, 0.0, 1.0), 2.2);
    float ripple = 0.5 + 0.5 * sin((texCoord.x + texCoord.y + GameTime * 0.2) * 24.0);
    vec3 deepColor = vec3(0.08, 0.24, 0.52);
    vec3 shallowColor = vec3(0.14, 0.55, 0.78);
    vec3 waterColor = mix(deepColor, shallowColor, ripple * 0.35 + 0.35);
    vec3 finalRgb = mix(waterColor, vec3(0.95, 0.98, 1.0), fresnel * 0.7);
    fragColor = vec4(finalRgb, vertexColor.a);
}
