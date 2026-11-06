package pub.godweer.util.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.module.impl.render.TargetEsp;
import pub.godweer.util.render.Render3DUtil;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.util.style.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Общая часть мировых эффектов Target ESP: считает время кадра, ведёт
 * появление/затухание, копит билборды и отдаёт их одним проходом.
 * <p>
 * Наследнику остаётся расставить спрайты в {@link #build}: билборд сам
 * разворачивается к камере, поэтому плоские квадраты с текстурой свечения
 * читаются как объёмные шары с любого угла.
 */
public abstract class TargetEspEffect {

    protected static final float MAX_DELTA = 0.05F;
    protected static final float BRIGHTNESS = 1.35F;
    private static final float APPEAR_SECONDS = 0.28F;

    protected final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();
    protected final List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>(320);

    protected double time;
    protected double delta;
    protected float appear;

    private long lastNanos;

    public void render(TargetEsp module, float tickDelta, LivingEntity target, int color) {
        this.delta = tickSeconds();
        this.time += this.delta;

        boolean present = target != null && target.isAlive() && !target.isRemoved();
        this.appear = approach(this.appear, present ? 1.0F : 0.0F, (float) (this.delta / APPEAR_SECONDS));

        this.sprites.clear();
        if (!present || this.appear <= 0.01F) return;

        Vec3 base = Render3DUtil.interpolatedPosition(target, tickDelta);
        build(module, target, base, color);

        if (!this.sprites.isEmpty()) {
            this.renderer.render(this.sprites, BRIGHTNESS, true, true);
        }
    }

    protected abstract void build(TargetEsp module, LivingEntity target, Vec3 base, int color);

    public void reset() {
        this.time = 0.0D;
        this.delta = 0.0D;
        this.appear = 0.0F;
        this.lastNanos = 0L;
        this.sprites.clear();
    }

    protected void add(Vec3 position, float halfWidth, float halfHeight, int color, float alpha) {
        if (alpha <= 0.01F) return;
        this.sprites.add(new ParticlesWorldRenderer.Sprite(position, halfWidth, halfHeight,
                ColorUtil.withAlpha(color, alpha)));
    }

    protected static int brighten(int color, float scale) {
        return ColorUtil.rgba(
                Math.min(255, Math.round(ColorUtil.red(color) * scale)),
                Math.min(255, Math.round(ColorUtil.green(color) * scale)),
                Math.min(255, Math.round(ColorUtil.blue(color) * scale)),
                255);
    }

    protected static float approach(float value, float goal, float step) {
        if (value < goal) return Math.min(goal, value + step);
        return Math.max(goal, value - step);
    }

    private double tickSeconds() {
        long now = System.nanoTime();
        double dt = this.lastNanos == 0L
                ? 1.0D / 60.0D
                : Math.min((now - this.lastNanos) / 1.0E9D, MAX_DELTA);
        this.lastNanos = now;
        return dt;
    }
}
