#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uHalfPixel;
uniform float uOffset;

void main() {
    vec2 halfPixel = uHalfPixel * uOffset;
    
    vec4 sum = vec4(0.0);
    sum += texture(uTexture, vTexCoord + vec2(-halfPixel.x * 2.0, 0.0));
    sum += texture(uTexture, vTexCoord + vec2(-halfPixel.x, halfPixel.y)) * 2.0;
    sum += texture(uTexture, vTexCoord + vec2(0.0, halfPixel.y * 2.0));
    sum += texture(uTexture, vTexCoord + vec2(halfPixel.x, halfPixel.y)) * 2.0;
    sum += texture(uTexture, vTexCoord + vec2(halfPixel.x * 2.0, 0.0));
    sum += texture(uTexture, vTexCoord + vec2(halfPixel.x, -halfPixel.y)) * 2.0;
    sum += texture(uTexture, vTexCoord + vec2(0.0, -halfPixel.y * 2.0));
    sum += texture(uTexture, vTexCoord + vec2(-halfPixel.x, -halfPixel.y)) * 2.0;
    
    fragColor = sum / 12.0;
}
