#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uBaseTexture;
uniform sampler2D uBloomTexture;
uniform float uBloomIntensity;
uniform float uExposure;

void main() {
    vec4 base = texture(uBaseTexture, vTexCoord);
    vec4 bloom = texture(uBloomTexture, vTexCoord);
    
    vec3 result = base.rgb + bloom.rgb * uBloomIntensity;
    result = vec3(1.0) - exp(-result * uExposure);
    
    fragColor = vec4(result, base.a);
}
