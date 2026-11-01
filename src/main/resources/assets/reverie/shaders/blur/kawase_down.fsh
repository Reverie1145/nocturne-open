#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uHalfPixel;
uniform float uOffset;

void main() {
    vec2 halfPixel = uHalfPixel * uOffset;
    
    vec4 sum = texture(uTexture, vTexCoord) * 4.0;
    sum += texture(uTexture, vTexCoord - halfPixel);
    sum += texture(uTexture, vTexCoord + halfPixel);
    sum += texture(uTexture, vTexCoord + vec2(halfPixel.x, -halfPixel.y));
    sum += texture(uTexture, vTexCoord - vec2(halfPixel.x, -halfPixel.y));
    
    fragColor = sum / 8.0;
}
