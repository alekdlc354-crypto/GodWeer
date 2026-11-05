package pub.godweer.hud;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import pub.godweer.IMinecraft;
import pub.godweer.event.list.Render2DEvent;
import pub.godweer.module.impl.combat.Aura;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.Render2DUtil;
import pub.godweer.util.render.font.Fonts;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.style.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Target HUD: карточка цели с головой, ником, полосой здоровья с догоняющим
 * следом урона, золотой полосой поглощения и строкой предметов над плашкой.
 * <p>
 * Цель берётся у {@link Aura}, иначе — с прицела. Когда цель пропадает, плашка
 * доигрывает затухание на последней цели, а цвет темы берётся из кэша: иначе
 * при закрытии он успевает дёрнуться.
 * <p>
 * Всё время считается по {@link System#nanoTime()}, поэтому анимации и физика
 * частиц не зависят от FPS. Предметы собираются в заранее созданные массивы —
 * ни одного нового списка за кадр.
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

    private static final int GOLD = ColorUtil.rgba(255, 202, 74, 255);
    private static final int HP_GOOD = ColorUtil.rgba(120, 226, 128, 255);
    private static final int HP_MID = ColorUtil.rgba(250, 206, 74, 255);
    private static final int HP_BAD = ColorUtil.rgba(238, 78, 78, 255);
    private static final int HURT_TINT = 0xFFFF6B6B;
    private static final int HURT_OVERLAY = ColorUtil.rgba(255, 74, 74, 255);

    private static final int MAX_PARTICLES = 14;

    private static final class Spark {
        float x;
        float y;
        float vx;
        float vy;
        float size;
        float life;
        float maxLife;
    }

    /** Массивы под предметы: создаются один раз, чтобы не мусорить в рендере. */
    private final ItemStack[] armorScratch = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final ItemStack[] displayItems = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
    private final List<Spark> sparks = new ArrayList<>(MAX_PARTICLES);

    private LivingEntity liveTarget;
    private LivingEntity lastTarget;
    private int lastHurtTime;

    private long lastNanos;
    private float appear;
    private float open;
    private float hurtPulse;
    private float widthAnim = -1.0F;

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
        super("target", "Target", 168.0F, 176.0F, 150.0F, 36.0F);
    }

    // ------------------------------------------------------------------ цель

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
        if (liveTarget != null && liveTarget != lastTarget) lastHurtTime = 0;
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

        float easedOpen = easeOutBack(open);
        float alpha = appear * Mth.clamp(open * 1.6F, 0.0F, 1.0F);
        if (alpha <= 0.01F) {
            this.width = 0.0F;
            this.height = 0.0F;
            return;
        }

        // ---------------------------------------------------------- размеры
        String name = nameOf(target);
        String hpText = String.format(Locale.ROOT, "%.1f", hpValue);
        String absText = absValue > 0.05F
                ? String.format(Locale.ROOT, " (+%.1f)", absValue)
                : "";
        float hpW = Fonts.SF_MEDIUM.getWidth(hpText, HP_SIZE)
                + Fonts.SF_MEDIUM.getWidth(absText, SMALL_SIZE);
        float rightW = Math.max(MIN_W - PAD * 2.0F - HEAD - GAP, hpW + Fonts.SF_MEDIUM.getWidth(name, NAME_SIZE) * 0.6F);
        float targetW = PAD * 2.0F + HEAD + GAP + rightW;

        if (widthAnim < 0.0F) widthAnim = targetW;
        widthAnim += (targetW - widthAnim) * (1.0F - (float) Math.exp(-12.0F * dt));

        this.width = widthAnim;
        this.height = PAD * 2.0F + HEAD;

        // ---------------------------------------------------------- геометрия
        float cx = x + width / 2.0F;
        float cy = y + height / 2.0F;
        float sx = cx - (width / 2.0F) * easedOpen;
        float sy = cy - (height / 2.0F) * easedOpen;
        float sw = width * easedOpen;
        float sh = height * easedOpen;

        drawItems(event, target, sx + sw - PAD * easedOpen, sy, alpha);

        HudStyle.drawPanel(sx, sy, sw, sh, RADIUS, alpha);
        if (hurtPulse > 0.02F) {
            Render2D.drawRoundedOutline(sx - 0.5F, sy - 0.5F, sw + 1.0F, sh + 1.0F, RADIUS + 0.5F, 1.0F,
                    ColorUtil.withAlpha(HURT_OVERLAY, Math.round(150.0F * hurtPulse * alpha)));
        }

        drawHead(target, sx + PAD, sy + (sh - HEAD) / 2.0F, alpha);

        float rx = sx + PAD + HEAD + GAP;
        float rightEdge = sx + sw - PAD;
        float avail = Math.max(28.0F, rightEdge - rx);

        // Ник с плавным уходом в прозрачность на длинном тексте.
        float nameRoom = Math.max(20.0F, avail - hpW - 6.0F);
        drawFadingText(name, rx, sy + 5.5F, NAME_SIZE, nameRoom,
                ColorUtil.multiplyAlpha(HudStyle.TEXT_PRIMARY, alpha));

        Fonts.drawString(Fonts.SF_MEDIUM, hpText, rightEdge - hpW, sy + 6.0F, HP_SIZE,
                ColorUtil.multiplyAlpha(absValue > 0.05F ? GOLD : HudStyle.TEXT_PRIMARY, alpha));
        if (!absText.isEmpty()) {
            Fonts.drawString(Fonts.SF_MEDIUM, absText,
                    rightEdge - Fonts.SF_MEDIUM.getWidth(absText, SMALL_SIZE),
                    sy + 7.0F, SMALL_SIZE, ColorUtil.multiplyAlpha(GOLD, alpha));
        }

        // ---------------------------------------------------------- полосы
        float barW = avail;
        float barY = sy + sh - PAD - ABS_H - 4.5F - BAR_H;

        int fill = healthColor(smoothFrac(hpBar));
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

        drawSparks(dt, sx + PAD + HEAD * 0.5F, sy + PAD + HEAD * 0.5F, alpha);
    }

    // ------------------------------------------------------------------ анимации

    private void animate(float dt, boolean live, float hp, float maxHp, float abs, LivingEntity target) {
        float targetAppear = live ? 1.0F : 0.0F;
        appear = approach(appear, targetAppear, 10.0F, dt);
        open = approach(open, targetAppear, 9.0F, dt);

        float hpFrac = Mth.clamp(hp / maxHp, 0.0F, 1.0F);
        float absFrac = Mth.clamp(abs / maxHp, 0.0F, 1.0F - hpFrac);

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

        int hurt = Math.max(0, target.hurtTime);
        hpPulse(dt, live, hurt);
    }

    /** hurtTime растёт на кадр удара: именно по приросту и пускаем искры. */
    private void hpPulse(float dt, boolean live, int hurt) {
        hurtPulse = Mth.clamp(hurtPulse - dt * 2.4F, 0.0F, 1.0F);
        if (live && hurt > lastHurtTime) {
            hurtPulse = 1.0F;
            spawnSparks(3);
        }
        lastHurtTime = hurt;
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

    private static float smoothFrac(float value) {
        return Mth.clamp(value, 0.0F, 1.0F);
    }

    private static int healthColor(float frac) {
        if (frac > 0.5F) {
            return ColorUtil.lerp(HP_MID, HP_GOOD, (frac - 0.5F) * 2.0F);
        }
        return ColorUtil.lerp(HP_BAD, HP_MID, frac * 2.0F);
    }

    // ------------------------------------------------------------------ части

    private void drawHead(LivingEntity target, float hx, float hy, float alpha) {
        int tint = ColorUtil.lerp(0xFFFFFFFF, HURT_TINT, hurtPulse);
        tint = ColorUtil.multiplyAlpha(tint, alpha);
        boolean drawn = false;

        if (target instanceof AbstractClientPlayer player) {
            try {
                Render2D.drawHead(player.getSkin().body().texturePath(), hx, hy, HEAD, HEAD_R, tint);
                drawn = true;
            } catch (Exception ignored) {
                drawn = false;
            }
        }
        if (!drawn) {
            Render2D.drawRoundedRect(hx, hy, HEAD, HEAD, HEAD_R,
                    ColorUtil.multiplyAlpha(ColorUtil.rgba(255, 255, 255, 20), alpha));
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, "?",
                    hx + HEAD / 2.0F, hy + HEAD / 2.0F - 4.5F, 12.0F,
                    ColorUtil.multiplyAlpha(HudStyle.TEXT_SECONDARY, alpha));
        }
        Render2D.drawRoundedOutline(hx, hy, HEAD, HEAD, HEAD_R, 1.0F,
                ColorUtil.multiplyAlpha(HudStyle.PANEL_EDGE, alpha));
        if (hurtPulse > 0.02F) {
            Render2D.drawRoundedRect(hx, hy, HEAD, HEAD, HEAD_R,
                    ColorUtil.multiplyAlpha(HURT_OVERLAY, Math.round(hurtPulse * 105.0F * alpha)));
        }
    }

    /** Броня, главная и вторая рука — в половину слота, над правым краем плашки. */
    private void drawItems(Render2DEvent event, LivingEntity target, float rightEdge, float top, float alpha) {
        var extractor = event.getGuiGraphicsExtractor();
        if (extractor == null || alpha <= 0.05F) return;

        armorScratch[0] = stackOrEmpty(target, EquipmentSlot.HEAD);
        armorScratch[1] = stackOrEmpty(target, EquipmentSlot.CHEST);
        armorScratch[2] = stackOrEmpty(target, EquipmentSlot.LEGS);
        armorScratch[3] = stackOrEmpty(target, EquipmentSlot.FEET);

        int count = 0;
        for (ItemStack stack : armorScratch) {
            if (!stack.isEmpty()) displayItems[count++] = stack;
        }
        ItemStack main = safeMainHand(target);
        ItemStack off = safeOffHand(target);
        if (!main.isEmpty()) displayItems[count++] = main;
        if (!off.isEmpty()) displayItems[count++] = off;

        if (count > 0) {
            float totalW = count * ITEM + (count - 1) * ITEM_GAP;
            float ix = rightEdge - totalW;
            float iy = top - ITEM - 3.0F;

            Render2DUtil.flush();
            for (int i = 0; i < count; i++) {
                ItemStack stack = displayItems[i];
                if (stack.isEmpty()) continue;
                try {
                    var pose = extractor.pose();
                    pose.pushMatrix();
                    pose.translate(ix + i * (ITEM + ITEM_GAP), iy);
                    pose.scale(ITEM / 16.0F, ITEM / 16.0F);
                    extractor.item(stack, 0, 0);
                    pose.popMatrix();
                } catch (Exception ignored) {
                }
                displayItems[i] = ItemStack.EMPTY;
            }
            Render2DUtil.flush();
        }

        for (int i = 0; i < displayItems.length; i++) displayItems[i] = ItemStack.EMPTY;
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

    // ------------------------------------------------------------------ искры

    private void spawnSparks(int amount) {
        for (int i = 0; i < amount && sparks.size() < MAX_PARTICLES; i++) {
            Spark spark = new Spark();
            spark.x = 0.0F;
            spark.y = 0.0F;
            spark.vx = (float) (Math.random() - 0.5) * 34.0F;
            spark.vy = (float) (Math.random() - 0.5) * 26.0F - 10.0F;
            spark.size = 1.1F + (float) Math.random() * 1.5F;
            spark.maxLife = 0.45F + (float) Math.random() * 0.35F;
            spark.life = spark.maxLife;
            sparks.add(spark);
        }
    }

    private void drawSparks(float dt, float cx, float cy, float alpha) {
        if (sparks.isEmpty()) return;

        for (int i = sparks.size() - 1; i >= 0; i--) {
            Spark spark = sparks.get(i);
            spark.life -= dt;
            if (spark.life <= 0.0F) {
                sparks.remove(i);
                continue;
            }
            spark.vy += 26.0F * dt;
            spark.vx *= 1.0F - 1.2F * dt;
            spark.vy *= 1.0F - 1.2F * dt;
            spark.x += spark.vx * dt;
            spark.y += spark.vy * dt;

            float life = spark.life / spark.maxLife;
            float fade = life * life * (3.0F - 2.0F * life);
            float size = spark.size * (0.5F + 0.5F * fade);
            Render2D.drawCircle(cx + spark.x, cy + spark.y, size,
                    ColorUtil.multiplyAlpha(cachedAccent, fade * alpha * 0.85F));
        }
    }

    // ------------------------------------------------------------------ текст

    /**
     * Ник с уходом в прозрачность: строка рисуется посимвольно, и последняя
     * четверть доступной ширины гаснет. Так длинный ник не обрывается многоточием.
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
            int alpha = Math.round(ColorUtil.alpha(color) * Mth.clamp(k, 0.0F, 1.0F));
            Fonts.drawString(Fonts.SF_MEDIUM, ch, x + pen, y, size, ColorUtil.withAlpha(color, alpha));
            pen += chW;
        }
    }

    private static String nameOf(LivingEntity target) {
        if (target instanceof AbstractClientPlayer player) return player.getGameProfile().name();
        return target.getName().getString();
    }

}
