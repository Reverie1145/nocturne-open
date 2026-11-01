#version 330 core

in vec2 vTexCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec4 uColor;

void main() {
    vec4 tex = texture(uTexture, vTexCoord);
    fragColor = vec4(tex.rgb * uColor.rgb, 1.0);
}
