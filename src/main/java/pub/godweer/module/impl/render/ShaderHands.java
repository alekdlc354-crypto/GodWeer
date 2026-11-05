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
 * Шейдерная заливка рук от первого лица: маска рук из разницы глубины
 * заливается выбранным эффектом. Старые aurora/nebula/holo/caustics/ember/frost
 * удалены целиком — пять новых эффектов написаны с нуля и работают на полной
 * геометрии кисти (позиция и нормаль восстанавливаются из глубины), поэтому
 * рисунок лежит на руке, а не плавает по экрану.
 * <p>
 * Новые эффекты — post-шейдеры в assets/godweer/shaders/post/hands:
 * <ul>
 *   <li>Chrome — жидкий металл с процедурной студией и анизотропной полировкой;</li>
 *   <li>Prism — тонкоплёночная интерференция с дисперсией по каналам;</li>
 *   <li>Voltage — электрические разряды по гребням доменно-искажённого fbm;</li>
 *   <li>Vapor — закрученный дым с доменным искажением и подсветкой кромки;</li>
 *   <li>Solar — плазма звезды на турбулентности в двух масштабах.</li>
 * </ul>
 */
public final class ShaderHands extends Module {

    public static final String CHROME = "Chrome";
    public static final String PRISM = "Prism";
    public static final String VOLTAGE = "Voltage";
    public static final String VAPOR = "Vapor";
    public static final String SOLAR = "Solar";

    public static ShaderHands INSTANCE;

    private final HeaderSetting main = header("Main");
    private final ModeSetting style = mode("Style", CHROME, CHROME, PRISM, VOLTAGE, VAPOR, SOLAR);
    private final SliderSetting speed = slider("Speed", 1.0f, 0.1f, 5.0f, 0.1f);
    private final SliderSetting opacity = slider("Opacity", 0.95f, 0.0f, 1.0f, 0.02f);
    private final CheckBox bothHands = checkbox("Both Hands", true);

    private final ShaderHandsRenderer renderer = new ShaderHandsRenderer();

    public ShaderHands() {
        super("ShaderHands", "Заливка рук: хром, призма, разряды, дым, плазма", Category.RENDER);
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
