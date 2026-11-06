package pub.godweer.util.target;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.module.impl.render.TargetEsp;

/**
 * Target ESP "Chains": две плоские ленты обвивают торс цели и сжимаются в
 * момент захвата.
 * <p>
 * Лента строится по кругу из сегментов-билбордов: сплошной полосы в геометрии
 * нет, но на аддитивном смешивании плотная цепочка квадратов сливается в
 * сплошное кольцо. Каждый сегмент рисуется трижды — два широких слабых слоя
 * дают неоновый ореол, третий, узкий и яркий, читается как сама цепь.
 * <p>
 * Плоскости колец наклоняются по синусу в противоположных направлениях, поэтому
 * цепи складываются в «X» и покачиваются, как гироскоп. Радиус на входе 1.2 от
 * базы и за время появления уходит к 0.7: цель визуально затягивается.
 */
public final class ChainTargetRenderer extends TargetEspEffect {

    private static final int SEGMENTS = 46;
    private static final int LAYERS = 3;
    private static final float TILT = (float) Math.toRadians(20.0F);

    @Override
    protected void build(TargetEsp module, LivingEntity target, Vec3 base, int color) {
        double height = Math.max(0.6D, target.getBbHeight());
        double width = Math.max(0.3D, target.getBbWidth());
        float speed = Math.max(0.2F, module.chainsSpeed.get());

        // 1.2 -> 0.7 по мере проявления эффекта, то есть за те же ~0.4 с.
        double grip = 1.2D - 0.5D * Mth.clamp(this.appear, 0.0F, 1.0F);
        double radius = (width * 0.55D + 0.14D) * grip;
        double centerY = height * 0.55D;

        float sway = TILT * Mth.sin((float) this.time * 1.1F);
        double spin = this.time * 1.7D * speed;

        for (int chain = 0; chain < 2; chain++) {
            // Вторая цепь входит только после того, как первая встала на место.
            float shown = chain == 0 ? 1.0F : Mth.clamp((this.appear - 0.95F) / 0.05F, 0.0F, 1.0F);
            if (shown <= 0.01F) continue;
            float sign = chain == 0 ? 1.0F : -1.0F;
            float tx = sway * sign;
            float tz = sway * 0.8F * sign;
            float cosX = Mth.cos(tx);
            float sinX = Mth.sin(tx);
            float cosZ = Mth.cos(tz);
            float sinZ = Mth.sin(tz);

            for (int i = 0; i < SEGMENTS; i++) {
                double angle = i * (Math.PI * 2.0D / SEGMENTS) + spin + chain * 0.8D;
                double x0 = Math.cos(angle) * radius;
                double z0 = Math.sin(angle) * radius;

                // Наклон кольца вокруг X, затем вокруг Z.
                double y1 = -z0 * sinX;
                double z1 = z0 * cosX;
                double x2 = x0 * cosZ - y1 * sinZ;
                double y2 = x0 * sinZ + y1 * cosZ;

                Vec3 point = new Vec3(base.x + x2, base.y + centerY + y2, base.z + z1);

                for (int layer = LAYERS - 1; layer >= 0; layer--) {
                    float glow = 1.0F + layer * 0.45F;
                    float alpha = (layer == 0 ? 0.85F : layer == 1 ? 0.26F : 0.13F) * shown * this.appear;
                    add(point, 0.055F * glow, 0.115F * glow, color, alpha);
                }
            }
        }
    }
}
