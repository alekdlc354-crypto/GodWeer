package pub.godweer.hud;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import pub.godweer.IMinecraft;
import pub.godweer.event.list.Render2DEvent;
import pub.godweer.module.impl.combat.Aura;
import pub.godweer.module.impl.render.Interface;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.Render2DUtil;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;

import java.util.Locale;

/**
 * Target HUD с двумя раскладками: {@code Default} — карточка с головой, ником,
 * полосой здоровья и золотой полосой поглощения; {@code Wave} — компактная
 * плашка с сердчишками и дистанцией. Стиль переключается в модуле Interface.
 * <p>
 * Цель берётся у {@link Aura}, иначе — с прицела. Когда цель пропадает, плашка
 * доигрывает затухание на последней цели, а цвет темы берётся из кэша: иначе
 * при закрытии он успевает дёрнуться.
 * <p>
 * Время считается по {@link System#nanoTime()}, поэтому анимации не зависят от
 * FPS. Массивы под предметы созданы заранее.
 */
public final class TargetHud extends HudElement implements IMinecraft {

    private static final float PAD = 5.0F;
    private static final float HEAD = 26.0F;
    private static final float HEAD_R = 5.0F;
    private static final float RADIUS = 6.5F;
    private static final float GAP = 7.0F;
    private static final float MIN_W = 128.0F;

    private static final float NAME_SIZE = 8.5F;
    private static final float HP_SIZE = 7.8F;
    private static final float SMALL_SIZE = 6.4F;

    private static final float BAR_H = 4.5F;
    private static final float ABS_H = 3.0F;

    /** Слот предмета: 16 px текстурного слота, рисуем в половину. */
    private static final float ITEM = 8.0F;
    private static final float ITEM_GAP = 1.5F;
    private static final float ITEM_COUNT_SIZE = 5.4F;

    // Wave: компактная плашка.
    private static final float WAVE_W = 110.0F;
    private static final float WAVE_H = 42.0F;
    private static final float WAVE_HEAD = 20.0F;
    private static final float WAVE_NAME = 7.2F;
    private static final float WAVE_META = 6.2F;
    private static final float HEARTS = 10.0F;
    private static final float HEART_H = 7.0F;

    private static final int GOLD = ColorUtil.rgba(255, 202, 74, 255);
    private static final int HP_GOOD = ColorUtil.rgba(120, 226, 128, 255);
    private static final int HP_MID = ColorUtil.rgba(250, 206, 74, 255);
    private static final int HP_BAD = ColorUtil.rgba(238, 78, 78, 255);
    /** Красная вспышка урона, как у ванильной модели: красный по голове, без обводок. */
    private static final int HURT_FLASH = ColorUtil.rgba(214, 46, 46, 255);
    private static final int HEART_SHADOW = ColorUtil.rgba(0, 0, 0, 120);
    private static final int MAX_ITEMS = 6;

    /** Массивы под предметы: создаются один раз, чтобы не мусорить в рендере. */
    private final ItemStack[] armorScratch = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final ItemStack[] displayItems = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final ItemStack[] displaySources = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final int[] displayCounts = new int[MAX_ITEMS];

    private LivingEntity liveTarget;
    private LivingEntity lastTarget;

    private long lastNanos;
    private float appear;
    private float open;
    private float hurtFlash;
    private float widthAnim = -1.0F;
    private float heightAnim = -1.0F;

    private float hpValue;
    private float hpBar;
    private float hpTrail;
    private float absValue;
    private float absBar;
    private float absTrail;

    private float frozenHp = 20.0F;
    private float frozenMaxHp = 20.0F;
    private float frozenAbs = 0.0F;
    private int cachedAccent = GOLD;

    public TargetHud() {
        super("target", "Target", 168.0F, 176.0F, 148.0F, 36.0F);
    }

    // ------------------------------------------------------------------ цель

    private static boolean waveStyle() {
        Interface ui = Interface.INSTANCE;
        return ui != null && ui.targetStyle.is("Wave");
    }

    private LivingEntity resolve() {
        if (mc.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living
                && living.isAlive() && !living.isDeadOrDying() && living != mc.player) {
            return living;
        }
        Aura aura = Aura.INSTANCE;
        if (aura != null && aura.isEnabled()) {
            LivingEntity t = aura.getTarget();
            if (t != null && t.isAlive() && t != mc.player) return t;
        }
        if (HudManager.editMode()) return mc.player;
        return null;
    }

    @Override
    public boolean shouldRender() {
        return enabled && (liveTarget != null || resolve() != null || appear > 0.01F);
    }

    // ------------------------------------------------------------------ кадр

    @Override
    public void draw(Render2DEvent event) {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 1.0F / 60.0F : (now - lastNanos) / 1_000_000_000.0F;
        lastNanos = now;
        dt = Mth.clamp(dt, 0.0F, 0.1F);

        liveTarget = resolve();
        LivingEntity target = liveTarget != null ? liveTarget : lastTarget;
        if (target == null) {
            this.width = 0.0F;
            this.height = 0.0F;
            appear = 0.0F;
            open = 0.0F;
            return;
        }
        if (liveTarget != null) lastTarget = liveTarget;

        float maxHp = Math.max(1.0F, target.getMaxHealth());
        float hp = Mth.clamp(target.getHealth(), 0.0F, maxHp);
        float abs = Math.max(0.0F, target.getAbsorptionAmount());

        if (liveTarget != null) {
            cachedAccent = Theme.getAccentColor();
            frozenHp = hp;
            frozenMaxHp = maxHp;
            frozenAbs = abs;
        } else {
            // Затухание: значения замирают на последнем кадре живой цели.
            hp = frozenHp;
            maxHp = frozenMaxHp;
            abs = frozenAbs;
        }

        animate(dt, liveTarget != null, hp, maxHp, abs, target);

        boolean wave = waveStyle();
        String name = nameOf(target);
        String hpText = String.format(Locale.ROOT, "%.1f", hpValue);
        String absText = absValue > 0.05F
                ? String.format(Locale.ROOT, " (+%.1f)", absValue)
                : "";

        float hpW = Fonts.SF_MEDIUM.getWidth(hpText, wave ? WAVE_META : HP_SIZE)
                + Fonts.SF_MEDIUM.getWidth(absText, SMALL_SIZE);

        float targetW;
        float targetH;
        if (wave) {
            targetW = Math.max(WAVE_W, PAD * 2.0F + WAVE_HEAD + 6.0F
                    + Fonts.SF_MEDIUM.getWidth(name, WAVE_NAME) * 0.55F);
            targetH = WAVE_H;
        } else {
            float rightW = Math.max(MIN_W - PAD * 2.0F - HEAD - GAP,
                    hpW + Fonts.SF_MEDIUM.getWidth(name, NAME_SIZE) * 0.6F);
            targetW = PAD * 2.0F + HEAD + GAP + rightW;
            targetH = PAD * 2.0F + HEAD;
        }

        if (widthAnim < 0.0F) widthAnim = targetW;
        if (heightAnim < 0.0F) heightAnim = targetH;
        widthAnim += (targetW - widthAnim) * (1.0F - (float) Math.exp(-12.0F * dt));
        heightAnim += (targetH - heightAnim) * (1.0F - (float) Math.exp(-12.0F * dt));

        this.width = widthAnim;
        this.height = heightAnim;

        float easedOpen = easeOutBack(open);
        float alpha = appear * Mth.clamp(open * 1.6F, 0.0F, 1.0F);
        if (alpha <= 0.01F) {
            this.width = 0.0F;
            this.height = 0.0F;
            return;
        }

        float cx = x + width / 2.0F;
        float cy = y + height / 2.0F;
        float sx = cx - (width / 2.0F) * easedOpen;
        float sy = cy - (height / 2.0F) * easedOpen;
        float sw = width * easedOpen;
        float sh = height * easedOpen;

        drawItems(event, target, sx + sw - PAD * easedOpen, sy, alpha);

        HudStyle.drawPanel(sx, sy, sw, sh, RADIUS, alpha);

        if (wave) {
            drawWaveBody(target, name, hpText, absText, sx, sy, sw, sh, alpha);
        } else {
            drawDefaultBody(target, name, hpText, absText, sx, sy, sw, sh, alpha);
        }
    }

    // ------------------------------------------------------------------ раскладки

    private void drawDefaultBody(LivingEntity target, String name, String hpText, String absText,
                                 float sx, float sy, float sw, float sh, float alpha) {
        drawHead(target, sx + PAD, sy + (sh - HEAD) / 2.0F, HEAD, alpha);

        float rx = sx + PAD + HEAD + GAP;
        float rightEdge = sx + sw - PAD;
        float avail = Math.max(28.0F, rightEdge - rx);

        float hpW = Fonts.SF_MEDIUM.getWidth(hpText, HP_SIZE)
                + Fonts.SF_MEDIUM.getWidth(absText, SMALL_SIZE);

        // Ник уходит в прозрачность, если не влезает целиком.
        drawFadingText(name, rx, sy + 5.5F, NAME_SIZE, Math.max(20.0F, avail - hpW - 6.0F),
                ColorUtil.multiplyAlpha(HudStyle.TEXT_PRIMARY, alpha));

        Fonts.drawString(Fonts.SF_MEDIUM, hpText, rightEdge - hpW, sy + 6.0F, HP_SIZE,
                ColorUtil.multiplyAlpha(absValue > 0.05F ? GOLD : HudStyle.TEXT_PRIMARY, alpha));
        if (!absText.isEmpty()) {
            Fonts.drawString(Fonts.SF_MEDIUM, absText,
                    rightEdge - Fonts.SF_MEDIUM.getWidth(absText, SMALL_SIZE),
                    sy + 7.0F, SMALL_SIZE, ColorUtil.multiplyAlpha(GOLD, alpha));
        }

        float barW = avail;
        float barY = sy + sh - PAD - ABS_H - 4.5F - BAR_H;
        int fill = healthColor(Mth.clamp(hpBar, 0.0F, 1.0F));

        Render2D.drawRoundedRect(rx, barY, barW, BAR_H, BAR_H / 2.0F,
                ColorUtil.multiplyAlpha(HudStyle.BAR_TRACK, alpha));
        float trailW = barW * Mth.clamp(hpTrail, 0.0F, 1.0F);
        if (trailW > 0.5F) {
            Render2D.drawRoundedRect(rx, barY, trailW, BAR_H, BAR_H / 2.0F,
                    ColorUtil.multiplyAlpha(HudStyle.BAR_GHOST, alpha));
        }
        float fillW = barW * Mth.clamp(hpBar, 0.0F, 1.0F);
        if (fillW > 0.5F) {
            Render2D.drawRoundedRect(rx, barY, fillW, BAR_H, BAR_H / 2.0F,
                    ColorUtil.multiplyAlpha(fill, alpha));
        }

        if (absBar > 0.005F || absTrail > 0.005F) {
            float absY = barY + BAR_H + 1.5F;
            Render2D.drawRoundedRect(rx, absY, barW, ABS_H, ABS_H / 2.0F,
                    ColorUtil.multiplyAlpha(HudStyle.BAR_TRACK, alpha));
            float absTrailW = barW * Mth.clamp(absTrail, 0.0F, 1.0F);
            if (absTrailW > 0.5F) {
                Render2D.drawRoundedRect(rx, absY, absTrailW, ABS_H, ABS_H / 2.0F,
                        ColorUtil.multiplyAlpha(HudStyle.BAR_GHOST, alpha));
            }
            float absW = barW * Mth.clamp(absBar, 0.0F, 1.0F);
            if (absW > 0.5F) {
                Render2D.drawRoundedRect(rx, absY, absW, ABS_H, ABS_H / 2.0F,
                        ColorUtil.multiplyAlpha(GOLD, alpha));
            }
        }
    }

    private void drawWaveBody(LivingEntity target, String name, String hpText, String absText,
                              float sx, float sy, float sw, float sh, float alpha) {
        float hx = sx + PAD;
        float hy = sy + PAD;
        drawHead(target, hx, hy, WAVE_HEAD, alpha);

        float rx = hx + WAVE_HEAD + 6.0F;
        float rightEdge = sx + sw - PAD;
        float avail = Math.max(20.0F, rightEdge - rx);

        drawFadingText(name, rx, hy + 0.5F, WAVE_NAME, avail,
                ColorUtil.multiplyAlpha(HudStyle.TEXT_PRIMARY, alpha));

        String meta = "HP: " + hpText;
        String dist = mc.player == null ? "" : "Dist: " + Math.round(mc.player.distanceTo(target));
        Fonts.drawString(Fonts.SF_MEDIUM, meta, rx, hy + 10.5F, WAVE_META,
                ColorUtil.multiplyAlpha(HudStyle.TEXT_SECONDARY, alpha));
        float metaW = Fonts.SF_MEDIUM.getWidth(meta, WAVE_META);
        if (!dist.isEmpty()) {
            String tail = " | " + dist;
            Fonts.drawString(Fonts.SF_MEDIUM, tail, rx + metaW, hy + 10.5F, WAVE_META,
                    ColorUtil.multiplyAlpha(HudStyle.TEXT_DIM, alpha));
        }
        if (!absText.isEmpty()) {
            Fonts.drawString(Fonts.SF_MEDIUM, absText.trim(), rightEdge
                            - Fonts.SF_MEDIUM.getWidth(absText.trim(), SMALL_SIZE),
                    hy + 10.5F, SMALL_SIZE, ColorUtil.multiplyAlpha(GOLD, alpha));
        }

        drawHearts(sx + PAD, sy + sh - PAD - HEART_H, sw - PAD * 2.0F, alpha);
    }

    /** Десять сердечек: целая часть — полная, дробная — подрезанная по ширине. */
    private void drawHearts(float hx, float hy, float hw, float alpha) {
        float gap = 1.5F;
        float size = Math.max(5.0F, (hw - gap * (HEARTS - 1.0F)) / HEARTS);
        float frac = Mth.clamp(hpBar, 0.0F, 1.0F) * HEARTS;

        int color = healthColor(Mth.clamp(hpBar, 0.0F, 1.0F));
        int track = ColorUtil.multiplyAlpha(HudStyle.BAR_TRACK, alpha);
        int shadow = ColorUtil.multiplyAlpha(HEART_SHADOW, alpha);

        for (int i = 0; i < (int) HEARTS; i++) {
            float hxPos = hx + i * (size + gap);
            heartShape(hxPos, hy + 0.9F, size, HEART_H - 0.9F, shadow);
            heartShape(hxPos, hy, size, HEART_H, track);

            float part = Mth.clamp(frac - i, 0.0F, 1.0F);
            if (part > 0.02F) {
                Render2D.pushScissor(hxPos - 0.5F, hy - 1.0F, (size + 1.0F) * part, HEART_H + 2.0F);
                heartShape(hxPos, hy, size, HEART_H, ColorUtil.multiplyAlpha(color, alpha));
                Render2D.popScissor();
            }
        }
    }

    private static void heartShape(float x, float y, float w, float h, int color) {
        float r = w * 0.27F;
        Render2D.drawCircle(x + w * 0.28F, y + h * 0.32F, r, color);
        Render2D.drawCircle(x + w * 0.72F, y + h * 0.32F, r, color);
        Render2D.drawRoundedRect(x + w * 0.06F, y + h * 0.30F, w * 0.88F, h * 0.46F, w * 0.22F, color);
        Render2D.drawCircle(x + w * 0.5F, y + h * 0.68F, w * 0.24F, color);
    }

    // ------------------------------------------------------------------ анимации

    private void animate(float dt, boolean live, float hp, float maxHp, float abs, LivingEntity target) {
        float targetAppear = live ? 1.0F : 0.0F;
        appear = approach(appear, targetAppear, 10.0F, dt);
        open = approach(open, targetAppear, 9.0F, dt);

        float hpFrac = Mth.clamp(hp / maxHp, 0.0F, 1.0F);
        float absFrac = Mth.clamp(abs / maxHp, 0.0F, 1.0F);

        hpValue = Mth.lerp(1.0F - (float) Math.exp(-9.0F * dt), hpValue, hp);
        hpBar = Mth.lerp(1.0F - (float) Math.exp(-16.0F * dt), hpBar, hpFrac);
        absValue = Mth.lerp(1.0F - (float) Math.exp(-9.0F * dt), absValue, abs);

        // След догоняет вниз медленно, а на лечении подтягивается сразу.
        float trailStep = dt * 0.6F;
        if (hpTrail > hpFrac) {
            hpTrail = Math.max(hpFrac, hpTrail - trailStep);
        } else {
            hpTrail = hpFrac;
        }
        absBar = Mth.lerp(1.0F - (float) Math.exp(-16.0F * dt), absBar, absFrac);
        if (absTrail > absFrac) {
            absTrail = Math.max(absFrac, absTrail - trailStep);
        } else {
            absTrail = absFrac;
        }

        hurtFlash = live ? Mth.clamp(target.hurtTime / 10.0F, 0.0F, 1.0F) : 0.0F;
    }

    private static float approach(float value, float target, float speed, float dt) {
        return Mth.lerp(1.0F - (float) Math.exp(-speed * dt), value, target);
    }

    private static float easeOutBack(float p) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float t = Mth.clamp(p, 0.0F, 1.0F) - 1.0F;
        return 1.0F + c3 * t * t * t + c1 * t * t;
    }

    private static int healthColor(float frac) {
        if (frac > 0.5F) {
            return ColorUtil.lerp(HP_MID, HP_GOOD, (frac - 0.5F) * 2.0F);
        }
        return ColorUtil.lerp(HP_BAD, HP_MID, frac * 2.0F);
    }

    // ------------------------------------------------------------------ голова

    private void drawHead(LivingEntity target, float hx, float hy, float size, float alpha) {
        int tint = ColorUtil.multiplyAlpha(0xFFFFFFFF, alpha);
        boolean drawn = false;

        if (target instanceof AbstractClientPlayer player) {
            try {
                Render2D.drawHead(player.getSkin().body().texturePath(), hx, hy, size, HEAD_R, tint);
                drawn = true;
            } catch (Exception ignored) {
                drawn = false;
            }
        }
        if (!drawn) {
            Render2D.drawRoundedRect(hx, hy, size, size, HEAD_R,
                    ColorUtil.multiplyAlpha(ColorUtil.rgba(255, 255, 255, 20), alpha));
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, "?",
                    hx + size / 2.0F, hy + size / 2.0F - 4.5F, size * 0.46F,
                    ColorUtil.multiplyAlpha(HudStyle.TEXT_SECONDARY, alpha));
        }
        Render2D.drawRoundedOutline(hx, hy, size, size, HEAD_R, 1.0F,
                ColorUtil.multiplyAlpha(HudStyle.PANEL_EDGE, alpha));
        if (hurtFlash > 0.02F) {
            // Ваниль заливает модель красным на hurtTime: тот же линейный спад.
            Render2D.drawRoundedRect(hx, hy, size, size, HEAD_R,
                    ColorUtil.multiplyAlpha(HURT_FLASH, hurtFlash * 0.55F * alpha));
        }
    }

    // ------------------------------------------------------------------ предметы

    /**
     * Броня, главная и вторая рука — в половину слота, над правым краем плашки.
     * Одинаковые предметы идут одной иконкой со счётчиком: два тотема в руках не
     * должны занимать две позиции. Зачарования, износ и имена с иконок снимаются —
     * на половине слота блеск и полоска прочности читаются как грязь.
     */
    private void drawItems(Render2DEvent event, LivingEntity target, float rightEdge, float top, float alpha) {
        var extractor = event.getGuiGraphicsExtractor();
        if (extractor == null || alpha <= 0.05F) return;

        armorScratch[0] = stackOrEmpty(target, EquipmentSlot.HEAD);
        armorScratch[1] = stackOrEmpty(target, EquipmentSlot.CHEST);
        armorScratch[2] = stackOrEmpty(target, EquipmentSlot.LEGS);
        armorScratch[3] = stackOrEmpty(target, EquipmentSlot.FEET);

        int count = 0;
        for (int i = 0; i < MAX_ITEMS; i++) {
            displaySources[i] = ItemStack.EMPTY;
            displayItems[i] = ItemStack.EMPTY;
            displayCounts[i] = 0;
        }

        for (int i = 0; i < 6; i++) {
            ItemStack raw = i < 4 ? armorScratch[i]
                    : i == 4 ? safeMainHand(target) : safeOffHand(target);
            if (raw == null || raw.isEmpty()) continue;

            int slot = -1;
            for (int j = 0; j < count; j++) {
                if (ItemStack.isSameItemSameComponents(displaySources[j], raw)) {
                    slot = j;
                    break;
                }
            }
            if (slot >= 0) {
                displayCounts[slot]++;
                continue;
            }
            if (count >= MAX_ITEMS) continue;
            displaySources[count] = raw;
            displayItems[count] = cleanCopy(raw);
            displayCounts[count] = 1;
            count++;
        }

        if (count > 0) {
            float totalW = count * ITEM + (count - 1) * ITEM_GAP;
            float ix = rightEdge - totalW;
            float iy = top - ITEM - 3.0F;

            Render2DUtil.flush();
            for (int i = 0; i < count; i++) {
                ItemStack stack = displayItems[i];
                if (stack.isEmpty()) continue;
                float iconX = ix + i * (ITEM + ITEM_GAP);
                var pose = extractor.pose();
                pose.pushMatrix();
                try {
                    pose.translate(iconX, iy);
                    pose.scale(ITEM / 16.0F, ITEM / 16.0F);
                    extractor.item(stack, 0, 0);
                } catch (Exception ignored) {
                } finally {
                    pose.popMatrix();
                }
                if (displayCounts[i] > 1) {
                    String label = String.valueOf(displayCounts[i]);
                    Fonts.drawString(Fonts.SF_MEDIUM, label,
                            iconX + ITEM - Fonts.SF_MEDIUM.getWidth(label, ITEM_COUNT_SIZE),
                            iy + ITEM - Fonts.SF_MEDIUM.textHeight(ITEM_COUNT_SIZE) + 1.0F,
                            ITEM_COUNT_SIZE, ColorUtil.multiplyAlpha(HudStyle.TEXT_PRIMARY, alpha));
                }
            }
            Render2DUtil.flush();
        }

        for (int i = 0; i < MAX_ITEMS; i++) {
            displaySources[i] = ItemStack.EMPTY;
            displayItems[i] = ItemStack.EMPTY;
            displayCounts[i] = 0;
        }
    }

    /** Копия предмета без зачарований, износа и количества — чистая иконка. */
    private static ItemStack cleanCopy(ItemStack source) {
        try {
            ItemStack clean = source.copy();
            clean.setCount(1);
            clean.remove(DataComponents.ENCHANTMENTS);
            clean.remove(DataComponents.STORED_ENCHANTMENTS);
            clean.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            clean.remove(DataComponents.DAMAGE);
            return clean;
        } catch (Exception ignored) {
            return source;
        }
    }

    private static ItemStack stackOrEmpty(LivingEntity target, EquipmentSlot slot) {
        try {
            ItemStack stack = target.getItemBySlot(slot);
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack safeMainHand(LivingEntity target) {
        try {
            ItemStack stack = target.getMainHandItem();
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack safeOffHand(LivingEntity target) {
        try {
            ItemStack stack = target.getOffhandItem();
            return stack == null ? ItemStack.EMPTY : stack;
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------ текст

    /**
     * Ник с уходом в прозрачность: строка рисуется посимвольно, и последняя
     * часть доступной ширины гаснет. Так длинный ник не обрывается многоточием.
     */
    private static void drawFadingText(String text, float x, float y, float size, float maxWidth, int color) {
        if (text == null || text.isEmpty()) return;
        float width = Fonts.SF_MEDIUM.getWidth(text, size);
        if (width <= maxWidth) {
            Fonts.drawString(Fonts.SF_MEDIUM, text, x, y, size, color);
            return;
        }

        float fadeStart = maxWidth * 0.62F;
        float pen = 0.0F;
        for (int i = 0; i < text.length(); i++) {
            String ch = text.substring(i, i + 1);
            float chW = Fonts.SF_MEDIUM.getWidth(ch, size);
            if (pen + chW > maxWidth) break;
            float k = 1.0F;
            if (pen > fadeStart) {
                k = 1.0F - (pen - fadeStart) / Math.max(1.0F, maxWidth - fadeStart);
            }
            int a = Math.round(ColorUtil.alpha(color) * Mth.clamp(k, 0.0F, 1.0F));
            Fonts.drawString(Fonts.SF_MEDIUM, ch, x + pen, y, size, ColorUtil.withAlpha(color, a));
            pen += chW;
        }
    }

    private static String nameOf(LivingEntity target) {
        if (target instanceof AbstractClientPlayer player) return player.getGameProfile().name();
        return target.getName().getString();
    }
}
