package pub.godweer.auxiliary.client.target;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.CoreShaders;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import pub.godweer.auxiliary.client.clients.ColorUtil;

/**
 * Crystals Target ESP — 14 faceted 3D crystals with neon glow orbiting around the target.
 */
public final class CrystalsTargetEspRenderer {

    private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath("godweer", "images/world/bloom.png");

    private static final int CRYSTAL_COUNT = 14;
    private static final float CRYSTAL_WIDTH = 0.07f;
    private static final float CRYSTAL_HEIGHT = 0.14f;
    private static final float GLOW_SCALE = 3.6f;
    private static final float GLOW_ALPHA = 0.45f;
    private static final float ANIM_DURATION = 0.35f;

    private LivingEntity target;
    private LivingEntity lastTarget;
    private float progress;
    private long lastFrameNs = System.nanoTime();

    public void setTarget(LivingEntity entity) {
        if (entity != null) lastTarget = entity;
        target = entity;
    }

    public void render(PoseStack matrices, float tickDelta, int colorA, int colorB, boolean throughWalls) {
        Minecraft mc = Minecraft.getInstance();
        
        updateAnimation();
        float anim = easeOutCubic(progress);
        if (anim <= 0.001f) return;

        LivingEntity entity = target != null ? target : lastTarget;
        if (entity == null || mc.player == null || mc.level == null) return;

        Camera camera = mc.gameRenderer.mainCamera();
        net.minecraft.world.phys.Vec3 camPos = camera.getPosition();

        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX()) - camPos.x;
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY()) - camPos.y;
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ()) - camPos.z;

        float entityHeight = entity.getBbHeight();
        float baseRadius = entity.getBbWidth() * 0.5f + 0.45f;

        float time = mc.player.tickCount + tickDelta;
        float globalRotation = time * 1.8f;
        float sector = 360.0f / CRYSTAL_COUNT;

        float[] px = new float[CRYSTAL_COUNT];
        float[] py = new float[CRYSTAL_COUNT];
        float[] pz = new float[CRYSTAL_COUNT];
        float[] angles = new float[CRYSTAL_COUNT];
        int[] colors = new int[CRYSTAL_COUNT];

        for (int i = 0; i < CRYSTAL_COUNT; i++) {
            float angleJitter = Mth.sin(i * 1.7f) * (sector * 0.35f);
            float heightFactor = Mth.sin(i * 2.3f + 0.6f) * 0.5f + 0.5f;
            float radiusFactor = Mth.cos(i * 3.1f + 1.2f);

            float angleDeg = globalRotation + i * sector + angleJitter;
            float rad = angleDeg * Mth.DEG_TO_RAD;

            float wave = Mth.sin(time * 0.08f + i * 0.9f) * 0.12f;
            float radius = baseRadius + radiusFactor * 0.18f;

            px[i] = Mth.cos(rad) * radius;
            py[i] = Mth.clamp(heightFactor * entityHeight + wave, 0.05f, entityHeight);
            pz[i] = Mth.sin(rad) * radius;
            angles[i] = angleDeg;

            float t = Mth.sin(rad) * 0.5f + 0.5f;
            colors[i] = ColorUtil.lerp(colorA, colorB, t);
        }

        matrices.pushPose();
        matrices.translate(x, y, z);

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, 
                               com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        if (throughWalls) RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);

        float glowSize = CRYSTAL_HEIGHT * GLOW_SCALE * anim;
        for (int i = 0; i < CRYSTAL_COUNT; i++) {
            matrices.pushPose();
            matrices.translate(px[i], py[i], pz[i]);
            matrices.mulPose(Axis.YP.rotationDegrees(-camera.getYRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            Matrix4f m = matrices.last().pose();

            int c = colors[i];
            float r = ColorUtil.getRGBAFloat(c)[0];
            float g = ColorUtil.getRGBAFloat(c)[1];
            float b = ColorUtil.getRGBAFloat(c)[2];
            float a = GLOW_ALPHA * anim;

            buffer.addVertex(m, -glowSize, -glowSize, 0).setUv(0, 0).setColor(r, g, b, a);
            buffer.addVertex(m, -glowSize, glowSize, 0).setUv(0, 1).setColor(r, g, b, a);
            buffer.addVertex(m, glowSize, glowSize, 0).setUv(1, 1).setColor(r, g, b, a);
            buffer.addVertex(m, glowSize, -glowSize, 0).setUv(1, 0).setColor(r, g, b, a);
            matrices.popPose();
        }
        
        MeshData meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        buffer = tessellator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);

        for (int i = 0; i < CRYSTAL_COUNT; i++) {
            matrices.pushPose();
            matrices.translate(px[i], py[i], pz[i]);
            matrices.mulPose(Axis.YP.rotationDegrees(-angles[i]));
            matrices.scale(anim, anim, anim);
            drawOctahedron(buffer, matrices.last().pose(), colors[i], anim);
            matrices.popPose();
        }
        
        meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        matrices.popPose();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private void drawOctahedron(BufferBuilder buf, Matrix4f m, int color, float alpha) {
        float w = CRYSTAL_WIDTH, h = CRYSTAL_HEIGHT;

        float[] top = {0, h, 0};
        float[] bottom = {0, -h, 0};
        float[][] eq = {
                {w, 0, 0},
                {0, 0, w},
                {-w, 0, 0},
                {0, 0, -w}
        };

        int bright = shade(color, 1.3f);
        int base = color;
        int dark = shade(color, 0.6f);
        float a = alpha;

        for (int k = 0; k < 4; k++) {
            float[] e1 = eq[k];
            float[] e2 = eq[(k + 1) % 4];
            boolean front = (k == 0 || k == 1);
            tri(buf, m, top, e1, e2, front ? bright : base, a);
            tri(buf, m, bottom, e2, e1, dark, a);
        }
    }

    private void tri(BufferBuilder buf, Matrix4f m, float[] v1, float[] v2, float[] v3, int c, float a) {
        float r = ColorUtil.getRGBAFloat(c)[0];
        float g = ColorUtil.getRGBAFloat(c)[1];
        float b = ColorUtil.getRGBAFloat(c)[2];
        buf.addVertex(m, v1[0], v1[1], v1[2]).setColor(r, g, b, a);
        buf.addVertex(m, v2[0], v2[1], v2[2]).setColor(r, g, b, a);
        buf.addVertex(m, v3[0], v3[1], v3[2]).setColor(r, g, b, a);
    }

    private void updateAnimation() {
        long now = System.nanoTime();
        float dt = Math.min((now - lastFrameNs) / 1_000_000_000f, 0.1f);
        lastFrameNs = now;
        float step = dt / ANIM_DURATION;
        progress = target != null ? Math.min(1f, progress + step) : Math.max(0f, progress - step);
        if (progress <= 0f && target == null) lastTarget = null;
    }

    private static float easeOutCubic(float x) {
        float inv = 1f - x;
        return 1f - inv * inv * inv;
    }

    private static int shade(int c, float f) {
        int r = Mth.clamp((int) (((c >> 16) & 0xFF) * f), 0, 255);
        int g = Mth.clamp((int) (((c >> 8) & 0xFF) * f), 0, 255);
        int b = Mth.clamp((int) ((c & 0xFF) * f), 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
