package pub.godweer.auxiliary.render.world.module;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import pub.godweer.kitty.module.impl.render.PopEffect;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Beautiful Pop Effect Renderer - Incredible visual effects for hits, kills, and totem pops
 * Created with love for stunning aesthetics
 */
public final class PopEffectRenderer {

    private final ParticlesWorldRenderer particleRenderer = new ParticlesWorldRenderer();
    private final List<EpicParticle> particles = new ArrayList<>();
    private final List<ShockWave> shockWaves = new ArrayList<>();
    private final List<EnergyBeam> energyBeams = new ArrayList<>();
    private final List<SpiralRing> spiralRings = new ArrayList<>();
    private final List<PopChamsInstance> chams = new ArrayList<>();
    private final List<LightningStrike> lightnings = new ArrayList<>();

    private long lastFrameTime = System.currentTimeMillis();

    public static class PopChamsInstance {
        public final EntityRenderState renderState;
        public Vec3 pos;
        public final Vec3 velocity;
        public final long startTime;
        public final long durationMs = 850;
        public final int color;

        public PopChamsInstance(EntityRenderState renderState, Vec3 startPos, Vec3 velocity, int color) {
            this.renderState = renderState;
            this.pos = startPos;
            this.velocity = velocity;
            this.startTime = System.currentTimeMillis();
            this.color = color;
        }

        public float getProgress() {
            return Math.min(1.0F, (System.currentTimeMillis() - this.startTime) / (float) this.durationMs);
        }

        public boolean isDead() {
            return getProgress() >= 1.0F;
        }

        public void update(float dt) {
            this.pos = this.pos.add(this.velocity.scale(dt * 20.0));
        }
    }

    public static class EpicParticle {
        Vec3 pos;
        Vec3 velocity;
        Entity target;
        long startTime;
        long durationMs;
        float baseSize;
        int color;
        int secondaryColor;
        boolean hasTrail;
        boolean spiral;
        double spiralAngle;
        double spiralRadius;
        double spiralSpeed;
        float rotation;
        float rotationSpeed;

        public EpicParticle(Vec3 pos, Vec3 velocity, Entity target, long durationMs, float baseSize, int color, int secondaryColor) {
            this.pos = pos;
            this.velocity = velocity;
            this.target = target;
            this.startTime = System.currentTimeMillis();
            this.durationMs = durationMs;
            this.baseSize = baseSize;
            this.color = color;
            this.secondaryColor = secondaryColor;
            this.hasTrail = false;
            this.spiral = false;
            this.rotation = ThreadLocalRandom.current().nextFloat() * 360.0F;
            this.rotationSpeed = (ThreadLocalRandom.current().nextFloat() - 0.5F) * 720.0F;
        }

        public float getProgress() {
            return Math.min(1.0F, (System.currentTimeMillis() - this.startTime) / (float) this.durationMs);
        }

        public boolean isDead() {
            return getProgress() >= 1.0F;
        }

        public void update(float dt, float speedScale) {
            Minecraft mc = Minecraft.getInstance();
            float progress = getProgress();

            this.rotation += this.rotationSpeed * dt;

            if (this.spiral) {
                this.spiralAngle += this.spiralSpeed * dt * speedScale;
                double radius = this.spiralRadius * (1.0 - progress * 0.7);
                this.pos = this.pos.add(
                        Math.cos(this.spiralAngle) * radius * dt * 20.0,
                        this.velocity.y * dt * 20.0 * speedScale,
                        Math.sin(this.spiralAngle) * radius * dt * 20.0
                );
                this.velocity = this.velocity.scale(Math.pow(0.95, dt * 20.0));
            } else if (this.target != null && this.target.isAlive()) {
                Vec3 targetCenter = this.target.position().add(0, this.target.getBbHeight() * 0.5, 0);
                if (progress > 0.3F) {
                    Vec3 toTarget = targetCenter.subtract(this.pos);
                    if (toTarget.length() > 0.1) {
                        Vec3 desiredVel = toTarget.normalize().scale((5.0 + progress * 10.0) * speedScale);
                        this.velocity = this.velocity.lerp(desiredVel, Math.min(1.0, dt * 8.0 * speedScale));
                    }
                } else {
                    this.velocity = this.velocity.scale(Math.pow(0.4, dt));
                }
                this.pos = this.pos.add(this.velocity.scale(dt * 20.0 * speedScale));
            } else {
                this.velocity = this.velocity.scale(Math.pow(0.6, dt)).add(0, -0.8 * dt, 0);
                this.pos = this.pos.add(this.velocity.scale(dt * 20.0 * speedScale));
            }
        }
    }

    public static class ShockWave {
        Vec3 center;
        long startTime;
        long durationMs;
        float maxRadius;
        int color;
        float thickness;

        public ShockWave(Vec3 center, long durationMs, float maxRadius, int color, float thickness) {
            this.center = center;
            this.startTime = System.currentTimeMillis();
            this.durationMs = durationMs;
            this.maxRadius = maxRadius;
            this.color = color;
            this.thickness = thickness;
        }

        public float getProgress() {
            return Math.min(1.0F, (System.currentTimeMillis() - this.startTime) / (float) this.durationMs);
        }

        public boolean isDead() {
            return getProgress() >= 1.0F;
        }
    }

    public static class EnergyBeam {
        Vec3 startPos;
        Vec3 endPos;
        long startTime;
        long durationMs;
        int color;
        float width;
        int segments;

        public EnergyBeam(Vec3 startPos, Vec3 endPos, long durationMs, int color, float width, int segments) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.startTime = System.currentTimeMillis();
            this.durationMs = durationMs;
            this.color = color;
            this.width = width;
            this.segments = segments;
        }

        public float getProgress() {
            return Math.min(1.0F, (System.currentTimeMillis() - this.startTime) / (float) this.durationMs);
        }

        public boolean isDead() {
            return getProgress() >= 1.0F;
        }
    }

    public static class SpiralRing {
        Vec3 center;
        long startTime;
        long durationMs;
        float radius;
        float height;
        int color;
        int particleCount;
        double rotationSpeed;
        boolean expanding;

        public SpiralRing(Vec3 center, long durationMs, float radius, float height, int color, int particleCount, double rotationSpeed, boolean expanding) {
            this.center = center;
            this.startTime = System.currentTimeMillis();
            this.durationMs = durationMs;
            this.radius = radius;
            this.height = height;
            this.color = color;
            this.particleCount = particleCount;
            this.rotationSpeed = rotationSpeed;
            this.expanding = expanding;
        }

        public float getProgress() {
            return Math.min(1.0F, (System.currentTimeMillis() - this.startTime) / (float) this.durationMs);
        }

        public boolean isDead() {
            return getProgress() >= 1.0F;
        }
    }

    public static class LightningStrike {
        List<Vec3> segments = new ArrayList<>();
        List<Vec3> branchSegments = new ArrayList<>();
        long startTime;
        long durationMs = 500;
        int color;
        int secondaryColor;

        public LightningStrike(Vec3 targetPos, int color, int secondaryColor) {
            this.startTime = System.currentTimeMillis();
            this.color = color;
            this.secondaryColor = secondaryColor;

            Vec3 top = targetPos.add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 4.0,
                    35.0,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 4.0
            );

            int count = 35;
            Vec3 current = top;
            this.segments.add(current);

            for (int i = 1; i <= count; i++) {
                float frac = (float) i / count;
                Vec3 ideal = top.lerp(targetPos, frac);
                if (i < count) {
                    double spread = (1.0F - frac) * 1.8 + 0.4;
                    ideal = ideal.add(
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * spread,
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5,
                            (ThreadLocalRandom.current().nextDouble() - 0.5) * spread
                    );

                    if (i == 12 || i == 24) {
                        Vec3 branchEnd = ideal.add(
                                (ThreadLocalRandom.current().nextDouble() - 0.5) * 5.0,
                                -4.0 - ThreadLocalRandom.current().nextDouble(4.0),
                                (ThreadLocalRandom.current().nextDouble() - 0.5) * 5.0
                        );
                        this.branchSegments.add(ideal);
                        this.branchSegments.add(ideal.lerp(branchEnd, 0.5).add(0.3, 0.3, -0.3));
                        this.branchSegments.add(branchEnd);
                    }
                }
                this.segments.add(ideal);
            }
        }

        public float getProgress() {
            return Math.min(1.0F, (System.currentTimeMillis() - this.startTime) / (float) this.durationMs);
        }

        public boolean isDead() {
            return getProgress() >= 1.0F;
        }
    }

    public synchronized void spawnHitEffect(Vec3 targetPos, PopEffect module) {
        int count = (int) module.hitCount.getValue().floatValue();
        float size = module.hitSize.getValue();
        long dur = (long) (module.hitLifetime.getValue() * 1000.0F);
        
        int primaryColor = module.getHitColor();
        int secondaryColor = ColorUtil.rgba(255, 255, 255, 255);
        
        String mode = module.hitEffect.getValue();
        
        if ("Explosion".equalsIgnoreCase(mode) || "Взрыв".equalsIgnoreCase(mode)) {
            // Epic explosion with multiple layers
            for (int i = 0; i < count; i++) {
                Vec3 vel = randomSphereVector().scale(0.6 + ThreadLocalRandom.current().nextDouble(0.8));
                EpicParticle p = new EpicParticle(
                        targetPos.add(randomOffset(0.2)),
                        vel,
                        null,
                        dur,
                        size * (0.7F + ThreadLocalRandom.current().nextFloat() * 0.6F),
                        ColorUtil.lerp(primaryColor, secondaryColor, ThreadLocalRandom.current().nextFloat() * 0.3F),
                        secondaryColor
                );
                p.spiral = ThreadLocalRandom.current().nextFloat() > 0.7F;
                if (p.spiral) {
                    p.spiralAngle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
                    p.spiralRadius = 0.3 + ThreadLocalRandom.current().nextDouble() * 0.5;
                    p.spiralSpeed = 3.0 + ThreadLocalRandom.current().nextDouble() * 4.0;
                }
                particles.add(p);
            }
            
            // Add shockwave
            shockWaves.add(new ShockWave(targetPos, dur / 2, 3.0F, primaryColor, 0.15F));
            
        } else if ("Lightning".equalsIgnoreCase(mode) || "Молния".equalsIgnoreCase(mode)) {
            // Multiple lightning strikes
            for (int i = 0; i < 3; i++) {
                Vec3 offset = randomOffset(1.5);
                lightnings.add(new LightningStrike(targetPos.add(offset), primaryColor, secondaryColor));
            }
            
            // Sparks
            for (int i = 0; i < count / 2; i++) {
                Vec3 vel = randomSphereVector().scale(0.3 + ThreadLocalRandom.current().nextDouble(0.5));
                particles.add(new EpicParticle(
                        targetPos.add(randomOffset(0.3)),
                        vel,
                        null,
                        dur / 2,
                        size * 0.5F,
                        secondaryColor,
                        primaryColor
                ));
            }
        }
    }

    public synchronized void spawnKillEffect(Vec3 pos, PopEffect module) {
        int count = (int) module.killCount.getValue().floatValue();
        float size = module.killSize.getValue();
        long dur = (long) (module.killLifetime.getValue() * 1000.0F);
        
        int primaryColor = module.getKillColor();
        int secondaryColor = ColorUtil.rgba(255, 255, 255, 255);
        int goldColor = ColorUtil.rgba(255, 215, 0, 255);
        
        String mode = module.killEffect.getValue();
        
        if ("Beam".equalsIgnoreCase(mode) || "Луч".equalsIgnoreCase(mode)) {
            // Epic energy beams shooting up
            for (int i = 0; i < 5; i++) {
                double angle = (i / 5.0) * Math.PI * 2.0;
                Vec3 beamEnd = pos.add(Math.cos(angle) * 2.0, 20.0, Math.sin(angle) * 2.0);
                energyBeams.add(new EnergyBeam(pos, beamEnd, dur, primaryColor, 0.3F, 20));
            }
            
            // Central beam
            energyBeams.add(new EnergyBeam(pos, pos.add(0, 30.0, 0), dur * 3 / 2, goldColor, 0.5F, 30));
            
            // Particles spiraling up
            for (int i = 0; i < count; i++) {
                EpicParticle p = new EpicParticle(
                        pos.add(randomOffset(0.5)),
                        new Vec3(0, 0.5 + ThreadLocalRandom.current().nextDouble() * 0.5, 0),
                        null,
                        dur,
                        size,
                        ColorUtil.lerp(primaryColor, goldColor, ThreadLocalRandom.current().nextFloat()),
                        secondaryColor
                );
                p.spiral = true;
                p.spiralAngle = (i / (double) count) * Math.PI * 2.0;
                p.spiralRadius = 1.0 + ThreadLocalRandom.current().nextDouble() * 1.5;
                p.spiralSpeed = 4.0 + ThreadLocalRandom.current().nextDouble() * 3.0;
                particles.add(p);
            }
            
            // Multiple shockwaves
            shockWaves.add(new ShockWave(pos, dur / 3, 4.0F, primaryColor, 0.2F));
            shockWaves.add(new ShockWave(pos, dur / 2, 6.0F, goldColor, 0.15F));
            
        } else if ("Explosion".equalsIgnoreCase(mode) || "Взрыв".equalsIgnoreCase(mode)) {
            // Massive explosion
            for (int i = 0; i < count * 2; i++) {
                Vec3 vel = randomSphereVector().scale(0.8 + ThreadLocalRandom.current().nextDouble(1.2));
                EpicParticle p = new EpicParticle(
                        pos.add(randomOffset(0.4)),
                        vel,
                        null,
                        dur,
                        size * (0.8F + ThreadLocalRandom.current().nextFloat() * 0.8F),
                        ColorUtil.lerp(primaryColor, goldColor, ThreadLocalRandom.current().nextFloat() * 0.5F),
                        secondaryColor
                );
                p.hasTrail = true;
                particles.add(p);
            }
            
            // Multiple shockwaves
            for (int i = 0; i < 3; i++) {
                shockWaves.add(new ShockWave(pos, dur / 2 + i * 200, 5.0F + i * 2.0F, 
                        i == 0 ? goldColor : primaryColor, 0.2F - i * 0.05F));
            }
        }
    }

    public synchronized void spawnTotemPop(Entity target, PopEffect module) {
        Minecraft mc = Minecraft.getInstance();
        int count = (int) module.totemCount.getValue().floatValue();
        float size = module.totemSize.getValue();
        long dur = (long) (module.totemLifetime.getValue() * 1000.0F);
        
        Vec3 center = target.position().add(0, target.getBbHeight() * 0.5, 0);
        
        int gold = ColorUtil.rgba(255, 220, 35, 255);
        int green = ColorUtil.rgba(40, 245, 120, 255);
        int white = ColorUtil.rgba(255, 255, 255, 255);
        
        String mode = module.totemEffect.getValue();
        
        if ("Spiral".equalsIgnoreCase(mode) || "Спираль".equalsIgnoreCase(mode)) {
            // Beautiful spiral rings
            for (int ring = 0; ring < 3; ring++) {
                spiralRings.add(new SpiralRing(
                        center.add(0, ring * 2.0, 0),
                        dur,
                        1.5F + ring * 0.5F,
                        0.5F,
                        ring % 2 == 0 ? gold : green,
                        20 + ring * 10,
                        5.0 + ring * 2.0,
                        true
                ));
            }
            
            // Particles following spirals
            for (int i = 0; i < count; i++) {
                EpicParticle p = new EpicParticle(
                        center.add(randomOffset(0.3)),
                        new Vec3(0, 0.3 + ThreadLocalRandom.current().nextDouble() * 0.3, 0),
                        target,
                        dur,
                        size * 0.8F,
                        i % 2 == 0 ? gold : green,
                        white
                );
                p.spiral = true;
                p.spiralAngle = (i / (double) count) * Math.PI * 2.0;
                p.spiralRadius = 1.5 + ThreadLocalRandom.current().nextDouble() * 1.0;
                p.spiralSpeed = 5.0 + ThreadLocalRandom.current().nextDouble() * 3.0;
                particles.add(p);
            }
            
        } else if ("Beam".equalsIgnoreCase(mode) || "Луч".equalsIgnoreCase(mode)) {
            // Golden beam of light
            energyBeams.add(new EnergyBeam(center, center.add(0, 40.0, 0), dur, gold, 0.6F, 40));
            
            // Helix particles
            for (int i = 0; i < count; i++) {
                int arm = i % 3;
                double baseAngle = (arm * (Math.PI * 2.0 / 3.0)) + ThreadLocalRandom.current().nextDouble(0.4);
                double height = ThreadLocalRandom.current().nextDouble() * 15.0;
                double radius = 1.2 + (height / 15.0) * 2.0;
                double rotSpeed = 1.0 + ThreadLocalRandom.current().nextDouble(0.5);
                int col = (i % 2 == 0) ? gold : green;

                EpicParticle p = new EpicParticle(
                        center.add(0, height, 0),
                        Vec3.ZERO,
                        target,
                        dur,
                        size,
                        col,
                        white
                );
                p.spiral = true;
                p.spiralAngle = baseAngle;
                p.spiralRadius = radius;
                p.spiralSpeed = rotSpeed;
                particles.add(p);
            }
            
            // Shockwave
            shockWaves.add(new ShockWave(center, dur / 3, 5.0F, gold, 0.25F));
            
        } else if ("Explosion".equalsIgnoreCase(mode) || "Взрыв".equalsIgnoreCase(mode)) {
            // Epic totem explosion
            for (int i = 0; i < count * 2; i++) {
                Vec3 vel = randomSphereVector().scale(0.7 + ThreadLocalRandom.current().nextDouble(1.0));
                int col = ThreadLocalRandom.current().nextBoolean() ? gold : green;
                EpicParticle p = new EpicParticle(
                        center.add(randomOffset(0.5)),
                        vel,
                        target,
                        dur,
                        size,
                        col,
                        white
                );
                p.hasTrail = true;
                particles.add(p);
            }
            
            // Multiple shockwaves
            shockWaves.add(new ShockWave(center, dur / 4, 6.0F, gold, 0.3F));
            shockWaves.add(new ShockWave(center, dur / 2, 8.0F, green, 0.2F));
        }
    }

    public synchronized void submitChams(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (this.chams.isEmpty() || camera == null) return;
        Minecraft mc = Minecraft.getInstance();

        for (PopChamsInstance ghost : this.chams) {
            float progress = ghost.getProgress();
            float scale = 1.0F + progress * 0.3F;

            double x = ghost.pos.x - camera.pos.x();
            double y = ghost.pos.y - camera.pos.y();
            double z = ghost.pos.z - camera.pos.z();

            poseStack.pushPose();
            poseStack.translate(x, y, z);
            poseStack.scale(scale, scale, scale);

            mc.getEntityRenderDispatcher().submit(
                    ghost.renderState,
                    camera,
                    0.0, 0.0, 0.0,
                    poseStack,
                    collector
            );

            poseStack.popPose();
        }
    }

    public synchronized void render(PopEffect module, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (now - this.lastFrameTime) / 1000.0F);
        this.lastFrameTime = now;

        float speedScale = module.speed.getValue();
        float brightness = module.brightness.getValue();
        boolean throughWalls = module.throughWalls.getValue();

        // Update and remove dead effects
        shockWaves.removeIf(ShockWave::isDead);
        energyBeams.removeIf(EnergyBeam::isDead);
        spiralRings.removeIf(SpiralRing::isDead);
        lightnings.removeIf(LightningStrike::isDead);

        chams.removeIf(PopChamsInstance::isDead);
        for (PopChamsInstance c : chams) {
            c.update(dt);
        }

        particles.removeIf(EpicParticle::isDead);
        for (EpicParticle p : particles) {
            p.update(dt, speedScale);
        }

        List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>();

        // Render shockwaves
        for (ShockWave wave : shockWaves) {
            float progress = wave.getProgress();
            float radius = wave.maxRadius * progress;
            float alpha = (1.0F - progress) * (1.0F - progress);
            
            int segments = 40;
            for (int i = 0; i < segments; i++) {
                double angle = (i / (double) segments) * Math.PI * 2.0;
                Vec3 pos = wave.center.add(
                        Math.cos(angle) * radius,
                        0,
                        Math.sin(angle) * radius
                );
                sprites.add(new ParticlesWorldRenderer.Sprite(pos, wave.thickness * (1.0F - progress * 0.5F), 
                        ColorUtil.multiplyAlpha(wave.color, alpha)));
            }
        }

        // Render energy beams
        for (EnergyBeam beam : energyBeams) {
            float progress = beam.getProgress();
            float alpha = (float) Math.sin(progress * Math.PI);
            
            for (int i = 0; i < beam.segments; i++) {
                float t = (float) i / beam.segments;
                Vec3 pos = beam.startPos.lerp(beam.endPos, t);
                float width = beam.width * (1.0F - t * 0.5F) * alpha;
                
                sprites.add(new ParticlesWorldRenderer.Sprite(pos, width * 2.0F, 
                        ColorUtil.multiplyAlpha(beam.color, alpha * 0.4F)));
                sprites.add(new ParticlesWorldRenderer.Sprite(pos, width, 
                        ColorUtil.multiplyAlpha(beam.color, alpha)));
                sprites.add(new ParticlesWorldRenderer.Sprite(pos, width * 0.3F, 
                        ColorUtil.rgba(255, 255, 255, (int) (alpha * 220))));
            }
        }

        // Render spiral rings
        for (SpiralRing ring : spiralRings) {
            float progress = ring.getProgress();
            float alpha = (1.0F - progress);
            float currentRadius = ring.expanding ? ring.radius * (1.0F + progress * 2.0F) : ring.radius;
            double rotation = System.currentTimeMillis() * 0.001 * ring.rotationSpeed;
            
            for (int i = 0; i < ring.particleCount; i++) {
                double angle = (i / (double) ring.particleCount) * Math.PI * 2.0 + rotation;
                Vec3 pos = ring.center.add(
                        Math.cos(angle) * currentRadius,
                        Math.sin(i * 0.5 + rotation) * ring.height,
                        Math.sin(angle) * currentRadius
                );
                sprites.add(new ParticlesWorldRenderer.Sprite(pos, 0.15F, 
                        ColorUtil.multiplyAlpha(ring.color, alpha * alpha)));
            }
        }

        // Render lightning
        for (LightningStrike bolt : lightnings) {
            float progress = bolt.getProgress();
            float flash = (1.0F - progress) * (ThreadLocalRandom.current().nextFloat() > 0.1F ? 1.0F : 0.5F);
            
            renderLightningLine(bolt.segments, sprites, 
                    ColorUtil.withAlpha(bolt.color, (int) (255 * flash)),
                    ColorUtil.rgba(255, 255, 255, (int) (255 * flash)),
                    0.7F, 0.2F);

            if (!bolt.branchSegments.isEmpty()) {
                renderLightningLine(bolt.branchSegments, sprites,
                        ColorUtil.withAlpha(bolt.secondaryColor, (int) (255 * flash * 0.7F)),
                        ColorUtil.rgba(255, 255, 255, (int) (255 * flash * 0.8F)),
                        0.45F, 0.12F);
            }
        }

        // Render particles
        for (EpicParticle p : particles) {
            float progress = p.getProgress();
            float alphaFactor = (float) Math.sin((1.0F - progress) * (Math.PI * 0.5));
            
            // Outer glow
            sprites.add(new ParticlesWorldRenderer.Sprite(p.pos, p.baseSize * 2.5F * alphaFactor,
                    ColorUtil.multiplyAlpha(p.color, alphaFactor * 0.3F)));
            
            // Main particle
            sprites.add(new ParticlesWorldRenderer.Sprite(p.pos, p.baseSize * alphaFactor,
                    ColorUtil.multiplyAlpha(p.color, alphaFactor)));
            
            // Bright core
            sprites.add(new ParticlesWorldRenderer.Sprite(p.pos, p.baseSize * 0.4F * alphaFactor,
                    ColorUtil.multiplyAlpha(p.secondaryColor, alphaFactor)));
        }

        if (!sprites.isEmpty()) {
            this.particleRenderer.render(sprites, brightness, throughWalls, true);
        }
    }

    private static void renderLightningLine(List<Vec3> points, List<ParticlesWorldRenderer.Sprite> out, 
                                           int outerColor, int coreColor, float outerR, float coreR) {
        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 p1 = points.get(i);
            Vec3 p2 = points.get(i + 1);
            double dist = p1.distanceTo(p2);
            double step = 0.04;

            for (double d = 0; d < dist; d += step) {
                Vec3 pos = p1.lerp(p2, (float) (d / dist));
                out.add(new ParticlesWorldRenderer.Sprite(pos, outerR, outerColor));
                out.add(new ParticlesWorldRenderer.Sprite(pos, coreR, coreColor));
            }
        }
    }

    private static Vec3 randomSphereVector() {
        double theta = ThreadLocalRandom.current().nextDouble() * Math.PI * 2.0;
        double phi = Math.acos(ThreadLocalRandom.current().nextDouble() * 2.0 - 1.0);
        double sinPhi = Math.sin(phi);
        return new Vec3(sinPhi * Math.cos(theta), Math.cos(phi), sinPhi * Math.sin(theta));
    }

    private static Vec3 randomOffset(double range) {
        return new Vec3(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * range * 2.0,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * range * 2.0,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * range * 2.0
        );
    }

    public synchronized boolean hasActive() {
        return !particles.isEmpty() || !shockWaves.isEmpty() || !energyBeams.isEmpty() 
                || !spiralRings.isEmpty() || !lightnings.isEmpty() || !chams.isEmpty();
    }

    public synchronized void clear() {
        particles.clear();
        shockWaves.clear();
        energyBeams.clear();
        spiralRings.clear();
        lightnings.clear();
        chams.clear();
    }
}
