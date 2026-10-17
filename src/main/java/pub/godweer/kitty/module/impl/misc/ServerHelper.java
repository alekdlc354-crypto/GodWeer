package pub.godweer.kitty.module.impl.misc;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemLore;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.KeyboardInputEvent;
import pub.godweer.enjoy.event.list.MouseInputEvent;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.kitty.display.mainmenu.PanelRefractions;
import pub.godweer.kitty.display.mainmenu.popup.ServerHelperModal;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.impl.combat.AutoTotem;
import pub.godweer.kitty.module.settings.impl.BindSetting;
import pub.godweer.kitty.module.settings.impl.ButtonSetting;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.auxiliary.player.InventoryUtil;
import pub.godweer.auxiliary.player.MoveBlockUtility;

import java.util.*;

/**
 * Create by daun kvass
 */
public final class ServerHelper extends Module {

    private static final int HOTBAR_SIZE = 9;

    public final ModeSetting server = mode("Server", "FunTime", "FunTime", "HolyWorld");
    public final ModeSetting useMode = mode("Use Mode", "Packet", "Packet", "Normal");
    public final CheckBox swapBack = checkbox("Swap Back", true);

    public final ButtonSetting openMenuBtn = button("Настроить бинды", () -> {
        PanelRefractions.openModal(new ServerHelperModal(this));
    });
    public final BindSetting ftDisorient = new BindSetting("Дезориентация");
    public final BindSetting ftDust      = new BindSetting("Явная пыль");
    public final BindSetting ftPlast     = new BindSetting("Пласт");
    public final BindSetting ftSnow      = new BindSetting("Снежок (ФТ)");
    public final BindSetting ftGodAura   = new BindSetting("Божья Аура");
    public final BindSetting ftTrap      = new BindSetting("Трапка (ФТ)");
    public final BindSetting ftFireSwirl = new BindSetting("Огненный смерч");
    public final BindSetting ftHolyWater = new BindSetting("Святая вода");
    public final BindSetting ftAnger     = new BindSetting("Зелье Гнева");
    public final BindSetting ftPaladin   = new BindSetting("Зелье Паладина");
    public final BindSetting ftAssassin  = new BindSetting("Зелье Ассасина");
    public final BindSetting ftRadiation = new BindSetting("Зелье Радиации");
    public final BindSetting ftSleep     = new BindSetting("Снотворное");
    public final BindSetting ftClapper   = new BindSetting("Хлопушка");
    public final BindSetting hwExplosiveTrap = new BindSetting("Взрывная трапка");
    public final BindSetting hwNormalTrap    = new BindSetting("Обычная трапка");
    public final BindSetting hwStun          = new BindSetting("Стан");
    public final BindSetting hwSnow          = new BindSetting("Снежок (ХВ)");
    public final BindSetting hwBomb          = new BindSetting("Взрывная штучка");

    private final Map<String, ItemInfo> itemConfigs = new HashMap<>();

    private enum State {
        IDLE,
        STOP_SPRINT,
        SWAP_AND_USE,
        COOLDOWN
    }

    private State state = State.IDLE;
    private ItemInfo pendingInfo = null;
    private int cooldownTicks = 0;

    public ServerHelper() {
        super("ServerHelper", "Использование предметов по биндам", Category.PLAYER);
        setupConfigs();
    }

    private static class EffectReq {
        final Holder<MobEffect> effect;
        final int minAmp;

        EffectReq(Holder<MobEffect> effect, int minAmp) {
            this.effect = effect;
            this.minAmp = minAmp;
        }
    }

    private static class ItemInfo {
        final Item item;
        final String displayName;
        final List<String> loreKeywords;
        final String nameFallback;
        final List<EffectReq> effectReqs;

        ItemInfo(Item item, String displayName, List<String> loreKeywords, String nameFallback, List<EffectReq> effectReqs) {
            this.item = item;
            this.displayName = displayName;
            this.loreKeywords = loreKeywords;
            this.nameFallback = nameFallback;
            this.effectReqs = effectReqs;
        }
    }

    private void setupConfigs() {
        itemConfigs.put("ft_disorient", new ItemInfo(Items.ENDER_EYE, "Дезориентация", List.of("чем ближе цель"), "дезориентация", null));
        itemConfigs.put("ft_dust", new ItemInfo(Items.SUGAR, "Явная пыль", List.of("световая вспышка", "свечение", "слепота"), "явная пыль", null));
        itemConfigs.put("ft_plast", new ItemInfo(Items.DRIED_KELP, "Пласт", List.of("нерушимая стена"), "пласт", null));
        itemConfigs.put("ft_snow", new ItemInfo(Items.SNOWBALL, "Снежок", List.of("ледяная сфера"), "снежок заморозка", null));
        itemConfigs.put("ft_god_aura", new ItemInfo(Items.PHANTOM_MEMBRANE, "Божья Аура", List.of("божественная аура"), "божья аура", null));
        itemConfigs.put("ft_trap", new ItemInfo(Items.NETHERITE_SCRAP, "Трапка", List.of("нерушимая клетка"), "трапка", null));
        itemConfigs.put("ft_fire_swirl", new ItemInfo(Items.FIRE_CHARGE, "Огненный смерч", List.of("огненная волна"), "огненный смерч", null));
        itemConfigs.put("ft_holy_water", new ItemInfo(Items.SPLASH_POTION, "Святая вода", null, "святая вода", List.of(new EffectReq(MobEffects.REGENERATION, 1), new EffectReq(MobEffects.INVISIBILITY, 0))));
        itemConfigs.put("ft_anger", new ItemInfo(Items.SPLASH_POTION, "Зелье Гнева", null, "зелье гнева", List.of(new EffectReq(MobEffects.STRENGTH, 3), new EffectReq(MobEffects.SLOWNESS, 2))));
        itemConfigs.put("ft_paladin", new ItemInfo(Items.SPLASH_POTION, "Зелье Паладина", null, "зелье паладина", List.of(new EffectReq(MobEffects.RESISTANCE, 0), new EffectReq(MobEffects.FIRE_RESISTANCE, 0))));
        itemConfigs.put("ft_assassin", new ItemInfo(Items.SPLASH_POTION, "Зелье Ассасина", null, "зелье ассасина", List.of(new EffectReq(MobEffects.STRENGTH, 2), new EffectReq(MobEffects.SPEED, 1))));
        itemConfigs.put("ft_radiation", new ItemInfo(Items.SPLASH_POTION, "Зелье Радиации", null, "зелье радиации", List.of(new EffectReq(MobEffects.POISON, 0), new EffectReq(MobEffects.WITHER, 0))));
        itemConfigs.put("ft_sleep", new ItemInfo(Items.SPLASH_POTION, "Снотворное", null, "снотворное", List.of(new EffectReq(MobEffects.WEAKNESS, 0), new EffectReq(MobEffects.MINING_FATIGUE, 0))));
        itemConfigs.put("ft_clapper", new ItemInfo(Items.SPLASH_POTION, "Хлопушка", null, "хлопушка", List.of(new EffectReq(MobEffects.SLOWNESS, 8), new EffectReq(MobEffects.BLINDNESS, 8))));
        itemConfigs.put("hw_exp_trap", new ItemInfo(Items.PRISMARINE_SHARD, "Взрывная трапка", null, "взрывная трапка", null));
        itemConfigs.put("hw_norm_trap", new ItemInfo(Items.POPPED_CHORUS_FRUIT, "Обычная трапка", null, "трапка", null));
        itemConfigs.put("hw_stun", new ItemInfo(Items.NETHER_STAR, "Стан", null, "стан", null));
        itemConfigs.put("hw_snow", new ItemInfo(Items.SNOWBALL, "Снежок", null, "снежок", null));
        itemConfigs.put("hw_bomb", new ItemInfo(Items.FIRE_CHARGE, "Взрывная штучка", null, "взрывная", null));
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        state = State.IDLE;
        pendingInfo = null;
        cooldownTicks = 0;
        MoveBlockUtility.unblock(this);
    }

    @EventTarget
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (AutoTotem.isSwapping()) return;
        if (event.getAction() != InputConstants.PRESS || mc.gui.screen() != null || PanelRefractions.isOpen() || state != State.IDLE) return;
        handleInput(event.getKey(), false);
    }

    @EventTarget
    public void onMouseInput(MouseInputEvent event) {
        if (AutoTotem.isSwapping()) return;
        if (event.getAction() != InputConstants.PRESS || mc.gui.screen() != null || PanelRefractions.isOpen() || state != State.IDLE) return;
        if (handleInput(event.getButton(), true)) {
            event.cancel();
        }
    }

    private boolean handleInput(int keyOrButton, boolean isMouse) {
        if (AutoTotem.isSwapping()) return false;

        if (server.is("FunTime")) {
            if (matches(ftDisorient, keyOrButton, isMouse)) return queueItem("ft_disorient");
            if (matches(ftDust, keyOrButton, isMouse)) return queueItem("ft_dust");
            if (matches(ftPlast, keyOrButton, isMouse)) return queueItem("ft_plast");
            if (matches(ftSnow, keyOrButton, isMouse)) return queueItem("ft_snow");
            if (matches(ftGodAura, keyOrButton, isMouse)) return queueItem("ft_god_aura");
            if (matches(ftTrap, keyOrButton, isMouse)) return queueItem("ft_trap");
            if (matches(ftFireSwirl, keyOrButton, isMouse)) return queueItem("ft_fire_swirl");

            if (matches(ftHolyWater, keyOrButton, isMouse)) return queueItem("ft_holy_water");
            if (matches(ftAnger, keyOrButton, isMouse)) return queueItem("ft_anger");
            if (matches(ftPaladin, keyOrButton, isMouse)) return queueItem("ft_paladin");
            if (matches(ftAssassin, keyOrButton, isMouse)) return queueItem("ft_assassin");
            if (matches(ftRadiation, keyOrButton, isMouse)) return queueItem("ft_radiation");
            if (matches(ftSleep, keyOrButton, isMouse)) return queueItem("ft_sleep");
            if (matches(ftClapper, keyOrButton, isMouse)) return queueItem("ft_clapper");
        } else if (server.is("HolyWorld")) {
            if (matches(hwExplosiveTrap, keyOrButton, isMouse)) return queueItem("hw_exp_trap");
            if (matches(hwNormalTrap, keyOrButton, isMouse)) return queueItem("hw_norm_trap");
            if (matches(hwStun, keyOrButton, isMouse)) return queueItem("hw_stun");
            if (matches(hwSnow, keyOrButton, isMouse)) return queueItem("hw_snow");
            if (matches(hwBomb, keyOrButton, isMouse)) return queueItem("hw_bomb");
        }
        return false;
    }

    private boolean matches(BindSetting setting, int keyOrBtn, boolean isMouse) {
        return isMouse ? setting.matchesMouse(keyOrBtn) : setting.matches(keyOrBtn);
    }

    private boolean queueItem(String key) {
        if (AutoTotem.isSwapping()) return false;

        ItemInfo info = itemConfigs.get(key);
        if (info == null || !inGame() || player() == null) return false;

        LocalPlayer player = player();

        int hotbarSlot = findInHotbar(player, info);
        if (hotbarSlot != -1) {
            useFromHotbar(player, hotbarSlot);
            return true;
        }

        int invSlot = findInMainInventory(player, info);
        if (invSlot != -1) {
            if (useMode.is("Packet")) {
                performPacketSwapUse(player, invSlot);
            } else {
                pendingInfo = info;
                state = State.STOP_SPRINT;
            }
            return true;
        }

        return false;
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        if (!inGame() || player() == null || mc.gameMode == null) {
            reset();
            return;
        }

        if (AutoTotem.isSwapping()) {
            reset();
            return;
        }

        LocalPlayer player = player();

        switch (state) {
            case STOP_SPRINT -> {
                if (player.isSprinting()) {
                    player.setSprinting(false);
                }

                if (player.onGround()) {
                    player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
                }

                MoveBlockUtility.freeze(this, 4);
                MoveBlockUtility.blockSprint(this, 5);

                state = State.SWAP_AND_USE;
            }

            case SWAP_AND_USE -> {
                if (player.onGround()) {
                    player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
                }

                if (pendingInfo != null) {
                    int invSlot = findInMainInventory(player, pendingInfo);
                    if (invSlot != -1) {
                        int selectedHotbar = player.getInventory().getSelectedSlot();

                        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);
                        InventoryUtil.useDirect(player);
                        if (swapBack.getValue()) {
                            mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);
                        }
                    }
                }

                cooldownTicks = 2;
                state = State.COOLDOWN;
            }

            case COOLDOWN -> {
                MoveBlockUtility.freeze(this, 2);
                if (player.onGround()) {
                    player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
                }

                if (--cooldownTicks <= 0) {
                    state = State.IDLE;
                    pendingInfo = null;
                    MoveBlockUtility.unblock(this);
                }
            }

            case IDLE -> {
            }
        }
    }

    private void useFromHotbar(LocalPlayer player, int hotbarSlot) {
        int previousSlot = player.getInventory().getSelectedSlot();
        player.getInventory().setSelectedSlot(hotbarSlot);
        InventoryUtil.useDirect(player);
        if (swapBack.getValue()) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
    }

    private void performPacketSwapUse(LocalPlayer player, int invSlot) {
        boolean wasSprinting = player.isSprinting();
        if (wasSprinting && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }

        int selectedHotbar = player.getInventory().getSelectedSlot();
        mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);
        InventoryUtil.useDirect(player);
        if (swapBack.getValue()) {
            mc.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, selectedHotbar, ContainerInput.SWAP, player);
        }

        if (wasSprinting && mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        }
    }

    private int findInHotbar(LocalPlayer player, ItemInfo info) {
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (matchesInfo(stack, info)) return i;
        }
        return -1;
    }

    private int findInMainInventory(LocalPlayer player, ItemInfo info) {
        return InventoryUtil.findInventorySlot(player, stack -> matchesInfo(stack, info));
    }

    private boolean matchesInfo(ItemStack stack, ItemInfo info) {
        if (stack == null || stack.isEmpty() || !stack.is(info.item)) return false;

        if (info.effectReqs != null && !info.effectReqs.isEmpty()) {
            return matchesPotion(stack, info.effectReqs);
        }

        if (info.loreKeywords != null && !info.loreKeywords.isEmpty()) {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    String clean = cleanText(line.getString());
                    for (String kw : info.loreKeywords) {
                        if (clean.contains(kw.toLowerCase())) return true;
                    }
                }
            }
        }

        if (info.nameFallback != null) {
            String name = cleanText(stack.getHoverName().getString());
            return name.contains(info.nameFallback.toLowerCase());
        }

        return true;
    }

    private boolean matchesPotion(ItemStack stack, List<EffectReq> reqs) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return false;

        Map<Holder<MobEffect>, Integer> activeEffects = new HashMap<>();
        for (MobEffectInstance instance : contents.customEffects()) {
            activeEffects.put(instance.getEffect(), instance.getAmplifier());
        }

        if (activeEffects.isEmpty()) return false;

        for (EffectReq req : reqs) {
            Integer amp = activeEffects.get(req.effect);
            if (amp == null || amp < req.minAmp) return false;
        }
        return true;
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.toLowerCase().replaceAll("§[0-9a-fk-or]", "");
    }
}
