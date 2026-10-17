package pub.godweer.kitty.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.ChamsRenderTypes;
import net.minecraft.client.renderer.rendertype.RenderType;
import pub.godweer.auxiliary.client.clients.ColorUtil;

/**
 * Target matching + tint packing for the shader chams (see
 * LivingEntityRendererMixin and HumanoidArmorLayerMixin).
 *
 * The chams pipelines have no custom uniforms, so every per-frame
 * parameter is packed into the vertex tint (the tint int reaches the
 * shader as vertexColor because it is written into the vertex buffer
 * by Model.renderToBuffer):
 *
 *   Glass:  rgb = glass tint, alpha = opacity.
 *   Glow:   rgb = accent * pulse * intensity, alpha = band scroll phase (0..1).
 *   Plasma: rgb = colour * pulse * intensity, alpha = flow phase (0..1).
 *
 * The ghost (through walls) variant is the same tint dimmed down: the
 * shaders derive their alpha from luminance / opacity, so the ghost pass
 * naturally stays weaker than the depth-tested solid pass.
 */
public final class ChamsHook {

    private ChamsHook() {
    }

    public static RenderType renderType(LivingEntityRenderState state) {
        Chams m = target(state);
        return m == null ? null : normalType(m);
    }

    public static RenderType throughType(LivingEntityRenderState state) {
        Chams m = target(state);
        if (m == null || !m.isThroughWalls()) return null;
        return throughType(m);
    }

    public static RenderType normalType(LivingEntityRenderState state) {
        Chams m = target(state);
        return m == null ? null : normalType(m);
    }

    public static Integer tint(LivingEntityRenderState state) {
        Chams m = target(state);
        return m == null ? null : tint(m, false);
    }

    public static Integer throughTint(LivingEntityRenderState state) {
        Chams m = target(state);
        if (m == null || !m.isThroughWalls()) return null;
        return tint(m, true);
    }

    private static RenderType throughType(Chams m) {
        if (m.isGlass()) return ChamsRenderTypes.CHAMS_GLASS_THROUGH;
        if (m.isPlasma()) return ChamsRenderTypes.CHAMS_PLASMA_THROUGH;
        return ChamsRenderTypes.CHAMS_GLOW_THROUGH;
    }

    private static RenderType normalType(Chams m) {
        if (m.isGlass()) return ChamsRenderTypes.CHAMS_GLASS;
        if (m.isPlasma()) return ChamsRenderTypes.CHAMS_PLASMA;
        return ChamsRenderTypes.CHAMS_GLOW;
    }

    private static int tint(Chams m, boolean ghost) {
        if (m.isGlass()) return glassTint(m, ghost);
        if (m.isPlasma()) return plasmaTint(m, ghost);
        return glowTint(m, ghost);
    }

    private static int glassTint(Chams m, boolean ghost) {
        int c = m.getColor();
        float k = ghost ? 0.7F : 1.0F;
        float alpha = m.getOpacity() * (ghost ? 0.5F : 1.0F);
        return ColorUtil.rgba(
                scale(ColorUtil.red(c), k),
                scale(ColorUtil.green(c), k),
                scale(ColorUtil.blue(c), k),
                Math.round(alpha * 255.0F));
    }

    private static int glowTint(Chams m, boolean ghost) {
        double t = System.currentTimeMillis() / 1000.0;
        float pulse = (0.78F + 0.22F * (float) Math.sin(t * 3.0))
                * m.getIntensity() * (ghost ? 0.55F : 1.0F);
        int phase = (int) Math.round(((t * m.getSpeed() * 0.35) % 1.0) * 255.0);
        return packed(m.getColor(), pulse, phase);
    }

    private static int plasmaTint(Chams m, boolean ghost) {
        double t = System.currentTimeMillis() / 1000.0;
        float pulse = (0.85F + 0.15F * (float) Math.sin(t * 4.0))
                * m.getIntensity() * (ghost ? 0.55F : 1.0F);
        int phase = (int) Math.round(((t * m.getSpeed() * 0.30) % 1.0) * 255.0);
        return packed(m.getColor(), pulse, phase);
    }

    private static int packed(int color, float pulse, int phase) {
        return ColorUtil.rgba(
                scale(ColorUtil.red(color), pulse),
                scale(ColorUtil.green(color), pulse),
                scale(ColorUtil.blue(color), pulse),
                phase & 0xFF);
    }

    private static int scale(int channel, float k) {
        return Math.max(0, Math.min(255, Math.round(channel * k)));
    }

    private static Chams target(LivingEntityRenderState state) {
        Chams m = Chams.INSTANCE;
        if (m == null || !m.isEnabled() || state == null) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;

        float range = m.getRange();
        if (state.distanceToCameraSq > (double) range * range) return null;

        if (state instanceof AvatarRenderState avatar) {
            if (!m.showPlayers()) return null;
            // AvatarRenderState.id is the rendered entity's id (Avatar.getId()),
            // so this identifies the local player's own render state.
            return m.showSelf() || avatar.id != mc.player.getId() ? m : null;
        }
        return m.showMobs() ? m : null;
    }
}
