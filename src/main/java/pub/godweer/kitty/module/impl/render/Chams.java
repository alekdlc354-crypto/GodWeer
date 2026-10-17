package pub.godweer.kitty.module.impl.render;

import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.HeaderSetting;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;

/**
 * Shader chams: the entity model itself is repainted with procedural
 * shaders (see the frag shaders under assets/godweer/shaders/world/chams),
 * with a depth-tested solid pass plus an optional depth-ignoring
 * through-walls ghost pass.
 *
 * Modes: Glass (fresnel glass body), Glow (scrolling energy bands with a
 * shader glow outline around the silhouette), Plasma (flowing plasma).
 * All per-frame parameters are packed into the vertex tint by ChamsHook:
 * rgb = colour with CPU-side pulse, alpha = animation phase / opacity.
 */
public final class Chams extends Module {

    public static final String GLASS = "Glass";
    public static final String GLOW = "Glow";
    public static final String PLASMA = "Plasma";

    public static Chams INSTANCE;

    private final HeaderSetting main = header("Main");
    private final ModeSetting mode = mode("Mode", GLASS, GLASS, GLOW, PLASMA);
    private final CheckBox throughWalls = checkbox("Through Walls", true);
    private final CheckBox players = checkbox("Players", true);
    private final CheckBox mobs = checkbox("Mobs", false);
    private final CheckBox self = checkbox("Self", false);
    private final SliderSetting range = slider("Range", 128.0f, 8.0f, 256.0f, 4.0f);

    private final HeaderSetting glassHeader = header("Glass").visible(() -> mode.is(GLASS));
    private final ColorSetting glassColor = color("Glass Color", ColorUtil.rgba(150, 215, 255, 255)).visible(() -> mode.is(GLASS));
    private final SliderSetting opacity = slider("Opacity", 0.35f, 0.05f, 1.0f, 0.05f).visible(() -> mode.is(GLASS));

    private final HeaderSetting glowHeader = header("Glow").visible(() -> mode.is(GLOW));
    private final ColorSetting glowColor = color("Glow Color", Theme.getAccentColor()).visible(() -> mode.is(GLOW));
    private final SliderSetting glowSpeed = slider("Speed", 1.6f, 0.1f, 6.0f, 0.1f).visible(() -> mode.is(GLOW));
    private final SliderSetting glowIntensity = slider("Intensity", 1.0f, 0.1f, 2.0f, 0.05f).visible(() -> mode.is(GLOW));

    private final HeaderSetting plasmaHeader = header("Plasma").visible(() -> mode.is(PLASMA));
    private final ColorSetting plasmaColor = color("Plasma Color", ColorUtil.rgba(215, 95, 255, 255)).visible(() -> mode.is(PLASMA));
    private final SliderSetting plasmaSpeed = slider("Speed", 1.2f, 0.1f, 6.0f, 0.1f).visible(() -> mode.is(PLASMA));
    private final SliderSetting plasmaIntensity = slider("Intensity", 1.0f, 0.1f, 2.0f, 0.05f).visible(() -> mode.is(PLASMA));

    public Chams() {
        super("Chams", "Шейдерное закрашивание моделей: стекло, glow, plasma", Category.RENDER);
        INSTANCE = this;
    }

    public boolean isGlass() {
        return mode.is(GLASS);
    }

    public boolean isGlow() {
        return mode.is(GLOW) || (!isGlass() && !isPlasma());
    }

    public boolean isPlasma() {
        return mode.is(PLASMA);
    }

    public boolean isThroughWalls() {
        return throughWalls.getValue();
    }

    public boolean showPlayers() {
        return players.getValue();
    }

    public boolean showMobs() {
        return mobs.getValue();
    }

    public boolean showSelf() {
        return self.getValue();
    }

    public float getRange() {
        return range.getValue().floatValue();
    }

    public int getColor() {
        if (isGlass()) return glassColor.getValue();
        if (isPlasma()) return plasmaColor.getValue();
        return glowColor.getValue();
    }

    public float getOpacity() {
        return opacity.getValue().floatValue();
    }

    public float getSpeed() {
        if (isPlasma()) return plasmaSpeed.getValue().floatValue();
        return glowSpeed.getValue().floatValue();
    }

    public float getIntensity() {
        if (isPlasma()) return plasmaIntensity.getValue().floatValue();
        return glowIntensity.getValue().floatValue();
    }
}
