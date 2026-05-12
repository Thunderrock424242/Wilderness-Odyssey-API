#version 150

uniform float GameTime;

in vec4 vertexColor;
in vec3 localPos;
in float facing;

out vec4 fragColor;

void main() {
    float radial = length(localPos.xz);
    float angle = atan(localPos.z, localPos.x);
    float temporalPulse = 0.68 + 0.32 * sin(GameTime * 620.0 + radial * 9.0 + angle * 2.0);
    float ripple = 0.78 + 0.22 * sin((radial - GameTime * 12.0) * 26.0);
    float core = 1.0 - smoothstep(0.02, 1.55, radial);
    vec3 cyanShift = vec3(0.18, 0.88, 1.0);
    vec3 color = mix(vertexColor.rgb, cyanShift, core * 0.42);
    float alpha = vertexColor.a * temporalPulse * ripple * facing;
    fragColor = vec4(color, alpha);
}
