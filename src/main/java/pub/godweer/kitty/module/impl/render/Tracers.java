package pub.godweer.kitty.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
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
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracers - Draws colored lines from crosshair to players, friends or enemies
 */
public final class Tracers extends Module {

    public final ModeSetting targetMode = mode("Target", "Players", "Players", "Mobs", "All");
    public final ModeSetting originMode = mode("Origin", "Crosshair", "Crosshair", "Bottom", "Center");

    public final CheckBox players = checkbox("Players", true);
    public final CheckBox mobs = checkbox("Mobs", false);
    public final CheckBox invisible = checkbox("Invisible", true);

    public final ColorSetting playerColor = color("Player Color", ColorUtil.rgba(255, 50, 50, 255));
    public final ColorSetting friendColor = color("Friend Color", ColorUtil.rgba(50, 255, 50, 255));
    public final ColorSetting mobColor = color("Mob Color", ColorUtil.rgba(255, 150, 50, 255));

    public final SliderSetting lineWidth = slider("Line Width", 1.5F, 0.5F, 4.0F, 0.5F);
    public final SliderSetting brightness = slider("Brightness", 3.0F, 1.0F, 6.0F, 0.5F);
    public final SliderSetting range = slider("Range", 128.0F, 32.0F, 256.0F, 16.0F);

    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();

    public Tracers() {
        super("Tracers", "Draws lines to entities", Category.RENDER);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Minecraft mc = event.getClient();
        if (mc.level == null || mc.player == null) return;

        List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>();
        double rangeSq = range.getValue() * range.getValue();
        String target = targetMode.getValue();

        // Get origin point
        Vec3 origin = getOrigin(mc);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;
            if (!entity.isAlive()) continue;

            double distSq = mc.player.distanceToSqr(entity.position());
            if (distSq > rangeSq) continue;

            boolean shouldRender = false;
            int color = playerColor.getValue();

            if (entity instanceof Player) {
                if (players.getValue() && (target.equals("Players") || target.equals("All"))) {
                    shouldRender = true;
                }
            } else if (entity instanceof Monster) {
                if (mobs.getValue() && (target.equals("Mobs") || target.equals("All"))) {
                    shouldRender = true;
                    color = mobColor.getValue();
                }
            }

            if (!shouldRender) continue;
            if (!invisible.getValue() && entity.isInvisible()) continue;

            Vec3 targetPos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            drawLine(sprites, origin, targetPos, color);
        }

        if (!sprites.isEmpty()) {
            renderer.render(sprites, brightness.getValue(), true, true);
        }
    }

    private Vec3 getOrigin(Minecraft mc) {
        String origin = originMode.getValue();
        
        if ("Crosshair".equals(origin)) {
            // Point in front of player where crosshair points
            Vec3 eyePos = mc.player.getEyePosition();
            Vec3 lookVec = mc.player.getViewVector(1.0F);
            return eyePos.add(lookVec.scale(2.0));
        } else if ("Bottom".equals(origin)) {
            return mc.player.position();
        } else { // Center
            return mc.player.position().add(0, mc.player.getBbHeight() * 0.5, 0);
        }
    }

    private void drawLine(List<ParticlesWorldRenderer.Sprite> sprites, Vec3 p1, Vec3 p2, int color) {
        float lineW = lineWidth.getValue() * 0.03F;
        double dist = p1.distanceTo(p2);
        double step = 0.15;
        
        for (double d = 0; d <= dist; d += step) {
            Vec3 pos = p1.lerp(p2, d / dist);
            sprites.add(new ParticlesWorldRenderer.Sprite(pos, lineW, color));
        }
    }
}
