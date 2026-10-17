package pub.godweer.hud;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.ChatScreen;
import pub.godweer.IMinecraft;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.KeyboardInputEvent;
import pub.godweer.enjoy.event.list.MouseInputEvent;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.Render2DUtil;
import pub.godweer.auxiliary.render.renders.font.Fonts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders HUD elements, drag-and-drop while chat is open.
 */
public final class HudManager implements IMinecraft {
    private static final HudManager INSTANCE = new HudManager();
    private final List<HudElement> elements = new ArrayList<>();
    private final Map<String, Float> appearAnims = new HashMap<>();
    private HudElement dragged;

    private HudManager() {
        register(new WatermarkHud());
        register(new PlayerHud());
        register(new PotionsHud());
        register(new StaffHud());
        register(new HotkeysHud());
    }

    public static HudManager getInstance() {
        return INSTANCE;
    }

    public void register(HudElement element) {
        elements.add(element);
    }

    public List<HudElement> getElements() {
        return elements;
    }

    public static boolean editMode() {
        return mc.gui != null && mc.gui.screen() instanceof ChatScreen;
    }

    public static double mouseX() {
        return mc.mouseHandler.xpos() / (double) mc.getWindow().getGuiScale();
    }

    public static double mouseY() {
        return mc.mouseHandler.ypos() / (double) mc.getWindow().getGuiScale();
    }

    private HudElement hovered;

    public boolean handleScroll(double vertical) {
        if (!editMode()) return false;
        try {
            boolean tDown = InputConstants.isKeyDown(InputConstants.KEY_T);
            boolean gDown = InputConstants.isKeyDown(InputConstants.KEY_G);
            if (!tDown && !gDown) return false;
        } catch (Exception ignored) { return false; }
        double mx = mouseX();
        double my = mouseY();
        HudElement target = hovered;
        if (target == null) {
            for (int i = elements.size() - 1; i >= 0; i--) {
                HudElement el = elements.get(i);
                if (!el.isEnabled() || !el.shouldRender()) continue;
                if (el.isHovered(mx, my)) { target = el; break; }
            }
        }
        if (target == null) return false;
        target.setScale(target.getScale() + (float) vertical * 0.08F);
        return true;
    }

    @EventTarget
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (!editMode()) return;
        if (event.getAction() != InputConstants.PRESS) return;
        int key = event.getKey();
        if (key != InputConstants.KEY_T && key != InputConstants.KEY_G) return;
        double mx = mouseX();
        double my = mouseY();
        HudElement target = null;
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement el = elements.get(i);
            if (!el.isEnabled() || !el.shouldRender()) continue;
            if (el.isHovered(mx, my)) { target = el; break; }
        }
        if (target == null) return;
        boolean isDecrease = key == InputConstants.KEY_G;
        if (isDecrease) target.setScale(target.getScale() - 0.10F);
        else target.setScale(target.getScale() + 0.10F);
        event.setCancelled(true);
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc == null || mc.getWindow() == null) return;
        boolean edit = editMode();

        if (dragged != null) {
            if (!edit || !dragged.isEnabled()) {
                dragged = null;
            } else {
                float w = mc.getWindow().getGuiScaledWidth();
                float h = mc.getWindow().getGuiScaledHeight();
                float nx = (float) mouseX() - dragged.grabDX;
                float ny = (float) mouseY() - dragged.grabDY;
                float sw = dragged.getScaledWidth();
                float sh = dragged.getScaledHeight();
                dragged.setX(Math.max(0.0F, Math.min(Math.max(0.0F, w - sw), nx)));
                dragged.setY(Math.max(0.0F, Math.min(Math.max(0.0F, h - sh), ny)));
            }
        }

        hovered = null;
        if (edit) {
            double mx = mouseX();
            double my = mouseY();
            for (int i = elements.size() - 1; i >= 0; i--) {
                HudElement el = elements.get(i);
                if (!el.isEnabled() || !el.shouldRender()) continue;
                if (el.isHovered(mx, my)) { hovered = el; break; }
            }
        }

        for (HudElement el : elements) {
            if (!el.isEnabled()) {
                appearAnims.put(el.getId(), 0.0F);
                continue;
            }
            boolean visible = el.shouldRender() || edit;
            float a = appearAnims.getOrDefault(el.getId(), 0.0F);
            a += ((visible ? 1.0F : 0.0F) - a) * 0.22F;
            appearAnims.put(el.getId(), a);
            if (a <= 0.02F) continue;

            var extractor = event.getGuiGraphicsExtractor();
            float hudScale = el.getScale();
            boolean hasHudScale = Math.abs(hudScale - 1.0F) > 0.001F;
            boolean hasAppear = a < 0.999F;
            if (extractor != null && (hasHudScale || hasAppear)) {
                Render2DUtil.flush();
                if (hasHudScale) {
                    extractor.pose().pushMatrix();
                    extractor.pose().translate(el.getX(), el.getY());
                    extractor.pose().scale(hudScale, hudScale);
                    extractor.pose().translate(-el.getX(), -el.getY());
                }
                if (hasAppear) {
                    float cx = el.getX() + el.getWidth() / 2.0F;
                    float cy = el.getY() + el.getHeight() / 2.0F;
                    float s = 0.92F + 0.08F * a;
                    extractor.pose().pushMatrix();
                    extractor.pose().translate(cx, cy + (1.0F - a) * -6.0F);
                    extractor.pose().scale(s, s);
                    extractor.pose().translate(-cx, -cy);
                    el.draw(event);
                    extractor.pose().popMatrix();
                } else {
                    el.draw(event);
                }
                if (hasHudScale) extractor.pose().popMatrix();
            } else {
                el.draw(event);
            }
            if (edit) {
                float ox = el.getX() - 1.0F;
                float oy = el.getY() - 1.0F;
                float ow = el.getScaledWidth() + 2.0F;
                float oh = el.getScaledHeight() + 2.0F;
                Render2D.drawRoundedOutline(ox, oy, ow, oh, 4.0F, 1.0F,
                        ColorUtil.rgba(255, 255, 255, el == dragged ? 220 : 110));
                if (el == hovered) {
                    String hint = String.format("%d%%  T/G", Math.round(el.getScale() * 100.0F));
                    float tw = Fonts.SF_MEDIUM.getWidth(hint, 6.5F);
                    float tx = el.getX() + el.getScaledWidth() / 2.0F - tw / 2.0F;
                    float ty = el.getY() + el.getScaledHeight() + 3.0F;
                    try {
                        float sw = mc.getWindow().getGuiScaledWidth();
                        tx = Math.max(2.0F, Math.min(sw - tw - 4.0F, tx));
                    } catch (Exception ignored) {}
                    Render2D.drawRoundedRect(tx - 3.0F, ty - 1.0F, tw + 6.0F, 9.0F, 4.0F, ColorUtil.rgba(0, 0, 0, 160));
                    Fonts.drawString(Fonts.SF_MEDIUM, hint, tx, ty, 6.5F, ColorUtil.rgba(255, 255, 255, 255));
                }
            }
        }
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        if (!editMode()) {
            dragged = null;
            return;
        }
        double mx = mouseX();
        double my = mouseY();
        if (event.getAction() == InputConstants.PRESS && event.getButton() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int i = elements.size() - 1; i >= 0; i--) {
                HudElement el = elements.get(i);
                if (!el.isEnabled() || !el.shouldRender()) continue;
                if (el.isHovered(mx, my)) {
                    dragged = el;
                    el.grabDX = (float) mx - el.getX();
                    el.grabDY = (float) my - el.getY();
                    event.cancel();
                    return;
                }
            }
        } else if (event.getAction() == InputConstants.RELEASE && event.getButton() == InputConstants.MOUSE_BUTTON_LEFT) {
            dragged = null;
        }
    }
}
