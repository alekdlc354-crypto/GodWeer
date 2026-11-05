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

// Rings: расходящиеся кольца от точки, которая медленно дрейфует по экрану.
// Кольца тонкие (крутой pow) и гаснут с расстоянием, поэтому работают как
// импульс по поверхности, а не как радуга.
void main() {
    float mask = texture(MaskSampler, uv).a;
    if (mask < 0.01) {
        discard;
    }

    float t = FillParams.x;
    vec3 base = FillColor.rgb;

    vec2 center = vec2(0.5 + 0.20 * sin(t * 0.21), 0.5 + 0.16 * cos(t * 0.17));
    float d = length(uv - center);

    float wave = sin(d * 34.0 - t * 2.6);
    float ring = pow(clamp(1.0 - abs(wave), 0.0, 1.0), 10.0);
    float falloff = exp(-d * 2.0);

    vec3 col = base * 0.12;
    col += mix(base, vec3(1.0), 0.62) * ring * falloff * 1.9;
    col += base * pow(clamp(1.0 - d, 0.0, 1.0), 3.0) * 0.28;
    // Второй набор колец чуть медленнее и в другом масштабе — нет «одного центра».
    float wave2 = sin(d * 21.0 - t * 1.5);
    col += base * pow(clamp(1.0 - abs(wave2), 0.0, 1.0), 14.0) * falloff * 0.7;

    finalColor = vec4(clamp(col, 0.0, 1.8),
                      clamp(mask * FillColor.a * (0.90 + 0.10 * ring), 0.0, 1.0));
}
