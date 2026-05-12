#version 150

uniform float GameTime;

in vec4 vertexColor;
in vec3 localPos;
in float facing;

out vec4 fragColor;

void main() {
    float spatial = length(localPos.xyz);
    float angle = atan(localPos.z + localPos.y * 0.22, localPos.x);
    float temporalPulse = 0.68 + 0.32 * sin(GameTime * 620.0 + spatial * 9.0 + angle * 2.0);
    float ripple = 0.80 + 0.20 * sin(localPos.x * 18.0 + localPos.y * 12.0 - localPos.z * 16.0 - GameTime * 19.0);
    float auroraFlow = 0.82 + 0.18 * sin(localPos.y * 10.0 + localPos.z * 14.0 + GameTime * 11.0);
    float core = 1.0 - smoothstep(0.02, 1.85, spatial);
    vec3 cyanShift = vec3(0.18, 0.88, 1.0);
    vec3 violetShift = vec3(0.56, 0.24, 1.0);
    vec3 auroraColor = mix(violetShift, cyanShift, 0.5 + 0.5 * sin(localPos.y * 5.0 + GameTime * 8.0));
    vec3 color = mix(vertexColor.rgb, auroraColor, core * 0.52);
    float alpha = vertexColor.a * temporalPulse * ripple * auroraFlow * facing;
    fragColor = vec4(color, alpha);
}
