#version 150

uniform sampler2D DiffuseSampler;
uniform float GradeAmount;
uniform float RiftAmount;
uniform float MotionAmount;
uniform float MotionPhase;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 sampleUv = texCoord;
    sampleUv.x += sin(texCoord.y * 12.0 + MotionPhase) * MotionAmount;
    sampleUv = clamp(sampleUv, vec2(0.0), vec2(1.0));
    vec4 scene = texture(DiffuseSampler, sampleUv);
    vec3 cool = scene.rgb * vec3(0.985, 1.018, 1.045);
    vec3 violet = scene.rgb * vec3(1.020, 0.991, 1.046);
    vec3 graded = mix(cool, violet, clamp(RiftAmount, 0.0, 1.0));
    fragColor = vec4(mix(scene.rgb, graded, clamp(GradeAmount, 0.0, 1.0)), scene.a);
}
