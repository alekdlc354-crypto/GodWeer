package pub.godweer.kitty.module.impl.render;

import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.hud.HotbarHud;
import pub.godweer.hud.HudElement;
import pub.godweer.hud.HudManager;
import pub.godweer.hud.ServerGui;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.MultiModeSetting;

import java.util.List;

/**
 * Toggles HUD elements.
 */
public final class Interface extends Module {

    public final MultiModeSetting elements = multiMode(
            "Elements",
            List.of("Watermark", "Player", "Potions", "Staff", "Hotkeys", "Hotbar", "Server GUI"),
            "Watermark", "Player", "Potions", "Staff", "Hotkeys", "Hotbar", "Server GUI"
    );

    public Interface() {
        super("Interface", "Интерфейс", Category.RENDER);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        updateHudState();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        updateHudState();
    }

    @EventTarget(priority = 1000)
    public void onRender2D(Render2DEvent event) {
        updateHudState();
    }

    private void updateHudState() {
        HudManager manager = HudManager.getInstance();
        boolean moduleActive = this.isEnabled();
        for (HudElement el : manager.getElements()) {
            el.setEnabled(moduleActive && this.elements.isEnabled(el.getName()));
        }
        HotbarHud.getInstance().setEnabled(moduleActive && this.elements.isEnabled("Hotbar"));
        ServerGui.setEnabled(moduleActive && this.elements.isEnabled("Server GUI"));
    }
}
