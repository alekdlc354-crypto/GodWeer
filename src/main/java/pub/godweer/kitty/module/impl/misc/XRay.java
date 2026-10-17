package pub.godweer.kitty.module.impl.misc;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.GameTickEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.MultiModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;

import java.util.List;

/**
 * XRay - Makes unimportant blocks invisible, revealing ores, caves and bases
 */
public final class XRay extends Module {

    public final MultiModeSetting ores = multiMode("Ores",
            List.of("Diamond", "Emerald", "Gold", "Iron", "Coal", "Lapis", "Redstone", "Netherite", "Copper"),
            "Diamond", "Emerald", "Gold", "Iron", "Coal", "Lapis", "Redstone", "Netherite", "Copper");

    public final MultiModeSetting other = multiMode("Other Blocks",
            List.of("Spawner", "Chest", "Ender Chest", "Furnace", "Hopper", "Barrel", "Shulker", "Obsidian", "Bedrock"),
            "Spawner", "Chest", "Ender Chest", "Obsidian");

    public final CheckBox caves = checkbox("Show Caves", true);
    public final SliderSetting opacity = slider("Opacity", 0.0F, 0.0F, 1.0F, 0.1F);

    private long lastReload = 0;

    public XRay() {
        super("XRay", "Reveals ores and caves by hiding other blocks", Category.MISC);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        reloadChunks();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        reloadChunks();
    }

    @EventTarget
    public void onTick(GameTickEvent event) {
        // Reload chunks periodically if needed
        long now = System.currentTimeMillis();
        if (now - lastReload > 2000) {
            lastReload = now;
            // Optional: auto-reload chunks
        }
    }

    public boolean shouldRender(BlockState state, BlockPos pos) {
        if (!isEnabled()) return true;

        Block block = state.getBlock();
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();

        // Check ores
        if (ores.isEnabled("Diamond") && (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)) return true;
        if (ores.isEnabled("Emerald") && (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)) return true;
        if (ores.isEnabled("Gold") && (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE)) return true;
        if (ores.isEnabled("Iron") && (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)) return true;
        if (ores.isEnabled("Coal") && (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE)) return true;
        if (ores.isEnabled("Lapis") && (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE)) return true;
        if (ores.isEnabled("Redstone") && (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE)) return true;
        if (ores.isEnabled("Netherite") && block == Blocks.ANCIENT_DEBRIS) return true;
        if (ores.isEnabled("Copper") && (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE)) return true;

        // Check other blocks
        if (other.isEnabled("Spawner") && block == Blocks.SPAWNER) return true;
        if (other.isEnabled("Chest") && (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST)) return true;
        if (other.isEnabled("Ender Chest") && block == Blocks.ENDER_CHEST) return true;
        if (other.isEnabled("Furnace") && (block == Blocks.FURNACE || block == Blocks.BLAST_FURNACE || block == Blocks.SMOKER)) return true;
        if (other.isEnabled("Hopper") && block == Blocks.HOPPER) return true;
        if (other.isEnabled("Barrel") && block == Blocks.BARREL) return true;
        if (other.isEnabled("Shulker") && name.contains("shulker_box")) return true;
        if (other.isEnabled("Obsidian") && block == Blocks.OBSIDIAN) return true;
        if (other.isEnabled("Bedrock") && block == Blocks.BEDROCK) return true;

        // Show caves (air blocks)
        if (caves.getValue() && state.isAir()) return true;

        return false;
    }

    private void reloadChunks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            mc.level.clearTintCaches();

            int renderDistance = mc.options.getEffectiveRenderDistance();
            int chunkX = mc.player.getBlockX() >> 4;
            int chunkZ = mc.player.getBlockZ() >> 4;

            mc.level.setSectionRangeDirty(
                    chunkX - renderDistance - 2, mc.level.getMinSectionY(), chunkZ - renderDistance - 2,
                    chunkX + renderDistance + 2, mc.level.getMaxSectionY(), chunkZ + renderDistance + 2
            );

            if (mc.levelRenderer != null) {
                mc.levelRenderer.clearVisibleSections();
            }
        }
    }
}
