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
    for (int i = 0; i < 3; i++) {
        sum += vnoise(p) * amp;
        p = p * 2.05 + vec2(13.7, 7.1);
        amp *= 0.5;
    }
    return sum;
}

// Silk: шёлковые полосы. Смещение полос берётся из медленного fbm, поэтому они
// текут, как ткань, а не качаются как синус. Гребни полос подсвечены — это даёт
// атласный перелив на изгибах кисти.
void main() {
    float mask = texture(MaskSampler, uv).a;
    if (mask < 0.01) {
        discard;
    }

    float t = FillParams.x;
    vec3 base = FillColor.rgb;

    float warp = fbm(vec2(uv.x * 3.2 + t * 0.10, uv.y * 2.1 - t * 0.07));
    float band = 0.5 + 0.5 * sin((uv.y * 6.5 + warp * 2.4 + t * 0.30) * 3.14159);

    vec3 cool = mix(base * 0.45, base, 0.55);
    vec3 warm = mix(base, vec3(1.0), 0.50);

    vec3 col = mix(cool, warm, smoothstep(0.20, 0.90, band));
    col += warm * pow(band, 6.0) * 0.55;
    // Второй, более редкий гребень — перелив «в противофазе».
    float anti = 0.5 + 0.5 * sin((uv.y * 2.3 - warp * 1.6 - t * 0.19) * 3.14159);
    col += cool * pow(anti, 8.0) * 0.35;

    finalColor = vec4(clamp(col, 0.0, 1.8),
                      clamp(mask * FillColor.a * (0.90 + 0.10 * band), 0.0, 1.0));
}
