package pub.godweer.kitty.module.impl.misc;

import net.minecraft.client.Minecraft;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.Render3DEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

/**
 * FovChanger - Allows setting FOV beyond game limits
 */
public final class FovChanger extends Module {

    public final SliderSetting fov = slider("FOV", 90.0F, 30.0F, 180.0F, 5.0F);
    public final CheckBox noFovEffect = checkbox("No FOV Effects", false);
    public final CheckBox smoothTransition = checkbox("Smooth Transition", true);
    public final SliderSetting smoothSpeed = slider("Smooth Speed", 0.2F, 0.05F, 0.5F, 0.05F);

    private float currentFov = 0;
    private float originalFov = 0;

    public FovChanger() {
        super("FovChanger", "Changes field of view beyond game limits", Category.MISC);
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

        float targetFov = fov.getValue();

        if (smoothTransition.getValue()) {
            float speed = smoothSpeed.getValue();
            currentFov += (targetFov - currentFov) * speed;
        } else {
            currentFov = targetFov;
        }

        mc.options.fov().set((int) currentFov);
    }

    public boolean shouldRemoveFovEffects() {
        return isEnabled() && noFovEffect.getValue();
    }
}
