#version 150

uniform sampler2D waterTexture;       // Base water texture
uniform sampler2D underwaterTexture;  // Underwater refraction texture
uniform float time;                   // Time uniform for animation
uniform float waterDepth;             // Maximum water depth for gradient
uniform vec3 lightDir;                // Direction of the light source
uniform vec3 viewDir;                 // Direction of the camera

in vec3 worldPos;                     // World position passed from vertex shader
in vec2 vUV;                          // UV coordinates from vertex shader

out vec4 FragColor;                   // Output fragment color

void main() {
    // Sample base water texture with animated UV distortion
    vec2 uv = vUV;
    vec2 flowUV = uv + vec2(sin(time + worldPos.x * 0.2) * 0.02, cos(time + worldPos.z * 0.2) * 0.02);
    vec4 baseColor = texture(waterTexture, flowUV);

    // Depth-based color gradient
    float depthFactor = smoothstep(0.0, waterDepth, worldPos.y);
    vec4 deepColor = vec4(0.0, 0.1, 0.3, 1.0);   // Deep blue
    vec4 shallowColor = vec4(0.3, 0.6, 0.9, 1.0); // Light aqua
    vec4 depthColor = mix(deepColor, shallowColor, depthFactor);

    // Foam effect based on wave peaks
    float foamIntensity = smoothstep(0.6, 0.8, abs(sin(worldPos.y * 5.0)));
    foamIntensity += sin(time + worldPos.x * 0.5 + worldPos.z * 0.5) * 0.05; // Dynamic foam motion
    vec4 foamColor = vec4(1.0, 1.0, 1.0, foamIntensity); // White foam with transparency

    // Specular highlights for sunlight
    vec3 normal = normalize(vec3(dFdx(worldPos.y), 1.0, dFdz(worldPos.y))); // Approximate normal
    vec3 reflection = reflect(-lightDir, normal); // Reflection vector
    float specular = pow(max(dot(reflection, viewDir), 0.0), 32.0); // Specular intensity
    vec4 highlightColor = vec4(1.0, 1.0, 0.8, specular); // Bright yellow-white highlights

    // Combine base color, depth gradient, foam, and highlights
    vec4 finalColor = mix(baseColor, depthColor, 0.5);
    finalColor = mix(finalColor, foamColor, foamIntensity);
    finalColor = mix(finalColor, highlightColor, specular);

    // Apply final color
    FragColor = finalColor;
}
