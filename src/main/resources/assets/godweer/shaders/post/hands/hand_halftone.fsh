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

// Halftone: печатные точки под наклоном. Радиус точки зависит от высоты и от
// медленной волны, поэтому картинка читается как растровая печать с градиентом,
// а не как ровная сетка.
void main() {
    float mask = texture(MaskSampler, uv).a;
    if (mask < 0.01) {
        discard;
    }

    float t = FillParams.x;
    vec3 base = FillColor.rgb;

    // Наклон сетки на 30 градусов.
    float a = 0.5236;
    mat2 rot = mat2(cos(a), -sin(a), sin(a), cos(a));
    vec2 gp = rot * uv * vec2(56.0, 38.0);

    float radius = 0.16 + 0.34 * (0.5 + 0.5 * sin(uv.x * 2.6 + uv.y * 1.9 - t * 0.7));

    vec2 cell = fract(gp) - 0.5;
    float dist = length(cell);
    float aa = fwidth(dist) * 1.5 + 1e-4;
    float dotMask = 1.0 - smoothstep(radius - aa, radius + aa, dist);

    vec3 hued = mix(base * 0.35, mix(base, vec3(1.0), 0.45), uv.y);
    vec3 col = hued * dotMask * 1.7 + base * 0.06;
    // Тонкий контур печати: чуть светлее по краю точки.
    col += mix(base, vec3(1.0), 0.5) * dotMask * 0.15;

    finalColor = vec4(clamp(col, 0.0, 1.8),
                      clamp(mask * FillColor.a * (0.94 + 0.06 * dotMask), 0.0, 1.0));
}
