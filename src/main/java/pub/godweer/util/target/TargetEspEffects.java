package pub.godweer.util.target;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import pub.godweer.module.impl.render.TargetEsp;
import pub.godweer.util.render.Render3DUtil;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.util.style.ColorUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Четыре режима Target ESP: Chains, Ring, Ghost, SoulRing.
 *
 * Цепи и кольцо — плоская геометрия (треугольники, без текстур: цвет темы,
 * полосы звена вместо chain.png, градиент вместо текстуры свечения).
 * Призраки и кристаллы — текстурированные billboard-спрайты свечения
 * (ParticlesWorldRenderer, bloom.png), которые всегда смотрят в камеру.
 */
public final class TargetEspEffects {

    private static final float CHAIN_ANIM_SECONDS = 0.4F;      // 400 мс захват цели
    private static final float APPEAR_SECONDS = 0.3F;          // общее появление/пропадание
    private static final float MAX_DT = 0.1F;
    private static final float SPRITE_BRIGHTNESS = 1.2F;
    private static final int VERTEX_SIZE = DefaultVertexFormat.POSITION_COLOR.getVertexSize();

    private static final RenderPipeline ADDITIVE_PIPELINE = pipeline("target_fx_additive", BlendFunction.ADDITIVE);
    private static final RenderPipeline ALPHA_PIPELINE = pipeline("target_fx_alpha", BlendFunction.TRANSLUCENT);
    private static final int TRANSFORM_SIZE = new Std140SizeCalculator().putMat4f().get();

    private final long startTime = System.currentTimeMillis();
    private final ParticlesWorldRenderer sprites = new ParticlesWorldRenderer();
    private final List<ParticlesWorldRenderer.Sprite> spriteList = new ArrayList<>();

    private final Layer additive = new Layer(ADDITIVE_PIPELINE);
    private final Layer alpha = new Layer(ALPHA_PIPELINE);

    private LivingEntity lastTarget;
    private float chainAnim1;
    private float chainAnim2;
    private float appear;
    private float moving;
    private long lastNanos;

    public void render(TargetEsp module, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) {
            release();
            return;
        }
        if (!module.usesFx()) {
            release();
            return;
        }

        LivingEntity target = module.resolveTarget();
        boolean hasTarget = valid(target);

        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 1.0F / 60.0F : Mth.clamp((now - lastNanos) / 1.0E9F, 0.0F, MAX_DT);
        lastNanos = now;

        if (hasTarget && target != lastTarget) {
            // новая цель: цепи заново захватывают её
            chainAnim1 = 0.0F;
            chainAnim2 = 0.0F;
        }
        if (hasTarget) lastTarget = target;

        float goal = hasTarget ? 1.0F : 0.0F;
        appear = Mth.clamp(appear + Mth.clamp(goal - appear, -dt / APPEAR_SECONDS, dt / APPEAR_SECONDS), 0.0F, 1.0F);
        if (hasTarget) {
            chainAnim1 = Math.min(1.0F, chainAnim1 + dt / CHAIN_ANIM_SECONDS);
            if (chainAnim1 >= 0.95F) {
                chainAnim2 = Math.min(1.0F, chainAnim2 + dt / CHAIN_ANIM_SECONDS);
            }
        }
        moving += dt * 60.0F * 0.5F;

        LivingEntity draw = hasTarget ? target : lastTarget;
        if (appear <= 0.01F || !valid(draw)) {
            if (appear <= 0.01F) {
                additive.clear();
                alpha.clear();
            }
            return;
        }

        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;
        Matrix4f projection = Render3DUtil.levelProjectionCopy();
        if (projection == null) return;

        Vec3 base = Render3DUtil.interpolatedPosition(draw, tickDelta);
        Vec3 cam = camera.position();
        double ox = base.x - cam.x;
        double oy = base.y - cam.y;
        double oz = base.z - cam.z;
        int accent = module.getAccentColor();
        float alphaAnim = appear;

        additive.clear();
        alpha.clear();
        spriteList.clear();

        if (module.usesChains()) {
            buildChains(draw, ox, oy, oz, accent, alphaAnim);
        } else if (module.usesRing()) {
            buildRing(draw, ox, oy, oz, accent, alphaAnim);
        } else if (module.usesGhost()) {
            buildGhosts(draw, base, accent, alphaAnim);
        } else if (module.usesSoulRing()) {
            buildSoulRing(draw, base, accent, alphaAnim, module.soulCount.getValue().intValue());
        }

        additive.submit(camera, projection);
        alpha.submit(camera, projection);
        if (!spriteList.isEmpty()) {
            sprites.render(spriteList, SPRITE_BRIGHTNESS, true, true);
        }
    }

    // =====================================================================
    // 1. ЦЕПИ — Chains
    // =====================================================================
    private void buildChains(LivingEntity t, double ox, double oy, double oz, int accent, float alphaAnim) {
        final float down = 0.45F;       // ширина ленты (1 блок в спецификации — здесь по высоте)
        final int modif = 22;
        final int chainSize = 4;
        final float rotSpeed = 2.0F;

        float time = (System.currentTimeMillis() - startTime) / 1000.0F;
        float gradusX = (float) ((Math.sin(time * 2.0F) * 0.5F + 0.5F) * 20.0F);
        float gradusZ = (float) ((Math.cos(time * 2.0F) * 0.5F + 0.5F) * 20.0F);

        float radius = t.getBbWidth() * 0.85F;
        float centerY = t.getBbHeight() * 0.5F;

        for (int chain = 0; chain < 2; chain++) {
            float anim = chain == 0 ? chainAnim1 : chainAnim2;
            if (chain == 1 && chainAnim1 < 0.95F) break;

            // радиус 1.2 → 0.7: цепи сжимаются и сковывают цель
            float val = 1.2F - 0.5F * anim;
            float ax = chain == 0 ? 15.0F + gradusX : -15.0F - gradusX;
            float az = chain == 0 ? 15.0F + gradusZ : -15.0F - gradusZ;

            // слои 2 и 1 — аддитивное свечение, слой 0 — сама цепь
            for (int glowLayer = 2; glowLayer >= 0; glowLayer--) {
                float glowSize = 1.0F + glowLayer * 0.4F;
                float half = down * glowSize / 2.0F;
                Layer layer = glowLayer > 0 ? additive : alpha;

                for (int i = 0; i <= 720 - modif; i += modif) {
                    double a1 = Math.toRadians(i + moving * rotSpeed);
                    double a2 = Math.toRadians(i + modif + moving * rotSpeed);
                    double[] p1 = { Math.sin(a1) * radius, 0.0D, Math.cos(a1) * radius };
                    double[] p2 = { Math.sin(a2) * radius, 0.0D, Math.cos(a2) * radius };

                    int color = colorAt(accent, i);
                    if (glowLayer > 0) {
                        int a = Math.round((glowLayer == 2 ? 35.0F : 70.0F) * anim * alphaAnim);
                        int c = ColorUtil.withAlpha(color, a);
                        Vec3 b1 = xf(p1[0], -half, p1[2], ax, az, val, centerY, ox, oy, oz);
                        Vec3 b2 = xf(p2[0], -half, p2[2], ax, az, val, centerY, ox, oy, oz);
                        Vec3 t2 = xf(p2[0], half, p2[2], ax, az, val, centerY, ox, oy, oz);
                        Vec3 t1 = xf(p1[0], half, p1[2], ax, az, val, centerY, ox, oy, oz);
                        layer.quad(b1, b2, t2, t1, c, c, c, c);
                    } else {
                        // Текстура chain.png заменена полосами звена: яркость зависит от u
                        int a = Math.round(255.0F * anim * alphaAnim);
                        float u1 = (float) i / 720.0F * chainSize;
                        float u2 = (float) (i + modif) / 720.0F * chainSize;
                        int c1 = ColorUtil.withAlpha(shade(color, u1), a);
                        int c2 = ColorUtil.withAlpha(shade(color, u2), a);
                        Vec3 b1 = xf(p1[0], -half, p1[2], ax, az, val, centerY, ox, oy, oz);
                        Vec3 b2 = xf(p2[0], -half, p2[2], ax, az, val, centerY, ox, oy, oz);
                        Vec3 t2 = xf(p2[0], half, p2[2], ax, az, val, centerY, ox, oy, oz);
                        Vec3 t1 = xf(p1[0], half, p1[2], ax, az, val, centerY, ox, oy, oz);
                        layer.quad(b1, b2, t2, t1, c1, c2, c2, c1);
                    }
                }
            }
        }
    }

    /** Поворот как ms.rotate(X) затем ms.rotate(Z) + масштаб + смещение к центру. */
    private static Vec3 xf(double x, double y, double z, float axDeg, float azDeg, float scale, float centerY,
                           double ox, double oy, double oz) {
        double az = Math.toRadians(azDeg);
        double cz = Math.cos(az), sz = Math.sin(az);
        double x1 = x * cz - y * sz;
        double y1 = x * sz + y * cz;
        double z1 = z;

        double ax = Math.toRadians(axDeg);
        double cx = Math.cos(ax), sx = Math.sin(ax);
        double y2 = y1 * cx - z1 * sx;
        double z2 = y1 * sx + z1 * cx;

        return new Vec3(ox + x1 * scale, oy + centerY + y2 * scale, oz + z2 * scale);
    }

    // =====================================================================
    // 2. КОЛЬЦО — Ring (Sigma Jello style)
    // =====================================================================
    private void buildRing(LivingEntity t, double ox, double oy, double oz, int accent, float alphaAnim) {
        final double duration = 2000.0;
        double elapsed = System.currentTimeMillis() % duration;
        boolean side = elapsed > duration / 2.0;
        double progress = (elapsed % (duration / 2.0)) / (duration / 2.0);
        if (side) progress = 1.0 - progress;

        // Ease-In-Out Quad
        progress = progress < 0.5
                ? 2.0 * progress * progress
                : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;

        double height = t.getBbHeight();
        double radius = t.getBbWidth() * 0.8;

        // Шлейф сжимается до нуля у головы и ног
        double tail = height * 0.4 * Math.sin(progress * Math.PI);
        double eased = side ? tail : -tail;
        double ringY = height * progress;

        int steps = 360 / 5;
        Vec3[] top = new Vec3[steps + 1];
        Vec3[] tailPts = new Vec3[steps + 1];
        int[] colors = new int[steps + 1];
        for (int s = 0; s <= steps; s++) {
            double rad = Math.toRadians(s * 5.0);
            double px = Math.sin(rad) * radius;
            double pz = Math.cos(rad) * radius;
            top[s] = new Vec3(ox + px, oy + ringY, oz + pz);
            tailPts[s] = new Vec3(ox + px, oy + ringY + eased, oz + pz);
            colors[s] = colorAt(accent, s * 5.0F);
        }

        // Световой занавес: кольцо (alpha 225) → хвост (alpha 1)
        for (int s = 0; s < steps; s++) {
            int c1 = ColorUtil.withAlpha(colors[s], Math.round(225.0F * alphaAnim));
            int c2 = ColorUtil.withAlpha(colors[s + 1], Math.round(225.0F * alphaAnim));
            int tail1 = ColorUtil.withAlpha(colors[s], 1);
            int tail2 = ColorUtil.withAlpha(colors[s + 1], 1);
            additive.quad(top[s], tailPts[s], tailPts[s + 1], top[s + 1], c1, tail1, tail2, c2);
        }

        // Ободок толщиной ~1.5 px: тонкое кольцо на той же высоте
        float lineHalf = 0.018F;
        for (int s = 0; s < steps; s++) {
            double a1 = Math.toRadians(s * 5.0);
            double a2 = Math.toRadians((s + 1) * 5.0);
            Vec3 o1 = new Vec3(ox + Math.sin(a1) * (radius + lineHalf), oy + ringY, oz + Math.cos(a1) * (radius + lineHalf));
            Vec3 o2 = new Vec3(ox + Math.sin(a2) * (radius + lineHalf), oy + ringY, oz + Math.cos(a2) * (radius + lineHalf));
            Vec3 i1 = new Vec3(ox + Math.sin(a1) * (radius - lineHalf), oy + ringY, oz + Math.cos(a1) * (radius - lineHalf));
            Vec3 i2 = new Vec3(ox + Math.sin(a2) * (radius - lineHalf), oy + ringY, oz + Math.cos(a2) * (radius - lineHalf));
            int c = ColorUtil.withAlpha(colors[s], Math.round(255.0F * alphaAnim));
            int c2 = ColorUtil.withAlpha(colors[s + 1], Math.round(255.0F * alphaAnim));
            additive.quad(o1, i1, i2, o2, c, c, c2, c2);
        }
    }

    // =====================================================================
    // 3. ПРИЗРАКИ — Ghost (Soul)
    // =====================================================================
    private void buildGhosts(LivingEntity t, Vec3 base, int accent, float alphaAnim) {
        final int length = 7;
        final int distance = 15;
        final float speed = 10.0F;
        final float size = 0.35F;

        double w = t.getBbWidth() * 0.9;
        double cy = base.y + t.getBbHeight() * 0.5;
        long elapsedBase = System.currentTimeMillis() - startTime;

        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < length; i++) {
                double angle = 0.15F * (elapsedBase - (long) i * distance) / speed;
                double s = Math.sin(angle) * w;
                double c = Math.cos(angle) * w;

                double dx;
                double dy;
                double dz;
                switch (j) {
                    case 0 -> { dx = s; dy = c; dz = -c; }
                    case 1 -> { dx = -s; dy = s; dz = -c; }
                    default -> { dx = s; dy = -c; dz = -c; }
                }

                // Хвост из клонов: по спецификации альфа не затухает (захардкоженный clamp 255)
                int color = colorAt(accent, i * 15 + j * 120);
                int col = ColorUtil.withAlpha(color, Math.round(255.0F * alphaAnim));
                spriteList.add(new ParticlesWorldRenderer.Sprite(
                        new Vec3(base.x + dx, cy + dy, base.z + dz), size, col));
            }
        }
    }

    // =====================================================================
    // 4. КРИСТАЛЛЫ — SoulRing
    // =====================================================================
    private void buildSoulRing(LivingEntity t, Vec3 base, int accent, float alphaAnim, int soulsValue) {
        final float size = 0.25F;
        final int stacks = 7;               // вытягивание 2.4× — стопкой billboard-спрайтов

        int count = Mth.clamp(soulsValue, 3, 20);
        double height = t.getBbHeight();
        double radius = t.getBbWidth() * 0.85;

        long now = System.currentTimeMillis();
        double rotation = (now / 10000.0) * 360.0;
        double vertical = Math.sin((now / 3500.0) * Math.PI * 2.0) * height * 0.3;
        double cy = base.y + height * 0.5 + vertical;

        for (int i = 0; i < count; i++) {
            double angle = i * (360.0 / count) + rotation;
            double rad = Math.toRadians(angle);
            double cx = base.x + Math.sin(rad) * radius;
            double cz = base.z + Math.cos(rad) * radius;
            int col = ColorUtil.withAlpha(colorAt(accent, (float) angle), Math.round(150.0F * alphaAnim));

            for (int k = 0; k < stacks; k++) {
                double frac = stacks == 1 ? 0.0 : (double) k / (stacks - 1) - 0.5;
                double dy = frac * 2.0 * size * 1.2;
                spriteList.add(new ParticlesWorldRenderer.Sprite(
                        new Vec3(cx, cy + dy, cz), size * 0.5F, col));
            }
        }
    }

    // =====================================================================
    // Цвет и тень
    // =====================================================================

    /** Цвет темы со сдвигом по оттенку: тот же «переливающийся» вид, что и в эталоне. */
    private static int colorAt(int accent, float deg) {
        float[] hsv = ColorUtil.toHsv(accent);
        float hue = hsv[0] + 0.08F * (float) Math.sin(Math.toRadians(deg));
        hue -= (float) Math.floor(hue);
        float sat = Mth.clamp(hsv[1], 0.45F, 1.0F);
        float val = Mth.clamp(hsv[2], 0.85F, 1.0F);
        return ColorUtil.fromHsv(hue, sat, val, 255);
    }

    /** Полосы звена: «светлые» участки текстуры вместо chain.png. */
    private static int shade(int color, float u) {
        float k = 0.6F + 0.4F * (float) Math.abs(Math.sin(Math.PI * u));
        return ColorUtil.pack(
                Math.round(ColorUtil.red(color) * k),
                Math.round(ColorUtil.green(color) * k),
                Math.round(ColorUtil.blue(color) * k),
                ColorUtil.alpha(color));
    }

    private static RenderPipeline pipeline(String name, BlendFunction blend) {
        return RenderPipeline.builder()
                .withLocation(Identifier.parse("godweer:pipeline/world/" + name))
                .withVertexShader(Identifier.parse("godweer:core/lines"))
                .withFragmentShader(Identifier.parse("godweer:core/lines"))
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withColorTargetState(new ColorTargetState(blend))
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .withCull(false)
                .build();
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    public void release() {
        lastTarget = null;
        chainAnim1 = 0.0F;
        chainAnim2 = 0.0F;
        appear = 0.0F;
        moving = 0.0F;
        lastNanos = 0L;
        additive.clear();
        alpha.clear();
        spriteList.clear();
        additive.close();
        alpha.close();
    }

    /** Треугольный меш в мировых координатах (относительно камеры) с собственным GPU-буфером. */
    private static final class Layer {
        private record Pt(double x, double y, double z, int color) {
        }

        private final RenderPipeline pipeline;
        private final List<Pt> points = new ArrayList<>();
        private GpuBuffer vertexBuffer;
        private GpuBuffer transformBuffer;

        Layer(RenderPipeline pipeline) {
            this.pipeline = pipeline;
        }

        void clear() {
            points.clear();
        }

        void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, int cb, int cc, int cd) {
            Pt pa = pt(a, ca), pb = pt(b, cb), pc = pt(c, cc), pd = pt(d, cd);
            points.add(pa); points.add(pb); points.add(pc);
            points.add(pa); points.add(pc); points.add(pd);
        }

        private static Pt pt(Vec3 v, int color) {
            return new Pt(v.x, v.y, v.z, color);
        }

        void submit(Camera camera, Matrix4f projection) {
            if (points.isEmpty()) return;
            int count = points.size();
            BufferBuilder builder = new BufferBuilder(
                    ByteBufferBuilder.exactlySized(count * VERTEX_SIZE),
                    PrimitiveTopology.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            for (Pt p : points) {
                builder.addVertex((float) p.x, (float) p.y, (float) p.z).setColor(p.color);
            }
            MeshData mesh = builder.buildOrThrow();
            try {
                var device = RenderSystem.getDevice();
                ByteBuffer data = mesh.vertexBuffer();
                int byteCount = data.remaining();
                int vertexCount = mesh.drawState().vertexCount();
                if (vertexCount <= 0) return;

                var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
                if (target == null || target.getColorTextureView() == null || target.getDepthTextureView() == null) return;

                ensureVertexCapacity(byteCount);
                device.createCommandEncoder().writeToBuffer(this.vertexBuffer.slice(0, byteCount), data);

                Matrix4f mvp = new Matrix4f(projection).mul(camera.getViewRotationMatrix(new Matrix4f()));
                ensureTransformBuffer();
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    ByteBuffer uniform = Std140Builder.onStack(stack, TRANSFORM_SIZE).putMat4f(mvp).get();
                    device.createCommandEncoder().writeToBuffer(this.transformBuffer.slice(), uniform);
                }

                try (RenderPass pass = device.createCommandEncoder().createRenderPass(
                        () -> "Godweer Target Effects",
                        target.getColorTextureView(),
                        Optional.empty(),
                        target.getDepthTextureView(),
                        OptionalDouble.empty())) {
                    pass.setPipeline(RenderSystem.getCompiledPipeline(this.pipeline));
                    pass.setUniform("Projection", this.transformBuffer);
                    pass.setVertexBuffer(0, this.vertexBuffer.slice(0, byteCount));
                    pass.draw(vertexCount, 1, 0, 0);
                }
            } finally {
                mesh.close();
            }
        }

        private void ensureTransformBuffer() {
            if (this.transformBuffer != null) return;
            this.transformBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Godweer Target Effects UBO",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, TRANSFORM_SIZE);
        }

        private void ensureVertexCapacity(int byteSize) {
            if (this.vertexBuffer != null && this.vertexBuffer.size() >= byteSize) return;
            if (this.vertexBuffer != null) this.vertexBuffer.close();
            int capacity = Math.max(byteSize + byteSize / 2, 64 * 1024);
            this.vertexBuffer = RenderSystem.getDevice().createBuffer(
                    () -> "Godweer Target Effects Vertices",
                    GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, capacity);
        }

        void close() {
            if (this.vertexBuffer != null) { this.vertexBuffer.close(); this.vertexBuffer = null; }
            if (this.transformBuffer != null) { this.transformBuffer.close(); this.transformBuffer = null; }
        }
    }
}
