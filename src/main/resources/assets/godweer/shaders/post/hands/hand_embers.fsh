#version 330 core

in vec2 uv;
out vec4 finalColor;

uniform sampler2D MaskSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform HandFillUniforms {
    vec4 FillColor;
    vec4 FillParams;
    mat4 InvProj;
};

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Embers: искры, поднимающиеся вверх. Каждая ячейка сетки даёт одну искру со
// своим размером и фазой мерцания; ячейки сами ползут вверх, поэтому поток
// получается сплошным. Фон чуть подсвечен снизу — как от горячего источника.
void main() {
    float mask = texture(MaskSampler, uv).a;
    if (mask < 0.01) {
        discard;
    }

    float t = FillParams.x;
    vec3 base = FillColor.rgb;

    vec2 gp = vec2(uv.x * 20.0, uv.y * 11.0 - t * 6.0);
    vec2 cell = floor(gp);
    vec2 f = fract(gp) - 0.5;

    float h = hash21(cell);
    float size = 0.09 + 0.15 * h;
    float spark = 1.0 - smoothstep(size * 0.55, size, length(f));
    float twinkle = 0.55 + 0.45 * sin(t * 2.6 + h * 21.0);

    vec3 hot = mix(base, vec3(1.0, 0.88, 0.66), 0.55);
    vec3 col = base * 0.10;
    col += hot * spark * twinkle * 1.8;
    col += base * pow(clamp(1.0 - uv.y, 0.0, 1.0), 2.5) * 0.16;

    finalColor = vec4(clamp(col, 0.0, 1.8),
                      clamp(mask * FillColor.a * (0.92 + 0.08 * spark), 0.0, 1.0));
}
