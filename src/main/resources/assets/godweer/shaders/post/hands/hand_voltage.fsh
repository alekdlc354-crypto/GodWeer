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
    for (int i = 0; i < 4; i++) {
        sum += vnoise(p) * amp;
        p = p * 2.07 + vec2(11.7, 23.1);
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

// Voltage: по кисти ползут электрические разряды. Русло тока — это гребень
// доменно-искажённого fbm (1 - |2n-1|)^k: тонкая жила с мягким свечением вокруг,
// от неё отходят ветки второго порядка. Фон почти чёрный, поэтому разряды
// читаются как настоящий ток, а не как узор.
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
    float fres = pow(1.0 - ndv, 2.2);

    // Основное русло.
    vec2 q = vec2(p.y * 2.1, p.x * 3.4 + p.z * 1.7);
    float n = fbm(q + vec2(t * 0.85, -t * 0.32));
    float ridge = 1.0 - abs(n * 2.0 - 1.0);
    float vein = pow(clamp(ridge, 0.0, 1.0), 7.0);

    // Ветки.
    float n2 = fbm(q * 2.4 + vec2(-t * 0.55, t * 0.45));
    float vein2 = pow(clamp(1.0 - abs(n2 * 2.0 - 1.0), 0.0, 1.0), 16.0);

    // Мягкая корона вокруг русла — то самое свечение плазмы.
    float halo = pow(clamp(ridge, 0.0, 1.0), 2.2) * 0.35;

    vec3 arcColor = mix(base, vec3(0.82, 0.92, 1.0), 0.65);
    vec3 col = base * (0.06 + 0.16 * ndv);
    col += arcColor * vein * 2.1;
    col += mix(base, vec3(1.0), 0.8) * vein2 * 1.5;
    col += base * halo;
    col += vec3(1.0) * pow(vein, 3.0) * 0.7;          // белое ядро жилы
    col += arcColor * fres * 1.1;

    finalColor = vec4(clamp(col, 0.0, 2.0),
                      clamp(mask * FillColor.a * (0.94 + 0.06 * fres), 0.0, 1.0));
}
