#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uResolution;
uniform float uRadius;
uniform float uSoftness;
uniform vec4 uVignetteColor;

void main() {
    vec4 base = texture(uTexture, vTexCoord);
    vec2 uv = (vTexCoord - 0.5) * 2.0;
    float dist = length(uv);
    
    float vignette = smoothstep(uRadius, uRadius - uSoftness, dist);
    vec3 col = mix(uVignetteColor.rgb, base.rgb, vignette);
    
    fragColor = vec4(col, base.a);
}
