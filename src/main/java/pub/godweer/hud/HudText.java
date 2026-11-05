package pub.godweer.hud;

import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.font.Fonts;

/**
 * Текстовые утилиты HUD: снятие §кодов, цветная строка в нашем шрифте и
 * обрезка по ширине. Раньше это жило в HudText вместе с табло-оверлеем,
 * tab-list и сайдбаром; оверлей удалён, остались только строки.
 */
public final class HudText {

    private HudText() {
    }

    // § color codes

    public static String stripCodes(String text) {
        if (text == null) return "";
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static int codeColor(char code, int defColor) {
        return switch (code) {
            case '0' -> ColorUtil.rgba(0, 0, 0, 255);
            case '1' -> ColorUtil.rgba(0, 0, 170, 255);
            case '2' -> ColorUtil.rgba(0, 170, 0, 255);
            case '3' -> ColorUtil.rgba(0, 170, 170, 255);
            case '4' -> ColorUtil.rgba(170, 0, 0, 255);
            case '5' -> ColorUtil.rgba(170, 0, 170, 255);
            case '6' -> ColorUtil.rgba(255, 170, 0, 255);
            case '7' -> ColorUtil.rgba(170, 170, 170, 255);
            case '8' -> ColorUtil.rgba(85, 85, 85, 255);
            case '9' -> ColorUtil.rgba(85, 85, 255, 255);
            case 'a' -> ColorUtil.rgba(85, 255, 85, 255);
            case 'b' -> ColorUtil.rgba(85, 255, 255, 255);
            case 'c' -> ColorUtil.rgba(255, 85, 85, 255);
            case 'd' -> ColorUtil.rgba(255, 85, 255, 255);
            case 'e' -> ColorUtil.rgba(255, 255, 85, 255);
            case 'f' -> ColorUtil.rgba(255, 255, 255, 255);
            case 'r' -> defColor;
            default -> -1;
        };
    }

    public static float coloredWidth(String text, float size) {
        return Fonts.SF_MEDIUM.getWidth(stripCodes(text), size);
    }

    public static void drawColoredString(String text, float x, float y, float size, int defColor) {
        if (text == null || text.isEmpty()) return;
        int col = defColor;
        StringBuilder cur = new StringBuilder();
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                if (cur.length() > 0) {
                    Fonts.drawString(Fonts.SF_MEDIUM, cur.toString(), cx, y, size, col);
                    cx += Fonts.SF_MEDIUM.getWidth(cur.toString(), size);
                    cur.setLength(0);
                }
                char code = Character.toLowerCase(text.charAt(++i));
                int cc = codeColor(code, defColor);
                if (cc != -1) col = cc;
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            Fonts.drawString(Fonts.SF_MEDIUM, cur.toString(), cx, y, size, col);
        }
    }

    public static void drawColoredCentered(String text, float centerX, float y, float size, int defColor) {
        drawColoredString(text, centerX - coloredWidth(text, size) / 2.0F, y, size, defColor);
    }

    public static String ellipsize(String text, float availWidth, float size) {
        if (text == null) return "";
        if (Fonts.SF_MEDIUM.getWidth(text, size) <= availWidth + 0.5F) return text;
        float dotsW = Fonts.SF_MEDIUM.getWidth("...", size);
        if (availWidth <= dotsW + 4.0F) return "...";
        return Fonts.SF_MEDIUM.trimToWidth(text, availWidth - dotsW, size) + "...";
    }
}
