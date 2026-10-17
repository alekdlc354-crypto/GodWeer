package pub.godweer.kitty.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
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
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * ESP - Extra Sensory Perception
 * Highlights players and mobs with 2D/3D boxes
 */
public final class ESP extends Module {

    public final ModeSetting mode = mode("Mode", "2D Box", "2D Box", "3D Box", "Outline");
    public final ModeSetting targetMode = mode("Target", "Players", "Players", "Mobs", "All", "Friends");

    public final CheckBox players = checkbox("Players", true);
    public final CheckBox mobs = checkbox("Mobs", false);
    public final CheckBox animals = checkbox("Animals", false);
    public final CheckBox invisible = checkbox("Invisible", true);

    public final ColorSetting playerColor = color("Player Color", ColorUtil.rgba(255, 50, 50, 255));
    public final ColorSetting friendColor = color("Friend Color", ColorUtil.rgba(50, 255, 50, 255));
    public final ColorSetting mobColor = color("Mob Color", ColorUtil.rgba(255, 150, 50, 255));
    public final ColorSetting animalColor = color("Animal Color", ColorUtil.rgba(50, 150, 255, 255));

    public final SliderSetting lineWidth = slider("Line Width", 2.0F, 0.5F, 5.0F, 0.5F);
    public final SliderSetting brightness = slider("Brightness", 3.0F, 1.0F, 6.0F, 0.5F);

    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();

    public ESP() {
        super("ESP", "Highlights entities with boxes", Category.RENDER);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Minecraft mc = event.getClient();
        if (mc.level == null || mc.player == null) return;

        List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>();
        String target = targetMode.getValue();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player) continue;
            if (!entity.isAlive()) continue;

            boolean shouldRender = false;
            int color = playerColor.getValue();

            if (entity instanceof Player player) {
                if (players.getValue() && (target.equals("Players") || target.equals("All"))) {
                    shouldRender = true;
                    // TODO: Check if friend
                }
            } else if (entity instanceof Monster) {
                if (mobs.getValue() && (target.equals("Mobs") || target.equals("All"))) {
                    shouldRender = true;
                    color = mobColor.getValue();
                }
            } else if (entity instanceof LivingEntity) {
                if (animals.getValue() && target.equals("All")) {
                    shouldRender = true;
                    color = animalColor.getValue();
                }
            }

            if (!shouldRender) continue;
            if (!invisible.getValue() && entity.isInvisible()) continue;

            AABB box = entity.getBoundingBox();
            Vec3 pos = entity.position();

            if ("2D Box".equals(mode.getValue())) {
                render2DBox(sprites, box, pos, entity, color);
            } else if ("3D Box".equals(mode.getValue())) {
                render3DBox(sprites, box, color);
            }
        }

        if (!sprites.isEmpty()) {
            renderer.render(sprites, brightness.getValue(), true, true);
        }
    }

    private void render2DBox(List<ParticlesWorldRenderer.Sprite> sprites, AABB box, Vec3 pos, Entity entity, int color) {
        double width = box.getXsize();
        double height = box.getYsize();
        double depth = box.getZsize();

        // Corners of the box
        Vec3[] corners = new Vec3[]{
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ)
        };

        float lineW = lineWidth.getValue() * 0.05F;

        // Draw vertical lines
        for (int i = 0; i < 4; i++) {
            Vec3 bottom = corners[i];
            Vec3 top = corners[i + 4];
            drawLine(sprites, bottom, top, color, lineW);
        }

        // Draw bottom rectangle
        for (int i = 0; i < 4; i++) {
            Vec3 p1 = corners[i];
            Vec3 p2 = corners[(i + 1) % 4];
            drawLine(sprites, p1, p2, color, lineW);
        }

        // Draw top rectangle
        for (int i = 4; i < 8; i++) {
            Vec3 p1 = corners[i];
            Vec3 p2 = corners[((i - 4 + 1) % 4) + 4];
            drawLine(sprites, p1, p2, color, lineW);
        }
    }

    private void render3DBox(List<ParticlesWorldRenderer.Sprite> sprites, AABB box, int color) {
        float lineW = lineWidth.getValue() * 0.05F;

        // Bottom face
        drawLine(sprites, new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ), color, lineW);
        drawLine(sprites, new Vec3(box.maxX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.maxZ), color, lineW);
        drawLine(sprites, new Vec3(box.maxX, box.minY, box.maxZ), new Vec3(box.minX, box.minY, box.maxZ), color, lineW);
        drawLine(sprites, new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.minX, box.minY, box.minZ), color, lineW);

        // Top face
        drawLine(sprites, new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ), color, lineW);
        drawLine(sprites, new Vec3(box.maxX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.maxZ), color, lineW);
        drawLine(sprites, new Vec3(box.maxX, box.maxY, box.maxZ), new Vec3(box.minX, box.maxY, box.maxZ), color, lineW);
        drawLine(sprites, new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.minX, box.maxY, box.minZ), color, lineW);

        // Vertical lines
        drawLine(sprites, new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.minX, box.maxY, box.minZ), color, lineW);
        drawLine(sprites, new Vec3(box.maxX, box.minY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ), color, lineW);
        drawLine(sprites, new Vec3(box.maxX, box.minY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ), color, lineW);
        drawLine(sprites, new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.minX, box.maxY, box.maxZ), color, lineW);
    }

    private void drawLine(List<ParticlesWorldRenderer.Sprite> sprites, Vec3 p1, Vec3 p2, int color, float width) {
        double dist = p1.distanceTo(p2);
        double step = 0.1;
        for (double d = 0; d <= dist; d += step) {
            Vec3 pos = p1.lerp(p2, d / dist);
            sprites.add(new ParticlesWorldRenderer.Sprite(pos, width, color));
        }
    }
}
