package pub.godweer.auxiliary.client.target;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.auxiliary.client.Annstable;
import pub.godweer.auxiliary.math.anim.EaseAnimation;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer.Sprite;

import java.util.ArrayList;
import java.util.List;

/**
 * Create by daun kvass
 */
public final class GhostTargetRenderer {
    private static final int STRANDS = 3;
    private static final int PARTICLES_PER_STRAND = 42;

    private final EaseAnimation appear = new EaseAnimation(450L, EaseAnimation.Easing.EASE_OUT_QUAD);
    private final List<Sprite> sprites = new ArrayList<>(STRANDS * PARTICLES_PER_STRAND + 20);
    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();
    private final TargetDeathDissolve deathDissolve = new TargetDeathDissolve();

    private final List<List<Vec3>> strandHistories = new ArrayList<>();

    private LivingEntity lastTarget;
    private Vec3 lastTargetPos;
    private int dissolvedTargetId = Integer.MIN_VALUE;
    private boolean animationTarget;
    private long lastFrameNanos;
    private float clock;

    public GhostTargetRenderer() {
        for (int i = 0; i < STRANDS; i++) {
            this.strandHistories.add(new ArrayList<>());
        }
    }

    public void render(LivingEntity activeTarget, float tickDelta, int color) {
        long nowNanos = System.nanoTime();
        long nowMillis = System.currentTimeMillis();
        float delta = this.lastFrameNanos == 0L
                ? 0.016F
                : Math.min(0.08F, (nowNanos - this.lastFrameNanos) / 1.0E9F);
        this.lastFrameNanos = nowNanos;

        boolean present = valid(activeTarget);
        if (present) {
            this.lastTarget = activeTarget;
            if (activeTarget.getId() == this.dissolvedTargetId) {
                this.dissolvedTargetId = Integer.MIN_VALUE;
            }
        }

        if (this.lastTarget != null && (!present || dead(this.lastTarget)) && this.lastTarget.getId() != this.dissolvedTargetId) {
            this.deathDissolve.burst(this.sprites, this.lastTarget, color, nowMillis);
            this.dissolvedTargetId = this.lastTarget.getId();
            present = false;
        }

        updateAnimations(present);

        float appearValue = this.appear.getValue();
        if (this.lastTarget == null || appearValue <= 0.01F) {
            if (!present && appearValue <= 0.01F) {
                clearHistories();
                this.lastTarget = null;
            }
            this.deathDissolve.render(delta, nowMillis);
            return;
        }

        this.clock += delta * 2.6F;

        Vec3 currentTargetPos = Render3DUtil.interpolatedPosition(this.lastTarget, tickDelta);
        if (this.lastTargetPos == null) {
            this.lastTargetPos = currentTargetPos;
        } else {
            this.lastTargetPos = this.lastTargetPos.lerp(currentTargetPos, Math.min(1.0, delta * 16.0));
        }

        float targetHeight = this.lastTarget.getBbHeight();
        float targetWidth = this.lastTarget.getBbWidth();
        float baseRadius = (targetWidth * 0.7F + 0.5F) * appearValue;
        int animatedColor = Annstable.blend(color, this.lastTarget, 1.0F);

        this.sprites.clear();

        for (int s = 0; s < STRANDS; s++) {
            double strandOffset = s * (Math.PI * 2.0D / STRANDS);
            double angle = this.clock + strandOffset;

            double waveY = Math.sin(this.clock * 2.0D + strandOffset) * 0.35D;
            double y = (targetHeight * 0.5D) + waveY;

            Vec3 headPos = this.lastTargetPos.add(
                    Math.cos(angle) * baseRadius,
                    y,
                    Math.sin(angle) * baseRadius
            );

            List<Vec3> history = this.strandHistories.get(s);
            history.add(0, headPos);

            while (history.size() > PARTICLES_PER_STRAND) {
                history.remove(history.size() - 1);
            }

            for (int p = 0; p < history.size(); p++) {
                Vec3 pPos = history.get(p);
                float progress = (float) p / (float) PARTICLES_PER_STRAND;

                float tailFade = (1.0F - progress);
                float alpha = (float) Math.pow(tailFade, 1.35) * appearValue * 0.95F;
                float sizeFactor = (0.11F + (float) Math.sin((1.0F - progress) * Math.PI * 0.5F) * 0.06F) * appearValue;

                if (alpha <= 0.005F || sizeFactor <= 0.001F) continue;

                this.sprites.add(new Sprite(
                        pPos,
                        sizeFactor,
                        multiplyAlpha(animatedColor, alpha)
                ));
            }
        }

        if (!this.sprites.isEmpty()) {
            this.renderer.render(this.sprites, 1.35F, true);
        }

        this.deathDissolve.render(delta, nowMillis);
    }

    public void reset() {
        this.lastTarget = null;
        this.lastTargetPos = null;
        this.animationTarget = false;
        this.lastFrameNanos = 0L;
        this.clock = 0.0F;
        this.dissolvedTargetId = Integer.MIN_VALUE;
        this.sprites.clear();
        this.deathDissolve.clear();
        clearHistories();
        this.appear.animate(0.0F, 0.0F, 0L, EaseAnimation.Easing.EASE_OUT_QUAD);
    }

    private void clearHistories() {
        for (List<Vec3> history : this.strandHistories) {
            history.clear();
        }
    }

    private void updateAnimations(boolean present) {
        if (present == this.animationTarget) {
            return;
        }
        this.animationTarget = present;
        float target = present ? 1.0F : 0.0F;
        this.appear.animate(this.appear.getValue(), target, present ? 450L : 350L, EaseAnimation.Easing.EASE_OUT_QUAD);
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    private static boolean dead(LivingEntity entity) {
        return entity != null && (entity.isDeadOrDying() || entity.deathTime > 0 || entity.getHealth() <= 0.0F);
    }

    private static int multiplyAlpha(int color, float alphaFactor) {
        int a = Math.round(((color >> 24) & 0xFF) * alphaFactor);
        return (a << 24) | (color & 0x00FFFFFF);
    }
    public boolean hasActive() {
        return this.appear.getValue() > 0.01F || this.deathDissolve.hasActive();
    }
}