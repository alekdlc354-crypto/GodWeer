package pub.godweer.kitty.module.impl.misc;

import net.minecraft.client.Minecraft;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.Render3DEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

/**
 * Zoom - Zooms in the view like OptiFine with smooth zoom multiplier
 */
public final class Zoom extends Module {

    public final SliderSetting zoomMultiplier = slider("Zoom Multiplier", 3.0F, 1.5F, 10.0F, 0.5F);
    public final CheckBox smoothZoom = checkbox("Smooth Zoom", true);
    public final SliderSetting smoothSpeed = slider("Smooth Speed", 0.15F, 0.05F, 0.5F, 0.05F);

    private float currentFov = 0;
    private float targetFov = 0;
    private float originalFov = 0;
    private boolean wasZooming = false;

    public Zoom() {
        super("Zoom", "Zooms in the view", Category.MISC);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null) {
            originalFov = mc.options.fov().get();
            currentFov = originalFov;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && originalFov > 0) {
            mc.options.fov().set((int) originalFov);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Minecraft mc = event.getClient();
        if (mc.options == null) return;

        float targetFov = originalFov / zoomMultiplier.getValue();

        if (smoothZoom.getValue()) {
            float speed = smoothSpeed.getValue();
            currentFov += (targetFov - currentFov) * speed;
        } else {
            currentFov = targetFov;
        }

        mc.options.fov().set((int) currentFov);
        wasZooming = true;
    }

    public boolean isZooming() {
        return isEnabled();
    }

    public float getZoomMultiplier() {
        return zoomMultiplier.getValue();
    }
}
