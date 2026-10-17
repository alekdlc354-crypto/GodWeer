package pub.godweer.hud;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import pub.godweer.Client;
import pub.godweer.IMinecraft;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.kitty.module.impl.combat.AuraModule;
import pub.godweer.auxiliary.client.Annstable;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.renders.font.Fonts;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Target HUD — unified with Potions/Staff/Hotkeys: blur dark card, accent HP bar.
 */
public final class PlayerHud extends HudElement implements IMinecraft {

    private static final float PAD = 7.0F;
    private static final float HEAD = 30.0F;
    private static final float RADIUS = 8.0F;
    private static final int BG = ColorUtil.rgba(8, 8, 12, 228);
    private static final int TRACK = ColorUtil.rgba(255, 255, 255, 20);
    private static final int HURT_TINT = 0xFFFF4040;
    private static final int HURT_OVERLAY = ColorUtil.rgba(255, 66, 66, 255);
    private static final float PARTICLE_GRAVITY = 210.0F;
    private static final float[] PARTICLE_SHADES = {0.72F, 0.88F, 1.0F, 1.18F, 1.4F};

    private final List<HitParticle> hitParticles = new ArrayList<>();
    private final Random random = new Random();
    private float blush = 0.0F;
    private LivingEntity lastTarget = null;
    private int lastHurtTime = 0;
    private long lastFrameNanos = 0L;

    private float animatedW = -1.0F;
    private float smoothHp = 20.0F;

    public PlayerHud() {
        super("player", "Player", 10.0F, 60.0F, 162.0F, 48.0F);
    }

    private LivingEntity resolve() {
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof LivingEntity le
                && le.isAlive() && !le.isDeadOrDying() && le != mc.player) {
            return le;
        }
        if (Client.INSTANCE != null && Client.INSTANCE.moduleManager != null) {
            AuraModule aura = Client.INSTANCE.moduleManager.getAuraModule();
            if (aura != null && aura.isEnabled() && aura.getTarget() != null
                    && aura.getTarget().isAlive() && aura.getTarget() != mc.player) {
                return aura.getTarget();
            }
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

    @Override
    public void draw(Render2DEvent event) {
        LivingEntity t = resolve();
        if (t == null) {
            this.width = 0;
            this.height = 0;
            return;
        }

        float maxHp = Math.max(1.0F, t.getMaxHealth());
        float curHp = Math.min(t.getHealth(), maxHp);
        smoothHp = Mth.lerp(0.18F, smoothHp, curHp);
        smoothHp = Math.max(0.0F, Math.min(smoothHp, maxHp));

        String nick = entityName(t);
        String hpNum = String.valueOf((int) Math.ceil(t.getHealth()));

        float nickW = Fonts.SF_MEDIUM.getWidth(nick, 9.0F);
        float hpNumW = Fonts.SF_MEDIUM.getWidth(hpNum, 9.0F);

        float targetW = PAD * 2.0F + HEAD + 8.0F + Math.max(nickW, 90.0F) + 10.0F + hpNumW;
        targetW = Math.max(158.0F, Math.min(220.0F, targetW));
        if (animatedW < 0.0F) animatedW = targetW;
        animatedW += (targetW - animatedW) * 0.24F;

        this.width = animatedW;
        this.height = 46.0F;

        int accent = Theme.getAccentColor();

        Render2D.drawBlur(x, y, width, height, RADIUS, BG, 1.0F);

        float hx = x + PAD;
        float hy = y + (height - HEAD) / 2.0F;

        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? (1.0F / 60.0F) : (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        if (dt < 0.0F) dt = 0.0F;
        if (dt > 0.1F) dt = 0.1F;

        if (t != lastTarget) {
            lastTarget = t;
            lastHurtTime = 0;
            blush = 0.0F;
            hitParticles.clear();
        }
        int hurt = Math.max(0, t.hurtTime);
        if (hurt > lastHurtTime) {
            blush = 1.0F;
            spawnHitParticles(hx, hy);
        }
        lastHurtTime = hurt;
        blush = Math.max(blush - dt * 2.4F, Annstable.factor(t));
        blush = Mth.clamp(blush, 0.0F, 1.0F);

        int headTint = ColorUtil.lerp(0xFFFFFFFF, HURT_TINT, blush);
        if (t instanceof AbstractClientPlayer p) {
            try {
                Render2D.drawHead(p.getSkin().body().texturePath(), hx, hy, HEAD, 7.0F, headTint);
            } catch (Exception ignored) {
                Render2D.drawRoundedRect(hx, hy, HEAD, HEAD, 7.0F, ColorUtil.rgba(255, 255, 255, 14));
            }
        } else {
            Render2D.drawRoundedRect(hx, hy, HEAD, HEAD, 7.0F, ColorUtil.rgba(255, 255, 255, 14));
        }

        if (blush > 0.02F) {
            Render2D.drawRoundedRect(hx, hy, HEAD, HEAD, 7.0F,
                    ColorUtil.withAlpha(HURT_OVERLAY, Math.round(blush * 130.0F)));
        }

        updateAndRenderParticles(dt);

        float tx = hx + HEAD + 8.0F;
        float ty = y + 10.0F;

        String trimmed = Fonts.SF_MEDIUM.trimToWidth(nick, width - (tx - x) - PAD - 8.0F, 9.0F);
        Fonts.drawString(Fonts.SF_MEDIUM, trimmed, tx, ty, 9.0F, ColorUtil.rgba(242, 244, 250, 255));

        float barH = 7.0F;
        float barY = y + height - 14.0F;
        float numX = x + width - PAD - hpNumW;
        float barX = tx;
        float barW = numX - 6.0F - barX;
        if (barW < 20.0F) barW = 60.0F;

        Render2D.drawRoundedRect(barX, barY, barW, barH, barH / 2.0F, TRACK);
        float pct = Mth.clamp(smoothHp / maxHp, 0.0F, 1.0F);
        float fillW = barW * pct;
        if (fillW > 0.5F) {
            Render2D.drawRoundedRect(barX, barY, fillW, barH, barH / 2.0F, Theme.getAccentColor());
        }

        Fonts.drawString(Fonts.SF_MEDIUM, hpNum, numX, barY - 0.5F, 9.0F, ColorUtil.rgba(235, 240, 248, 255));

        try {
            var ex = event.getGuiGraphicsExtractor();
            if (ex != null) {
                List<ItemStack> stacks = new ArrayList<>(6);
                addStack(stacks, t.getMainHandItem());
                addStack(stacks, t.getOffhandItem());
                addStack(stacks, t.getItemBySlot(EquipmentSlot.HEAD));
                addStack(stacks, t.getItemBySlot(EquipmentSlot.CHEST));
                addStack(stacks, t.getItemBySlot(EquipmentSlot.LEGS));
                addStack(stacks, t.getItemBySlot(EquipmentSlot.FEET));
                if (!stacks.isEmpty()) {
                    float iw = 14.0F;
                    float gap = 3.0F;
                    float rowW = stacks.size() * iw + (stacks.size() - 1) * gap;
                    float sx = x + width / 2.0F - rowW / 2.0F;
                    float iy = y - 16.0F;
                    Render2D.drawRoundedRect(sx - 3.0F, iy - 3.0F, rowW + 6.0F, iw + 6.0F, 5.0F, ColorUtil.rgba(35, 38, 52, 235));
                    Render2D.drawRoundedOutline(sx - 3.0F, iy - 3.0F, rowW + 6.0F, iw + 6.0F, 5.0F, 1.0F, ColorUtil.rgba(255, 255, 255, 14));
                    float sc = iw / 16.0F;
                    for (int i = 0; i < stacks.size(); i++) {
                        ex.pose().pushMatrix();
                        ex.pose().translate(sx + i * (iw + gap), iy);
                        ex.pose().scale(sc, sc);
                        ex.item(stacks.get(i), 0, 0);
                        ex.pose().popMatrix();
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static void addStack(List<ItemStack> stacks, ItemStack stack) {
        if (stack != null && !stack.isEmpty()) stacks.add(stack);
    }

    private void spawnHitParticles(float hx, float hy) {
        float cx = hx + HEAD * 0.5F;
        float cy = hy + HEAD * 0.42F;
        int accent = Theme.getAccentColor();
        int count = 10 + random.nextInt(6);
        for (int i = 0; i < count; i++) {
            float ox = (random.nextFloat() - 0.5F) * HEAD * 0.7F;
            float oy = (random.nextFloat() - 0.5F) * HEAD * 0.7F;

            // Fly outward, away from the point they spawned at, so they scatter in all directions.
            double outward = (ox == 0.0F && oy == 0.0F)
                    ? random.nextDouble() * Math.PI * 2.0
                    : Math.atan2(oy, ox);
            double angle = outward + (random.nextDouble() - 0.5) * (Math.PI * 0.55);

            float speed = 30.0F + random.nextFloat() * 72.0F;
            float vx = (float) Math.cos(angle) * speed;
            float vy = (float) Math.sin(angle) * speed;

            float life = 0.30F + random.nextFloat() * 0.40F;
            float radius = 1.6F + random.nextFloat() * 2.2F;
            float shade = PARTICLE_SHADES[random.nextInt(PARTICLE_SHADES.length)];
            int color = shade(accent, shade);
            hitParticles.add(new HitParticle(cx + ox, cy + oy, vx, vy, life, life, radius, color));
        }
    }

    private static int shade(int color, float mul) {
        int r = Math.min(255, Math.round(ColorUtil.red(color) * mul));
        int g = Math.min(255, Math.round(ColorUtil.green(color) * mul));
        int b = Math.min(255, Math.round(ColorUtil.blue(color) * mul));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void updateAndRenderParticles(float dt) {
        for (int i = hitParticles.size() - 1; i >= 0; i--) {
            HitParticle pt = hitParticles.get(i);
            pt.life -= dt;
            if (pt.life <= 0.0F) {
                hitParticles.remove(i);
                continue;
            }
            pt.vy += PARTICLE_GRAVITY * dt;
            pt.x += pt.vx * dt;
            pt.y += pt.vy * dt;
            pt.vx *= 1.0F - Math.min(1.0F, 2.4F * dt);
            pt.vy *= 1.0F - Math.min(1.0F, 0.7F * dt);
            float ratio = Mth.clamp(pt.life / pt.maxLife, 0.0F, 1.0F);
            float r = pt.radius * (0.30F + 0.70F * ratio);
            int alpha = Math.round(255.0F * ratio * ratio);
            Render2D.drawCircle(pt.x, pt.y, r, ColorUtil.withAlpha(pt.color, alpha));
        }
    }

    private static final class HitParticle {
        float x;
        float y;
        float vx;
        float vy;
        float life;
        final float maxLife;
        final float radius;
        final int color;

        HitParticle(float x, float y, float vx, float vy, float life, float maxLife, float radius, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.maxLife = maxLife;
            this.radius = radius;
            this.color = color;
        }
    }
}
