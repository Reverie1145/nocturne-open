#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform sampler2D uBlurTexture;
uniform vec4 uTintColor;
uniform float uBlurMix;
uniform float uNoiseIntensity;
uniform float uRefraction;
uniform float uTime;

float rand(vec2 co) {
    return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    float noise = (rand(vTexCoord + vec2(uTime * 0.01)) - 0.5) * uNoiseIntensity;
    vec2 offset = vec2(noise * uRefraction);
    
    vec4 sharp = texture(uTexture, vTexCoord + offset);
    vec4 blurred = texture(uBlurTexture, vTexCoord + offset);
    
    vec4 mixed = mix(sharp, blurred, uBlurMix);
    vec4 tinted = mixed * uTintColor;
    
    tinted.rgb += vec3(noise * 0.5);
    fragColor = tinted;
}
