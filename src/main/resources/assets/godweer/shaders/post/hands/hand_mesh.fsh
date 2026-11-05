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

// Mesh: плывущая неоновая сетка. Координаты узла искажаются синусами, поэтому
// линии не просто едут, а колышутся; толщина считается через fwidth, так что
// линия остаётся тонкой на любом разрешении.
void main() {
    float mask = texture(MaskSampler, uv).a;
    if (mask < 0.01) {
        discard;
    }

    float t = FillParams.x;
    vec3 base = FillColor.rgb;

    vec2 p = uv * vec2(9.0, 6.0);
    p += 0.32 * vec2(sin(uv.y * 6.0 + t * 0.9), cos(uv.x * 5.0 - t * 0.7));

    vec2 cell = abs(fract(p) - 0.5);
    vec2 aa = fwidth(p) * 1.4 + 1e-5;
    float line = max(1.0 - smoothstep(0.0, aa.x, cell.x),
                     1.0 - smoothstep(0.0, aa.y, cell.y));
    float halo = exp(-cell.x * 12.0) + exp(-cell.y * 12.0);

    vec3 col = base * (0.10 + line * 1.55 + halo * 0.22);
    col += mix(base, vec3(1.0), 0.7) * pow(line, 3.0) * 0.85;

    // Медленный блик, проходящий по кисти сверху вниз.
    float sheen = pow(clamp(1.0 - abs(fract(t * 0.12) - uv.y) * 7.0, 0.0, 1.0), 2.0);
    col += base * sheen * 0.35;

    finalColor = vec4(clamp(col, 0.0, 1.8),
                      clamp(mask * FillColor.a * (0.92 + 0.08 * line), 0.0, 1.0));
}
