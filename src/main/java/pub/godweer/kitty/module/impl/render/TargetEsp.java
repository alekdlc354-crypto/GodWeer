package pub.godweer.kitty.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import pub.godweer.Client;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.client.target.TargetEspRenderer;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.impl.combat.AttackAura;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

/**
 * Target ESP: highlights the target with a themed 2D marker in its center and/or
 * a rotating ring of bloom souls with trails around it.
 */
public final class TargetEsp extends Module {

    public static final String MODE_MARKER = "Marker";
    public static final String MODE_SOULS = "Souls";
    public static final String MODE_LIGHTNING = "Lightning";

    public final ModeSetting mode = mode("Режим", MODE_MARKER, MODE_MARKER, MODE_SOULS, MODE_LIGHTNING);

    public final SliderSetting soulSpeed = slider("Speed Souls", 1.0f, 0.0f, 4.0f, 0.1f);

    public final CheckBox themeColor = checkbox("Color From Theme", true);
    public final ColorSetting color = color("Color", ColorUtil.rgba(255, 170, 45, 255)).visible(() -> !themeColor.getValue());

    public final SliderSetting lightningCount = slider("Молнии: Количество", 3.0f, 1.0f, 8.0f, 1.0f).visible(this::usesLightning);
    public final SliderSetting lightningWidth = slider("Молнии: Толщина", 1.0f, 0.4f, 2.5f, 0.1f).visible(this::usesLightning);
    public final CheckBox lightningHurt = checkbox("Молнии: Вспышка при уроне", true).visible(this::usesLightning);

    private final TargetEspRenderer renderer = new TargetEspRenderer();

    public TargetEsp() {
        super("TargetEsp", "Подсвечивает цель маркером, душами или молниями", Category.RENDER);
    }

    public LivingEntity resolveTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) return null;

        if (Client.INSTANCE != null && Client.INSTANCE.moduleManager != null) {
            AttackAura aura = Client.INSTANCE.moduleManager.getAttackAura();
            if (aura != null && aura.isEnabled() && aura.getTarget() != null
                    && aura.getTarget().isAlive() && !aura.getTarget().isRemoved()
                    && !aura.getTarget().isDeadOrDying() && aura.getTarget() != mc.player
                    && aura.getTarget().level() == mc.level) {
                return aura.getTarget();
            }
        }
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le
                && le.isAlive() && !le.isDeadOrDying() && le != mc.player) {
            return le;
        }
        return null;
    }

    public int getAccentColor() {
        return themeColor.getValue() ? Theme.getAccentColor() : color.getValue();
    }

    public TargetEspRenderer getRenderer() {
        return this.renderer;
    }

    public boolean hasTarget() {
        return resolveTarget() != null;
    }

    public boolean usesMarker() {
        return mode.getValue().equalsIgnoreCase(MODE_MARKER);
    }

    public boolean usesSouls() {
        return mode.getValue().equalsIgnoreCase(MODE_SOULS);
    }

    public boolean usesLightning() {
        return mode.getValue().equalsIgnoreCase(MODE_LIGHTNING);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (event.getGuiGraphicsExtractor() == null) return;
        this.renderer.render(event, this);
    }
}