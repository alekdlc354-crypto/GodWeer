package pub.godweer.util.target;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.module.impl.render.TargetEsp;
import pub.godweer.util.render.Render3DUtil;
import pub.godweer.util.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.util.style.ColorUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Target ESP "Ghost": три светящихся сгустка летают по перекрестным орбитам
 * вокруг цели, каждый тянет за собой хвост из таких же сгустков.
 * <p>
 * Сгусток — плоский квадрат с текстурой свечения, который всегда развёрнут к
 * камере (билборд делает {@link ParticlesWorldRenderer}), поэтому объёма в
 * геометрии нет, а шар читается с любого угла.
 * <p>
 * Хвост отстаёт по времени: элемент {@code i} летит по тому же кругу, но с
 * задержкой {@code i * TRAIL_STEP}, и гаснет к концу хвоста. Три орбиты
 * отличаются знаком при координатах, из-за чего траектории идут восьмёрками и
 * пересекаются, а не складываются в один круг.
 */
public final class GhostTargetRenderer {

    private static final int ORBITS = 3;
    private static final float TRAIL_STEP = 0.045F;
    private static final float BASE_ANGULAR_SPEED = 2.0F;
    private static final float MAX_DELTA = 0.05F;
    private static final float BRIGHTNESS = 1.35F;

    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();
    private final List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>(ORBITS * 8 * 2);

    private double time;
    private long lastNanos;
    private float appear;

    public void render(TargetEsp module, float tickDelta, LivingEntity target, int color) {
        double dt = tickSeconds();
        this.time += dt;

        boolean present = valid(target);
        this.appear = approach(this.appear, present ? 1.0F : 0.0F, (float) (dt / 0.30));
        if (!present || this.appear <= 0.01F) {
            this.sprites.clear();
            return;
        }

        Vec3 base = Render3DUtil.interpolatedPosition(target, tickDelta);
        double bodyHeight = Math.max(0.6D, target.getBbHeight());
        double bodyWidth = Math.max(0.35D, target.getBbWidth());
        double radius = bodyWidth * 0.95D * Math.max(0.2D, module.ghostRadius.get());
        double speed = Math.max(0.2D, module.ghostSpeed.get());
        int trail = Mth.clamp((int) module.ghostLength.get(), 1, 12);

        double centerY = bodyHeight * 0.55D;
        double timeSeconds = this.time * speed;

        this.sprites.clear();
        for (int orbit = 0; orbit < ORBITS; orbit++) {
            for (int i = 0; i < trail; i++) {
                double angle = BASE_ANGULAR_SPEED * (timeSeconds - i * TRAIL_STEP);
                double sin = Math.sin(angle);
                double cos = Math.cos(angle);

                double ox;
                double oy;
                double oz;
                switch (orbit) {
                    case 0 -> {
                        ox = sin;
                        oy = cos * 0.55D;
                        oz = -cos;
                    }
                    case 1 -> {
                        ox = -sin;
                        oy = sin * 0.55D;
                        oz = -cos;
                    }
                    default -> {
                        ox = sin;
                        oy = -cos * 0.55D;
                        oz = -cos;
                    }
                }

                // Хвост гаснет и слегка ужимается к концу: без этого он обрывается
                // резкой ступенькой.
                float tail = 1.0F - i / (float) trail;
                float alpha = tail * tail * this.appear;
                if (alpha <= 0.01F) continue;

                Vec3 center = new Vec3(
                        base.x + ox * radius,
                        base.y + centerY + oy * radius,
                        base.z + oz * radius);

                float size = (float) (0.085D * bodyWidth * (0.6D + 0.4D * tail));
                int core = ColorUtil.withAlpha(brighten(color, 1.2F), alpha * 0.85F);
                int glow = ColorUtil.withAlpha(color, alpha * 0.28F);

                this.sprites.add(new ParticlesWorldRenderer.Sprite(center, size * 2.4F, glow));
                this.sprites.add(new ParticlesWorldRenderer.Sprite(center, size, core));
            }
        }

        if (!this.sprites.isEmpty()) {
            this.renderer.render(this.sprites, BRIGHTNESS, true, true);
        }
    }

    public void reset() {
        this.time = 0.0D;
        this.appear = 0.0F;
        this.lastNanos = 0L;
        this.sprites.clear();
    }

    private double tickSeconds() {
        long now = System.nanoTime();
        double dt = this.lastNanos == 0L
                ? 1.0D / 60.0D
                : Math.min((now - this.lastNanos) / 1.0E9D, MAX_DELTA);
        this.lastNanos = now;
        return dt;
    }

    private static float approach(float value, float goal, float step) {
        if (value < goal) return Math.min(goal, value + step);
        return Math.max(goal, value - step);
    }

    private static int brighten(int color, float scale) {
        return ColorUtil.rgba(
                Math.min(255, Math.round(ColorUtil.red(color) * scale)),
                Math.min(255, Math.round(ColorUtil.green(color) * scale)),
                Math.min(255, Math.round(ColorUtil.blue(color) * scale)),
                255);
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }
}
