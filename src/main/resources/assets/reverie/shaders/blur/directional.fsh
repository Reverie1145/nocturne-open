#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uDirection;
uniform int uSamples;
uniform float uStrength;

void main() {
    vec4 color = vec4(0.0);
    float total = 0.0;
    
    for (int i = -uSamples; i <= uSamples; ++i) {
        float factor = 1.0 - abs(float(i)) / float(uSamples + 1);
        vec2 offset = uDirection * (float(i) * uStrength);
        color += texture(uTexture, vTexCoord + offset) * factor;
        total += factor;
    }
    
    fragColor = color / total;
}
