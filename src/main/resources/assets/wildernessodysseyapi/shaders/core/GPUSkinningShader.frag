#version 450

layout(location = 0) in vec2 fragTexCoords;
layout(location = 1) in vec3 fragNormal;

layout(location = 0) out vec4 outColor;

uniform sampler2D entityTexture;

void main() {
    // Sample texture color
    vec4 texColor = texture(entityTexture, fragTexCoords);

    // Simple lighting (basic diffuse shading)
    vec3 lightDir = normalize(vec3(0.5, 1.0, 0.5)); // Example light direction
    float lightIntensity = max(dot(normalize(fragNormal), lightDir), 0.2);

    // Apply lighting and output final color
    outColor = texColor * vec4(vec3(lightIntensity), 1.0);
}
