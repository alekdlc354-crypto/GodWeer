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

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), u.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), u.x), u.y);
}

// Турбулентность: сумма |шум| — она даёт резкие границы ячеек, как у кипящей
// поверхности, а не мягкие облака.
float turbulence(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += abs(vnoise(p) * 2.0 - 1.0) * amp;
        p = p * 2.05 + vec2(19.1, 41.7);
        amp *= 0.5;
    }
    return sum;
}

vec3 handPosition() {
    float depth = texture(DepthSampler, uv).r;
    vec4 clip = vec4(uv * 2.0 - 1.0, depth, 1.0);
    vec4 view = InvProj * clip;
    return view.xyz / max(abs(view.w), 1e-6);
}

vec3 handNormal(vec3 p) {
    vec3 n = cross(dFdx(p), dFdy(p));
    float len = length(n);
    return len > 1e-9 ? n / len : vec3(0.0, 0.0, -1.0);
}

// Solar: поверхность звезды. Основа — турбулентность в двух масштабах, между
// ними течёт «поток»; тёмные прожилки остаются холодными, ячейки уходят в
// белое, кромка получает корону. Никакого fresnel-полоса, только настоящая
// плазма.
void main() {
    float mask = texture(MaskSampler, uv).a;
    if (mask < 0.01) {
        discard;
    }

    float t = FillParams.x;
    vec3 base = FillColor.rgb;
    vec3 p = handPosition();
    vec3 N = handNormal(p);
    vec3 V = normalize(-p);
    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);
    float fres = pow(1.0 - ndv, 2.0);

    // Крупные ячейки + мелкая рябь, сдвинутые друг относительно друга.
    vec2 q = vec2(p.y * 3.4, p.x * 4.2);
    float cells = turbulence(q + vec2(0.0, -t * 0.55));
    float fine = turbulence(q * 2.6 + vec2(t * 0.42, t * 0.17));

    float heat = clamp(cells * 0.82 + fine * 0.30, 0.0, 1.0);
    float core = pow(heat, 3.2);
    float veins = smoothstep(0.42, 0.05, cells);      // тёмные прожилки

    vec3 ember = vec3(1.0, 0.35, 0.06);
    vec3 hot = mix(base, vec3(1.0, 0.86, 0.42), 0.55);
    vec3 white = vec3(1.0, 0.98, 0.92);

    vec3 col = mix(base * 0.20, ember, 0.55) * (0.45 + 0.55 * heat);
    col = mix(col, hot, pow(heat, 1.7));
    col += white * core * 1.5;
    col *= 1.0 - veins * 0.45;
    col += hot * fres * 1.15;
    col += ember * pow(core, 4.0) * 0.8;              // внутреннее свечение

    finalColor = vec4(clamp(col, 0.0, 2.0),
                      clamp(mask * FillColor.a * (0.95 + 0.05 * fres), 0.0, 1.0));
}
