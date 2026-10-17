package pub.godweer.kitty.module.impl.render;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.GameTickEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;

import java.nio.ByteBuffer;

/**
 * Fire Hands — анимированное пламя, свечение и огненный шлейф вокруг рук
 * и предметов от первого лица.
 */
public final class FireHands extends Module {
    public static FireHands INSTANCE;

    public final ModeSetting colorMode = mode("Color Mode", "Client", "Item", "Custom", "Client", "Rainbow");
    public final CheckBox onlyWithItems = checkbox("Only With Items", true);

    public final ColorSetting customColor = color("Custom Color", ColorUtil.rgba(255, 106, 0, 204)).visible(() -> "Custom".equals(colorMode.getValue()));
    public final ColorSetting clientColorA = color("Client Color A", ColorUtil.rgba(138, 43, 226, 255)).visible(() -> "Client".equals(colorMode.getValue()));
    public final ColorSetting clientColorB = color("Client Color B", ColorUtil.rgba(0, 200, 255, 255)).visible(() -> "Client".equals(colorMode.getValue()));
    public final SliderSetting clientPulseSpeed = slider("Pulse Speed", 2.0F, 0.1F, 20.0F, 0.1F).visible(() -> "Client".equals(colorMode.getValue()));
    public final SliderSetting rainbowPeriod = slider("Rainbow Period", 4.0F, 0.5F, 30.0F, 0.5F).visible(() -> "Rainbow".equals(colorMode.getValue()));

    public final SliderSetting strength = slider("Strength", 1.0F, 0.1F, 2.0F, 0.1F);
    public final SliderSetting brightness = slider("Brightness", 1.2F, 0.1F, 3.0F, 0.1F);
    public final SliderSetting riseSpeed = slider("Rise Speed", 1.5F, 0.0F, 6.0F, 0.1F);
    public final SliderSetting wobble = slider("Wobble", 0.6F, 0.0F, 3.0F, 0.1F);
    public final SliderSetting turbulence = slider("Turbulence", 0.8F, 0.0F, 3.0F, 0.1F);
    public final SliderSetting length = slider("Length", 0.6F, 0.05F, 3.0F, 0.05F);
    public final SliderSetting width = slider("Width", 4.0F, 0.0F, 16.0F, 0.5F);
    public final SliderSetting fire = slider("Fire Spread", 1.5F, 0.0F, 6.0F, 0.1F);

    // Render values
    private static boolean rEnabled = false;
    private static boolean rOnlyItems = true;
    private static int rMode = 2;
    private static int rCustom, rClientA, rClientB;
    private static float rPulse, rRainbow, rStrength, rBrightness, rRise,
            rWobble, rTurb, rLength, rWidth, rFire;

    // GPU Resources
    private static int progTrail, progComposite, vao;
    private static int texBefore, texAfter, fboBefore, fboAfter, fboMain;
    private static final int[] texTrail = new int[2];
    private static final int[] fboTrail = new int[2];
    private static int ping = 0;

    private static int texW = -1, texH = -1, attachedMainId = -1;
    private static boolean beforeCaptured = false;
    private static boolean needClear = true;
    private static boolean broken = false;

    private static long startNanos = System.nanoTime();
    private static long lastNanos = 0L;

    public FireHands() {
        super("FireHands", "Animated fire effect around hands and items", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        rEnabled = true;
        needClear = true;
    }

    @Override
    public void onDisable() {
        rEnabled = false;
        needClear = true;
        lastNanos = 0L;
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        updateRenderValues();
    }

    private void updateRenderValues() {
        rOnlyItems = onlyWithItems.getValue();
        
        String mode = colorMode.getValue();
        if ("Item".equals(mode)) rMode = 0;
        else if ("Custom".equals(mode)) rMode = 1;
        else if ("Client".equals(mode)) rMode = 2;
        else rMode = 3; // Rainbow

        rCustom = customColor.getValue();
        rClientA = clientColorA.getValue();
        rClientB = clientColorB.getValue();
        rPulse = clamp(clientPulseSpeed.getValue(), 0.1f, 20f);
        rRainbow = clamp(rainbowPeriod.getValue(), 0.5f, 30f);
        rStrength = clamp(strength.getValue(), 0.1f, 2f);
        rBrightness = clamp(brightness.getValue(), 0.1f, 3f);
        rRise = clamp(riseSpeed.getValue(), 0f, 6f);
        rWobble = clamp(wobble.getValue(), 0f, 3f);
        rTurb = clamp(turbulence.getValue(), 0f, 3f);
        rLength = clamp(length.getValue(), 0.05f, 3f);
        rWidth = clamp(width.getValue(), 0f, 16f);
        rFire = clamp(fire.getValue(), 0f, 6f);
    }

    // =====================================================================
    //                              ХУКИ
    // =====================================================================

    public static void onBeforeHands() {
        beforeCaptured = false;
        if (!shouldRun()) {
            needClear = true;
            lastNanos = 0L;
            return;
        }

        RenderTarget rt = Minecraft.getInstance().getMainRenderTarget();
        int mainColor = glId(rt.getColorTexture());
        if (mainColor == 0) return;

        GlState s = GlState.save();
        try {
            if (!ensureResources(rt.width, rt.height, mainColor)) return;
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            blit(fboMain, fboBefore, texW, texH);
            beforeCaptured = true;
        } finally {
            s.restore();
        }
    }

    public static void onAfterHands() {
        if (!beforeCaptured) return;
        beforeCaptured = false;

        Minecraft mc = Minecraft.getInstance();
        RenderTarget rt = mc.getMainRenderTarget();
        int mainColor = glId(rt.getColorTexture());
        int mainDepth = rt.getDepthTexture() != null ? glId(rt.getDepthTexture()) : 0;
        if (mainColor == 0) return;

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 1f / 60f : (now - lastNanos) / 1_000_000_000f;
        dt = clamp(dt, 1f / 1000f, 0.1f);
        lastNanos = now;
        float time = ((now - startNanos) / 1_000_000_000f) % 3600f;

        GlState s = GlState.save();
        try {
            if (!ensureResources(rt.width, rt.height, mainColor)) return;

            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDepthMask(false);
            GL11.glColorMask(true, true, true, true);

            if (needClear) {
                clearTrails();
                needClear = false;
            }

            blit(fboMain, fboAfter, texW, texH);

            GL30.glBindVertexArray(vao);
            GL11.glViewport(0, 0, texW, texH);

            float scale = texH / 1080f;
            float frame = dt * 60f;
            float decay = (float) Math.pow(0.02, dt / rLength);

            // Trail Pass
            int src = ping, dst = 1 - ping;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboTrail[dst]);
            GL11.glDisable(GL11.GL_BLEND);
            GL20.glUseProgram(progTrail);
            bindTex(0, texBefore);
            bindTex(1, texAfter);
            bindTex(2, texTrail[src]);
            u1i(progTrail, "uBefore", 0);
            u1i(progTrail, "uAfter", 1);
            u1i(progTrail, "uHistory", 2);
            u2f(progTrail, "uTexel", 1f / texW, 1f / texH);
            u1f(progTrail, "uTime", time);
            u1f(progTrail, "uFrame", frame);
            u1f(progTrail, "uScale", scale);
            u1f(progTrail, "uRise", rRise);
            u1f(progTrail, "uWobble", rWobble);
            u1f(progTrail, "uTurb", rTurb);
            u1f(progTrail, "uDecay", decay);
            u1f(progTrail, "uWidth", rWidth);
            u1f(progTrail, "uSpread", rFire);
            u1f(progTrail, "uStrength", rStrength);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            ping = dst;

            // Composite Pass
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboMain);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
            GL11.glColorMask(true, true, true, false);
            GL20.glUseProgram(progComposite);
            bindTex(0, texTrail[ping]);
            bindTex(1, texBefore);
            bindTex(2, texAfter);
            bindTex(3, mainDepth);
            u1i(progComposite, "uTrail", 0);
            u1i(progComposite, "uBefore", 1);
            u1i(progComposite, "uAfter", 2);
            u1i(progComposite, "uDepth", 3);
            u1i(progComposite, "uHasDepth", mainDepth != 0 ? 1 : 0);
            u2f(progComposite, "uTexel", 1f / texW, 1f / texH);
            u1f(progComposite, "uTime", time);
            u1f(progComposite, "uScale", scale);
            u1f(progComposite, "uRise", rRise);
            u1f(progComposite, "uBrightness", rBrightness);
            u1f(progComposite, "uSpread", rFire);
            u1f(progComposite, "uRainbowPeriod", rRainbow);
            u1f(progComposite, "uPulseSpeed", rPulse);
            u1i(progComposite, "uMode", rMode);
            u4c(progComposite, "uCustom", rCustom);
            u4c(progComposite, "uClientA", rClientA);
            u4c(progComposite, "uClientB", rClientB);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        } finally {
            s.restore();
        }
    }

    // =====================================================================
    //                              ЛОГИКА
    // =====================================================================

    private static boolean shouldRun() {
        if (!rEnabled || broken) return false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return false;
        if (!mc.options.getCameraType().isFirstPerson()) return false;
        if (rOnlyItems && p.getMainHandItem().isEmpty() && p.getOffhandItem().isEmpty()) return false;
        return true;
    }

    private static boolean ensureResources(int w, int h, int mainColorId) {
        if (broken || w <= 0 || h <= 0) return false;

        if (progTrail == 0) {
            progTrail = link(VS, TRAIL_FS);
            progComposite = link(VS, COMPOSITE_FS);
            if (progTrail == 0 || progComposite == 0) {
                broken = true;
                return false;
            }
            vao = GL30.glGenVertexArrays();
            fboMain = GL30.glGenFramebuffers();
        }

        if (w != texW || h != texH) {
            deleteTargets();
            texW = w;
            texH = h;
            texBefore = createTex(w, h, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
            texAfter = createTex(w, h, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
            fboBefore = createFbo(texBefore);
            fboAfter = createFbo(texAfter);
            for (int i = 0; i < 2; i++) {
                texTrail[i] = createTex(w, h, GL30.GL_RGBA16F, GL11.GL_FLOAT);
                fboTrail[i] = createFbo(texTrail[i]);
            }
            needClear = true;
        }

        if (mainColorId != attachedMainId) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboMain);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, mainColorId, 0);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
                broken = true;
                return false;
            }
            attachedMainId = mainColorId;
        }
        return true;
    }

    private static void deleteTargets() {
        int[] tex = {texBefore, texAfter, texTrail[0], texTrail[1]};
        int[] fbo = {fboBefore, fboAfter, fboTrail[0], fboTrail[1]};
        for (int t : tex) if (t != 0) GL11.glDeleteTextures(t);
        for (int f : fbo) if (f != 0) GL30.glDeleteFramebuffers(f);
        texBefore = texAfter = fboBefore = fboAfter = 0;
        texTrail[0] = texTrail[1] = fboTrail[0] = fboTrail[1] = 0;
        texW = texH = -1;
    }

    private static void clearTrails() {
        GL11.glClearColor(0f, 0f, 0f, 0f);
        for (int i = 0; i < 2; i++) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboTrail[i]);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
    }

    // =====================================================================
    //                              GL УТИЛИТЫ
    // =====================================================================

    private static int glId(GpuTexture t) {
        return t instanceof GlTexture g ? g.glId() : 0;
    }

    private static void blit(int from, int to, int w, int h) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, from);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, to);
        GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static int createTex(int w, int h, int internal, int type) {
        int t = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, t);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, w, h, 0, GL11.GL_RGBA, type, (ByteBuffer) null);
        return t;
    }

    private static int createFbo(int tex) {
        int f = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, f);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, tex, 0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) broken = true;
        return f;
    }

    private static void bindTex(int unit, int tex) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL33.glBindSampler(unit, 0);
    }

    private static int compile(int type, String src) {
        int sh = GL20.glCreateShader(type);
        GL20.glShaderSource(sh, src);
        GL20.glCompileShader(sh);
        if (GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            System.err.println("[FireHands] shader error:\n" + GL20.glGetShaderInfoLog(sh));
            GL20.glDeleteShader(sh);
            return 0;
        }
        return sh;
    }

    private static int link(String vs, String fs) {
        int v = compile(GL20.GL_VERTEX_SHADER, vs);
        int f = compile(GL20.GL_FRAGMENT_SHADER, fs);
        if (v == 0 || f == 0) return 0;
        int p = GL20.glCreateProgram();
        GL20.glAttachShader(p, v);
        GL20.glAttachShader(p, f);
        GL20.glLinkProgram(p);
        GL20.glDeleteShader(v);
        GL20.glDeleteShader(f);
        if (GL20.glGetProgrami(p, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            System.err.println("[FireHands] link error:\n" + GL20.glGetProgramInfoLog(p));
            GL20.glDeleteProgram(p);
            return 0;
        }
        return p;
    }

    private static void u1i(int p, String n, int v) { GL20.glUniform1i(GL20.glGetUniformLocation(p, n), v); }
    private static void u1f(int p, String n, float v) { GL20.glUniform1f(GL20.glGetUniformLocation(p, n), v); }
    private static void u2f(int p, String n, float a, float b) { GL20.glUniform2f(GL20.glGetUniformLocation(p, n), a, b); }
    private static void u4c(int p, String n, int argb) {
        GL20.glUniform4f(GL20.glGetUniformLocation(p, n),
                ((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
    }

    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }

    private static final class GlState {
        int program, drawFbo, readFbo, vaoId, activeTex;
        final int[] tex = new int[4], samp = new int[4], viewport = new int[4];
        final float[] clear = new float[4];
        boolean blend, depth, cull, scissor, depthMask;
        int srcRgb, dstRgb, srcA, dstA, eqRgb, eqA;
        final boolean[] colorMask = new boolean[4];

        static GlState save() {
            GlState s = new GlState();
            s.program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            s.drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            s.readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            s.vaoId = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            s.activeTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            for (int i = 0; i < 4; i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                s.tex[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                s.samp[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            }
            GL13.glActiveTexture(s.activeTex);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, s.viewport);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, s.clear);
            s.blend = GL11.glIsEnabled(GL11.GL_BLEND);
            s.depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            s.cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            s.scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            s.depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            s.srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            s.dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            s.srcA = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            s.dstA = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            s.eqRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
            s.eqA = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
            try (MemoryStack st = MemoryStack.stackPush()) {
                ByteBuffer b = st.malloc(16);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, b);
                for (int i = 0; i < 4; i++) s.colorMask[i] = b.get(i) != 0;
            }
            return s;
        }

        void restore() {
            GL20.glUseProgram(program);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL30.glBindVertexArray(vaoId);
            for (int i = 0; i < 4; i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex[i]);
                GL33.glBindSampler(i, samp[i]);
            }
            GL13.glActiveTexture(activeTex);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glClearColor(clear[0], clear[1], clear[2], clear[3]);
            set(GL11.GL_BLEND, blend);
            set(GL11.GL_DEPTH_TEST, depth);
            set(GL11.GL_CULL_FACE, cull);
            set(GL11.GL_SCISSOR_TEST, scissor);
            GL11.glDepthMask(depthMask);
            GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcA, dstA);
            GL20.glBlendEquationSeparate(eqRgb, eqA);
            GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        }

        private static void set(int cap, boolean on) {
            if (on) GL11.glEnable(cap); else GL11.glDisable(cap);
        }
    }

    // =====================================================================
    //                              ШЕЙДЕРЫ
    // =====================================================================

    private static final String VS = """
            #version 150
            out vec2 vUv;
            void main() {
                vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
                vUv = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    private static final String TRAIL_FS = """
            #version 150
            in vec2 vUv;
            out vec4 fragColor;

            uniform sampler2D uBefore;
            uniform sampler2D uAfter;
            uniform sampler2D uHistory;
            uniform vec2  uTexel;
            uniform float uTime, uFrame, uScale;
            uniform float uRise, uWobble, uTurb, uDecay;
            uniform float uWidth, uSpread, uStrength;

            float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
            float noise(vec2 p) {
                vec2 i = floor(p), f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
                           mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
            }

            float diffMask(vec2 c) {
                vec3 a = texture(uBefore, c).rgb;
                vec3 b = texture(uAfter,  c).rgb;
                return smoothstep(0.012, 0.05, length(b - a));
            }

            void main() {
                vec2 p = vUv / uTexel / uScale;
                float s = uFrame * uScale;

                float wob = sin(p.y * 0.035 + uTime * 5.0) * uWobble;
                float tur = (noise(p * 0.04 + vec2(0.0, uTime * 3.0)) - 0.5) * 4.0 * uTurb;
                vec2 off = vec2(wob + tur, uRise) * s;
                vec2 src = vUv - off * uTexel;

                float r = (1.0 + uSpread) * uScale;
                vec4 h = texture(uHistory, src) * 0.4
                       + (texture(uHistory, src + vec2( r, 0.0) * uTexel)
                        + texture(uHistory, src + vec2(-r, 0.0) * uTexel)
                        + texture(uHistory, src + vec2(0.0,  r) * uTexel)
                        + texture(uHistory, src + vec2(0.0, -r) * uTexel)) * 0.15;

                float erode = mix(1.0, 0.8 + 0.4 * noise(p * 0.08 - vec2(0.0, uTime * 4.0)),
                                  clamp(uTurb * 0.35, 0.0, 1.0));
                h.a *= uDecay * pow(clamp(erode, 0.0, 1.0), uFrame);

                float best = diffMask(vUv);
                vec3 col = texture(uAfter, vUv).rgb;
                float w = uWidth * uScale;
                if (w > 0.5) {
                    for (int i = 0; i < 8; i++) {
                        float ang = float(i) * 0.7853982;
                        vec2 d = vec2(cos(ang), sin(ang));
                        for (int k = 1; k <= 2; k++) {
                            vec2 c = vUv + d * w * (float(k) * 0.5) * uTexel;
                            float mm = diffMask(c) * (1.0 - 0.35 * float(k - 1));
                            if (mm > best) { best = mm; col = texture(uAfter, c).rgb; }
                        }
                    }
                }

                float heat = best * uStrength * (0.75 + 0.5 * noise(p * 0.1 + vec2(0.0, -uTime * 6.0)));
                float a = max(h.a, heat);
                float wNew = heat / (heat + h.a + 1e-4);
                vec3 rgb = mix(h.rgb, col, wNew);

                fragColor = vec4(rgb, clamp(a, 0.0, 4.0));
            }
            """;

    private static final String COMPOSITE_FS = """
            #version 150
            in vec2 vUv;
            out vec4 fragColor;

            uniform sampler2D uTrail;
            uniform sampler2D uBefore;
            uniform sampler2D uAfter;
            uniform sampler2D uDepth;
            uniform int   uHasDepth;
            uniform vec2  uTexel;
            uniform float uTime, uScale, uRise, uBrightness, uSpread;
            uniform float uRainbowPeriod, uPulseSpeed;
            uniform int   uMode;
            uniform vec4  uCustom, uClientA, uClientB;

            float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
            float noise(vec2 p) {
                vec2 i = floor(p), f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x),
                           mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
            }
            float fbm(vec2 p) {
                float v = 0.0, a = 0.5;
                for (int i = 0; i < 4; i++) { v += a * noise(p); p *= 2.03; a *= 0.5; }
                return v;
            }
            vec3 hsv2rgb(vec3 c) {
                vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
                vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
                return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
            }

            void main() {
                vec2 p = vUv / uTexel / uScale;
                vec4 tr = texture(uTrail, vUv);

                float f = fbm(p * vec2(0.02, 0.012) + vec2(0.0, -uTime * (0.6 + uRise * 0.5)));
                float flame = smoothstep(0.08, 1.0, tr.a * (0.45 + 1.1 * f));

                float glow = 0.0;
                vec3 glowCol = vec3(0.0);
                float R = (6.0 + uSpread * 6.0) * uScale;
                for (int i = 0; i < 12; i++) {
                    float ang = float(i) * 0.5235988;
                    float rr = R * (0.35 + 0.65 * fract(float(i) * 0.618034));
                    vec4 s = texture(uTrail, vUv + vec2(cos(ang), sin(ang)) * rr * uTexel);
                    glow += s.a;
                    glowCol += s.rgb * s.a;
                }
                glowCol /= max(glow, 1e-4);
                glow /= 12.0;

                vec3 base;
                if (uMode == 0) {
                    vec3 c = tr.a > 0.001 ? tr.rgb : glowCol;
                    float mx = max(max(c.r, c.g), max(c.b, 1e-3));
                    base = mix(vec3(1.0, 0.45, 0.1), c / mx, step(0.08, mx));
                } else if (uMode == 1) {
                    base = uCustom.rgb;
                } else if (uMode == 2) {
                    base = mix(uClientA.rgb, uClientB.rgb, 0.5 + 0.5 * sin(uTime * uPulseSpeed));
                } else {
                    base = hsv2rgb(vec3(fract(uTime / uRainbowPeriod + vUv.y * 0.2), 0.85, 1.0));
                }
                float alphaMul = (uMode == 1) ? uCustom.a : 1.0;

                vec3 hot = mix(base, vec3(1.0, 0.97, 0.9), smoothstep(0.65, 1.0, flame) * 0.55);

                float handNow = smoothstep(0.012, 0.05,
                        length(texture(uAfter, vUv).rgb - texture(uBefore, vUv).rgb));
                if (uHasDepth == 1) handNow *= step(texture(uDepth, vUv).r, 0.999999);
                float occl = 1.0 - handNow * 0.7;

                vec3 outc = (hot * flame * occl + base * glow * 0.55) * uBrightness * alphaMul;
                fragColor = vec4(outc, 0.0);
            }
            """;
}
