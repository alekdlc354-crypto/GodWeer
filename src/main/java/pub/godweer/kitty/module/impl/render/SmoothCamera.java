package pub.godweer.kitty.module.impl.render;

import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.GameTickEvent;
import pub.godweer.enjoy.mixin.render.SmoothCameraMixin;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

/**
 * SmoothCamera - инерционная камера для плавного движения от 3-го лица
 * Сглаживает поворот и позицию камеры
 */
public final class SmoothCamera extends Module {
    public static SmoothCamera INSTANCE;

    public final SliderSetting smooth = slider("Smooth", 0.1F, 0.01F, 0.3F, 0.01F);

    public SmoothCamera() {
        super("SmoothCamera", "Smooth camera movement in 3rd person view", Category.RENDER);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        SmoothCameraMixin.setEnabled(true);
        SmoothCameraMixin.setSmooth(smooth.getValue());
    }

    @Override
    public void onDisable() {
        SmoothCameraMixin.setEnabled(false);
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        if (isEnabled()) {
            SmoothCameraMixin.setSmooth(smooth.getValue());
        }
    }
}
