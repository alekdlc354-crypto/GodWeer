package pub.godweer.util.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.module.impl.render.TargetEsp;

/**
 * Target ESP "Ring": обруч ходит от ног к макушке и обратно, за ним тянется
 * вертикальный световой шлейф.
 * <p>
 * Ход считается по ease-in-out quad: у груди обруч разгоняется, у головы и ног
 * тормозит перед разворотом. Шлейф тянется позади движения — при подъёме вниз,
 * при спуске вверх, — а у самых краёв сжимается в ноль, чтобы не висеть в
 * воздухе над макушкой. Сам обруч дополнительно обводится плотным рядом мелких
 * спрайтов: это и есть чёткая линия контура.
 */
public final class RingTargetRenderer extends TargetEspEffect {

    private static final double CYCLE_MS = 2000.0D;
    private static final int RIM_SEGMENTS = 72;
    private static final int CURTAIN_SEGMENTS = 48;
    private static final int CURTAIN_STEPS = 4;

    @Override
    protected void build(TargetEsp module, LivingEntity target, Vec3 base, int color) {
        double height = Math.max(0.6D, target.getBbHeight());
        double width = Math.max(0.3D, target.getBbWidth());
        float speed = Math.max(0.2F, module.ringSpeed.get());

        double radius = width * 0.8D + 0.06D;
        double elapsed = this.time * 1000.0D * speed;
        double phase = (elapsed % CYCLE_MS) / CYCLE_MS;
        boolean rising = ((long) (elapsed / CYCLE_MS)) % 2L == 0L;

        double eased = phase < 0.5D
                ? 2.0D * phase * phase
                : 1.0D - Math.pow(-2.0D * phase + 2.0D, 2.0D) / 2.0D;

        double from = 0.10D;
        double span = Math.max(0.05D, height - from * 2.0D);
        double ringY = base.y + from + (rising ? eased : 1.0D - eased) * span;

        // Длина шлейфа: максимум в середине хода, ноль у головы и ног.
        double tail = 0.55D * Math.sin(Math.PI * eased) * height;
        double tailSign = rising ? -1.0D : 1.0D;

        // Контур обруча.
        for (int i = 0; i < RIM_SEGMENTS; i++) {
            double angle = i * (Math.PI * 2.0D / RIM_SEGMENTS) + this.time * 0.4D;
            Vec3 point = new Vec3(
                    base.x + Math.cos(angle) * radius,
                    ringY,
                    base.z + Math.sin(angle) * radius);
            add(point, 0.022F, 0.030F, brighten(color, 1.3F), 0.75F * this.appear);
        }

        // Световой шлейф под обручем.
        if (tail > 0.02D) {
            for (int i = 0; i < CURTAIN_SEGMENTS; i++) {
                double angle = i * (Math.PI * 2.0D / CURTAIN_SEGMENTS) + this.time * 0.4D;
                double x = base.x + Math.cos(angle) * radius;
                double z = base.z + Math.sin(angle) * radius;
                for (int step = 0; step < CURTAIN_STEPS; step++) {
                    double k = step / (double) CURTAIN_STEPS;
                    float alpha = (1.0F - (float) k) * 0.30F * this.appear;
                    add(new Vec3(x, ringY + tail * tailSign * k, z),
                            0.030F, 0.055F, color, alpha);
                }
            }
        }
    }
}
