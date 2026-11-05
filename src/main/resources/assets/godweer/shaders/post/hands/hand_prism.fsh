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

// Prism: тонкоплёночная интерференция. Толщина плёнки медленно ползёт по кисти
// и по времени, поэтому цвет переливается радугой ровно так, как это делает
// бензиновая плёнка: у края силуэта сдвиг фазы больше, в центре — меньше.
// Сверху добавлена дисперсия: каждый канал сдвинут по фазе, поэтому переходы
// цветные, а не серые.
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
    float fres = pow(1.0 - ndv, 2.4);

    // Толщина плёнки: крупные волны по форме + мелкая рябь.
    float film = p.y * 3.2 + p.x * 2.1 + vnoise(vec2(p.y * 4.0 - t * 0.5, p.x * 5.0)) * 1.6;
    film += (1.0 - ndv) * 2.6;
    film += t * 0.35;

    vec3 phase = vec3(0.0, 0.42, 0.84);   // дисперсия между каналами
    vec3 irid = 0.5 + 0.5 * cos(6.28318 * (film * 0.55 + phase));

    // Плёночный отклик не бывает «просто радугой»: он модулирован углом.
    vec3 col = mix(base * 0.16, irid, 0.82) * (0.55 + 0.45 * ndv);

    // Мягкий блик-софтбокс и узкая искра.
    vec3 R = reflect(-V, N);
    col += vec3(0.9, 0.95, 1.0) * pow(max(dot(R, normalize(vec3(0.2, 0.85, 0.5))), 0.0), 24.0) * 0.55;
    col += vec3(1.0) * pow(max(dot(R, normalize(vec3(0.2, 0.85, 0.5))), 0.0), 160.0) * 1.1;

    // Кромка светится цветом противоположной фазы — читаемый силуэт.
    col += (1.0 - irid) * fres * 1.25;

    finalColor = vec4(clamp(col, 0.0, 1.8),
                      clamp(mask * FillColor.a * (0.86 + 0.14 * fres), 0.0, 1.0));
}
