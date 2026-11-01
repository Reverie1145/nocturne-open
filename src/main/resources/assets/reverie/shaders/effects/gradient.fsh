#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform int uGradientType; // 0 = Linear, 1 = Radial, 2 = Angular/Conic
uniform vec2 uPointA;
uniform vec2 uPointB;
uniform vec4 uColorA;
uniform vec4 uColorB;
uniform vec4 uColorC;
uniform vec4 uColorD;
uniform float uAngle;

#define PI 3.14159265359

void main() {
    float t = 0.0;
    
    if (uGradientType == 0) {
        // Linear
        vec2 dir = uPointB - uPointA;
        float len = length(dir);
        if (len > 0.0) {
            vec2 norm = dir / len;
            t = clamp(dot(vTexCoord - uPointA, norm) / len, 0.0, 1.0);
        }
    } else if (uGradientType == 1) {
        // Radial
        float dist = distance(vTexCoord, uPointA);
        float radius = distance(uPointA, uPointB);
        t = clamp(dist / max(radius, 0.0001), 0.0, 1.0);
    } else if (uGradientType == 2) {
        // Angular / Conic
        vec2 delta = vTexCoord - uPointA;
        float angle = atan(delta.y, delta.x) + uAngle;
        t = fract(angle / (2.0 * PI));
    }
    
    vec4 result;
    if (t < 0.33) {
        result = mix(uColorA, uColorB, t / 0.33);
    } else if (t < 0.66) {
        result = mix(uColorB, uColorC, (t - 0.33) / 0.33);
    } else {
        result = mix(uColorC, uColorD, (t - 0.66) / 0.34);
    }
    
    fragColor = result;
}
