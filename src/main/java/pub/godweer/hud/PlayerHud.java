package pub.godweer.hud;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import pub.godweer.IMinecraft;
import pub.godweer.event.list.Render2DEvent;
import pub.godweer.module.impl.combat.Aura;
import pub.godweer.util.misc.Annstable;
import pub.godweer.util.style.ColorUtil;
import pub.godweer.util.render.Render2D;
import pub.godweer.util.render.font.Fonts;

import java.util.Locale;

/**
 * Target HUD: карточка цели в стиле остального интерфейса.
 * <p>
 * Слева — голова (или буква для мобов), справа — ник, справа в строку с ником
 * текущее здоровье, ниже — строка метаданных (броня, дистанция, пинг цели) и
 * две полоски: здоровье с призрачным следом урона и бронёй, плюс полоска
 * поглощения поверх здоровья, если на цели есть абсорбция.
 * Цель берётся из {@link Aura} или с прицела на модели.
 */
public final class PlayerHud extends HudElement implements IMinecraft {

    private static final float PAD = 7.0F;
    private static final float HEAD = 30.0F;
    private static final float HEAD_R = 6.0F;
    private static final float RADIUS = 8.0F;
    private static final float GAP = 7.0F;

    private static final float NAME_SIZE = 9.5F;
    private static final float HP_SIZE = 8.5F;
    private static final float META_SIZE = 6.6F;

    private static final float HP_BAR_H = 5.0F;
    private static final float ARMOR_BAR_H = 3.0F;
    private static final float MIN_RIGHT_W = 82.0F;

    private static final int HURT_TINT = 0xFFFF6B6B;
    private static final int HURT_OVERLAY = ColorUtil.rgba(255, 74, 74, 255);

    private LivingEntity lastTarget;
    private int lastHurtTime;
    private long lastFrameNanos;

    private float appear;
    private float hurtPulse;
    private float animatedW = -1.0F;
    private float smoothHp = 20.0F;
    private float ghostHp = 20.0F;
    private float smoothAbs;

    public PlayerHud() {
        super("player", "Player", 275.0F, 175.0F, 158.0F, 44.0F);
    }

    private LivingEntity resolve() {
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le
                && le.isAlive() && !le.isDeadOrDying() && le != mc.player) {
            return le;
        }
        Aura aura = Aura.INSTANCE;
        if (aura != null && aura.isEnabled() && aura.getTarget() != null
                && aura.getTarget().isAlive() && aura.getTarget() != mc.player) {
            return aura.getTarget();
        }
        if (HudManager.editMode()) return mc.player;
        return null;
    }

    @Override
    public boolean shouldRender() {
        return enabled && resolve() != null;
    }

    private static String entityName(LivingEntity t) {
        if (t instanceof AbstractClientPlayer p) return p.getGameProfile().name();
        return t.getName().getString();
    }

    private static int pingOf(LivingEntity t) {
        if (!(t instanceof AbstractClientPlayer p) || mc.getConnection() == null) return -1;
        PlayerInfo info = mc.getConnection().getPlayerInfo(p.getUUID());
        return info == null ? -1 : Math.max(0, info.getLatency());
    }

    private static String trim(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Override
    public void draw(Render2DEvent event) {
        LivingEntity t = resolve();
        if (t == null) {
            this.width = 0.0F;
            this.height = 0.0F;
            return;
        }

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? (1.0F / 60.0F) : (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        dt = Mth.clamp(dt, 0.0F, 0.1F);

        float maxHp = Math.max(1.0F, t.getMaxHealth());
        float curHp = Mth.clamp(t.getHealth(), 0.0F, maxHp);
        float curAbs = Math.max(0.0F, t.getAbsorptionAmount());

        if (t != lastTarget) {
            lastTarget = t;
            lastHurtTime = 0;
            hurtPulse = 0.0F;
            appear = 0.0F;
            smoothHp = curHp;
            ghostHp = curHp;
            smoothAbs = curAbs;
        }

        appear = Mth.lerp(1.0F - (float) Math.pow(0.001F, dt), appear, 1.0F);
        smoothHp = Mth.lerp(1.0F - (float) Math.pow(0.0009F, dt), smoothHp, curHp);
        smoothAbs = Mth.lerp(1.0F - (float) Math.pow(0.0009F, dt), smoothAbs, curAbs);

        // Призрак уползает вниз медленнее самой полоски — видно, сколько сняли.
        if (ghostHp > smoothHp) {
            ghostHp = Math.max(smoothHp, ghostHp - dt * maxHp * 0.55F);
        } else {
            ghostHp = smoothHp;
        }

        int hurt = Math.max(0, t.hurtTime);
        if (hurt > lastHurtTime) hurtPulse = 1.0F;
        lastHurtTime = hurt;
        hurtPulse = Math.max(hurtPulse - dt * 2.2F, Annstable.factor(t));
        hurtPulse = Mth.clamp(hurtPulse, 0.0F, 1.0F);

        String nick = entityName(t);
        String hpText = trim(curHp);
        if (curAbs > 0.05F) hpText = hpText + " +" + trim(curAbs);

        int ping = pingOf(t);
        float dist = mc.player == null ? 0.0F : mc.player.distanceTo(t);
        int armor = Math.max(0, t.getArmorValue());

        StringBuilder meta = new StringBuilder();
        if (armor > 0) meta.append("ARM ").append(armor);
        if (dist > 0.05F) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(String.format(Locale.ROOT, "%.1fm", dist));
        }
        if (ping >= 0) {
            if (meta.length() > 0) meta.append("  ·  ");
            meta.append(ping).append("ms");
        }

        float nameW = Fonts.SF_MEDIUM.getWidth(nick, NAME_SIZE);
        float hpW = Fonts.SF_MEDIUM.getWidth(hpText, HP_SIZE);
        float metaW = Fonts.SF_MEDIUM.getWidth(meta.toString(), META_SIZE);

        float rightW = Math.max(MIN_RIGHT_W, Math.max(nameW + 12.0F + hpW, metaW));
        float targetW = PAD * 2.0F + HEAD + GAP + rightW;

        if (animatedW < 0.0F) animatedW = targetW;
        animatedW += (targetW - animatedW) * 0.22F;

        this.width = animatedW;
        this.height = PAD * 2.0F + HEAD;

        // Появление: карточка чуть раздувается из 0.94 и подъезжает снизу.
        float rise = (1.0F - appear) * 5.0F;
        float shake = hurtPulse > 0.35F ? (float) Math.sin(now / 22_000_000.0D) * hurtPulse * 1.6F : 0.0F;
        float cx = x + shake;
        float cy = y + rise;

        HudStyle.drawPanel(cx, cy, width, height, RADIUS);
        if (hurtPulse > 0.02F) {
            Render2D.drawRoundedOutline(cx - 0.5F, cy - 0.5F, width + 1.0F, height + 1.0F, RADIUS + 0.5F, 1.0F,
                    ColorUtil.withAlpha(HURT_OVERLAY, Math.round(150 * hurtPulse)));
        }

        // ------------------------------------------------------------ голова
        float headX = cx + PAD;
        float headY = cy + (height - HEAD) / 2.0F;
        int headTint = ColorUtil.lerp(0xFFFFFFFF, HURT_TINT, hurtPulse);
        boolean drawn = false;
        if (t instanceof AbstractClientPlayer p) {
            try {
                Render2D.drawHead(p.getSkin().body().texturePath(), headX, headY, HEAD, HEAD_R, headTint);
                drawn = true;
            } catch (Exception ignored) {
                drawn = false;
            }
        }
        if (!drawn) {
            Render2D.drawRoundedRect(headX, headY, HEAD, HEAD, HEAD_R,
                    ColorUtil.rgba(255, 255, 255, 18));
            String letter = nick.isEmpty() ? "?" : nick.substring(0, 1).toUpperCase(Locale.ROOT);
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, letter, headX + HEAD / 2.0F,
                    headY + HEAD / 2.0F - 4.0F, 13.0F, HudStyle.TEXT_PRIMARY);
        }
        Render2D.drawRoundedOutline(headX, headY, HEAD, HEAD, HEAD_R, 1.0F, HudStyle.PANEL_EDGE);
        if (hurtPulse > 0.02F) {
            Render2D.drawRoundedRect(headX, headY, HEAD, HEAD, HEAD_R,
                    ColorUtil.withAlpha(HURT_OVERLAY, Math.round(hurtPulse * 110.0F)));
        }

        // ------------------------------------------------------------ тексты
        float rx = headX + HEAD + GAP;
        float rightEdge = cx + width - PAD;
        float avail = Math.max(30.0F, rightEdge - rx);

        Fonts.drawString(Fonts.SF_MEDIUM,
                HudText.ellipsize(nick, Math.max(24.0F, avail - hpW - 8.0F), NAME_SIZE),
                rx, cy + 6.0F, NAME_SIZE, HudStyle.TEXT_PRIMARY);
        Fonts.drawString(Fonts.SF_MEDIUM, hpText, rightEdge - hpW, cy + 7.0F, HP_SIZE,
                curAbs > 0.05F ? HudStyle.HP_ABSORB : HudStyle.HP_FILL);

        if (meta.length() > 0) {
            Fonts.drawString(Fonts.SF_MEDIUM, HudText.ellipsize(meta.toString(), avail, META_SIZE),
                    rx, cy + 17.5F, META_SIZE, HudStyle.TEXT_SECONDARY);
        }

        // ------------------------------------------------------------ полоски
        float hpFrac = Mth.clamp(smoothHp / maxHp, 0.0F, 1.0F);
        float ghostFrac = Mth.clamp(ghostHp / maxHp, 0.0F, 1.0F);
        float absFrac = Mth.clamp(smoothAbs / maxHp, 0.0F, 1.0F - hpFrac);

        // Полоски внизу: здоровье, под ним броня. Призрак урона — в той же полоске.
        float barY = cy + height - 18.0F;
        HudStyle.drawBarWithGhost(rx, barY, avail, HP_BAR_H, hpFrac, ghostFrac, HudStyle.HP_FILL);
        if (absFrac > 0.005F) {
            float absW = avail * absFrac;
            Render2D.drawRoundedRect(rx + avail * hpFrac, barY, absW, HP_BAR_H,
                    HP_BAR_H / 2.0F, HudStyle.HP_ABSORB);
        }

        if (armor > 0) {
            float armorFrac = Mth.clamp(armor / 20.0F, 0.0F, 1.0F);
            HudStyle.drawBar(rx, barY + HP_BAR_H + 1.5F, avail, ARMOR_BAR_H, armorFrac, HudStyle.BAR_ARMOR);
        }
    }
}
