package pub.godweer.module.impl.render;

import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.world.ShaderHandsRenderer;

/**
 * Заливка рук от первого лица: маска рук из разницы глубины закрашивается
 * выбранным эффектом.
 * <p>
 * Все пять эффектов — чистый 2D по экранным координатам: глубина и нормаль не
 * восстанавливаются, узор строится из uv и времени. Старые наборы удалены.
 * Файлы лежат в assets/godweer/shaders/post/hands: mesh, silk, rings, halftone,
 * embers.
 */
public final class ShaderHands extends Module {

    public static final String MESH = "Mesh";
    public static final String SILK = "Silk";
    public static final String RINGS = "Rings";
    public static final String HALFTONE = "Halftone";
    public static final String EMBERS = "Embers";

    public static ShaderHands INSTANCE;

    private final HeaderSetting main = header("Main");
    private final ModeSetting style = mode("Style", MESH, MESH, SILK, RINGS, HALFTONE, EMBERS);
    private final SliderSetting speed = slider("Speed", 1.0f, 0.1f, 5.0f, 0.1f);
    private final SliderSetting opacity = slider("Opacity", 0.95f, 0.0f, 1.0f, 0.02f);
    private final CheckBox bothHands = checkbox("Both Hands", true);

    private final ShaderHandsRenderer renderer = new ShaderHandsRenderer();

    public ShaderHands() {
        super("ShaderHands", "Заливка рук: сетка, шёлк, кольца, полутон, искры", Category.RENDER);
        INSTANCE = this;
    }

    public ShaderHandsRenderer getRenderer() {
        return this.renderer;
    }

    public String getStyle() {
        return style.getValue();
    }

    public float getSpeed() {
        return speed.getValue().floatValue();
    }

    public float getOpacity() {
        return opacity.getValue().floatValue();
    }

    public int getColor() {
        return Theme.getAccentColor();
    }

    public boolean bothHands() {
        return bothHands.getValue();
    }
}
