#version 450

// Input vertex attributes from VBO
layout(location = 0) in vec3 position;  // Vertex position
layout(location = 1) in vec3 normal;    // Vertex normal
layout(location = 2) in vec2 texCoords; // Texture coordinates
layout(location = 3) in ivec4 boneIndices; // Bone indices affecting this vertex
layout(location = 4) in vec4 boneWeights;  // Weights for each bone
layout(location = 5) in mat4 instanceTransform; // Per-instance transformation (for instancing)

// Shader Storage Buffer Object (SSBO) for bone transformations
layout(std430, binding = 0) buffer BoneData {
    mat4 boneTransforms[100]; // Supports up to 100 bones per entity
};

// Output to fragment shader
layout(location = 0) out vec2 fragTexCoords;
layout(location = 1) out vec3 fragNormal;

uniform mat4 projectionMatrix;
uniform mat4 viewMatrix;

void main() {
    // Retrieve bone transformation matrices using indices
    mat4 boneTransform =
    boneTransforms[boneIndices.x] * boneWeights.x +
    boneTransforms[boneIndices.y] * boneWeights.y +
    boneTransforms[boneIndices.z] * boneWeights.z +
    boneTransforms[boneIndices.w] * boneWeights.w;

    // Transform vertex position using bone weights and instance transformation
    vec4 worldPos = instanceTransform * boneTransform * vec4(position, 1.0);

    // Transform normal using bone influence
    fragNormal = mat3(instanceTransform) * mat3(boneTransform) * normal;

    // Pass texture coordinates to fragment shader
    fragTexCoords = texCoords;

    // Apply view and projection transformations
    gl_Position = projectionMatrix * viewMatrix * worldPos;
}
