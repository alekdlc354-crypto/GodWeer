package pub.godweer.kitty.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.Render3DEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Beautiful trails effect - leaves glowing particle trails behind moving entities
 * Created with love for aesthetics
 */
public final class Trails extends Module {

    public final ModeSetting mode = mode("Mode", "Rainbow", 
            "Rainbow", "Gradient", "Theme", "Crystal", "Aurora", "Neon");
    
    public final ModeSetting entityType = mode("Entity Type", "Players", 
            "Players", "All Living", "All Entities");
    
    public final CheckBox onlyMoving = checkbox("Only Moving", true);
    public final CheckBox selfTrails = checkbox("Self Trails", true);
    
    public final SliderSetting trailLength = slider("Trail Length", 20.0F, 8.0F, 50.0F, 1.0F);
    public final SliderSetting trailWidth = slider("Trail Width", 0.2F, 0.1F, 0.6F, 0.05F);
    public final SliderSetting fadeSpeed = slider("Fade Speed", 1.2F, 0.3F, 2.5F, 0.1F);
    public final SliderSetting brightness = slider("Brightness", 3.5F, 1.0F, 8.0F, 0.5F);
    public final SliderSetting pulseSpeed = slider("Pulse Speed", 1.0F, 0.0F, 4.0F, 0.2F);
    
    public final ColorSetting startColor = color("Start Color", ColorUtil.rgba(255, 100, 200, 255));
    public final ColorSetting endColor = color("End Color", ColorUtil.rgba(100, 200, 255, 255));
    
    public final CheckBox throughWalls = checkbox("Through Walls", true);
    public final CheckBox spiralMode = checkbox("Spiral Mode", false);
    public final SliderSetting spiralRadius = slider("Spiral Radius", 0.3F, 0.1F, 0.8F, 0.05F).visible(() -> spiralMode.getValue());

    private final List<TrailPoint> trails = new ArrayList<>();
    private final ParticlesWorldRenderer particleRenderer = new ParticlesWorldRenderer();
    private long lastTime = System.currentTimeMillis();

    public Trails() {
        super("Trails", "Beautiful glowing trails behind entities", Category.RENDER);
    }

    private static class TrailPoint {
        Vec3 pos;
        long spawnTime;
        int color;
        float size;
        int entityId;
        double angle;

        public TrailPoint(Vec3 pos, int color, float size, int entityId, double angle) {
            this.pos = pos;
            this.spawnTime = System.currentTimeMillis();
            this.color = color;
            this.size = size;
            this.entityId = entityId;
            this.angle = angle;
        }

        public float getAge() {
            return (System.currentTimeMillis() - spawnTime) / 1000.0F;
        }

        public boolean isDead(float fadeSpeed) {
            return getAge() > (2.5F / fadeSpeed);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Minecraft mc = event.getClient();
        if (mc.level == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (now - lastTime) / 1000.0F);
        lastTime = now;

        // Spawn new trail points
        spawnTrails(mc);

        // Remove dead trails
        float fadeSpeed = this.fadeSpeed.getValue();
        trails.removeIf(t -> t.isDead(fadeSpeed));

        // Render trails
        renderTrails(mc, fadeSpeed);
    }

    private void spawnTrails(Minecraft mc) {
        List<Entity> entities = new ArrayList<>();
        
        // Collect entities based on settings
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!entity.isAlive()) continue;
            
            boolean shouldTrack = false;
            String type = entityType.getValue();
            
            if ("Players".equals(type) && entity instanceof Player) {
                shouldTrack = true;
            } else if ("All Living".equals(type) && entity instanceof net.minecraft.world.entity.LivingEntity) {
                shouldTrack = true;
            } else if ("All Entities".equals(type)) {
                shouldTrack = true;
            }
            
            if (shouldTrack) {
                if (!selfTrails.getValue() && entity == mc.player) continue;
                if (onlyMoving.getValue() && entity.getDeltaMovement().lengthSqr() < 0.01) continue;
                entities.add(entity);
            }
        }

        for (Entity entity : entities) {
            Vec3 pos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            double speed = entity.getDeltaMovement().length();
            
            if (speed < 0.05) continue;

            float width = trailWidth.getValue();
            
            // Spawn multiple points per frame for smoother trails
            int pointsPerFrame = Math.max(2, (int) (speed * 4.0));
            
            for (int i = 0; i < pointsPerFrame; i++) {
                double offset = (double) i / pointsPerFrame;
                Vec3 trailPos = pos.add(entity.getDeltaMovement().scale(-offset * 0.3));
                
                // Spiral mode - smoother rotation
                if (spiralMode.getValue()) {
                    double radius = spiralRadius.getValue();
                    double time = System.currentTimeMillis() * 0.001;
                    double angle = time * 6.0 + entity.getId() * 0.1;
                    trailPos = trailPos.add(
                            Math.cos(angle) * radius,
                            Math.sin(angle * 0.7) * radius * 0.6,
                            Math.sin(angle) * radius
                    );
                }
                
                int color = getColorForTrail(entity, trailPos, speed);
                trails.add(new TrailPoint(trailPos, color, width, entity.getId(), 
                        spiralMode.getValue() ? System.currentTimeMillis() * 0.01 : 0));
            }
        }

        // Limit trail count based on trail length setting
        int maxLength = this.trailLength.getValue().intValue() * 100;
        while (trails.size() > maxLength) {
            trails.remove(0);
        }
    }

    private int getColorForTrail(Entity entity, Vec3 pos, double speed) {
        String mode = this.mode.getValue();
        float time = System.currentTimeMillis() * 0.001F;
        
        switch (mode) {
            case "Rainbow": {
                float hue = (float) ((pos.x * 0.05 + pos.z * 0.05 + time * 0.3) % 1.0F);
                if (hue < 0) hue += 1.0F;
                return ColorUtil.fromHsv(hue, 0.85F, 1.0F, 255);
            }
            
            case "Gradient": {
                float t = (float) ((pos.y * 0.2 + time * 0.5) % 2.0) / 2.0F;
                if (t < 0) t += 1.0F;
                // Smoother ping-pong
                t = (float) Math.sin(t * Math.PI);
                return ColorUtil.lerp(startColor.getValue(), endColor.getValue(), t);
            }
            
            case "Theme": {
                return Theme.getAccentColor();
            }
            
            case "Crystal": {
                float hue = (float) (0.55 + Math.sin(time * 1.5 + entity.getId() * 0.1) * 0.15);
                return ColorUtil.fromHsv(hue, 0.7F, 1.0F, 255);
            }
            
            case "Aurora": {
                float hue = (float) (0.35 + Math.sin(time * 0.8 + pos.x * 0.2) * 0.12 + Math.cos(time * 0.6 + pos.z * 0.2) * 0.12);
                return ColorUtil.fromHsv(hue, 0.85F, 1.0F, 255);
            }
            
            case "Neon": {
                int neonIndex = (int) (time * 1.5) % 4;
                float blend = (float) ((time * 1.5) % 1.0);
                // Smooth transition between neon colors
                int colors[] = {
                    ColorUtil.rgba(255, 50, 255, 255),
                    ColorUtil.rgba(50, 255, 255, 255),
                    ColorUtil.rgba(255, 255, 50, 255),
                    ColorUtil.rgba(50, 255, 150, 255)
                };
                int next = (neonIndex + 1) % 4;
                return ColorUtil.lerp(colors[neonIndex], colors[next], blend);
            }
            
            default:
                return Theme.getAccentColor();
        }
    }

    private void renderTrails(Minecraft mc, float fadeSpeed) {
        if (trails.isEmpty()) return;

        float pulseSpeed = this.pulseSpeed.getValue();
        float brightness = this.brightness.getValue();
        boolean throughWalls = this.throughWalls.getValue();
        
        float time = System.currentTimeMillis() * 0.001F;
        float pulse = pulseSpeed > 0 ? (float) (Math.sin(time * pulseSpeed * 2.0 * Math.PI) * 0.15 + 0.85) : 1.0F;

        List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>();

        for (TrailPoint trail : trails) {
            float age = trail.getAge();
            float maxAge = 2.5F / fadeSpeed;
            float lifeProgress = 1.0F - (age / maxAge);
            
            // Smoother fade out with cubic easing
            float alpha = lifeProgress * lifeProgress * lifeProgress;
            alpha *= pulse;
            
            if (alpha < 0.02F) continue;

            int color = ColorUtil.multiplyAlpha(trail.color, alpha);
            float size = trail.size * (0.3F + alpha * 0.7F);

            // Outer glow - softer and larger
            sprites.add(new ParticlesWorldRenderer.Sprite(trail.pos, size * 2.0F, 
                    ColorUtil.multiplyAlpha(color, 0.25F)));
            
            // Main particle
            sprites.add(new ParticlesWorldRenderer.Sprite(trail.pos, size, color));
            
            // Inner bright core - smaller for better definition
            sprites.add(new ParticlesWorldRenderer.Sprite(trail.pos, size * 0.35F, 
                    ColorUtil.rgba(255, 255, 255, (int) (alpha * 180))));
        }

        if (!sprites.isEmpty()) {
            particleRenderer.render(sprites, brightness, throughWalls, true);
        }
    }

    @Override
    protected void onDisable() {
        trails.clear();
    }
}
