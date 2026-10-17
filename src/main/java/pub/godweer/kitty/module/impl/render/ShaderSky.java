package pub.godweer.kitty.module.impl.render;

import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.HeaderSetting;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;
import pub.godweer.auxiliary.client.clients.Theme;

/**
 * Animated shader sky: fills the sky with a moving aurora-veil shader.
 */
public class ShaderSky extends Module {
    public static ShaderSky INSTANCE;

    public static final String STYLE_AURORA = "Aurora Veil";
    public static final String STYLE_CRIMSON = "Crimson Nebula";
    public static final String STYLE_OCEAN = "Ocean Abyss";
    public static final String STYLE_SUNSET = "Sunset Bloom";
    public static final String STYLE_STORM = "Storm Veil";
    public static final String STYLE_EMERALD = "Emerald Flow";
    public static final String STYLE_SOLAR = "Solar Winds";
    public static final String STYLE_VOID = "Void Pulse";

    private final HeaderSetting skyHeader = header("Shader Sky");
    private final ModeSetting style = mode("Style", STYLE_AURORA,
            STYLE_AURORA, STYLE_CRIMSON, STYLE_OCEAN, STYLE_SUNSET, STYLE_STORM, STYLE_EMERALD,
            STYLE_SOLAR, STYLE_VOID);
    private final SliderSetting speed = slider("Speed", 4.0f, 0.1f, 8.0f, 0.1f);
    private final SliderSetting intensity = slider("Intensity", 3.0f, 0.1f, 5.0f, 0.1f);
    private final SliderSetting aurora = slider("Aurora", 1.0f, 0.0f, 1.5f, 0.05f);
    private final SliderSetting stars = slider("Stars", 1.0f, 0.0f, 1.5f, 0.05f);

    public ShaderSky() {
        super("ShaderSky", "Анимированный шейдер неба", Category.RENDER);
        INSTANCE = this;
    }

    public String getStyle() {
        return style.getValue();
    }

    public int getColor1() {
        return Theme.getAccentColor();
    }

    public int getColor2() {
        return Theme.getAccentColor();
    }

    public float getSpeed() {
        return speed.getValue().floatValue();
    }

    public float getIntensity() {
        return intensity.getValue().floatValue();
    }

    public float getAurora() {
        return aurora.getValue().floatValue();
    }

    public float getStars() {
        return stars.getValue().floatValue();
    }
}
