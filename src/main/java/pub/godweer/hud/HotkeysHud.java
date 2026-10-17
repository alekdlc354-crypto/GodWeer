package pub.godweer.hud;

import pub.godweer.Client;
import pub.godweer.IMinecraft;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.auxiliary.render.renders.font.IconUse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hotkeys list: header icon, category icon, rounded key pills, row animations.
 */
public final class HotkeysHud extends HudElement implements IMinecraft {

    private static final float PAD = 6.0F;
    private static final float HEADER_H = 16.0F;
    private static final float ROW_H = 13.5F;
    private static final float ICON_SZ = 8.0F;
    private static final int BG = ColorUtil.rgba(8, 8, 12, 228);

    private record Entry(Module module, String key) {}

    private static IconUse catIcon(Category c) {
        return switch (c) {
            case COMBAT -> IconUse.FIGHT;
            case MOVEMENT -> IconUse.MOVEMENT;
            case RENDER -> IconUse.RENDER;
            case PLAYER -> IconUse.PLAYER;
            case MISC -> IconUse.MISC;
        };
    }

    private final Map<String, Float> anims = new HashMap<>();
    private final Map<String, Float> switchAnims = new HashMap<>();
    private float animatedW = -1.0F;

    public HotkeysHud() {
        super("hotkeys", "Hotkeys", 262.0F, 60.0F, 116.0F, 70.0F);
    }

    @Override
    public boolean shouldRender() {
        if (!enabled) return false;
        if (HudManager.editMode()) return true;
        return !hotkeys().isEmpty();
    }

    private List<Entry> hotkeys() {
        List<Entry> out = new ArrayList<>();
        if (Client.INSTANCE != null && Client.INSTANCE.moduleManager != null) {
            for (Module m : Client.INSTANCE.moduleManager.getModules()) {
                if (m.getBind() != null && m.getBind().isBound()) {
                    String d = m.getBind().getDisplayValue();
                    if (!d.equalsIgnoreCase("NONE")) out.add(new Entry(m, d));
                }
            }
            // Enabled first, then by name — stable order.
            out.sort((a, b) -> {
                boolean ao = a.module() != null && a.module().isEnabled();
                boolean bo = b.module() != null && b.module().isEnabled();
                if (ao != bo) return ao ? -1 : 1;
                String an = a.module() != null ? a.module().getName() : "";
                String bn = b.module() != null ? b.module().getName() : "";
                return an.compareToIgnoreCase(bn);
            });
        }
        if (out.isEmpty() && HudManager.editMode()) {
            out.add(new Entry(null, "R"));
            out.add(new Entry(null, "R"));
            out.add(new Entry(null, "-"));
            out.add(new Entry(null, "O"));
        }
        return out;
    }

    private String demoName(int idx) {
        return switch (idx) {
            case 0 -> "Aura";
            case 1 -> "AirStuck";
            case 2 -> "Levitation";
            default -> "GriefGlide";
        };
    }

    private static Category demoCat(int idx) {
        return switch (idx) {
            case 0 -> Category.COMBAT;
            case 1 -> Category.PLAYER;
            case 2 -> Category.MOVEMENT;
            default -> Category.MOVEMENT;
        };
    }

    @Override
    public void draw(Render2DEvent event) {
        List<Entry> keys = hotkeys();
        if (keys.isEmpty() && !HudManager.editMode()) {
            this.width = 0;
            this.height = 0;
            return;
        }

        for (int i = 0; i < keys.size(); i++) {
            anims.putIfAbsent(rowId(keys.get(i), i).toLowerCase(), 0.0F);
        }
        float listH = 0.0F;
        float maxW = Fonts.SF_MEDIUM.getWidth("Hotkeys", 8.0F) + 26.0F;
        for (int i = 0; i < keys.size(); i++) {
            Entry k = keys.get(i);
            String name = k.module() != null ? k.module().getName() : demoName(i);
            float a = anims.getOrDefault(rowId(k, i).toLowerCase(), 0.0F);
            a += (1.0F - a) * 0.2F;
            anims.put(rowId(k, i).toLowerCase(), a);
            listH += ROW_H * a;
            if (a > 0.05F) {
                maxW = Math.max(maxW, Fonts.SF_MEDIUM.getWidth(name, 7.5F)
                        + Fonts.SF_MEDIUM.getWidth(k.key(), 7.0F) + 64.0F);
            }
        }
        anims.keySet().removeIf(k -> {
            for (int i = 0; i < keys.size(); i++) {
                if (rowId(keys.get(i), i).toLowerCase().equals(k)) return false;
            }
            return true;
        });

        float targetW = Math.max(134.0F, maxW + PAD * 2.0F);
        if (animatedW < 0.0F) animatedW = targetW;
        animatedW += (targetW - animatedW) * 0.25F;

        this.width = animatedW;
        this.height = PAD * 2.0F + HEADER_H + Math.max(ROW_H, listH);

        Render2D.drawBlur(x, y, width, height, 8.0F, BG, 1.0F);

        int accent = Theme.getAccentColor();
        int white = ColorUtil.rgba(240, 242, 248, 255);
        Render2D.drawRoundedRect(x + PAD, y + PAD + 1.0F, 12.0F, 12.0F, 4.0F,
                ColorUtil.withAlpha(accent, 45));
        Fonts.drawIcon(IconUse.KEYBOARD, x + PAD + 1.5F, y + PAD + 2.5F, 9.0F,
                ColorUtil.withAlpha(accent, 255));
        Fonts.drawString(Fonts.SF_MEDIUM, "Hotkeys", x + PAD + 16.0F, y + PAD + 2.5F, 8.0F,
                ColorUtil.withAlpha(accent, 255));

        float cy = y + PAD + HEADER_H;
        for (int i = 0; i < keys.size(); i++) {
            Entry k = keys.get(i);
            String name = k.module() != null ? k.module().getName() : demoName(i);
            Category cat = k.module() != null ? k.module().getCategory() : demoCat(i);
            float a = anims.getOrDefault(rowId(k, i).toLowerCase(), 1.0F);
            float rh = ROW_H * a;
            if (a <= 0.04F) continue;
            float slide = (1.0F - a) * -5.0F;
            boolean on = entryOn(k, i);
            float dim = on ? 1.0F : 0.5F;

            // Category icon with accent tint
            Fonts.drawIcon(catIcon(cat), x + PAD + slide, cy + (rh - ICON_SZ) / 2.0F - 0.5F, ICON_SZ,
                    ColorUtil.multiplyAlpha(on ? accent : Theme.TEXT_MUTED, a * dim));

            // Key pill (cell) on the right
            String key = k.key();
            float bw = Fonts.SF_MEDIUM.getWidth(key, 7.0F) + 8.0F;
            float bx = x + width - PAD - bw;

            // Animated on/off switch left of the key pill
            float swA = switchAnim(k, i);
            float swW = 18.0F, swH = 9.0F;
            float swX = bx - 5.0F - swW;
            float swY = cy + (rh - swH) / 2.0F;
            int track = ColorUtil.lerp(ColorUtil.rgba(52, 55, 68, 255), accent, swA);
            Render2D.drawRoundedRect(swX, swY, swW, swH, swH / 2.0F,
                    ColorUtil.multiplyAlpha(track, a * (on ? 1.0F : 0.75F)));
            float thumbD = 6.5F;
            float thumbX = swX + 1.25F + (swW - thumbD - 2.5F) * swA;
            Render2D.drawCircle(thumbX + thumbD / 2.0F, swY + swH / 2.0F, thumbD / 2.0F,
                    ColorUtil.rgba(255, 255, 255, (int) (255 * a)));

            float nameAvail = swX - 5.0F - (x + PAD + ICON_SZ + 4.0F);
            Fonts.drawString(Fonts.SF_MEDIUM, Fonts.SF_MEDIUM.trimToWidth(name, Math.max(10.0F, nameAvail), 7.5F),
                    x + PAD + ICON_SZ + 4.0F + slide, cy + 2.5F, 7.5F,
                    ColorUtil.multiplyAlpha(white, a * dim));

            Render2D.drawRoundedRectWithOutline(bx, cy + 2.0F, bw, 10.0F, 5.0F,
                    ColorUtil.rgba(255, 255, 255, (int) (18 * a)), 0.75F,
                    ColorUtil.withAlpha(on ? accent : Theme.TEXT_MUTED, (int) ((on ? 140 : 70) * a)));
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, key, bx + bw / 2.0F, cy + 3.0F, 7.0F,
                    ColorUtil.rgba(235, 238, 245, (int) (255 * a * dim)));
            cy += rh;
        }
    }

    private String rowId(Entry e, int idx) {
        return e.module() != null ? e.module().getName() : "demo" + idx;
    }

    private boolean entryOn(Entry e, int idx) {
        if (e.module() != null) return e.module().isEnabled();
        return idx != 2;
    }

    private float switchAnim(Entry e, int idx) {
        String id = rowId(e, idx).toLowerCase() + "#sw";
        float target = entryOn(e, idx) ? 1.0F : 0.0F;
        float v = switchAnims.getOrDefault(id, target);
        v += (target - v) * 0.3F;
        if (Math.abs(target - v) < 0.002F) v = target;
        switchAnims.put(id, v);
        return v;
    }
}
