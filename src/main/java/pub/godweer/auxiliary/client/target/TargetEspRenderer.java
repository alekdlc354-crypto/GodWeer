package pub.godweer.auxiliary.client.target;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3fc;
import pub.godweer.auxiliary.client.Annstable;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.kitty.module.impl.render.TargetEsp;

import java.util.ArrayList;
import java.util.List;

/**
 * Target ESP.
 * Marker: rotating 2D sprite in the GUI pass, centered on the target.
 * Souls: strong additive bloom souls with trails orbiting around the target,
 * rotating around its axis (Rockstar Ghosts2 style).
 * Both flash red while the target is taking damage (Target HUD rules).
 */
public final class TargetEspRenderer {

    public static final Identifier MARKER_TEXTURE = Identifier.fromNamespaceAndPath("godweer", "images/world/esp/marker.png");

    private static final float MARKER_SCALE = 4.0F;

    // damage flash — same colour and timing as Target HUD
    private static final int HURT_TINT = 0xFFFF4040;

    // souls orbit: two crossed axes around the target — 2 souls on each,
    // the first axis leans 45° to the player's right, the second 45° to the left
    private static final int SOUL_RING_COUNT = 2;
    private static final int SOULS_PER_RING = 2;
    private static final int SOUL_COUNT = SOUL_RING_COUNT * SOULS_PER_RING;
    private static final float SOUL_RADIUS = 0.65F;
    private static final double SOUL_AXIS_TILT = Math.toRadians(45.0D);
    private static final float SOUL_HEAD_SIZE = 0.20F;
    private static final int MAX_TRAIL = 12;
    private static final long TRAIL_INTERVAL_MS = 30L;
    private static final float SOUL_BRIGHTNESS = 1.5F;

    private final ParticlesWorldRenderer particles = new ParticlesWorldRenderer();

    // shared hurt-flash state (Target HUD style)
    private LivingEntity lastHurtTarget;
    private int lastHurtTime;
    private float blush;
    private long lastFrameNanos;

    // soul orbit state
    private final Soul[] souls = new Soul[SOUL_COUNT];
    private long lastGhostUpdateMs;
    private long lastTrailMs;

    // scratch buffers — reused every frame, no per-frame allocation
    private final List<ParticlesWorldRenderer.Sprite> spriteScratch =
            new ArrayList<>(SOUL_COUNT * (MAX_TRAIL + 1));
    private final double[] basisA = new double[SOUL_RING_COUNT * 3];
    private final double[] basisB = new double[SOUL_RING_COUNT * 3];
    private final double[] axisTmp = new double[3];

    public void render(Render2DEvent event, TargetEsp module) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || event.getGuiGraphicsExtractor() == null) return;

        LivingEntity target = module.resolveTarget();
        if (target == null) return;

        float tickDelta = event.getDeltaTracker() != null
                ? event.getDeltaTracker().getGameTimeDeltaPartialTick(false) : 1.0F;

        if (module.usesMarker()) {
            renderMarker(event, mc, target, tickDelta, hurtBlend(module.getAccentColor(), target));
        }
    }

    private void renderMarker(Render2DEvent event, Minecraft mc, LivingEntity target, float tickDelta,
                              int color) {
        double height = Math.max(0.4D, target.getBbHeight());
        Vec3 center = Render3DUtil.interpolatedPosition(target, tickDelta).add(0.0D, height * 0.5D, 0.0D);
        Render3DUtil.ScreenPoint sp = Render3DUtil.projectToScreen(mc, center);
        if (sp == null || mc.gameRenderer == null) return;

        float dist = (float) Math.max(0.1D, mc.gameRenderer.mainCamera().position().distanceTo(center));
        float fade = Mth.clamp(1.0F - dist / 70.0F, 0.2F, 1.0F);
        int tint = ColorUtil.withAlpha(color, Math.round(ColorUtil.alpha(color) * fade));

        float time = (float) (System.currentTimeMillis() % 40000L) / 1000.0F;
        float size = 16.0F * MARKER_SCALE * Mth.clamp(24.0F / (dist + 10.0F), 0.6F, 1.4F);
        size *= 1.0F + 0.04F * Mth.sin(time * 2.0F);
        if (size <= 1.0F) return;

        // Spins a full circle from time to time, then turns back.
        float rotation = Mth.sin(time * 0.5F) * 360.0F;

        Matrix3x2fStack pose = event.getGuiGraphicsExtractor().pose();
        pose.pushMatrix();
        pose.translate(sp.x(), sp.y());
        pose.rotate((float) Math.toRadians(rotation));
        Render2D.drawTexture(MARKER_TEXTURE, -size / 2.0F, -size / 2.0F, size, size, 0.0F, tint);
        pose.popMatrix();
    }

    /**
     * World-pass entry: bloom souls with trails orbiting around the target.
     */
    public void renderSouls3D(TargetEsp module, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) return;

        LivingEntity target = module.resolveTarget();
        if (!valid(target)) return;

        renderSouls3D(mc, target, tickDelta, module.getAccentColor(), module.soulSpeed.getValue());
    }

    public void renderSouls3D(Minecraft mc, LivingEntity target, float tickDelta,
                              int color, float speed) {
        if (!valid(target) || mc.gameRenderer == null) return;
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return;

        int accent = hurtBlend(color, target);
        ensureSouls();

        long now = System.currentTimeMillis();
        long deltaMs = lastGhostUpdateMs == 0L ? 16L : Math.min(100L, now - lastGhostUpdateMs);
        lastGhostUpdateMs = now;
        float rotation = deltaMs * 0.003F * Math.max(0.0F, speed);

        Vec3 feet = Render3DUtil.interpolatedPosition(target, tickDelta);
        double height = Math.max(0.4D, target.getBbHeight());
        double cx = feet.x;
        double cy = feet.y + height * 0.5D;
        double cz = feet.z;

        // Two crossed orbital axes around the target. The first leans 45° to the
        // player's right, the second 45° to the left; each axis carries 2 souls
        // sitting on opposite sides of its ring.
        buildBases(camera);

        boolean sampleTrail = now - lastTrailMs >= TRAIL_INTERVAL_MS;
        if (sampleTrail) {
            lastTrailMs = now;
        }

        spriteScratch.clear();
        for (int s = 0; s < souls.length; s++) {
            Soul soul = souls[s];
            soul.angle += rotation;
            double a = soul.angle;
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            int b = soul.ring * 3;
            soul.x = cx + (basisA[b] * ca + basisB[b] * sa) * SOUL_RADIUS;
            soul.y = cy + (basisA[b + 1] * ca + basisB[b + 1] * sa) * SOUL_RADIUS;
            soul.z = cz + (basisA[b + 2] * ca + basisB[b + 2] * sa) * SOUL_RADIUS;

            if (sampleTrail) {
                soul.pushTrail(soul.x, soul.y, soul.z);
            }

            int trailSize = soul.trailSize;
            if (trailSize > 0) {
                int head = soul.trailHead;
                for (int i = trailSize - 1; i >= 0; i--) {
                    int idx = (head + i) % MAX_TRAIL;
                    float fade = 1.0F - i / (float) trailSize;
                    float alpha = 255.0F * fade * fade * 0.60F;
                    float half = SOUL_HEAD_SIZE * (0.25F + 0.55F * fade);
                    int t = idx * 3;
                    spriteScratch.add(new ParticlesWorldRenderer.Sprite(
                            new Vec3(soul.trail[t], soul.trail[t + 1], soul.trail[t + 2]),
                            half, ColorUtil.withAlpha(accent, Math.round(alpha))));
                }
            }
            spriteScratch.add(new ParticlesWorldRenderer.Sprite(
                    new Vec3(soul.x, soul.y, soul.z), SOUL_HEAD_SIZE,
                    ColorUtil.withAlpha(accent, 255)));
        }

        if (!spriteScratch.isEmpty()) {
            particles.render(spriteScratch, SOUL_BRIGHTNESS, true, true);
        }
    }

    /**
     * Fills the orbital bases for both crossed axes, allocation-free.
     * Axis 0 leans 45° to the player's right, axis 1 leans 45° to the left.
     */
    private void buildBases(Camera camera) {
        Vector3fc left = camera.leftVector();
        double rx = -left.x();
        double ry = -left.y();
        double rz = -left.z();

        double cosTilt = Math.cos(SOUL_AXIS_TILT);
        double sinTilt = Math.sin(SOUL_AXIS_TILT);

        for (int r = 0; r < SOUL_RING_COUNT; r++) {
            double side = (r == 0) ? 1.0D : -1.0D;

            // axis = up * cosTilt + right * sinTilt * side
            double ax = rx * sinTilt * side;
            double ay = cosTilt + ry * sinTilt * side;
            double az = rz * sinTilt * side;
            double len = Math.sqrt(ax * ax + ay * ay + az * az);
            if (len < 1.0E-6D) len = 1.0D;
            ax /= len;
            ay /= len;
            az /= len;

            // basisA = axis x up  = (az, 0, -ax)
            double pax = az;
            double pay = 0.0D;
            double paz = -ax;
            double l2 = Math.sqrt(pax * pax + paz * paz);
            if (l2 < 1.0E-6D) l2 = 1.0D;
            pax /= l2;
            paz /= l2;

            // basisB = axis x basisA
            int b = r * 3;
            basisA[b] = pax;
            basisA[b + 1] = pay;
            basisA[b + 2] = paz;

            double qx = ay * paz - az * pay;
            double qy = az * pax - ax * paz;
            double qz = ax * pay - ay * pax;
            double l3 = Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (l3 < 1.0E-6D) l3 = 1.0D;
            basisB[b] = qx / l3;
            basisB[b + 1] = qy / l3;
            basisB[b + 2] = qz / l3;
        }
    }

    private void ensureSouls() {
        if (souls[0] != null) {
            return;
        }
        int i = 0;
        for (int r = 0; r < SOUL_RING_COUNT; r++) {
            for (int k = 0; k < SOULS_PER_RING; k++) {
                souls[i++] = new Soul(r, k * Math.PI);
            }
        }
    }

    // ---- damage flash (Target HUD rules) ----

    private float updateBlush(LivingEntity target) {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? (1.0F / 60.0F) : (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        dt = Mth.clamp(dt, 0.0F, 0.1F);

        if (target != lastHurtTarget) {
            lastHurtTarget = target;
            lastHurtTime = 0;
            blush = 0.0F;
        }
        int hurt = Math.max(0, target.hurtTime);
        if (hurt > lastHurtTime) {
            blush = 1.0F;
        }
        lastHurtTime = hurt;
        blush = Math.max(blush - dt * 2.4F, Annstable.factor(target));
        blush = Mth.clamp(blush, 0.0F, 1.0F);
        return blush;
    }

    private int hurtBlend(int color, LivingEntity target) {
        float b = updateBlush(target);
        return b > 0.02F ? ColorUtil.lerp(color, HURT_TINT, b) : color;
    }

    public void release() {
        java.util.Arrays.fill(souls, null);
        spriteScratch.clear();
        lastGhostUpdateMs = 0L;
        lastTrailMs = 0L;
        lastHurtTarget = null;
        lastHurtTime = 0;
        blush = 0.0F;
        lastFrameNanos = 0L;
    }

    private static boolean valid(LivingEntity entity) {
        return entity != null && entity.isAlive() && !entity.isRemoved();
    }

    private static final class Soul {
        final int ring;
        double angle;
        double x;
        double y;
        double z;

        // fixed-size ring buffer, index 0 of `trailHead` is the newest sample
        final float[] trail = new float[MAX_TRAIL * 3];
        int trailSize;
        int trailHead;

        Soul(int ring, double angle) {
            this.ring = ring;
            this.angle = angle;
        }

        void pushTrail(double x, double y, double z) {
            if (trailSize > 0) {
                float hx = trail[trailHead * 3];
                float hy = trail[trailHead * 3 + 1];
                float hz = trail[trailHead * 3 + 2];
                float dx = (float) x - hx;
                float dy = (float) y - hy;
                float dz = (float) z - hz;
                if (dx * dx + dy * dy + dz * dz > 100.0F) {
                    trailSize = 0;
                }
            }
            trailHead = (trailHead + MAX_TRAIL - 1) % MAX_TRAIL;
            int t = trailHead * 3;
            trail[t] = (float) x;
            trail[t + 1] = (float) y;
            trail[t + 2] = (float) z;
            if (trailSize < MAX_TRAIL) {
                trailSize++;
            }
        }
    }
}