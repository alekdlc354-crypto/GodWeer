package pub.godweer.kitty.module.impl.render;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
import pub.godweer.auxiliary.render.world.particles.ParticlesWorldRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * HoleESP - Highlights safe holes made of bedrock and obsidian for PvP
 */
public final class HoleESP extends Module {

    public final ModeSetting renderMode = mode("Render Mode", "Box", "Box", "Flat", "Glow");

    public final CheckBox bedrockHoles = checkbox("Bedrock Holes", true);
    public final CheckBox obsidianHoles = checkbox("Obsidian Holes", true);
    public final CheckBox mixedHoles = checkbox("Mixed Holes", true);
    public final CheckBox doubleHoles = checkbox("Double Holes", true);

    public final ColorSetting bedrockColor = color("Bedrock Color", ColorUtil.rgba(50, 255, 50, 255));
    public final ColorSetting obsidianColor = color("Obsidian Color", ColorUtil.rgba(255, 50, 50, 255));
    public final ColorSetting mixedColor = color("Mixed Color", ColorUtil.rgba(255, 200, 50, 255));

    public final SliderSetting lineWidth = slider("Line Width", 2.0F, 0.5F, 5.0F, 0.5F);
    public final SliderSetting brightness = slider("Brightness", 3.5F, 1.0F, 6.0F, 0.5F);
    public final SliderSetting range = slider("Range", 16.0F, 4.0F, 32.0F, 2.0F);
    public final SliderSetting height = slider("Height", 0.2F, 0.05F, 1.0F, 0.05F);

    private final ParticlesWorldRenderer renderer = new ParticlesWorldRenderer();

    public HoleESP() {
        super("HoleESP", "Highlights safe holes for PvP", Category.RENDER);
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        Minecraft mc = event.getClient();
        if (mc.level == null || mc.player == null) return;

        List<ParticlesWorldRenderer.Sprite> sprites = new ArrayList<>();
        int rangeVal = (int) range.getValue().floatValue();
        BlockPos playerPos = mc.player.blockPosition();

        for (int x = -rangeVal; x <= rangeVal; x++) {
            for (int y = -rangeVal; y <= rangeVal; y++) {
                for (int z = -rangeVal; z <= rangeVal; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);
                    
                    if (!isHole(pos)) continue;

                    int color = getHoleColor(pos);
                    if (color == -1) continue;

                    AABB box = new AABB(pos);
                    
                    if ("Box".equals(renderMode.getValue())) {
                        renderBox(sprites, box, color);
                    } else if ("Flat".equals(renderMode.getValue())) {
                        renderFlat(sprites, pos, color);
                    } else { // Glow
                        renderGlow(sprites, pos, color);
                    }
                }
            }
        }

        if (!sprites.isEmpty()) {
            renderer.render(sprites, brightness.getValue(), true, true);
        }
    }

    private boolean isHole(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        
        // Check if the hole is air
        if (!mc.level.getBlockState(pos).isAir()) return false;
        if (!mc.level.getBlockState(pos.above()).isAir()) return false;
        if (!mc.level.getBlockState(pos.above(2)).isAir()) return false;

        // Check all 4 sides and bottom
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
        
        for (Direction dir : sides) {
            BlockPos checkPos = pos.relative(dir);
            BlockState state = mc.level.getBlockState(checkPos);
            
            if (state.isAir()) return false;
            
            boolean isBedrock = state.getBlock() == Blocks.BEDROCK;
            boolean isObsidian = state.getBlock() == Blocks.OBSIDIAN;
            
            if (!isBedrock && !isObsidian) return false;
        }

        return true;
    }

    private int getHoleColor(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN};
        
        int bedrockCount = 0;
        int obsidianCount = 0;

        for (Direction dir : sides) {
            BlockPos checkPos = pos.relative(dir);
            BlockState state = mc.level.getBlockState(checkPos);
            
            if (state.getBlock() == Blocks.BEDROCK) {
                bedrockCount++;
            } else if (state.getBlock() == Blocks.OBSIDIAN) {
                obsidianCount++;
            }
        }

        if (bedrockCount == 5 && bedrockHoles.getValue()) {
            return bedrockColor.getValue();
        } else if (obsidianCount == 5 && obsidianHoles.getValue()) {
            return obsidianColor.getValue();
        } else if (bedrockCount > 0 && obsidianCount > 0 && mixedHoles.getValue()) {
            return mixedColor.getValue();
        }

        return -1;
    }

    private void renderBox(List<ParticlesWorldRenderer.Sprite> sprites, AABB box, int color) {
        float lineW = lineWidth.getValue() * 0.05F;
        float h = height.getValue();
        
        AABB renderBox = new AABB(box.minX, box.minY, box.minZ, box.maxX, box.minY + h, box.maxZ);

        // Bottom face
        drawLine(sprites, new Vec3(renderBox.minX, renderBox.minY, renderBox.minZ), 
                new Vec3(renderBox.maxX, renderBox.minY, renderBox.minZ), color, lineW);
        drawLine(sprites, new Vec3(renderBox.maxX, renderBox.minY, renderBox.minZ), 
                new Vec3(renderBox.maxX, renderBox.minY, renderBox.maxZ), color, lineW);
        drawLine(sprites, new Vec3(renderBox.maxX, renderBox.minY, renderBox.maxZ), 
                new Vec3(renderBox.minX, renderBox.minY, renderBox.maxZ), color, lineW);
        drawLine(sprites, new Vec3(renderBox.minX, renderBox.minY, renderBox.maxZ), 
                new Vec3(renderBox.minX, renderBox.minY, renderBox.minZ), color, lineW);

        // Top face
        drawLine(sprites, new Vec3(renderBox.minX, renderBox.maxY, renderBox.minZ), 
                new Vec3(renderBox.maxX, renderBox.maxY, renderBox.minZ), color, lineW);
        drawLine(sprites, new Vec3(renderBox.maxX, renderBox.maxY, renderBox.minZ), 
                new Vec3(renderBox.maxX, renderBox.maxY, renderBox.maxZ), color, lineW);
        drawLine(sprites, new Vec3(renderBox.maxX, renderBox.maxY, renderBox.maxZ), 
                new Vec3(renderBox.minX, renderBox.maxY, renderBox.maxZ), color, lineW);
        drawLine(sprites, new Vec3(renderBox.minX, renderBox.maxY, renderBox.maxZ), 
                new Vec3(renderBox.minX, renderBox.maxY, renderBox.minZ), color, lineW);

        // Vertical lines
        drawLine(sprites, new Vec3(renderBox.minX, renderBox.minY, renderBox.minZ), 
                new Vec3(renderBox.minX, renderBox.maxY, renderBox.minZ), color, lineW);
        drawLine(sprites, new Vec3(renderBox.maxX, renderBox.minY, renderBox.minZ), 
                new Vec3(renderBox.maxX, renderBox.maxY, renderBox.minZ), color, lineW);
        drawLine(sprites, new Vec3(renderBox.maxX, renderBox.minY, renderBox.maxZ), 
                new Vec3(renderBox.maxX, renderBox.maxY, renderBox.maxZ), color, lineW);
        drawLine(sprites, new Vec3(renderBox.minX, renderBox.minY, renderBox.maxZ), 
                new Vec3(renderBox.minX, renderBox.maxY, renderBox.maxZ), color, lineW);
    }

    private void renderFlat(List<ParticlesWorldRenderer.Sprite> sprites, BlockPos pos, int color) {
        float lineW = lineWidth.getValue() * 0.05F;
        Vec3 base = new Vec3(pos.getX(), pos.getY() + 0.01, pos.getZ());

        // Draw X on the ground
        drawLine(sprites, base, base.add(1, 0, 1), color, lineW);
        drawLine(sprites, base.add(1, 0, 0), base.add(0, 0, 1), color, lineW);
        
        // Draw border
        drawLine(sprites, base, base.add(1, 0, 0), color, lineW);
        drawLine(sprites, base.add(1, 0, 0), base.add(1, 0, 1), color, lineW);
        drawLine(sprites, base.add(1, 0, 1), base.add(0, 0, 1), color, lineW);
        drawLine(sprites, base.add(0, 0, 1), base, color, lineW);
    }

    private void renderGlow(List<ParticlesWorldRenderer.Sprite> sprites, BlockPos pos, int color) {
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        float size = 0.3F;
        
        sprites.add(new ParticlesWorldRenderer.Sprite(center, size * 2.5F, 
                ColorUtil.multiplyAlpha(color, 0.3F)));
        sprites.add(new ParticlesWorldRenderer.Sprite(center, size, color));
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
