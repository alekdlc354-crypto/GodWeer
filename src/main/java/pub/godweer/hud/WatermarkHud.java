package pub.godweer.hud;

import pub.godweer.IMinecraft;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.auxiliary.render.renders.font.IconUse;

/**
 * Watermark — unified with Potions/Staff/Hotkeys: blur dark card, accent header square.
 * Layout: [M] head  GROUP nick  ...  FPS
 */
public final class WatermarkHud extends HudElement implements IMinecraft {

    private static final float PAD = 6.0F;
    private static final float GAP = 6.0F;
    private static final float FONT = 8.5F;
    private static final float ICON = 8.0F;
    private static final float HEAD = 14.0F;
    private static final float RADIUS = 8.0F;
    private static final int BG = ColorUtil.rgba(8, 8, 12, 228);

    private float animatedW = -1.0F;

    public WatermarkHud() {
        super("watermark", "Watermark", 10.0F, 10.0F, 165.0F, 24.0F);
    }

    @Override
    public void draw(Render2DEvent event) {
        int accent = Theme.getAccentColor();
        int white = ColorUtil.rgba(240, 242, 248, 255);
        int gray = ColorUtil.rgba(150, 155, 170, 255);

        String nick = mc.player != null ? mc.player.getGameProfile().name() : "Amnith";
        String fpsText = (mc.player != null ? mc.getFps() : 127) + " FPS";
        String dots = "...";

        float box = 12.0F;
        float mW = Fonts.SF_MEDIUM.getWidth("M", 8.0F);
        float nickW = Fonts.SF_MEDIUM.getWidth(nick, FONT);
        float fpsW = Fonts.SF_MEDIUM.getWidth(fpsText, FONT);
        float dotsW = Fonts.SF_MEDIUM.getWidth(dots, FONT);

        float contentW = box + 5.0F + HEAD + GAP + (ICON + 3.0F + nickW) + GAP + (dotsW + 4.0F + fpsW);
        float target = contentW + PAD * 2.0F;
        if (animatedW < 0.0F) animatedW = target;
        animatedW += (target - animatedW) * 0.22F;
        this.width = Math.max(130.0F, Math.max(target, animatedW));
        this.height = 24.0F;

        Render2D.drawBlur(x, y, width, height, RADIUS, BG, 1.0F);

        float h = height;
        float cy = y + h / 2.0F;
        float cx = x + PAD;

        // M in accent square (like Potions/Staff header)
        Render2D.drawRoundedRect(cx, cy - box / 2.0F, box, box, 4.0F, ColorUtil.withAlpha(accent, 45));
        Fonts.drawString(Fonts.SF_MEDIUM, "M", cx + (box - mW) / 2.0F, cy - 4.2F, 8.0F, ColorUtil.withAlpha(accent, 255));
        cx += box + 5.0F;

        float hx = cx;
        float hy = cy - HEAD / 2.0F;
        if (mc.player != null) {
            try {
                Render2D.drawHead(mc.player.getSkin().body().texturePath(), hx, hy, HEAD, 3.5F);
            } catch (Exception ignored) {
                Render2D.drawRoundedRect(hx, hy, HEAD, HEAD, 3.5F, ColorUtil.rgba(255, 255, 255, 14));
            }
        } else {
            Render2D.drawRoundedRect(hx, hy, HEAD, HEAD, 3.5F, ColorUtil.rgba(255, 255, 255, 14));
        }
        cx += HEAD + GAP;

        Fonts.drawIcon(IconUse.GROUP, cx, cy - ICON / 2.0F - 0.5F, ICON, ColorUtil.withAlpha(accent, 255));
        cx += ICON + 3.0F;
        Fonts.drawString(Fonts.SF_MEDIUM, nick, cx, cy - FONT / 2.0F - 0.6F, FONT, white);
        cx += nickW + GAP;

        Fonts.drawString(Fonts.SF_MEDIUM, dots, cx, cy - FONT / 2.0F - 0.6F, FONT, gray);
        cx += dotsW + 4.0F;
        Fonts.drawString(Fonts.SF_MEDIUM, fpsText, cx, cy - FONT / 2.0F - 0.6F, FONT, white);
    }
}

