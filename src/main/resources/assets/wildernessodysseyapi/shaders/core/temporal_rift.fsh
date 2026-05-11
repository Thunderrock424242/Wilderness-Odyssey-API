#version 150

uniform float GameTime;

in vec4 vertexColor;
in vec3 localPos;
in float facing;

out vec4 fragColor;

void main() {
    float verticalPulse = 0.68 + 0.32 * sin(GameTime * 620.0 + localPos.y * 6.0);
    float scanline = 0.78 + 0.22 * sin((localPos.y + GameTime * 34.0) * 22.0);
    float core = 1.0 - smoothstep(0.02, 1.55, length(localPos.xz));
    vec3 cyanShift = vec3(0.18, 0.88, 1.0);
    vec3 color = mix(vertexColor.rgb, cyanShift, core * 0.42);
    float alpha = vertexColor.a * verticalPulse * scanline * facing;
    fragColor = vec4(color, alpha);
}
