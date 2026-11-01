#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform float uThreshold;
uniform float uSoftKnee;

void main() {
    vec4 color = texture(uTexture, vTexCoord);
    float brightness = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
    
    float knee = uThreshold * uSoftKnee;
    float soft = brightness - uThreshold + knee;
    soft = clamp(soft, 0.0, 2.0 * knee);
    soft = soft * soft / (4.0 * knee + 0.00001);
    
    float contribution = max(soft, brightness - uThreshold);
    contribution /= max(brightness, 0.00001);
    
    fragColor = color * max(contribution, 0.0);
}
