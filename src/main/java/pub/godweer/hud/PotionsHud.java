package pub.godweer.hud;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import pub.godweer.IMinecraft;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.Render2DUtil;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.auxiliary.render.renders.font.IconUse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Active potions list with icon header, rounded slots and row animations.
 */
public final class PotionsHud extends HudElement implements IMinecraft {

    private static final float PAD = 6.0F;
    private static final float HEADER_H = 16.0F;
    private static final float ROW_H = 14.0F;
    private static final int BG = ColorUtil.rgba(8, 8, 12, 228);
    private static final int RED = ColorUtil.rgba(255, 77, 82, 255);
    private static final int RING_TRACK = ColorUtil.rgba(255, 255, 255, 28);
    private static final float RING_R = 4.4F;
    private static final float RING_THICK = 1.7F;
    private static final float RING_SIZE = RING_R * 2.0F + RING_THICK + 2.0F;

    private static final class Potion {
        final Identifier icon;
        final String name;
        final String time;
        final boolean bad;
        final int duration;
        final boolean infinite;

        Potion(Identifier icon, String name, String time, boolean bad, int duration, boolean infinite) {
            this.icon = icon;
            this.name = name;
            this.time = time;
            this.bad = bad;
            this.duration = duration;
            this.infinite = infinite;
        }

        String key() {
            return icon.toString() + "|" + name;
        }
    }

    private final Map<String, Float> anims = new HashMap<>();
    private final Map<String, Integer> maxDurations = new HashMap<>();
    private float animatedW = -1.0F;

    public PotionsHud() {
        super("potions", "Potions", 10.0F, 98.0F, 140.0F, 60.0F);
    }

    @Override
    public boolean shouldRender() {
        if (!enabled) return false;
        if (HudManager.editMode()) return true;
        return mc.player != null && !mc.player.getActiveEffects().isEmpty();
    }

    private List<Potion> potions() {
        List<Potion> out = new ArrayList<>();
        if (mc.player != null) {
            Collection<MobEffectInstance> fx = mc.player.getActiveEffects();
            for (MobEffectInstance inst : fx) {
                Holder<MobEffect> holder = inst.getEffect();
                MobEffect eff = holder.value();
                Identifier id = holder.unwrapKey().map(k -> k.identifier()).orElse(null);
                if (id == null) continue;
                Identifier tex = id.withPath(p -> "textures/mob_effect/" + p + ".png");
                String name = eff.getDisplayName().getString();
                if (inst.getAmplifier() > 0) name += " " + (inst.getAmplifier() + 1);
                String t;
                boolean infinite = inst.isInfiniteDuration();
                if (infinite) {
                    t = "**:**";
                } else {
                    int sec = inst.getDuration() / 20;
                    t = String.format("%d:%02d", sec / 60, sec % 60);
                }
                out.add(new Potion(tex, name, t, eff.getCategory() == MobEffectCategory.HARMFUL,
                        inst.getDuration(), infinite));
            }
        }
        if (out.isEmpty() && HudManager.editMode()) {
            out.add(new Potion(Identifier.withDefaultNamespace("textures/mob_effect/fire_resistance.png"), "Огнестойкость", "4:35", false, 5500, false));
            out.add(new Potion(Identifier.withDefaultNamespace("textures/mob_effect/absorption.png"), "Поглощение", "1:35", false, 1900, false));
            out.add(new Potion(Identifier.withDefaultNamespace("textures/mob_effect/resistance.png"), "Сопротивление", "4:03", false, 4860, false));
        }
        return out;
    }

    @Override
    public void draw(Render2DEvent event) {
        List<Potion> pots = potions();
        if (pots.isEmpty() && !HudManager.editMode()) {
            this.width = 0;
            this.height = 0;
            return;
        }

        for (Potion p : pots) {
            anims.putIfAbsent(p.key(), 0.0F);
        }
        float listH = 0.0F;
        float maxW = Fonts.SF_MEDIUM.getWidth("Active potions", 8.0F) + 26.0F;
        for (Potion p : pots) {
            float a = anims.getOrDefault(p.key(), 0.0F);
            a += (1.0F - a) * 0.2F;
            anims.put(p.key(), a);
            if (!p.infinite) {
                int prev = maxDurations.getOrDefault(p.key(), 0);
                if (p.duration > prev) maxDurations.put(p.key(), p.duration);
            }
            listH += ROW_H * a;
            if (a > 0.05F) {
                maxW = Math.max(maxW, 16.0F + Fonts.SF_MEDIUM.getWidth(p.name, 7.5F)
                        + RING_SIZE + 3.0F + Fonts.SF_MEDIUM.getWidth(p.time, 7.5F) + 14.0F);
            }
        }
        anims.keySet().removeIf(k -> {
            boolean gone = true;
            for (Potion p : pots) {
                if (p.key().equals(k)) {
                    gone = false;
                    break;
                }
            }
            return gone;
        });
        maxDurations.keySet().removeIf(k -> {
            for (Potion p : pots) {
                if (p.key().equals(k)) return false;
            }
            return true;
        });

        float targetW = Math.max(120.0F, maxW + PAD * 2.0F);
        if (animatedW < 0.0F) animatedW = targetW;
        animatedW += (targetW - animatedW) * 0.25F;

        this.width = animatedW;
        this.height = PAD * 2.0F + HEADER_H + Math.max(ROW_H, listH);

        Render2D.drawBlur(x, y, width, height, 8.0F, BG, 1.0F);

        int accent = Theme.getAccentColor();
        int white = ColorUtil.rgba(240, 242, 248, 255);
        int gray = ColorUtil.rgba(150, 155, 170, 255);

        Render2D.drawRoundedRect(x + PAD, y + PAD + 1.0F, 12.0F, 12.0F, 4.0F,
                ColorUtil.withAlpha(accent, 45));
        Fonts.drawIcon(IconUse.POTION, x + PAD + 2.0F, y + PAD + 3.0F, 8.0F,
                ColorUtil.withAlpha(accent, 255));
        Fonts.drawString(Fonts.SF_MEDIUM, "Active potions", x + PAD + 16.0F, y + PAD + 2.5F, 8.0F,
                ColorUtil.withAlpha(accent, 255));

        var extractor = event.getGuiGraphicsExtractor();
        float cy = y + PAD + HEADER_H;
        for (Potion p : pots) {
            float a = anims.getOrDefault(p.key(), 1.0F);
            float rh = ROW_H * a;
            if (a <= 0.04F) continue;
            float slide = (1.0F - a) * -5.0F;

            Render2D.drawRoundedRect(x + PAD + slide, cy + 1.5F, 11.0F, 11.0F, 3.5F,
                    ColorUtil.rgba(255, 255, 255, (int) (10 * a)));
            float iconSize = 9.0F * a;
            if (extractor != null && iconSize > 2.0F) {
                Render2DUtil.flush();
                extractor.pose().pushMatrix();
                extractor.pose().translate(x + PAD + slide + (11.0F - iconSize) / 2.0F, cy + 1.5F + (11.0F - iconSize) / 2.0F);
                float sc = iconSize / 18.0F;
                extractor.pose().scale(sc, sc);
                extractor.blit(p.icon, 0, 0, 18, 18, 0.0F, 1.0F, 0.0F, 1.0F);
                extractor.pose().popMatrix();
            }
            float ty = cy + (rh - 8.0F) / 2.0F + 0.5F;
            float tw = Fonts.SF_MEDIUM.getWidth(p.time, 7.5F);
            float timeX = x + width - PAD - tw;

            float ringCx = timeX - 4.0F - RING_R;
            float ringCy = cy + rh / 2.0F;
            float nameAvail = (ringCx - RING_R) - (x + PAD + 15.0F + slide) - 3.0F;
            if (nameAvail < 10.0F) nameAvail = 10.0F;

            Fonts.drawString(Fonts.SF_MEDIUM, Fonts.SF_MEDIUM.trimToWidth(p.name, nameAvail, 7.5F),
                    x + PAD + 15.0F + slide, ty, 7.5F, ColorUtil.multiplyAlpha(p.bad ? RED : white, a));

            int timeColor = p.bad
                    ? ColorUtil.rgba(255, 130, 130, (int) (255 * a))
                    : ColorUtil.rgba(150, 155, 170, (int) (255 * a));
            Fonts.drawString(Fonts.SF_MEDIUM, p.time, timeX, ty, 7.5F, timeColor);

            float frac;
            if (p.infinite) {
                frac = 1.0F;
            } else {
                int max = maxDurations.getOrDefault(p.key(), Math.max(1, p.duration));
                frac = max <= 0 ? 1.0F : Mth.clamp(p.duration / (float) max, 0.0F, 1.0F);
            }
            Render2D.drawArc(ringCx, ringCy, RING_R, RING_THICK, 0.0F, 360.0F,
                    ColorUtil.multiplyAlpha(RING_TRACK, a));
            if (frac > 0.001F) {
                Render2D.drawArc(ringCx, ringCy, RING_R, RING_THICK, -90.0F, 360.0F * frac,
                        ColorUtil.multiplyAlpha(p.bad ? RED : accent, a));
            }
            cy += rh;
        }
    }
}
