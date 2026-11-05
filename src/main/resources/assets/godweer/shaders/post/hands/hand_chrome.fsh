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

// ---------------------------------------------------------------- шум

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

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
        p = p * 2.03 + vec2(17.3, 9.1);
        amp *= 0.5;
    }
    return sum;
}

// ------------------------------------------------------- восстановление вида

// Позиция пикселя руки в пространстве вида: узор лежит на кисти и едет вместе
// с ней, а не плавает по экрану.
vec3 handPosition() {
    float depth = texture(DepthSampler, uv).r;
    vec4 clip = vec4(uv * 2.0 - 1.0, depth, 1.0);
    vec4 view = InvProj * clip;
    return view.xyz / max(abs(view.w), 1e-6);
}

// Нормаль из производных восстановленной позиции — по форме кисти, а не по
// плоской маске.
vec3 handNormal(vec3 p) {
    vec3 n = cross(dFdx(p), dFdy(p));
    float len = length(n);
    return len > 1e-9 ? n / len : vec3(0.0, 0.0, -1.0);
}

// Chrome: жидкий металл. Отражение берётся из процедурной «студии» — две
// широкие световые полосы и холодный пол, — поэтому по кисти бегут настоящие
// блики, а не плоская заливка. Вдоль предплечья тянутся анизотропные штрихи
// полировки, на изломе формы вспыхивает узкий спекуляр.
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
    vec3 R = reflect(-V, N);

    float ndv = clamp(abs(dot(N, V)), 0.0, 1.0);
    float fres = pow(1.0 - ndv, 2.0);

    // Студия: горизонтальные полосы света, верх холодный, низ тёмный.
    float band = sin(R.y * 6.5 + t * 0.55) * 0.5 + 0.5;
    float bar = smoothstep(0.62, 1.0, sin(R.y * 3.1 - t * 0.22) * 0.5 + 0.5);
    vec3 env = mix(vec3(0.02, 0.03, 0.05), vec3(0.42, 0.50, 0.64), band * 0.7);
    env += vec3(0.93, 0.97, 1.0) * pow(bar, 2.0) * 0.9;
    env += base * 0.25;

    // Анизотропная полировка: штрихи вдоль оси руки.
    float streak = fbm(vec2(p.y * 5.0 + t * 0.4, p.x * 26.0));
    env += mix(base, vec3(1.0), 0.35) * streak * 0.30;

    vec3 col = env * (0.52 + 0.48 * ndv);
    col += mix(base, vec3(1.0), 0.7) * fres * 1.15;

    // Узкий горячий блик от «верхнего софтбокса».
    vec3 lightDir = normalize(vec3(0.25, 0.9, 0.35));
    col += vec3(1.0) * pow(max(dot(R, lightDir), 0.0), 72.0) * 1.3;
    // Второй, мягкий, снизу — чтобы металл не был чёрным в тенях.
    col += base * pow(max(dot(R, normalize(vec3(-0.4, -0.8, 0.2))), 0.0), 8.0) * 0.35;

    finalColor = vec4(clamp(col, 0.0, 1.8),
                      clamp(mask * FillColor.a * (0.92 + 0.08 * fres), 0.0, 1.0));
}
