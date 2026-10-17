package pub.godweer.kitty.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.Render3DEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Trajectories - Draws trajectory line and landing point for projectiles
 */
public final class Trajectories extends Module {

    public final CheckBox arrows = checkbox("Arrows", true);
    public final CheckBox pearls = checkbox("Ender Pearls", true);
    public final CheckBox snowballs = checkbox("Snowballs", true);
    public final CheckBox eggs = checkbox("Eggs", true);
    public final CheckBox potions = checkbox("Potions", true);
    public final CheckBox fishing = checkbox("Fishing Rod", true);

    public final ColorSetting lineColor = color("Line Color", ColorUtil.rgba(50, 255, 50, 255));
    public final ColorSetting landingColor = color("Landing Color", ColorUtil.rgba(255, 50, 50, 255));

    public final SliderSetting lineWidth = slider("Line Width", 1.5F, 0.5F, 4.0F, 0.5F);
    public final SliderSetting brightness = slider("Brightness", 3.0F, 1.0F, 6.0F, 0.5F);
    public final SliderSetting maxPoints = slider("Max Points", 200.0F, 50.0F, 500.0F, 10.0F);

    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();

    public Trajectories() {
        super("Trajectories", "Shows projectile trajectories", Category.RENDER);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Minecraft mc = event.getClient();
        if (mc.level == null || mc.player == null) return;

        List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>();

        // Check if player is holding a throwable/shootable item
        ItemStack heldItem = mc.player.getMainHandItem();
        if (heldItem.isEmpty()) return;

        Item item = heldItem.getItem();
        boolean shouldRender = false;
        float power = 1.0F;

        if ((item instanceof BowItem || item instanceof CrossbowItem) && arrows.getValue()) {
            shouldRender = true;
            // Calculate bow power
            int useDuration = mc.player.getUseItemRemainingTicks();
            power = Math.min(1.0F, (20 - useDuration) / 20.0F);
            power = (power * power + power * 2.0F) / 3.0F;
            if (power > 1.0F) power = 1.0F;
        } else if (item instanceof EnderpearlItem && pearls.getValue()) {
            shouldRender = true;
        } else if (item instanceof SnowballItem && snowballs.getValue()) {
            shouldRender = true;
        } else if (item instanceof EggItem && eggs.getValue()) {
            shouldRender = true;
        } else if (item instanceof ThrowablePotionItem && potions.getValue()) {
            shouldRender = true;
        } else if (item instanceof FishingRodItem && fishing.getValue()) {
            shouldRender = true;
        }

        if (!shouldRender) return;

        // Calculate trajectory
        Vec3 startPos = mc.player.getEyePosition();
        Vec3 lookVec = mc.player.getViewVector(1.0F);
        Vec3 velocity = lookVec.scale(1.5 * power);
        Vec3 pos = startPos;

        double gravity = item instanceof BowItem ? 0.05 : 0.03;
        int points = (int) maxPoints.getValue().floatValue();

        List<Vec3> trajectory = new ArrayList<>();
        trajectory.add(pos);

        for (int i = 0; i < points; i++) {
            pos = pos.add(velocity);
            velocity = velocity.add(0, -gravity, 0);
            velocity = velocity.scale(0.99);

            trajectory.add(pos);

            // Check if hit ground or entity
            if (mc.level.getBlockState(pos.toBlockPos()).isSolid()) {
                break;
            }
        }

        // Draw trajectory line
        float lineW = lineWidth.getValue() * 0.03F;
        for (int i = 0; i < trajectory.size() - 1; i++) {
            Vec3 p1 = trajectory.get(i);
            Vec3 p2 = trajectory.get(i + 1);
            drawLine(sprites, p1, p2, lineColor.getValue(), lineW);
        }

        // Draw landing point
        if (!trajectory.isEmpty()) {
            Vec3 landingPos = trajectory.get(trajectory.size() - 1);
            float size = 0.15F;
            sprites.add(new ParticlesWorldRenderer.Sprite(landingPos, size * 2.0F, 
                    ColorUtil.multiplyAlpha(landingColor.getValue(), 0.5F)));
            sprites.add(new ParticlesWorldRenderer.Sprite(landingPos, size, landingColor.getValue()));
        }

        if (!sprites.isEmpty()) {
            renderer.render(sprites, brightness.getValue(), true, true);
        }
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
