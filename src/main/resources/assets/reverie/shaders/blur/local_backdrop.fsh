#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uTexelSize;
uniform vec2 uDirection;
uniform vec4 uOffsets;
uniform vec4 uWeights;

void main() {
    vec2 stepUv = uTexelSize * uDirection;
    vec4 sum = texture(uTexture, vTexCoord);
    float weightSum = 1.0;

    // Pair adjacent Gaussian taps. Linear texture filtering reconstructs the pair
    // with one fetch per side, matching a 17-tap kernel with only nine fetches.
    vec2 d0 = stepUv * uOffsets.x;
    vec2 d1 = stepUv * uOffsets.y;
    vec2 d2 = stepUv * uOffsets.z;
    vec2 d3 = stepUv * uOffsets.w;
    sum += (texture(uTexture, vTexCoord + d0) + texture(uTexture, vTexCoord - d0)) * uWeights.x;
    sum += (texture(uTexture, vTexCoord + d1) + texture(uTexture, vTexCoord - d1)) * uWeights.y;
    sum += (texture(uTexture, vTexCoord + d2) + texture(uTexture, vTexCoord - d2)) * uWeights.z;
    sum += (texture(uTexture, vTexCoord + d3) + texture(uTexture, vTexCoord - d3)) * uWeights.w;
    weightSum += 2.0 * (uWeights.x + uWeights.y + uWeights.z + uWeights.w);
    fragColor = sum / weightSum;
}
