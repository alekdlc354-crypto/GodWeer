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

float fbm(vec2 p) {
    float sum = 0.0;
    float amp = 0.5;
    for (int i = 0; i < 5; i++) {
        sum += vnoise(p) * amp;
        p = p * 2.02 + vec2(31.4, 7.9);
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

// Vapor: рука тонет в медленно ползущем дыме. Дым — доменно-искажённый fbm:
// координаты сэмпла сами сдвигаются вторым fbm, из-за этого клубы закручиваются,
// а не просто едут. По кромке дым подсвечен — так силуэт отделяется от мира.
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

    vec2 q = vec2(p.y * 2.6 - t * 0.18, p.x * 3.1);
    vec2 warp = vec2(fbm(q * 1.3 + vec2(0.0, t * 0.22)),
                     fbm(q * 1.3 + vec2(5.2, -t * 0.19)));
    float smoke = fbm(q + warp * 1.5 + vec2(t * 0.12, 0.0));

    // Клубы: плотное ядро и разряжённые края.
    float density = smoothstep(0.32, 0.86, smoke);
    float wisps = pow(clamp(smoke, 0.0, 1.0), 3.0);

    vec3 cool = mix(base * 0.35, base, 0.5);
    vec3 hot = mix(base, vec3(0.92, 0.96, 1.0), 0.55);

    vec3 col = cool * (0.20 + 0.35 * ndv);
    col += hot * density * 0.75;
    col += hot * wisps * 0.45;
    // Подсветка кромки: дым «обтекает» силуэт.
    col += hot * fres * 1.0;

    // Лёгкое мерцание плотности, чтобы дым не выглядел застывшим.
    float shimmer = 0.94 + 0.06 * sin(t * 1.7 + smoke * 9.0);
    col *= shimmer;

    float alpha = mask * FillColor.a * clamp(0.72 + density * 0.22 + fres * 0.16, 0.72, 1.0);
    finalColor = vec4(clamp(col, 0.0, 1.8), clamp(alpha, 0.0, 1.0));
}
