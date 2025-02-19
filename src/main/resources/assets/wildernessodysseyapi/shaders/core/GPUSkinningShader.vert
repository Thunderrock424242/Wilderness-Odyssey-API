#version 450
layout(location = 0) in vec3 position;
layout(location = 1) in vec3 normal;
layout(location = 2) in vec2 texCoords;
layout(location = 3) in mat4 instanceTransform;

layout(std430, binding = 0) buffer BoneData {
    mat4 boneTransforms[100];
};

layout(location = 0) out vec2 fragTexCoords;

void main() {
    mat4 boneTransform = boneTransforms[gl_InstanceID];
    vec4 worldPos = instanceTransform * boneTransform * vec4(position, 1.0);
    gl_Position = worldPos;
    fragTexCoords = texCoords;
}
