package pub.godweer.kitty.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.phys.AABB;
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
 * Storage ESP - Highlights chests, shulkers, hoppers, furnaces and dropped items
 */
public final class StorageESP extends Module {

    public final CheckBox chests = checkbox("Chests", true);
    public final CheckBox enderChests = checkbox("Ender Chests", true);
    public final CheckBox shulkers = checkbox("Shulkers", true);
    public final CheckBox barrels = checkbox("Barrels", true);
    public final CheckBox hoppers = checkbox("Hoppers", false);
    public final CheckBox furnaces = checkbox("Furnaces", false);
    public final CheckBox dispensers = checkbox("Dispensers", false);
    public final CheckBox droppedItems = checkbox("Dropped Items", true);

    public final ColorSetting chestColor = color("Chest Color", ColorUtil.rgba(255, 200, 50, 255));
    public final ColorSetting enderColor = color("Ender Color", ColorUtil.rgba(200, 50, 255, 255));
    public final ColorSetting shulkerColor = color("Shulker Color", ColorUtil.rgba(255, 100, 200, 255));
    public final ColorSetting otherColor = color("Other Color", ColorUtil.rgba(150, 150, 150, 255));
    public final ColorSetting itemColor = color("Item Color", ColorUtil.rgba(50, 255, 255, 255));

    public final SliderSetting lineWidth = slider("Line Width", 2.0F, 0.5F, 5.0F, 0.5F);
    public final SliderSetting brightness = slider("Brightness", 3.5F, 1.0F, 6.0F, 0.5F);
    public final SliderSetting range = slider("Range", 64.0F, 16.0F, 128.0F, 8.0F);

    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();

    public StorageESP() {
        super("StorageESP", "Highlights storage containers and dropped items", Category.RENDER);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Minecraft mc = event.getClient();
        if (mc.level == null || mc.player == null) return;

        List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>();
        double rangeSq = range.getValue() * range.getValue();

        // Render block entities
        for (BlockEntity blockEntity : mc.level.blockEntityList()) {
            BlockPos pos = blockEntity.getBlockPos();
            double distSq = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distSq > rangeSq) continue;

            int color = getColorForBlockEntity(blockEntity);
            if (color == -1) continue;

            AABB box = new AABB(pos);
            renderBox(sprites, box, color);
        }

        // Render dropped items
        if (droppedItems.getValue()) {
            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
                    double distSq = mc.player.distanceToSqr(itemEntity.position());
                    if (distSq > rangeSq) continue;

                    AABB box = itemEntity.getBoundingBox();
                    renderBox(sprites, box, itemColor.getValue());
                }
            }
        }

        if (!sprites.isEmpty()) {
            renderer.render(sprites, brightness.getValue(), true, true);
        }
    }

    private int getColorForBlockEntity(BlockEntity blockEntity) {
        if (blockEntity instanceof ChestBlockEntity || blockEntity instanceof TrappedChestBlockEntity) {
            return chests.getValue() ? chestColor.getValue() : -1;
        } else if (blockEntity instanceof EnderChestBlockEntity) {
            return enderChests.getValue() ? enderColor.getValue() : -1;
        } else if (blockEntity instanceof ShulkerBoxBlockEntity) {
            return shulkers.getValue() ? shulkerColor.getValue() : -1;
        } else if (blockEntity instanceof BarrelBlockEntity) {
            return barrels.getValue() ? chestColor.getValue() : -1;
        } else if (blockEntity instanceof HopperBlockEntity) {
            return hoppers.getValue() ? otherColor.getValue() : -1;
        } else if (blockEntity instanceof AbstractFurnaceBlockEntity) {
            return furnaces.getValue() ? otherColor.getValue() : -1;
        } else if (blockEntity instanceof DispenserBlockEntity || blockEntity instanceof DropperBlockEntity) {
            return dispensers.getValue() ? otherColor.getValue() : -1;
        }
        return -1;
    }

    private void renderBox(List<ParticlesWorldRenderer.Sprite> sprites, AABB box, int color) {
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
