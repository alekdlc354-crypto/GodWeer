package pub.godweer.auxiliary.client.target;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Crystals Target ESP — 14 faceted 3D crystals with neon glow orbiting around the target.
 * Uses ParticlesWorldRenderer for rendering.
 */
public final class CrystalsTargetEspRenderer {

    private static final int CRYSTAL_COUNT = 14;
    private static final float CRYSTAL_SIZE = 0.12f;
    private static final float GLOW_SCALE = 2.5f;
    private static final float GLOW_ALPHA = 0.4f;
    private static final float ANIM_DURATION = 0.35f;

    private final ParticlesWorldRenderer particleRenderer = new ParticlesWorldRenderer();
    private LivingEntity target;
    private LivingEntity lastTarget;
    private float progress;
    private long lastFrameNs = System.nanoTime();

    public void setTarget(LivingEntity entity) {
        if (entity != null) lastTarget = entity;
        target = entity;
    }

    public void render(float tickDelta, int colorA, int colorB, boolean throughWalls) {
        Minecraft mc = Minecraft.getInstance();
        
        updateAnimation();
        float anim = easeOutCubic(progress);
        if (anim <= 0.001f) return;

        LivingEntity entity = target != null ? target : lastTarget;
        if (entity == null || mc.player == null || mc.level == null) return;

        float entityHeight = entity.getBbHeight();
        float baseRadius = entity.getBbWidth() * 0.5f + 0.45f;

        float time = (System.currentTimeMillis() % 100000L) / 1000.0f;
        float globalRotation = time * 1.8f;
        float sector = 360.0f / CRYSTAL_COUNT;

        List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>();

        for (int i = 0; i < CRYSTAL_COUNT; i++) {
            float angleJitter = (float) Math.sin(i * 1.7f) * (sector * 0.35f);
            float heightFactor = (float) Math.sin(i * 2.3f + 0.6f) * 0.5f + 0.5f;
            float radiusFactor = (float) Math.cos(i * 3.1f + 1.2f);

            float angleDeg = globalRotation + i * sector + angleJitter;
            float rad = (float) Math.toRadians(angleDeg);

            float wave = (float) Math.sin(time * 0.08f + i * 0.9f) * 0.12f;
            float radius = baseRadius + radiusFactor * 0.18f;

            float px = (float) Math.cos(rad) * radius;
            float py = Math.min(Math.max(heightFactor * entityHeight + wave, 0.05f), entityHeight);
            float pz = (float) Math.sin(rad) * radius;

            float t = (float) Math.sin(rad) * 0.5f + 0.5f;
            int color = ColorUtil.lerp(colorA, colorB, t);

            Vec3 crystalPos = entity.position().add(px, py, pz);

            // Glow effect
            float glowSize = CRYSTAL_SIZE * GLOW_SCALE * anim;
            sprites.add(new ParticlesWorldRenderer.Sprite(
                crystalPos,
                glowSize,
                ColorUtil.multiplyAlpha(color, GLOW_ALPHA * anim)
            ));

            // Crystal core
            float coreSize = CRYSTAL_SIZE * anim;
            sprites.add(new ParticlesWorldRenderer.Sprite(
                crystalPos,
                coreSize,
                ColorUtil.multiplyAlpha(color, anim)
            ));

            // Bright center
            float centerSize = CRYSTAL_SIZE * 0.4f * anim;
            sprites.add(new ParticlesWorldRenderer.Sprite(
                crystalPos,
                centerSize,
                ColorUtil.rgba(255, 255, 255, (int) (anim * 200))
            ));
        }

        if (!sprites.isEmpty()) {
            particleRenderer.render(sprites, 3.5f, throughWalls, true);
        }
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
}
