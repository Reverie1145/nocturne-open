#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform vec2 uResolution;
uniform vec2 uPosition;
uniform vec2 uSize;
uniform vec4 uRadii;
uniform vec4 uColor;
uniform float uRadius;
uniform float uSpread;

float roundedBoxSDF(vec2 p, vec2 b, vec4 r) {
    r.xy = (p.x > 0.0) ? r.xy : r.zw;
    r.x  = (p.y > 0.0) ? r.x  : r.y;
    vec2 q = abs(p) - b + r.x;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
}

void main() {
    vec2 pixelCoord = vTexCoord * uResolution;
    vec2 center = uPosition + uSize * 0.5;
    vec2 p = pixelCoord - center;
    vec2 halfSize = (uSize * 0.5) + vec2(uSpread);
    
    float dist = roundedBoxSDF(p, halfSize, uRadii);
    float alpha = 1.0 - smoothstep(0.0, uRadius, dist);
    
    fragColor = vec4(uColor.rgb, uColor.a * alpha);
}
