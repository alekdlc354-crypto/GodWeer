package pub.godweer.hud;

import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.render.font.IconUse;

/**
 * Shared visual language for HUD elements, matching the reference screenshot:
 * every metric, header and row is its own small near-black pill — no glass, no
 * blur, no gradient, no outline, no shared card behind a list. Text is white /
 * grey, icons are tiny and the accent color only touches progress and glyphs.
 */
public final class HudStyle {

    /** Pill fill: near-black and almost opaque, so the world barely shows through. */
    public static final int CARD_BG = ColorUtil.rgba(6, 7, 12, 232);

    /** Pill geometry, shared by every HUD element so rows line up across panels. */
    public static final float PILL_H = 16.0F;
    public static final float PILL_R = 4.5F;
    public static final float PILL_GAP = 4.0F;
    public static final float PILL_PAD = 6.0F;
    /** Visible height every icon is fitted to, so glyphs of different size read as one line. */
    public static final float ICON = 8.0F;

    /** Subtle row / slot fills used inside the cards. */
    public static final int ROW_BG = ColorUtil.rgba(255, 255, 255, 7);
    public static final int ROW_BG_ACTIVE = ColorUtil.rgba(255, 255, 255, 14);
    public static final int PILL_BG = ColorUtil.rgba(255, 255, 255, 9);
    public static final int TRACK = ColorUtil.rgba(255, 255, 255, 22);
    /** Thin vertical stroke between segments of one pill. */
    public static final int DIVIDER = ColorUtil.rgba(255, 255, 255, 34);

    public static final int TEXT_PRIMARY = ColorUtil.rgba(240, 243, 247, 255);
    public static final int TEXT_SECONDARY = ColorUtil.rgba(150, 156, 168, 255);
    public static final int TEXT_DIM = ColorUtil.rgba(112, 118, 130, 255);

    /** Health bar from the reference: flat yellow body, lavender absorption tail. */
    public static final int HP_FILL = ColorUtil.rgba(252, 213, 0, 255);
    public static final int HP_ABSORB = ColorUtil.rgba(168, 160, 232, 255);

    // ------------------------------------------------------------------ panels
    //
    // Панели — общий язык для «крупных» элементов HUD (цель, кулдауны, стафф,
    // музыка, уведомления). Плоский пилюльный drawCard остаётся у watermark и
    // hotkeys: их внешний вид менять не просили.

    /** Верх панели: чуть светлее, чтобы карточка читалась как объёмная. */
    public static final int PANEL_TOP = ColorUtil.rgba(23, 25, 34, 238);
    /** Низ панели: почти чёрный. */
    public static final int PANEL_BOTTOM = ColorUtil.rgba(9, 10, 15, 244);
    /** Тонкая обводка панели. */
    public static final int PANEL_EDGE = ColorUtil.rgba(255, 255, 255, 20);
    /** Светлая линия-блик по верхней кромке. */
    public static final int PANEL_GLINT = ColorUtil.rgba(255, 255, 255, 34);
    /** Тень под панелью. */
    public static final int PANEL_SHADOW = ColorUtil.rgba(0, 0, 0, 90);

    public static final int BAR_TRACK = ColorUtil.rgba(255, 255, 255, 24);
    /** Светлый «призрак» здоровья, который догоняет полоску после урона. */
    public static final int BAR_GHOST = ColorUtil.rgba(255, 255, 255, 120);
    public static final int BAR_ARMOR = ColorUtil.rgba(126, 170, 255, 255);

    private HudStyle() {
    }

    /** Крупная панель: тень, вертикальный градиент и волосяная обводка. */
    public static void drawPanel(float x, float y, float w, float h, float radius) {
        drawPanel(x, y, w, h, radius, 1.0F);
    }

    public static void drawPanel(float x, float y, float w, float h, float radius, float alpha) {
        if (w <= 0.5F || h <= 0.5F || alpha <= 0.01F) return;
        Render2D.drawShadow(x, y + 1.5F, w, h, radius, 6.0F, ColorUtil.multiplyAlpha(PANEL_SHADOW, alpha));
        Render2D.drawGradientRound(x, y, w, h, radius,
                ColorUtil.multiplyAlpha(PANEL_TOP, alpha),
                ColorUtil.multiplyAlpha(PANEL_TOP, alpha),
                ColorUtil.multiplyAlpha(PANEL_BOTTOM, alpha),
                ColorUtil.multiplyAlpha(PANEL_BOTTOM, alpha));
        Render2D.drawRoundedOutline(x, y, w, h, radius, 1.0F, ColorUtil.multiplyAlpha(PANEL_EDGE, alpha));
        // Блик по верхней кромке не даёт панели слипнуться с миром за ней.
        if (h > 6.0F) {
            Render2D.drawRoundedRect(x + radius * 0.6F, y + 0.5F, Math.max(0.0F, w - radius * 1.2F), 1.0F,
                    0.5F, ColorUtil.multiplyAlpha(PANEL_GLINT, alpha));
        }
    }

    /** Полоска-трек: фон + заливка слева. Никаких излишеств. */
    public static void drawBar(float x, float y, float w, float h, float fraction, int fillColor) {
        if (w <= 0.5F || h <= 0.5F) return;
        float r = h / 2.0F;
        Render2D.drawRoundedRect(x, y, w, h, r, BAR_TRACK);
        float fill = w * Math.max(0.0F, Math.min(1.0F, fraction));
        if (fill > 0.5F) {
            Render2D.drawRoundedRect(x, y, fill, h, r, fillColor);
        }
    }

    /**
     * Полоска с «призраком»: сначала светлый след, который уползает вниз после
     * урона, потом сама заливка. След рисуется только когда он шире заливки.
     */
    public static void drawBarWithGhost(float x, float y, float w, float h,
                                        float fraction, float ghostFraction, int fillColor) {
        if (w <= 0.5F || h <= 0.5F) return;
        float r = h / 2.0F;
        Render2D.drawRoundedRect(x, y, w, h, r, BAR_TRACK);
        float ghost = w * Math.max(0.0F, Math.min(1.0F, ghostFraction));
        if (ghost > 0.5F) {
            Render2D.drawRoundedRect(x, y, ghost, h, r, BAR_GHOST);
        }
        float fill = w * Math.max(0.0F, Math.min(1.0F, fraction));
        if (fill > 0.5F) {
            Render2D.drawRoundedRect(x, y, fill, h, r, fillColor);
        }
    }

    /** Волосяной разделитель по горизонтали. */
    public static void drawHLine(float x, float y, float w, int color) {
        Render2D.drawRoundedRect(x, y, w, 1.0F, 0.5F, color);
    }

    /** Небольшая подпись-капс: мелкий серый текст. */
    public static void drawCaption(String text, float x, float y, float size) {
        Fonts.drawString(Fonts.SF_MEDIUM, text, x, y, size, TEXT_DIM);
    }

    public static void drawCard(float x, float y, float w, float h, float radius) {
        drawCard(x, y, w, h, radius, 1.0F);
    }

    public static void drawCard(float x, float y, float w, float h, float radius, float alpha) {
        if (w <= 0.5F || h <= 0.5F || alpha <= 0.01F) return;
        // Flat translucent fill: no outline, no blur, no gradient.
        Render2D.drawRoundedRect(x, y, w, h, radius, ColorUtil.multiplyAlpha(CARD_BG, alpha));
    }

    /** Standard pill: the one shape every HUD row, metric and header uses. */
    public static void drawPill(float x, float y, float w) {
        drawCard(x, y, w, PILL_H, PILL_R);
    }

    /** Thin vertical stroke between pill segments. Returns the x after it. */
    public static final float DIVIDER_W = 1.0F;

    public static float drawDivider(float x, float cy) {
        Render2D.drawRoundedRect(x, cy - 4.0F, DIVIDER_W, 8.0F, 0.5F, DIVIDER);
        return x + DIVIDER_W;
    }

    /** Width of `icon | text` content, the shape every pill segment is built from. */
    public static float segmentWidth(IconUse icon, String text, float size) {
        return Fonts.iconFittedWidth(icon, ICON) + 4.0F + DIVIDER_W + 4.0F
                + Fonts.SF_MEDIUM.getWidth(text, size);
    }

    /** Ширина `icon text` без разделителя — для шапок HUD. */
    public static float plainSegmentWidth(IconUse icon, String text, float size) {
        return Fonts.iconFittedWidth(icon, ICON) + 6.0F + Fonts.SF_MEDIUM.getWidth(text, size);
    }

    /** `icon text` без вертикальной черты между ними. */
    public static float drawPlainSegment(float x, float cy, IconUse icon, String text, float size,
                                         int iconColor, int textColor) {
        Fonts.drawIconFitted(icon, x, cy, ICON, iconColor);
        x += Fonts.iconFittedWidth(icon, ICON) + 6.0F;
        Fonts.drawString(Fonts.SF_MEDIUM, text, x,
                cy - Fonts.SF_MEDIUM.textHeight(size) / 2.0F + 0.35F, size, textColor);
        return x + Fonts.SF_MEDIUM.getWidth(text, size);
    }

    /** Draws `icon | text` at x, vertically centred on cy. Returns the x after it. */
    public static float drawSegment(float x, float cy, IconUse icon, String text, float size,
                                    int iconColor, int textColor) {
        Fonts.drawIconFitted(icon, x, cy, ICON, iconColor);
        x += Fonts.iconFittedWidth(icon, ICON) + 4.0F;
        x = drawDivider(x, cy);
        Fonts.drawString(Fonts.SF_MEDIUM, text, x + 4.0F,
                cy - Fonts.SF_MEDIUM.textHeight(size) / 2.0F + 0.35F, size, textColor);
        return x + 4.0F + Fonts.SF_MEDIUM.getWidth(text, size);
    }

    /**
     * Compact label row: muted title on the left, tiny accent icon on the right.
     * No divider, no header pill. Returns the y where card content starts.
     */
    public static float drawLabel(float x, float y, float w, float labelH,
                                  String title, IconUse rightIcon) {
        float padX = 8.0F;
        float size = 7.5F;
        float midY = y + labelH / 2.0F;
        float ty = midY - Fonts.SF_MEDIUM.textHeight(size) / 2.0F + 0.3F;

        float textX = x + padX;
        float titleW = w - padX * 2.0F;
        if (rightIcon != null) titleW -= 15.0F;
        String shownTitle = HudText.ellipsize(title, Math.max(0.0F, titleW), size);
        Fonts.drawString(Fonts.SF_MEDIUM, shownTitle, textX, ty, size, TEXT_PRIMARY);

        if (rightIcon != null) {
            float glyphW = Fonts.iconFittedWidth(rightIcon, size);
            Fonts.drawIconFitted(rightIcon, x + w - padX - glyphW, midY, size,
                    ColorUtil.withAlpha(Theme.getAccentColor(), 235));
        }
        // Волосяная линия под шапкой отделяет её от содержимого.
        drawHLine(x + padX, y + labelH - 0.5F, Math.max(0.0F, w - padX * 2.0F), PANEL_EDGE);
        return y + labelH;
    }

    /**
     * Flat status dot with a thin ring (no soft glow / halo).
     */
    public static void drawStatusDot(float cx, float cy, float radius, int color, float alpha) {
        if (alpha <= 0.02F) return;
        Render2D.drawCircle(cx, cy, radius, ColorUtil.multiplyAlpha(color, alpha));
        Render2D.drawCircleOutline(cx, cy, radius + 1.0F, 0.8F,
                ColorUtil.withAlpha(color, Math.round(70 * alpha)));
    }
}
