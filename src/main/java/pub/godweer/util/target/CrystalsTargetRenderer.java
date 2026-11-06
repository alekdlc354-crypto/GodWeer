package pub.godweer.util.target;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.module.impl.render.TargetEsp;
import pub.godweer.util.style.ColorUtil;

/**
 * Target ESP "Crystals": хоровод вытянутых свечений вокруг пояса цели.
 * <p>
 * Каждый элемент — тот же билборд, но сжатый по ширине и растянутый по высоте
 * в 2.4 раза: квадрат свечения превращается в вертикальный кристалл. Круг
 * делится на равные углы, весь хоровод медленно вращается (оборот за 10 с) и
 * целиком покачивается по синусу с периодом 3.5 с. Оттенок каждого кристалла
 * гуляет по кругу между темой и её же осветлённой версией.
 */
public final class CrystalsTargetRenderer extends TargetEspEffect {

    private static final double SPIN_SECONDS = 10.0D;
    private static final double BOB_SECONDS = 3.5D;
    private static final float WIDTH = 0.045F;
    private static final float HEIGHT = WIDTH * 2.4F;

    @Override
    protected void build(TargetEsp module, LivingEntity target, Vec3 base, int color) {
        int count = Mth.clamp((int) module.crystalCount.get(), 3, 20);
        double height = Math.max(0.6D, target.getBbHeight());
        double width = Math.max(0.3D, target.getBbWidth());
        float speed = Math.max(0.2F, module.crystalSpeed.get());

        double radius = width * 0.75D + 0.10D;
        double spin = this.time * (Math.PI * 2.0D / SPIN_SECONDS) * speed;
        double bob = Math.sin(this.time * (Math.PI * 2.0D / BOB_SECONDS)) * height * 0.3D;
        double y = base.y + height * 0.5D + bob;

        int light = brighten(color, 1.5F);

        for (int i = 0; i < count; i++) {
            double angle = spin + i * (Math.PI * 2.0D / count);
            Vec3 point = new Vec3(
                    base.x + Math.cos(angle) * radius,
                    y,
                    base.z + Math.sin(angle) * radius);

            float mix = (float) (0.5D + 0.5D * Math.sin(angle * 3.0D));
            int tint = ColorUtil.lerp(color, light, mix);

            add(point, WIDTH * 1.8F, HEIGHT * 1.25F, tint, 0.16F * this.appear);
            add(point, WIDTH, HEIGHT, tint, 0.80F * this.appear);
        }
    }
}
