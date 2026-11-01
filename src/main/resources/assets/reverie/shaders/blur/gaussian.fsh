#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uDirection;
uniform float uRadius;
uniform float uWeights[32];
uniform int uKernelSize;

void main() {
    vec4 result = texture(uTexture, vTexCoord) * uWeights[0];
    
    for (int i = 1; i < uKernelSize; ++i) {
        vec2 offset = uDirection * (float(i) * uRadius);
        result += texture(uTexture, vTexCoord + offset) * uWeights[i];
        result += texture(uTexture, vTexCoord - offset) * uWeights[i];
    }
    
    fragColor = result;
}
