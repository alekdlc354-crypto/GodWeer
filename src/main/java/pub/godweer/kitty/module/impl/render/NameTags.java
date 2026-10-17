package pub.godweer.kitty.module.impl.render;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.BreezeWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import org.joml.Matrix3x2fStack;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.AttackEvent;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.kitty.sosiski.friend.FriendManager;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.MultiModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.Render3DUtil;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.auxiliary.render.renders.font.MsdfFont;
import pub.godweer.auxiliary.render.Render2DUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static pub.godweer.auxiliary.client.clients.Theme.*;

/**
 * Create by daun kvass
 */
public final class NameTags extends Module {

    private static final float SCALE = 1;
    private static final float PILL_HEIGHT = 30;
    private static final float RADIUS = 8;
    private static final float PADDING = 7;
    private static final float GAP = 6;

    private static final float BASE_HEAD_SIZE = 16;
    private static final float BIG_HEAD_SIZE = 20;

    private static final float ITEM_SIZE = 14;

    private static final float EQUIP_ITEM_SIZE = 18;
    private static final float EQUIP_VALUABLE_SIZE = 22;

    private static final float DIVIDER_HEIGHT = 12;
    private static final float TEXT_SIZE = 11;
    private static final float HEAD_OFFSET = 0.28f;

    private static final EquipmentSlot[] EQUIPMENT_ORDER = {
            EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND,
    };

    private record Donate(String name, int color) {}
    private record ItemStyle(int color, float textSize, float iconSize) {}

    private record LoggedOutPlayer(
            UUID uuid,
            String name,
            Identifier skin,
            Vec3 position,
            float height,
            long logOutTime,
            List<ItemStack> equipment,
            Donate donates,
            int health,
            float maxHealth,
            boolean isFriend
    ) {}

    private record CachedPlayer(
            UUID uuid,
            String name,
            Identifier skin,
            Vec3 position,
            float height,
            List<ItemStack> equipment,
            Donate donates,
            int health,
            float maxHealth,
            boolean isFriend,
            boolean isAlive
    ) {}

    private final Map<UUID, CachedPlayer> playerCache = new HashMap<>();
    private final Map<UUID, LoggedOutPlayer> loggedOutPlayers = new ConcurrentHashMap<>();

    private int targetId = -1;
    private long firstAttackTime = 0L;
    private long lastAttackTime = 0L;

    public final MultiModeSetting targets = multiMode(
            "Targets",
            List.of("Игроки", "Голые", "Друзья"),
            "Игроки", "Мобы", "Животные", "Голые", "Друзья", "Предметы", "Снаряды"
    );
    public final SliderSetting scale = slider("Размер", 1.0f, 0.5f, 1.5f, 0.05f);
    public final CheckBox highlightTarget = checkbox("Выделять таргет", true);
    public final CheckBox highlightValuables = checkbox("Выделять ценное", true);
    public final CheckBox stackItems = checkbox("Стакать предметы", true).visible(() -> targets.isEnabled("Предметы"));
    public final CheckBox logOut = checkbox("LogOut", true);
    public final CheckBox health = checkbox("Отображает хп", true);
    public final CheckBox items = checkbox("Предметы брони", true);
    public final ModeSetting itemsPos = mode("Позиция брони", "Под ногами", "Под ногами", "Сверху").visible(items::getValue);

    public NameTags() {
        super("NameTags", "Включаешь такой предметы и комп тако скыбыдышь", Category.RENDER);
    }

    @Override
    protected void onDisable() {
        loggedOutPlayers.clear();
        playerCache.clear();
        targetId = -1;
        firstAttackTime = 0L;
        lastAttackTime = 0L;
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (event.getTarget() instanceof LivingEntity living && living != mc.player) {
            long now = System.currentTimeMillis();

            if (this.targetId != living.getId() || (now - this.lastAttackTime) > 5000L) {
                this.firstAttackTime = now;
            }

            this.targetId = living.getId();
            this.lastAttackTime = now;
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        Minecraft mc = event.getClient();
        if (mc == null || mc.level == null || mc.player == null) {
            loggedOutPlayers.clear();
            playerCache.clear();
            return;
        }

        float tickDelta = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float guiScale = (float) mc.getWindow().getGuiScale();
        float unit = (SCALE * this.scale.getValue()) / (guiScale > 0 ? guiScale : 1.0F);
        double maxDistSq = 228 * 228;

        List<ItemEntity> itemEntities = new ArrayList<>();
        Set<UUID> currentFramePlayers = new HashSet<>();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) {
                continue;
            }
            if (entity.isRemoved()) {
                continue;
            }
            if (mc.player.distanceToSqr(entity) > maxDistSq) {
                continue;
            }

            if (entity instanceof ItemEntity itemEntity) {
                if (this.targets.isEnabled("Предметы") && !itemEntity.getItem().isEmpty()) {
                    itemEntities.add(itemEntity);
                }
                continue;
            }

            if (entity instanceof Projectile projectile) {
                if (this.targets.isEnabled("Снаряды")) {
                    drawProjectileTag(event, projectile, tickDelta, unit);
                }
                continue;
            }

            if (entity instanceof LivingEntity living) {
                if (living instanceof AbstractClientPlayer player && player != mc.player) {
                    currentFramePlayers.add(player.getUUID());
                    loggedOutPlayers.remove(player.getUUID());

                    boolean isFriend = FriendManager.getInstance().isFriend(player);
                    playerCache.put(player.getUUID(), new CachedPlayer(
                            player.getUUID(),
                            player.getGameProfile().name(),
                            player.getSkin().body().texturePath(),
                            player.position(),
                            player.getBbHeight(),
                            equipment(player),
                            donates(player),
                            (int) Math.ceil(player.getHealth() + player.getAbsorptionAmount()),
                            player.getMaxHealth(),
                            isFriend,
                            player.isAlive() && !player.isDeadOrDying()
                    ));
                }

                if (!living.isAlive() || living.isSpectator()) {
                    continue;
                }
                if (!isValidTarget(living)) {
                    continue;
                }

                drawTag(event, living, tickDelta, unit);
            }
        }

        if (this.logOut.getValue()) {
            Iterator<Map.Entry<UUID, CachedPlayer>> cacheIt = playerCache.entrySet().iterator();
            while (cacheIt.hasNext()) {
                Map.Entry<UUID, CachedPlayer> entry = cacheIt.next();
                UUID uuid = entry.getKey();
                CachedPlayer cached = entry.getValue();

                if (!currentFramePlayers.contains(uuid)) {
                    if (cached.isAlive && !loggedOutPlayers.containsKey(uuid)) {
                        loggedOutPlayers.put(uuid, new LoggedOutPlayer(
                                cached.uuid,
                                cached.name,
                                cached.skin,
                                cached.position,
                                cached.height,
                                System.currentTimeMillis(),
                                cached.equipment,
                                cached.donates,
                                cached.health,
                                cached.maxHealth,
                                cached.isFriend
                        ));
                    }
                    cacheIt.remove();
                }
            }

            renderLogOutTags(event, tickDelta, unit);
        } else {
            loggedOutPlayers.clear();
        }

        if (!itemEntities.isEmpty() && this.targets.isEnabled("Предметы")) {
            if (this.stackItems.getValue()) {
                renderClusteredItems(event, itemEntities, tickDelta, unit);
            } else {
                for (ItemEntity item : itemEntities) {
                    drawItemTag(event, item, tickDelta, unit);
                }
            }
        }
    }

    private void renderLogOutTags(Render2DEvent event, float tickDelta, float unit) {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, LoggedOutPlayer>> it = loggedOutPlayers.entrySet().iterator();

        while (it.hasNext()) {
            LoggedOutPlayer logged = it.next().getValue();
            long elapsed = now - logged.logOutTime;

            if (elapsed >= 120_000L) {
                it.remove();
                continue;
            }

            float alpha = 1.0F;
            if (elapsed > 115_000L) {
                alpha = Math.max(0.0F, 1.0F - (float) (elapsed - 115_000L) / 5000.0F);
            }

            int totalSec = (int) (elapsed / 1000L);
            String timerStr = String.format("%d:%02d", totalSec / 60, totalSec % 60);

            drawLoggedOutTag(event, logged, timerStr, alpha, unit);
        }
    }

    private void drawLoggedOutTag(Render2DEvent event, LoggedOutPlayer logged, String timerStr, float alpha, float unit) {
        Minecraft mc = event.getClient();
        Vec3 headPosition = logged.position.add(0.0D, logged.height + HEAD_OFFSET, 0.0D);

        Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, headPosition);
        if (anchor == null) return;

        MsdfFont font = Fonts.SF_MEDIUM;
        float textSize = TEXT_SIZE * unit;
        float headSize = (highlightValuables.getValue() ? BIG_HEAD_SIZE : BASE_HEAD_SIZE) * unit;
        float gap = GAP * unit;
        float dividerWidth = Math.max(1.0F, 1.0F * unit);

        String healthText = this.health.getValue() ? logged.health + " HP" : null;
        List<ItemStack> equipment = this.items.getValue() ? logged.equipment : List.of();

        float friendTagWidth = logged.isFriend ? font.getWidth("[F] ", textSize) : 0.0F;
        float nameWidth = font.getWidth(logged.name, textSize);
        float donatWidth = (logged.donates != null) ? font.getWidth(logged.donates.name(), textSize) : 0.0F;
        float hpWidth = (healthText != null) ? font.getWidth(healthText, textSize) : 0.0F;
        float timerWidth = font.getWidth("[" + timerStr + "]", textSize);

        float width = PADDING * unit * 2.0F + headSize + gap;
        if (logged.isFriend) width += friendTagWidth;
        if (logged.donates != null) width += donatWidth + gap + dividerWidth + gap;
        width += nameWidth;
        if (healthText != null) width += gap + dividerWidth + gap + hpWidth;
        width += gap + dividerWidth + gap + timerWidth;

        float pillHeight = (highlightValuables.getValue() ? (PILL_HEIGHT + 4.0F) : PILL_HEIGHT) * unit;
        float pillX = anchor.x() - width / 2.0F;
        float pillY = anchor.y() - pillHeight;
        float centerY = pillY + pillHeight / 2.0F;
        float textY = font.centeredTextY(centerY, textSize);

        int baseBg = logged.isFriend
                ? ColorUtil.rgba(20, 55, 28, (int) (Theme.getPanelAlpha() * 255))
                : ColorUtil.rgba(28, 16, 18, (int) (Theme.getPanelAlpha() * 255));
        int backgroundColor = ColorUtil.multiplyAlpha(baseBg, alpha);

        Render2D.drawBlur(pillX, pillY, width, pillHeight, RADIUS * unit, 1, backgroundColor, alpha);

        float cursor = pillX + PADDING * unit;

        if (logged.skin != null) {
            Render2D.drawHead(logged.skin, cursor, centerY - headSize / 2.0F, headSize, headSize * 0.25F);
            cursor += headSize + gap;
        }

        if (logged.isFriend) {
            Fonts.drawString(font, "[F] ", cursor, textY, textSize, ColorUtil.rgba(85, 255, 85, (int) (255 * alpha)));
            cursor += friendTagWidth;
        }

        if (logged.donates != null) {
            Fonts.drawString(font, logged.donates.name(), cursor, textY, textSize, ColorUtil.multiplyAlpha(logged.donates.color(), alpha));
            cursor += donatWidth;
            cursor = drawDivider(cursor, centerY, dividerWidth, gap, unit, alpha);
        }

        Fonts.drawString(font, logged.name, cursor, textY, textSize, ColorUtil.rgba(255, 255, 255, (int) (255 * alpha)));
        cursor += nameWidth;

        if (healthText != null) {
            cursor = drawDivider(cursor, centerY, dividerWidth, gap, unit, alpha);
            float maxHp = logged.maxHealth > 0 ? logged.maxHealth : 20.0F;
            float hpPercent = Math.max(0.0F, Math.min(1.0F, (float) logged.health / maxHp));
            int hpColor = ColorUtil.rgba((int) ((1.0F - hpPercent) * 255), (int) (hpPercent * 255), 70, (int) (255 * alpha));
            Fonts.drawString(font, healthText, cursor, textY, textSize, hpColor);
            cursor += hpWidth;
        }

        cursor = drawDivider(cursor, centerY, dividerWidth, gap, unit, alpha);
        int timerColor = ColorUtil.rgba(255, 110, 110, (int) (255 * alpha));
        Fonts.drawString(font, "[" + timerStr + "]", cursor, textY, textSize, timerColor);

        if (!equipment.isEmpty()) {
            if (itemsPos.getValue().equals("Под ногами")) {
                Vec3 feetPos = logged.position.subtract(0.0D, 0.05D, 0.0D);
                Render3DUtil.ScreenPoint feetAnchor = Render3DUtil.projectToScreen(mc, feetPos);
                if (feetAnchor != null) {
                    drawEquipment(event, equipment, feetAnchor.x(), feetAnchor.y() + 4.0F * unit, gap, unit);
                }
            } else {
                float maxItemHeight = (highlightValuables.getValue() ? EQUIP_VALUABLE_SIZE : EQUIP_ITEM_SIZE) * unit;
                float equipY = pillY - maxItemHeight - 4.0F * unit;
                drawEquipment(event, equipment, anchor.x(), equipY, gap, unit);
            }
        }
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity instanceof Player player) {
            boolean isFriend = FriendManager.getInstance().isFriend(player);
            if (isFriend) {
                return this.targets.isEnabled("Друзья");
            }

            boolean naked = isNaked(player);
            if (naked) {
                return this.targets.isEnabled("Голые");
            } else {
                return this.targets.isEnabled("Игроки");
            }
        }
        if (entity instanceof Monster || entity instanceof Enemy) {
            return this.targets.isEnabled("Мобы");
        }
        if (entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature) {
            return this.targets.isEnabled("Животные");
        }
        return false;
    }

    private static boolean isNaked(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ItemStyle getItemStyle(ItemStack stack, float baseTextSize, float baseItemSize, float unit) {
        if (stack == null || stack.isEmpty()) {
            return new ItemStyle(ColorUtil.rgba(255, 255, 255, 255), baseTextSize, baseItemSize);
        }

        boolean isHead = stack.is(Items.PLAYER_HEAD);
        boolean isTotem = stack.is(Items.TOTEM_OF_UNDYING) && (stack.isEnchanted() || stack.hasFoil());
        String name = stack.getHoverName().getString().toLowerCase();
        boolean isTalisman = name.contains("талисман") || name.contains("талик") || name.contains("сфера") || name.contains("шар") || name.contains("шарик");

        if (highlightValuables.getValue()) {
            if (isTalisman) {
                return new ItemStyle(ColorUtil.rgba(255, 85, 85, 255), baseTextSize * 1.2F, baseItemSize * 1.3F);
            } else if (isHead) {
                return new ItemStyle(ColorUtil.rgba(255, 255, 85, 255), baseTextSize * 1.2F, baseItemSize * 1.3F);
            } else if (isTotem) {
                return new ItemStyle(ColorUtil.rgba(255, 215, 0, 255), baseTextSize * 1.2F, baseItemSize * 1.3F);
            }
        }

        return new ItemStyle(ColorUtil.rgba(255, 255, 255, 255), baseTextSize, baseItemSize);
    }

    private boolean isValuableItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(Items.TOTEM_OF_UNDYING) && (stack.isEnchanted() || stack.hasFoil())) return true;
        if (stack.is(Items.PLAYER_HEAD)) return true;
        String name = stack.getHoverName().getString().toLowerCase();
        return name.contains("талисман") || name.contains("сфера") || name.contains("шар") || name.contains("шарик") || name.contains("талик");
    }

    private void drawTag(Render2DEvent event, LivingEntity entity, float tickDelta, float baseUnit) {
        Minecraft mc = event.getClient();

        float targetFactor = 0.0F;
        if (this.highlightTarget.getValue() && entity.getId() == this.targetId) {
            long currentTime = System.currentTimeMillis();
            long elapsedSinceLast = currentTime - this.lastAttackTime;

            if (elapsedSinceLast <= 5000L) {
                long elapsedSinceFirst = currentTime - this.firstAttackTime;

                if (elapsedSinceFirst < 200L) {
                    targetFactor = elapsedSinceFirst / 200.0F;
                } else if (elapsedSinceLast > 4000L) {
                    targetFactor = 1.0F - ((elapsedSinceLast - 4000.0F) / 1000.0F);
                } else {
                    targetFactor = 1.0F;
                }

                targetFactor = Math.max(0.0F, Math.min(1.0F, targetFactor));
            }
        }

        float unit = baseUnit * (1.0F + 0.15F * targetFactor);

        Vec3 headPosition = Render3DUtil.interpolatedPosition(entity, tickDelta)
                .add(0.0D, entity.getBbHeight() + HEAD_OFFSET, 0.0D);

        Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, headPosition);
        if (anchor == null) {
            return;
        }

        MsdfFont font = Fonts.SF_MEDIUM;
        float textSize = TEXT_SIZE * unit;
        boolean hasHead = entity instanceof AbstractClientPlayer;

        float headSize = hasHead ? (highlightValuables.getValue() ? BIG_HEAD_SIZE : BASE_HEAD_SIZE) * unit : 0.0F;
        float gap = GAP * unit;
        float dividerWidth = Math.max(1.0F, 1.0F * unit);

        boolean isFriend = entity instanceof Player player && FriendManager.getInstance().isFriend(player);

        String name = (entity instanceof AbstractClientPlayer player)
                ? player.getGameProfile().name()
                : entity.getDisplayName().getString();

        Donate donat = (entity instanceof AbstractClientPlayer player)
                ? donates(player)
                : null;

        int currentHp = (int) Math.ceil(entity.getHealth() + entity.getAbsorptionAmount());
        String healthText = this.health.getValue() ? currentHp + " HP" : null;
        List<ItemStack> equipment = this.items.getValue() ? equipment(entity) : List.of();

        float friendTagWidth = isFriend ? font.getWidth("[F] ", textSize) : 0.0F;
        float nameWidth = font.getWidth(name, textSize);
        float donatWidth = (donat != null) ? font.getWidth(donat.name(), textSize) : 0.0F;
        float hpWidth = (healthText != null) ? font.getWidth(healthText, textSize) : 0.0F;

        float width = PADDING * unit * 2.0F;
        if (hasHead) width += headSize + gap;
        if (isFriend) width += friendTagWidth;
        if (donat != null) width += donatWidth + gap + dividerWidth + gap;
        width += nameWidth;
        if (healthText != null) width += gap + dividerWidth + gap + hpWidth;

        float pillHeight = (hasHead && highlightValuables.getValue() ? (PILL_HEIGHT + 4.0F) : PILL_HEIGHT) * unit;
        float pillX = anchor.x() - width / 2.0F;
        float pillY = anchor.y() - pillHeight;
        float centerY = pillY + pillHeight / 2.0F;
        float textY = font.centeredTextY(centerY, textSize);

        int baseBg = isFriend
                ? ColorUtil.rgba(20, 55, 28, (int) (Theme.getPanelAlpha() * 255))
                : BG_COLOR;

        int targetBgColor = ColorUtil.rgba(60, 18, 22, (int) (Theme.getPanelAlpha() * 255));
        int backgroundColor = interpolateColor(baseBg, targetBgColor, targetFactor);

        Render2D.drawBlur(pillX, pillY, width, pillHeight, RADIUS * unit, 1, backgroundColor, 1.0F);

        float cursor = pillX + PADDING * unit;

        if (hasHead) {
            drawHead((AbstractClientPlayer) entity, cursor, centerY - headSize / 2.0F, headSize);
            cursor += headSize + gap;
        }

        if (isFriend) {
            Fonts.drawString(font, "[F] ", cursor, textY, textSize, ColorUtil.rgba(85, 255, 85, 255));
            cursor += friendTagWidth;
        }

        if (donat != null) {
            Fonts.drawString(font, donat.name(), cursor, textY, textSize, donat.color());
            cursor += donatWidth;
            cursor = drawDivider(cursor, centerY, dividerWidth, gap, unit, 1.0F);
        }

        Fonts.drawString(font, name, cursor, textY, textSize, ColorUtil.rgba(255, 255, 255, 255));
        cursor += nameWidth;

        if (healthText != null) {
            cursor = drawDivider(cursor, centerY, dividerWidth, gap, unit, 1.0F);
            float maxHp = entity.getMaxHealth() > 0 ? entity.getMaxHealth() : 20.0F;
            float hpPercent = Math.max(0.0F, Math.min(1.0F, entity.getHealth() / maxHp));
            int hpColor = ColorUtil.rgba((int) ((1.0F - hpPercent) * 255), (int) (hpPercent * 255), 70, 255);
            Fonts.drawString(font, healthText, cursor, textY, textSize, hpColor);
        }

        if (!equipment.isEmpty()) {
            if (itemsPos.getValue().equals("Под ногами")) {
                Vec3 feetPos = Render3DUtil.interpolatedPosition(entity, tickDelta).subtract(0.0D, 0.05D, 0.0D);
                Render3DUtil.ScreenPoint feetAnchor = Render3DUtil.projectToScreen(mc, feetPos);
                if (feetAnchor != null) {
                    drawEquipment(event, equipment, feetAnchor.x(), feetAnchor.y() + 4.0F * unit, gap, unit);
                }
            } else {
                float maxItemHeight = (highlightValuables.getValue() ? EQUIP_VALUABLE_SIZE : EQUIP_ITEM_SIZE) * unit;
                float equipY = pillY - maxItemHeight - 4.0F * unit;
                drawEquipment(event, equipment, anchor.x(), equipY, gap, unit);
            }
        }
    }

    private void drawEquipment(Render2DEvent event, List<ItemStack> equipment, float anchorX, float y, float gap, float unit) {
        var extractor = event.getGuiGraphicsExtractor();
        if (extractor == null || equipment.isEmpty()) return;

        float totalWidth = 0.0F;
        float maxHeight = 0.0F;

        for (int i = 0; i < equipment.size(); i++) {
            ItemStack stack = equipment.get(i);
            boolean valuable = highlightValuables.getValue() && isValuableItem(stack);
            float currentItemSize = (valuable ? EQUIP_VALUABLE_SIZE : EQUIP_ITEM_SIZE) * unit;
            totalWidth += currentItemSize;
            if (i < equipment.size() - 1) {
                totalWidth += gap * 0.5F;
            }
            if (currentItemSize > maxHeight) {
                maxHeight = currentItemSize;
            }
        }

        float startX = anchorX - totalWidth / 2.0F;

        Render2D.drawBlur(startX - 4.0F * unit, y - 2.0F * unit, totalWidth + 8.0F * unit, maxHeight + 4.0F * unit, 4.0F * unit, 1, BG_COLOR, 1.0F);
        Render2DUtil.flush();

        Matrix3x2fStack pose = extractor.pose();
        float cursor = startX;

        for (ItemStack stack : equipment) {
            boolean valuable = highlightValuables.getValue() && isValuableItem(stack);
            float currentItemSize = (valuable ? EQUIP_VALUABLE_SIZE : EQUIP_ITEM_SIZE) * unit;
            float itemScale = currentItemSize / 16.0F;
            float itemCenterY = y + (maxHeight - currentItemSize) / 2.0F;

            pose.pushMatrix();
            pose.translate(cursor, itemCenterY);
            pose.scale(itemScale, itemScale);
            extractor.item(stack, 0, 0);
            pose.popMatrix();

            cursor += currentItemSize + (gap * 0.5F);
        }
    }

    private void renderClusteredItems(Render2DEvent event, List<ItemEntity> items, float tickDelta, float unit) {
        List<List<ItemEntity>> clusters = new ArrayList<>();
        boolean[] visited = new boolean[items.size()];
        double maxDistSq = 3.0 * 3.0;

        for (int i = 0; i < items.size(); i++) {
            if (visited[i]) continue;
            List<ItemEntity> cluster = new ArrayList<>();
            ItemEntity current = items.get(i);
            cluster.add(current);
            visited[i] = true;

            for (int j = i + 1; j < items.size(); j++) {
                if (!visited[j]) {
                    ItemEntity other = items.get(j);
                    if (current.distanceToSqr(other) <= maxDistSq) {
                        cluster.add(other);
                        visited[j] = true;
                    }
                }
            }
            clusters.add(cluster);
        }

        for (List<ItemEntity> cluster : clusters) {
            if (cluster.size() == 1) {
                drawItemTag(event, cluster.get(0), tickDelta, unit);
            } else {
                drawClusteredItemTag(event, cluster, tickDelta, unit);
            }
        }
    }

    private void drawClusteredItemTag(Render2DEvent event, List<ItemEntity> cluster, float tickDelta, float unit) {
        Minecraft mc = event.getClient();

        double avgX = 0, avgY = 0, avgZ = 0;
        for (ItemEntity e : cluster) {
            Vec3 pos = Render3DUtil.interpolatedPosition(e, tickDelta);
            avgX += pos.x;
            avgY += pos.y;
            avgZ += pos.z;
        }
        avgX /= cluster.size();
        avgY /= cluster.size();
        avgZ /= cluster.size();

        Vec3 position = new Vec3(avgX, avgY + 0.35D, avgZ);
        Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, position);
        if (anchor == null) return;

        MsdfFont font = Fonts.SF_MEDIUM;
        float baseTextSize = TEXT_SIZE * unit;
        float baseItemSize = ITEM_SIZE * unit;
        float gap = GAP * unit;
        float rowGap = 3.0F * unit;

        float maxRowWidth = 0.0F;
        float totalHeight = PADDING * unit * 2.0F;

        for (int i = 0; i < cluster.size(); i++) {
            ItemStack stack = cluster.get(i).getItem();
            ItemStyle style = getItemStyle(stack, baseTextSize, baseItemSize, unit);

            String countStr = stack.getCount() > 1 ? ("x" + stack.getCount() + " ") : "";
            String itemName = countStr + stack.getHoverName().getString();
            float textWidth = font.getWidth(itemName, style.textSize());

            float rowWidth = style.iconSize() + gap + textWidth;
            if (rowWidth > maxRowWidth) {
                maxRowWidth = rowWidth;
            }

            float rowH = Math.max(style.iconSize(), style.textSize()) + 2.0F * unit;
            totalHeight += rowH;
            if (i < cluster.size() - 1) {
                totalHeight += rowGap;
            }
        }

        float pillWidth = PADDING * unit * 2.0F + maxRowWidth;
        float pillX = anchor.x() - pillWidth / 2.0F;
        float pillY = anchor.y() - totalHeight;

        Render2D.drawBlur(pillX, pillY, pillWidth, totalHeight, RADIUS * unit, 1, BG_COLOR, 1.0F);

        float cursorY = pillY + PADDING * unit;

        for (int i = 0; i < cluster.size(); i++) {
            ItemStack stack = cluster.get(i).getItem();
            ItemStyle style = getItemStyle(stack, baseTextSize, baseItemSize, unit);

            String countStr = stack.getCount() > 1 ? ("x" + stack.getCount() + " ") : "";
            String itemName = countStr + stack.getHoverName().getString();

            float rowH = Math.max(style.iconSize(), style.textSize()) + 2.0F * unit;
            float centerY = cursorY + rowH / 2.0F;

            var extractor = event.getGuiGraphicsExtractor();
            if (extractor != null) {
                Render2DUtil.flush();
                Matrix3x2fStack pose = extractor.pose();
                float itemScale = style.iconSize() / 16.0F;
                pose.pushMatrix();
                pose.translate(pillX + PADDING * unit, centerY - style.iconSize() / 2.0F);
                pose.scale(itemScale, itemScale);
                extractor.item(stack, 0, 0);
                pose.popMatrix();
            }

            float textX = pillX + PADDING * unit + style.iconSize() + gap;
            float textY = font.centeredTextY(centerY, style.textSize());
            Fonts.drawString(font, itemName, textX, textY, style.textSize(), style.color());

            cursorY += rowH + rowGap;
        }
    }

    private void drawItemTag(Render2DEvent event, ItemEntity entity, float tickDelta, float unit) {
        Minecraft mc = event.getClient();
        Vec3 position = Render3DUtil.interpolatedPosition(entity, tickDelta)
                .add(0.0D, entity.getBbHeight() + 0.25D, 0.0D);

        Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, position);
        if (anchor == null) return;

        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) return;

        ItemStyle style = getItemStyle(stack, TEXT_SIZE * unit, ITEM_SIZE * unit, unit);
        MsdfFont font = Fonts.SF_MEDIUM;
        float gap = GAP * unit;

        String countStr = stack.getCount() > 1 ? ("x" + stack.getCount() + " ") : "";
        String itemName = countStr + stack.getHoverName().getString();

        float textWidth = font.getWidth(itemName, style.textSize());
        float width = PADDING * unit + style.iconSize() + gap + textWidth + PADDING * unit;
        float pillHeight = Math.max(PILL_HEIGHT - 6.0F, style.iconSize() + 6.0F) * unit;
        float pillX = anchor.x() - width / 2.0F;
        float pillY = anchor.y() - pillHeight;
        float centerY = pillY + pillHeight / 2.0F;
        float textY = font.centeredTextY(centerY, style.textSize());

        Render2D.drawBlur(pillX, pillY, width, pillHeight, RADIUS * unit, 1, BG_COLOR, 1.0F);

        var extractor = event.getGuiGraphicsExtractor();
        if (extractor != null) {
            Render2DUtil.flush();
            Matrix3x2fStack pose = extractor.pose();
            float itemScale = style.iconSize() / 16.0F;
            pose.pushMatrix();
            pose.translate(pillX + PADDING * unit, centerY - style.iconSize() / 2.0F);
            pose.scale(itemScale, itemScale);
            extractor.item(stack, 0, 0);
            pose.popMatrix();
        }

        float textX = pillX + PADDING * unit + style.iconSize() + gap;
        Fonts.drawString(font, itemName, textX, textY, style.textSize(), style.color());
    }

    private void drawProjectileTag(Render2DEvent event, Projectile projectile, float tickDelta, float unit) {
        Minecraft mc = event.getClient();
        Vec3 position = Render3DUtil.interpolatedPosition(projectile, tickDelta)
                .add(0.0D, projectile.getBbHeight() + 0.25D, 0.0D);

        Render3DUtil.ScreenPoint anchor = Render3DUtil.projectToScreen(mc, position);
        if (anchor == null) return;

        ItemStack stack = getProjectileItemStack(projectile);
        String name = projectile.getDisplayName().getString();
        if (stack != null && !stack.isEmpty()) {
            name = stack.getHoverName().getString();
        }

        MsdfFont font = Fonts.SF_MEDIUM;
        float textSize = TEXT_SIZE * unit;
        float itemSize = ITEM_SIZE * unit;
        float gap = GAP * unit;

        float textWidth = font.getWidth(name, textSize);
        boolean hasItem = stack != null && !stack.isEmpty();
        float width = PADDING * unit + (hasItem ? (itemSize + gap) : 0.0F) + textWidth + PADDING * unit;
        float pillHeight = (PILL_HEIGHT - 6.0F) * unit;
        float pillX = anchor.x() - width / 2.0F;
        float pillY = anchor.y() - pillHeight;
        float centerY = pillY + pillHeight / 2.0F;
        float textY = font.centeredTextY(centerY, textSize);

        Render2D.drawBlur(pillX, pillY, width, pillHeight, RADIUS * unit, 1, BG_COLOR, 1.0F);

        float cursor = pillX + PADDING * unit;

        if (hasItem) {
            var extractor = event.getGuiGraphicsExtractor();
            if (extractor != null) {
                Render2DUtil.flush();
                Matrix3x2fStack pose = extractor.pose();
                float itemScale = itemSize / 16.0F;
                pose.pushMatrix();
                pose.translate(cursor, centerY - itemSize / 2.0F);
                pose.scale(itemScale, itemScale);
                extractor.item(stack, 0, 0);
                pose.popMatrix();
            }
            cursor += itemSize + gap;
        }

        Fonts.drawString(font, name, cursor, textY, textSize, ColorUtil.rgba(255, 255, 255, 255));
    }

    private static int interpolateColor(int color1, int color2, float factor) {
        if (factor <= 0.0F) return color1;
        if (factor >= 1.0F) return color2;

        int a1 = (color1 >> 24) & 0xFF;
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int a2 = (color2 >> 24) & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int a = (int) (a1 + (a2 - a1) * factor);
        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static ItemStack getProjectileItemStack(Projectile projectile) {
        if (projectile instanceof ThrownEnderpearl) {
            return Items.ENDER_PEARL.getDefaultInstance();
        } else if (projectile instanceof ThrownTrident) {
            return Items.TRIDENT.getDefaultInstance();
        } else if (projectile instanceof SpectralArrow) {
            return Items.SPECTRAL_ARROW.getDefaultInstance();
        } else if (projectile instanceof AbstractArrow) {
            return Items.ARROW.getDefaultInstance();
        } else if (projectile instanceof ThrownSplashPotion potion) {
            return potion.getItem();
        } else if (projectile instanceof ThrownExperienceBottle) {
            return Items.EXPERIENCE_BOTTLE.getDefaultInstance();
        } else if (projectile instanceof Fireball || projectile instanceof SmallFireball) {
            return Items.FIRE_CHARGE.getDefaultInstance();
        } else if (projectile instanceof WindCharge || projectile instanceof BreezeWindCharge) {
            return Items.WIND_CHARGE.getDefaultInstance();
        }
        return ItemStack.EMPTY;
    }

    private static void drawHead(AbstractClientPlayer player, float x, float y, float size) {
        Identifier skin = player.getSkin().body().texturePath();
        float radius = size * 0.25F;
        Render2D.drawHead(skin, x, y, size, radius);
    }

    private float drawDivider(float cursor, float centerY, float dividerWidth, float gap, float unit, float alpha) {
        cursor += gap;
        float h = DIVIDER_HEIGHT * unit;
        Render2D.drawRoundedRect(cursor, centerY - h / 2.0F, dividerWidth, h, 0.5F * unit, ColorUtil.multiplyAlpha(DIVIDER_COLOR, alpha));
        return cursor + dividerWidth + gap;
    }

    private static List<ItemStack> equipment(LivingEntity entity) {
        List<ItemStack> stacks = new ArrayList<>(EQUIPMENT_ORDER.length);
        for (EquipmentSlot slot : EQUIPMENT_ORDER) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                stacks.add(stack.copy());
            }
        }
        return stacks;
    }

    private static Donate donates(AbstractClientPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team == null) return null;
        Component prefix = team.getPlayerPrefix();
        if (prefix == null) return null;

        String raw = prefix.getString();
        String name = cleanName(raw);
        if (name.isEmpty()) return null;

        Integer color = extractColor(prefix);
        if (color == null) color = legacyColor(raw);

        int rgb = color != null
                ? ColorUtil.rgba((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, 255)
                : Theme.getAccentColor();
        return new Donate(name, rgb);
    }

    private static String cleanName(String text) {
        if (text == null || text.isEmpty()) return "";

        String noColor = text.replaceAll("(?i)§[0-9a-fk-orx]", "").replaceAll("§.", "");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < noColor.length(); i++) {
            char c = noColor.charAt(i);
            c = normalizeSmallCapChar(c);

            if (isRenderableChar(c)) {
                sb.append(c);
            }
        }

        return sb.toString().replaceAll("\\s+", " ").trim();
    }

    private static char normalizeSmallCapChar(char c) {
        return switch (c) {
            case 'ᴀ' -> 'A';
            case 'ʙ' -> 'B';
            case 'ᴄ' -> 'C';
            case 'ᴅ' -> 'D';
            case 'ᴇ' -> 'E';
            case 'ғ' -> 'F';
            case 'ɢ' -> 'G';
            case 'ʜ' -> 'H';
            case 'ɪ' -> 'I';
            case 'ᴊ' -> 'J';
            case 'ᴋ' -> 'K';
            case 'ʟ' -> 'L';
            case 'ᴍ' -> 'M';
            case 'ɴ' -> 'N';
            case 'ᴏ' -> 'O';
            case 'ᴘ' -> 'P';
            case 'ǫ', 'ϙ' -> 'Q';
            case 'ʀ' -> 'R';
            case 'ꜱ', 's' -> 'S';
            case 'ᴛ' -> 'T';
            case 'ᴜ' -> 'U';
            case 'ᴠ' -> 'V';
            case 'ᴡ' -> 'W';
            case 'ʏ' -> 'Y';
            case 'ᴢ' -> 'Z';
            case 'а' -> 'А';
            case 'б' -> 'Б';
            case 'в' -> 'В';
            case 'г' -> 'Г';
            case 'д' -> 'Д';
            case 'е' -> 'Е';
            case 'ж' -> 'Ж';
            case 'з' -> 'З';
            case 'и' -> 'И';
            case 'й' -> 'Й';
            case 'к' -> 'К';
            case 'л' -> 'Л';
            case 'м' -> 'М';
            case 'н' -> 'Н';
            case 'о' -> 'О';
            case 'п' -> 'П';
            case 'р' -> 'Р';
            case 'с' -> 'С';
            case 'т' -> 'Т';
            case 'у' -> 'У';
            case 'ф' -> 'Ф';
            case 'х' -> 'Х';
            case 'ц' -> 'Ц';
            case 'ч' -> 'Ч';
            case 'ш' -> 'Ш';
            case 'щ' -> 'Щ';
            case 'ъ' -> 'Ъ';
            case 'ы' -> 'Ы';
            case 'ь' -> 'Ь';
            case 'э' -> 'Э';
            case 'ю' -> 'Ю';
            case 'я' -> 'Я';
            default -> c;
        };
    }

    private static boolean isRenderableChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || (c >= 'а' && c <= 'я') || (c >= 'А' && c <= 'Я') || c == 'ё' || c == 'Ё'
                || c == ' ' || c == '[' || c == ']' || c == '(' || c == ')' || c == '{' || c == '}'
                || c == '-' || c == '+' || c == '_' || c == '|' || c == ':' || c == '.' || c == '!'
                || c == '?' || c == '*' || c == '#' || c == '<' || c == '>';
    }

    private static Integer legacyColor(String text) {
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) != '§') continue;
            ChatFormatting formatting = ChatFormatting.getByCode(text.charAt(i + 1));
            if (formatting == null) continue;
            TextColor color = TextColor.fromLegacyFormat(formatting);
            if (color != null) return color.getValue();
        }
        return null;
    }

    private static Integer extractColor(Component component) {
        if (component == null) return null;
        TextColor color = component.getStyle().getColor();
        if (color != null) return color.getValue();
        for (Component sibling : component.getSiblings()) {
            Integer siblingColor = extractColor(sibling);
            if (siblingColor != null) return siblingColor;
        }
        return null;
    }

}