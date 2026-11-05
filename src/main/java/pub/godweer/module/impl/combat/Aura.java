package pub.godweer.module.impl.combat;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pub.godweer.event.EventTarget;
import pub.godweer.event.list.AttackEvent;
import pub.godweer.event.list.PlayerInputEvent;
import pub.godweer.event.list.PlayerTickEvent;
import pub.godweer.friend.FriendManager;
import pub.godweer.module.Category;
import pub.godweer.module.Module;
import pub.godweer.module.settings.impl.CheckBox;
import pub.godweer.module.settings.impl.HeaderSetting;
import pub.godweer.module.settings.impl.ModeSetting;
import pub.godweer.module.settings.impl.MultiModeSetting;
import pub.godweer.module.settings.impl.SliderSetting;
import pub.godweer.rotation.Angle;
import pub.godweer.rotation.RayTraceUtils;
import pub.godweer.rotation.RotationHandler;
import pub.godweer.rotation.UBoxPoints;
import pub.godweer.util.player.InventorySwaps;
import pub.godweer.util.player.MoveUtility;

import java.util.List;

/**
 * Aura — единственная аура клиента (бывшая KillAura3, вобравшая коррекции движения
 * KillAura2 и все остальные переносы).
 * <p>
 * Что внутри:
 * <ul>
 *   <li>целевой угол квантуется под сетку чувствительности мыши ({@link Angle#applyGCD()});</li>
 *   <li>на элитрах цель берётся по предсказанной позиции (позиция + дельта-движение), точка
 *       на 50 % высоты со сдвигом вперёд по вектору взгляда цели;</li>
 *   <li>четыре коррекции движения из KillAura2/KillAura3: None / Free / Focused / Full;</li>
 *   <li>три режима наводки: Smooth (сама доводит угол), None (не наводится и бьёт без проверки
 *       наводки) и Trigger (не наводится, бьёт только если ты уже смотришь в цель);</li>
 *   <li>логика удара: Smart (бьёт на земле и в падении) / Crits (только в падении, только
 *       криты), порог падения для крита — 0.1 тика, то есть первый же тик падения;</li>
 *   <li>булава бьёт только после настоящего падения на {@code Mace Fall} блоков;</li>
 *   <li>откат считается по шкале заряда с запасом в 1.5 тика.</li>
 * </ul>
 * Дальность удара: не выше 2.92 блоков — 3.0 в 26.3 это ровно
 * {@code Unfair Advantage(Reach-Hitbox)}.
 */
public class Aura extends Module {
    public static Aura INSTANCE;

    /** Логика удара: бьём и на земле, и в падении. */
    public static final String HIT_SMART = "Smart";
    /** Логика удара: только криты, то есть только в настоящем падении. */
    public static final String HIT_CRITS = "Crits";

    /** Наводка: аура сама доводит угол (silent-ротация). */
    public static final String AIM_SMOOTH = "Smooth";
    /** Наводка: аура не наводится и бьёт без проверки наводки. */
    public static final String AIM_NONE = "None";
    /** Наводка: аура не наводится, бьёт только если ты навёлся сам. */
    public static final String AIM_TRIGGER = "Trigger";

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private static final long ATTACK_COOLDOWN_MS = 467L;
    /** Процент нужного захода удара: 90 % — это 0.9 отката из оригинальной спеки. */
    private static final float DEFAULT_COOLDOWN_PERCENT = 90.0F;
    /**
     * Отдельный порог для булавы. Её сила идёт не от полного отката, а от падения
     * (smash), поэтому ждать 90 % заряда смысла нет: по умолчанию 10 %.
     */
    private static final float DEFAULT_MACE_COOLDOWN_PERCENT = 10.0F;
    /** Шкала захода с запасом в 1.5 тика — как getAttackCooldownProgress(p, 1.5F) в спеке. */
    private static final float CHARGE_BASE_TICKS = 1.5F;
    private static final float MAX_SAFE_REACH = 2.92F;
    /** Центр хитбокса считается в дистанции, пока цель не дальше этого. */
    private static final double CENTER_DISTANCE = 3.0D;
    /** Оба на элитрах: удар только ближе этого расстояния до предсказанной точки. */
    private static final double BOTH_GLIDE_MAX = 5.0D;
    /**
     * Порог падения для крита, в тиках. 0.1 — удар разрешён уже на первом тике
     * падения: раньше стояло 2 тика (≈0.1 с), и аура в прыжках с низкой опорой
     * до порога почти никогда не доходила — успевала приземлиться.
     */
    private static final float CRIT_FALL_TICKS = 0.1F;
    /** Предсказание цели на элитрах: вперёд на столько тиков и на столько же по её взгляду. */
    private static final double GLIDE_PREDICT_TICKS = 1.0D;
    private static final double GLIDE_FORWARD = 1.0D;
    /** Сколько блоков нужно пролететь булаве, чтобы удар вышел smash-ударом. */
    private static final float DEFAULT_MACE_FALL = 2.0F;

    private final HeaderSetting targetHeader = header("Targets");
    private final MultiModeSetting targets = multiMode("Targets",
            List.of("Players", "Naked", "Invisible", "Mobs"),
            "Players", "Naked", "Invisible", "Peaceful", "Mobs");
    private final SliderSetting attackRange = slider("Attack Range", 3.0f, 0.0f, 6.0f, 0.05f);
    private final SliderSetting aimRange = slider("Aim Range", 3.0f, 0.0f, 6.0f, 0.05f);
    private final SliderSetting glideRange = slider("Glide Range", 50.0f, 10.0f, 100.0f, 0.05f);
    private final ModeSetting priority = mode("Priority", "Distance", "Distance", "Health", "Angle", "None");

    private final HeaderSetting attackHeader = header("Attack");
    private final CheckBox throughWalls = checkbox("Through Walls", true);
    private final CheckBox noAttackWhileEating = checkbox("Pause while Using", true);
    /** Логика удара — тот же вид настройки, что и у коррекции движения. */
    private final ModeSetting hitLogic = mode("Hit Logic", HIT_SMART, HIT_SMART, HIT_CRITS);
    private final SliderSetting cooldownPercent = slider("Cooldown",
            DEFAULT_COOLDOWN_PERCENT, 0.0f, 100.0f, 1.0f);
    private final SliderSetting maceCooldownPercent = slider("Mace Cooldown",
            DEFAULT_MACE_COOLDOWN_PERCENT, 0.0f, 100.0f, 1.0f);
    private final SliderSetting maceFall = slider("Mace Fall", DEFAULT_MACE_FALL, 0.0f, 4.0f, 0.1f);
    private final CheckBox sprintReset = checkbox("Sprint Reset", true);
    private final CheckBox lowerShield = checkbox("Release Shield", false);
    private final CheckBox shieldBreak = checkbox("Shield Break", true);

    private final HeaderSetting rotationHeader = header("Aim");
    /** Режим наводки — тоже набор готовых кнопок, как у Movement. */
    private final ModeSetting aimMode = mode("Rotation", AIM_SMOOTH, AIM_SMOOTH, AIM_NONE, AIM_TRIGGER);
    private final CheckBox clientLook = checkbox("Client Look", false).visible(this::isSmooth);
    private final CheckBox raycastCheck = checkbox("Raycast Check", false).visible(this::isSmooth);
    private final ModeSetting correction = mode("Movement", "None", "None", "Free", "Focused", "Full")
            .visible(this::isSmooth);

    private LivingEntity target;
    private long nextAttackAt = 0L;
    private int fallTicks = 0;
    private boolean needSprintReset = false;
    private boolean sprintResetDone = false;
    private int sprintResetTicks = 0;
    private boolean rotationOwned = false;

    public Aura() {
        super("Aura", "Аура: GCD-квант угла, предсказание на элитрах, четыре коррекции движения, "
                        + "режимы наводки Smooth/None/Trigger и логика удара Smart/Crits",
                Category.COMBAT);
        INSTANCE = this;
    }

    /** Окно, в котором KeyboardInputMixin гасит шаг вперёд: сброс спринта без setSprinting. */
    public boolean sprintHoldActive() {
        return isEnabled() && needSprintReset;
    }

    public LivingEntity getTarget() {
        return target;
    }

    private boolean isSmooth() {
        return aimMode.is(AIM_SMOOTH);
    }

    private boolean isTrigger() {
        return aimMode.is(AIM_TRIGGER);
    }

    private boolean isCrits() {
        return hitLogic.is(HIT_CRITS);
    }

    @Override
    protected void onEnable() {
        target = null;
        nextAttackAt = 0L;
        fallTicks = 0;
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
    }

    @Override
    protected void onDisable() {
        target = null;
        fallTicks = 0;
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
        releaseRotation();
    }

    @EventTarget
    public void onAttack(AttackEvent event) {
        LocalPlayer player = player();
        if (!isEnabled() || player == null || level() == null) return;
        if (event.getTarget() instanceof LivingEntity living && isValidTarget(living, player)) {
            target = living;
        }
    }

    @EventTarget
    public void onInput(PlayerInputEvent event) {
        LocalPlayer player = player();
        if (!isEnabled() || player == null || target == null) return;
        // Коррекции движения имеют смысл только у Smooth: None и Trigger угол не трогают,
        // значит расхождения «камера / серверный угол» у них нет.
        if (!isSmooth()) return;

        switch (correction.getValue()) {
            // Free: идём туда, куда смотрит камера, а клавиши пересчитываются под
            // серверный угол. Без silent-ротации угла расхождения нет и коррекция не нужна.
            case "Free" -> MoveUtility.fixMovement(event, cameraYaw(player));
            // Focused: вжимание в цель относительно серверного угла.
            case "Focused" -> MoveUtility.fixMovementFocus(event, target);
            // Full: направление движения — ровно по yaw в центр хитбокса цели.
            case "Full" -> MoveUtility.fixMovementFocus(event, yawToBoxCenter(player, target));
            default -> {
            }
        }
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        LocalPlayer player = player();
        if (!isEnabled() || event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        if (player == null || level() == null || gameMode() == null) return;

        // Forward был погашен миксином в тике между этим PRE и предыдущим.
        if (needSprintReset) {
            needSprintReset = false;
            sprintResetDone = true;
            sprintResetTicks = 0;
        }
        if (sprintResetDone) {
            sprintResetTicks++;
        }

        updateFallTicks(player);
        updateTarget(player);
        rotate(player);
        processAttack(player);
    }

    // ------------------------------------------------------------------ таргеты

    /** Подряд идущие тики падения; на земле счётчик обнуляется. */
    private void updateFallTicks(LocalPlayer player) {
        boolean falling = !player.onGround() && player.fallDistance > 0.0F;
        fallTicks = falling ? fallTicks + 1 : 0;
    }

    private void updateTarget(LocalPlayer player) {
        if (target != null && isValidTarget(target, player)) return;
        target = findTarget(player);
    }

    /** Сортировка по приоритету; в None порядок тот, в котором мир отдаёт сущности. */
    private LivingEntity findTarget(LocalPlayer player) {
        double area = maxAimRange(player) + 1.0D;
        AABB box = player.getBoundingBox().inflate(area);
        List<LivingEntity> candidates = level().getEntitiesOfClass(LivingEntity.class, box, e -> e != player);

        LivingEntity best = null;
        double bestValue = Double.MAX_VALUE;
        String mode = priority.getValue();

        for (LivingEntity living : candidates) {
            if (!isValidTarget(living, player)) continue;
            if ("None".equals(mode)) return living;

            double value = switch (mode) {
                case "Health" -> living.getHealth();
                case "Angle" -> angleError(living, player);
                // Distance в оригинале считается до ЦЕНТРА хитбокса, а не до ближайшей точки.
                default -> player.getEyePosition().distanceToSqr(boxCenter(living));
            };
            if (value < bestValue) {
                bestValue = value;
                best = living;
            }
        }
        return best;
    }

    private boolean isValidTarget(LivingEntity entity, LocalPlayer player) {
        if (entity == null || entity == player) return false;
        if (!entity.isAlive() || entity.getHealth() <= 0.0F || entity.isRemoved()) return false;
        if (entity instanceof ArmorStand || entity instanceof IronGolem || entity instanceof Bat) return false;

        if (entity instanceof Player other) {
            if (!targets.isEnabled("Players")) return false;
            if (FriendManager.getInstance().isFriend(other)) return false;
            if (isNaked(other) && !targets.isEnabled("Naked")) return false;
            if (entity.isInvisible() && !targets.isEnabled("Invisible")) return false;
        } else if (entity instanceof Mob mob) {
            if (mob instanceof Enemy ? !targets.isEnabled("Mobs") : !targets.isEnabled("Peaceful")) {
                return false;
            }
        } else {
            return false;
        }

        Vec3 eyes = player.getEyePosition();
        double maxAim = maxAimRange(player);
        if (eyes.distanceToSqr(UBoxPoints.getClosestBoxPoint(eyes, entity.getBoundingBox())) > maxAim * maxAim) {
            return false;
        }
        // В оригинале видимость проверяется по макушке и по ближайшей точке хитбокса.
        if (!throughWalls.getValue() && !canSee(player, eyes, entity)) return false;
        return true;
    }

    private boolean canSee(LocalPlayer player, Vec3 eyes, LivingEntity entity) {
        if (RayTraceUtils.canSeePoint(eyes, entity.getEyePosition())) return true;
        return RayTraceUtils.canSeePoint(eyes, UBoxPoints.getClosestBoxPoint(eyes, entity.getBoundingBox()));
    }

    private double angleError(LivingEntity entity, LocalPlayer player) {
        Angle aim = Angle.fromVec(player.getEyePosition(), boxCenter(entity));
        return Math.abs(Mth.wrapDegrees(aim.getYaw() - sendYaw(player)))
                + Math.abs(aim.getPitch() - sendPitch(player));
    }

    private static boolean isNaked(Player player) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!player.getItemBySlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private double maxAimRange(LocalPlayer player) {
        return player.isFallFlying() ? glideRange.get() : attackRange.get() + aimRange.get();
    }

    // ------------------------------------------------------------------ наводка

    private static Vec3 boxCenter(LivingEntity entity) {
        return entity.getBoundingBox().getCenter();
    }

    private static float yawToBoxCenter(LocalPlayer player, LivingEntity entity) {
        Vec3 center = boxCenter(entity);
        double dx = center.x - player.getX();
        double dz = center.z - player.getZ();
        return (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
    }

    /**
     * Точка наводки: обычная — 60 % высоты от ног; если оба летят на элитрах, цель
     * берётся по предсказанной позиции (позиция + дельта-движение на тик), на половине
     * высоты и со сдвигом вперёд по вектору взгляда цели.
     */
    private Vec3 aimPoint(LivingEntity entity, LocalPlayer player) {
        if (!(player.isFallFlying() && entity.isFallFlying())) {
            return entity.position().add(0.0D, entity.getBbHeight() * 0.6D, 0.0D);
        }

        Vec3 predicted = entity.position()
                .add(entity.getDeltaMovement().scale(GLIDE_PREDICT_TICKS))
                .add(entity.getLookAngle().scale(GLIDE_FORWARD));
        return predicted.add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    private void rotate(LocalPlayer player) {
        if (target == null) {
            releaseRotation();
            return;
        }
        // None и Trigger не наводятся вообще: угол остаётся за игроком.
        if (!isSmooth()) {
            releaseRotation();
            return;
        }

        // Скорости 360°/тик + ускорение в оригинале означают, что угол доводится за
        // один тик, поэтому здесь сразу целевой угол; квант под GCD — как roundToGcd.
        Angle aim = Angle.fromVec(player.getEyePosition(), aimPoint(target, player)).applyGCD();

        if (clientLook.getValue()) {
            player.setYRot(aim.getYaw());
            player.setXRot(aim.getPitch());
            player.yHeadRot = aim.getYaw();
            releaseRotation();
            return;
        }

        RotationHandler.setRotation(aim.getYaw(), aim.getPitch());
        rotationOwned = true;
    }

    private void releaseRotation() {
        if (!rotationOwned) return;
        rotationOwned = false;
        RotationHandler.disengage("Instant");
    }

    /** Камера игрока: свободный угол хендлера, пока он держит ротацию. */
    private static float cameraYaw(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getFreeYaw() : player.getYRot();
    }

    // -------------------------------------------------------------------- удар

    private void processAttack(LocalPlayer player) {
        if (target == null) {
            nextAttackAt = System.currentTimeMillis();
            needSprintReset = false;
            sprintResetDone = false;
            sprintResetTicks = 0;
            releaseRotation();
            return;
        }

        if (!shouldAttack(player) || System.currentTimeMillis() < nextAttackAt) return;
        if (blocksAttackWhileUsing(player)) return;

        if (sprintReset.getValue() && player.isSprinting() && !sprintResetDone) {
            needSprintReset = true;
            return;
        }
        if (sprintResetDone && sprintResetTicks < 1) {
            return;
        }

        attack(player);
        needSprintReset = false;
        sprintResetDone = false;
        sprintResetTicks = 0;
    }

    private boolean shouldAttack(LocalPlayer player) {
        if (player.getAttackStrengthScale(CHARGE_BASE_TICKS) < requiredCharge(player)) return false;

        boolean bothGliding = player.isFallFlying() && target.isFallFlying();

        // Trigger бьёт только когда ты сам навёл прицел; None не проверяет наводку вовсе;
        // Smooth проверяет её только по галочке Raycast Check.
        if (!isNoneAim() && !bothGliding) {
            boolean checkAim = isTrigger() || raycastCheck.getValue();
            if (checkAim && !RayTraceUtils.isLookingAt(target, maxAimRange(player) * 2.0D,
                    sendYaw(player), sendPitch(player), false)) {
                return false;
            }
        }

        Vec3 eyes = player.getEyePosition();
        if (bothGliding) {
            if (eyes.distanceTo(aimPoint(target, player)) > BOTH_GLIDE_MAX) return false;
        } else {
            double dist = eyes.distanceTo(boxCenter(target));
            if (dist > CENTER_DISTANCE) {
                dist = eyes.distanceTo(UBoxPoints.getClosestBoxPoint(eyes, target.getBoundingBox()));
            }
            if (dist > hitReach()) return false;
        }

        return hitLogicAllows(player);
    }

    private boolean isNoneAim() {
        return aimMode.is(AIM_NONE);
    }

    /**
     * Логика удара.
     * <ul>
     *   <li>Crits: удар только там, где сервер засчитает крит — настоящее падение,
     *       без воды, лавы, транспорта и слепоты;</li>
     *   <li>Smart: бьёт и стоя на земле, и в падении (в падении удар выходит критом сам),
     *       но не в воде, лаве и на транспорте.</li>
     * </ul>
     * Булава в обоих режимах требует настоящего падения на {@code Mace Fall} блоков:
     * без него smash-удар не засчитывается сервером.
     */
    private boolean hitLogicAllows(LocalPlayer player) {
        boolean mace = player.getMainHandItem().is(Items.MACE);
        if (mace && player.fallDistance < maceFall.get()) return false;

        if (player.isInWater() || player.isInLava()) return false;
        if (player.isPassenger()) return false;
        if (player.hasEffect(MobEffects.BLINDNESS)) return false;

        if (isCrits()) {
            return canCritical(player);
        }
        // Smart: на земле или в падении (тот же порог, что у крита: 0.1 тика).
        return player.onGround() || (player.fallDistance > 0.0F && fallTicks >= CRIT_FALL_TICKS);
    }

    /**
     * canCritical оригинала: удар выходит критом только в настоящем падении. Без воды,
     * лавы, транспорта и слепоты. По 26.3 не проверено лишь «цепляние за паутину»: в
     * маппингах этого геттера нет, вместо него падение и так не засчитывается с нулём
     * fallDistance.
     */
    private boolean canCritical(LocalPlayer player) {
        // Падение засчитывается с первого же тика: fallTicks >= 0.1.
        if (fallTicks < CRIT_FALL_TICKS) return false;
        if (player.onGround() || player.fallDistance <= 0.0F) return false;
        if (player.isInWater() || player.isInLava()) return false;
        if (player.isPassenger()) return false;
        return !player.hasEffect(MobEffects.BLINDNESS);
    }

    private void attack(LocalPlayer player) {
        InteractionHand lowered = null;
        if (lowerShield.getValue()) {
            lowered = ownShieldHand(player);
            if (lowered != null) gameMode().releaseUsingItem(player);
        }

        boolean handled = false;
        if (shieldBreak.getValue() && isShieldBlocking(target)) {
            handled = axeStrike(player, target);
        }
        if (!handled) {
            gameMode().attack(player, target);
            swing(player);
        }

        if (lowered != null && !player.isUsingItem()) {
            gameMode().useItem(player, lowered);
        }

        nextAttackAt = System.currentTimeMillis() + ATTACK_COOLDOWN_MS;
    }

    /** Рука, в которой игрок держит блок щитом, или null. */
    private InteractionHand ownShieldHand(LocalPlayer player) {
        if (!player.isBlocking() || !player.getUseItem().is(Items.SHIELD)) return null;
        return player.getUsedItemHand();
    }

    private static void swing(LocalPlayer player) {
        SwingAnimation anim = player.getMainHandItem().getAttackAnimation();
        player.swing(InteractionHand.MAIN_HAND, anim, false);
    }

    private static boolean isShieldBlocking(LivingEntity entity) {
        return entity != null && entity.isBlocking() && entity.getUseItem().is(Items.SHIELD);
    }

    /**
     * Ломка щита топориком: топор в хотбаре — тихая подмена слота вокруг удара; топор в
     * сетках — свап через открытый инвентарь, удар и обратный свап.
     */
    private boolean axeStrike(LocalPlayer player, LivingEntity blocking) {
        int hotbar = findAxe(0, 9);
        if (hotbar != -1) {
            return attackWithSlot(player, blocking, hotbar);
        }

        int inventory = findAxe(9, 36);
        if (inventory == -1) return false;
        // Свап живёт только в открытом инвентаре: контейнер 0 должен быть активен.
        if (!(screen() instanceof InventoryScreen)) return false;
        if (player.inventoryMenu.containerId != 0) return false;

        int selected = player.getInventory().getSelectedSlot();
        int swapSlot = selected == 8 ? 7 : 8;

        InventorySwaps.grimSwapToHotbar(player, inventory, swapSlot);
        try {
            return attackWithSlot(player, blocking, swapSlot);
        } finally {
            InventorySwaps.grimSwapToHotbar(player, inventory, swapSlot);
        }
    }

    private boolean attackWithSlot(LocalPlayer player, LivingEntity blocking, int slot) {
        int selected = player.getInventory().getSelectedSlot();
        if (slot != selected) setSlot(slot);
        gameMode().attack(player, blocking);
        swing(player);
        if (slot != selected) setSlot(selected);
        return true;
    }

    // ------------------------------------------------------------------- утилиты

    /** Используемый предмет мешает удару, кроме своего же щита при включённом Release Shield. */
    private boolean blocksAttackWhileUsing(LocalPlayer player) {
        if (!noAttackWhileEating.getValue()) return false;
        if (!player.isUsingItem()) return false;
        return !(lowerShield.getValue() && player.isBlocking()
                && player.getUseItem().is(Items.SHIELD));
    }

    /**
     * Нужный заход удара в долях шкалы, берётся из ползунка в процентах. У булавы свой:
     * её удар о землю сервер читает по падению, а не по полному откату, поэтому ждать
     * там 90 % бессмысленно.
     */
    private float requiredCharge(LocalPlayer player) {
        boolean mace = player.getMainHandItem().is(Items.MACE);
        return (mace ? maceCooldownPercent.get() : cooldownPercent.get()) / 100.0F;
    }

    private double hitReach() {
        return Math.min(attackRange.get(), MAX_SAFE_REACH);
    }

    private static float sendYaw(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getServerYaw() : player.getYRot();
    }

    private static float sendPitch(LocalPlayer player) {
        return RotationHandler.isActive() ? RotationHandler.getServerPitch() : player.getXRot();
    }

    private static int findAxe(int from, int to) {
        LocalPlayer player = mc.player;
        if (player == null) return -1;
        List<ItemStack> stacks = player.getInventory().getNonEquipmentItems();
        for (int i = from; i < Math.min(to, stacks.size()); i++) {
            if (stacks.get(i).is(ItemTags.AXES)) return i;
        }
        return -1;
    }

    private void setSlot(int slot) {
        LocalPlayer player = player();
        if (player == null || slot < 0 || slot > 8 || player.connection == null) return;
        player.getInventory().setSelectedSlot(slot);
        player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }
}
