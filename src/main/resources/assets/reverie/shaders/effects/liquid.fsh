#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform vec2 uResolution;
uniform float uTime;
uniform vec4 uColor1;
uniform vec4 uColor2;
uniform vec4 uColor3;

void main() {
    vec2 uv = (gl_FragCoord.xy * 2.0 - uResolution.xy) / min(uResolution.x, uResolution.y);
    
    float t = uTime * 0.5;
    for (float i = 1.0; i < 4.0; i++) {
        uv.x += 0.6 / i * cos(i * 2.5 * uv.y + t);
        uv.y += 0.6 / i * sin(i * 1.5 * uv.x + t);
    }
    
    float wave = sin(uv.x + uv.y + t) * 0.5 + 0.5;
    float wave2 = cos(uv.x * 1.5 - uv.y + t * 0.8) * 0.5 + 0.5;
    
    vec4 col = mix(uColor1, uColor2, wave);
    col = mix(col, uColor3, wave2 * 0.5);
    
    fragColor = col;
}
