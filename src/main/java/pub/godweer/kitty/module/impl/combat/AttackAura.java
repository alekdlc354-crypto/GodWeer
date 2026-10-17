package pub.godweer.kitty.module.impl.combat;

import lombok.Getter;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import pub.godweer.auxiliary.player.MoveUtility;
import pub.godweer.cc.AttackHandler;
import pub.godweer.cc.RayTraceUtils;
import pub.godweer.cc.RotationHandler;
import pub.godweer.cc.UBoxPoints;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.EventPreMotion;
import pub.godweer.enjoy.event.list.PlayerInputEvent;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.enjoy.event.list.Render3DEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.MultiModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;
import pub.godweer.kitty.sosiski.friend.FriendManager;

import java.util.List;
import java.util.Random;

/**
 * Create by daun kvass
 */
@Getter
public class AttackAura extends Module {
    public static AttackAura INSTANCE;

    private final ModeSetting mode = mode("Режим", "Умный", "Умный", "Только криты");
    private final ModeSetting rotation = mode("Ротация", "Smooth", "Smooth", "None");
    private final ModeSetting moveFix = mode("Коррекция движения", "Free", "Free", "Focus", "None");
    private final MultiModeSetting targets = multiMode("Цели", List.of("Игроки"), "Игроки", "Мобы", "Животные");
    private final CheckBox notWhileEating = checkbox("Не бить когда ешь", true);
    private final CheckBox onlyTools = checkbox("Бить только инструментами", false);
    private final CheckBox releaseShield = checkbox("Отжимать щит", true);
    private final SliderSetting shieldTicks = slider("Тики отжима", 3.0F, 3.0F, 5.0F, 1.0F)
            .visible(releaseShield::getValue);
    private final SliderSetting attackRange = slider("Длина удара", 3.0F, 1.0F, 6.0F, 0.1F);
    private final SliderSetting aimRange = slider("Дистанция наводки", 4.5F, 3.0F, 8.0F, 0.1F);
    private final SliderSetting elytraAim = slider("Длина наводки на элитрах", 20.0F, 20.0F, 100.0F, 1.0F);
    private final CheckBox noAimThroughWalls = checkbox("Не наводиться через стены", true);
    private final CheckBox noHitThroughWalls = checkbox("Не бить через стены", true);
    private final CheckBox sprintReset = checkbox("Сброс спринта", true);

    private final Random random = new Random();
    private LivingEntity target;
    private int shieldWait;
    private boolean pendingHit;
    private boolean pendingAxeHit;
    private int shieldBreakCooldown;
    private int axeReturnSlot = -1;
    private int axeHotbarSlot = -1;
    private int axeInvSlot = -1;
    // Порог удара в воздухе, в тысячных процента: 86.123% = 86123 (86.000-93.000)
    private int airThresholdThousandths = 90_000;
    // На земле удар возможен не раньше, чем через 30-100 мс после 100% заряда
    private long groundReadyAtMs;
    // Окно сброса спринта: 1-2 тика замирания ДО удара + 1-2 тика ПОСЛЕ удара.
    // «Заморозка» работает на уровне отправляемой позиции: сервер перестаёт
    // видеть движение вперёд, клиент при этом идёт как обычно.
    private int preStopTicks;
    private int postStopTicks;
    private double freezePosX;
    private double freezePosZ;
    private boolean sprintResetRunning;

    public AttackAura() {
        super("AttackAura", "Бьет при наводке", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        randomizeCooldown();
    }

    @Override
    public void onDisable() {
        clearTarget();
        shieldWait = 0;
        pendingHit = false;
        pendingAxeHit = false;
        shieldBreakCooldown = 0;
        preStopTicks = 0;
        postStopTicks = 0;
        sprintResetRunning = false;
        groundReadyAtMs = 0;
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.PRE) return;
        if (!inGame() || player() == null || mc.gameMode == null || mc.level == null) {
            clearTarget();
            return;
        }
        if (shieldWait > 0) shieldWait--;
        if (shieldBreakCooldown > 0) shieldBreakCooldown--;
        if (postStopTicks > 0) {
            postStopTicks--;
        } else if (sprintResetRunning && !pendingHit) {
            sprintResetRunning = false;
        }
        if (screen() != null) {
            clearTarget();
            return;
        }

        float aimDist = selectAimDist();
        LivingEntity aimed = findAimedTarget(aimDist);
        if (aimed != null && aimed != target) {
            restoreAxeSlot();
            shieldBreakCooldown = 0;
            target = aimed;
        }
        if (target == null || !isCandidate(target, aimDist)) {
            clearTarget();
            return;
        }
        if (rotation.is("None")) {
            // NotRotate (триггербот): ротацию не двигаем вообще,
            // бьём только когда сам игрок уже навёл прицел на цель.
            if (RotationHandler.isActive()) RotationHandler.disengage("Instant");
        } else {
            Vec3 aimPoint = getAimPoint(target);
            if (noAimThroughWalls.getValue()
                    && !RayTraceUtils.canSeePoint(player().getEyePosition(), aimPoint)) {
                clearTarget();
                return;
            }
            smoothRotate(aimPoint);
        }

        // Ломаем щит топором, когда цель реально смотрит на нас (|Δyaw| < 90°).
        if (!target.isBlocking() || shieldBreakCooldown > 0 || !targetFacingMe(target)) {
            restoreAxeSlot();
        } else if (axeHotbarSlot < 0) {
            int axeSlot = findAxeSlot();
            if (axeSlot >= 0 && prepareAxe(axeSlot)) return;
        } else if (player().getInventory().getSelectedSlot() != axeHotbarSlot
                || !player().getMainHandItem().is(ItemTags.AXES)) {
            restoreAxeSlot();
            return;
        }

        if (pendingHit) {
            if (preStopTicks > 0) preStopTicks--;
            return;
        }
        if (!readyToHit(target)) return;

        // Удар уходит в POST: пакет движения этого тика уже покажет серверу
        // новый поворот, атака выйдет следом за ним.
        pendingHit = true;
        pendingAxeHit = target.isBlocking() && player().getMainHandItem().is(ItemTags.AXES);
        // Сброс спринта: окно замирания стартует за 1-2 тика до удара и живёт
        // ещё 1-2 тика после него. Серверу замирает позиция, клиент идёт
        // свободно — ввод и спринт-флаги не трогаются вовсе.
        if (sprintReset.getValue() && player().onGround() && !alwaysHittable()) {
            preStopTicks = 1 + random.nextInt(2);
            freezePosX = player().getX();
            freezePosZ = player().getZ();
            sprintResetRunning = true;
        } else {
            preStopTicks = 0;
        }
    }

    @EventTarget
    public void onTickPost(PlayerTickEvent event) {
        if (event.getPhase() != PlayerTickEvent.Phase.POST) return;
        if (!pendingHit) return;
        if (!inGame() || player() == null || mc.gameMode == null || target == null) {
            cancelPendingHit();
            return;
        }
        // POST = tick TAIL: пакет движения с новым поворотом уже отправлен,
        // атака уходит после него. Мягкая перепроверка: между PRE и POST
        // игроки уже сдвинулись (aiStep), строгий rech здесь отменял бы удар
        // из-за долей градуса. Промах — это просто свинг без урона.
        if (!isCandidate(target, selectAimDist()) || isEating() || shieldWait > 0) {
            cancelPendingHit();
            return;
        }
        // Окно замирания ещё не доросло до удара (1-2 тика до него).
        if (preStopTicks > 0) return;
        boolean axeHit = pendingAxeHit && target.isBlocking()
                && player().getMainHandItem().is(ItemTags.AXES);
        mc.gameMode.attack(player(), target);
        player().swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        player().resetAttackStrengthTicker();
        randomizeCooldown();
        if (axeHit) shieldBreakCooldown = 4;
        pendingHit = false;
        pendingAxeHit = false;
        // Разрешаем идти вперед через 1-2 тика после удара.
        postStopTicks = 1 + random.nextInt(2);
        restoreAxeSlot();
    }

    private void cancelPendingHit() {
        pendingHit = false;
        pendingAxeHit = false;
        preStopTicks = 0;
    }

    private void clearTarget() {
        target = null;
        shieldBreakCooldown = 0;
        pendingHit = false;
        pendingAxeHit = false;
        preStopTicks = 0;
        restoreAxeSlot();
        RotationHandler.disengage("Smooth");
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (!inGame() || player() == null || mc.gameMode == null || target == null) return;
        if (rotation.is("None") || !isCandidate(target, selectAimDist())) return;
        // Ротация обновляется каждый кадр, а не каждый тик —
        // взгляд доезжает плавно и быстро между тиками (20 Гц → FPS).
        Vec3 aimPoint = getAimPoint(target);
        if (noAimThroughWalls.getValue()
                && !RayTraceUtils.canSeePoint(player().getEyePosition(), aimPoint)) return;
        smoothRotate(aimPoint);
    }

    /** Пока идёт окно сброса — сервер видит игрока стоящим на месте старта. */
    @EventTarget
    public void onPreMotion(EventPreMotion event) {
        if (!inGame() || player() == null || !sprintResetRunning || target == null) return;
        if (pendingHit || postStopTicks > 0) {
            // «Запрет идти вперед на стороне сервера»: позиция заморожена на
            // точке старта окна, клиентское движение, ввод и спринт не тронуты.
            event.setPosX(freezePosX);
            event.setPosZ(freezePosZ);
        }
    }

    @EventTarget
    public void onInput(PlayerInputEvent event) {
        if (!inGame() || player() == null) return;
        if (target != null && !moveFix.is("None")) {
            if (moveFix.is("Focus")) {
                // Focus работает и в NotRotate: pressIntoTarget сам берёт
                // player.getYRot(), когда ротация не захвачена.
                MoveUtility.pressIntoTarget(event, targetYawTo());
            } else if (RotationHandler.isActive()) {
                // Free-коррекция имеет смысл только при захваченной ротации;
                // в NotRotate камера и «серверный» поворот совпадают — она не нужна.
                MoveUtility.fixMovement(event, RotationHandler.getFreeYaw());
            }
        }
    }

    private float selectAimDist() {
        if (player().isFallFlying()) return elytraAim.getValue();
        return aimRange.getValue();
    }

    private float targetYawTo() {
        Vec3 d = target.getBoundingBox().getCenter().subtract(player().getEyePosition());
        return (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
    }

    private LivingEntity findAimedTarget(float aimDist) {
        float camYaw = RotationHandler.isActive() ? RotationHandler.getFreeYaw() : player().getYRot();
        float camPitch = RotationHandler.isActive() ? RotationHandler.getFreePitch() : player().getXRot();
        LivingEntity best = null;
        double bestDistSq = Double.MAX_VALUE;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity living) || !isCandidate(living, aimDist)) continue;
            if (!RayTraceUtils.isLookingAt(living, aimDist, camYaw, camPitch, noAimThroughWalls.getValue())) continue;
            double d = living.getBoundingBox().distanceToSqr(player().getEyePosition());
            if (d < bestDistSq) {
                best = living;
                bestDistSq = d;
            }
        }
        return best;
    }

    private boolean isCandidate(LivingEntity entity, float aimDist) {
        if (entity == player() || !entity.isAlive() || entity.isRemoved() || entity.isDeadOrDying()) return false;
        if (entity.level() != mc.level
                || entity.getBoundingBox().distanceToSqr(player().getEyePosition()) > aimDist * aimDist) return false;
        if (entity instanceof Player p) {
            if (FriendManager.getInstance().isFriend(p)) return false;
            return targets.isEnabled("Игроки");
        }
        if (entity instanceof Enemy) return targets.isEnabled("Мобы");
        if (entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature) {
            return targets.isEnabled("Животные");
        }
        return false;
    }

    private Vec3 getAimPoint(LivingEntity t) {
        Vec3 eyes = player().getEyePosition();
        Vec3 closest = UBoxPoints.getClosestBoxPoint(eyes, t.getBoundingBox().deflate(0.001));
        if ((!noAimThroughWalls.getValue() && !noHitThroughWalls.getValue())
                || RayTraceUtils.canSeePoint(eyes, closest)) return closest;
        UBoxPoints.TargetPoint point = UBoxPoints.getBestPoint(t);
        return point != null && point.isVisible() ? point.point() : closest;
    }

    private void smoothRotate(Vec3 point) {
        Vec3 d = point.subtract(player().getEyePosition());
        if (d.lengthSqr() < 1.0e-8) return;
        float rawYaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float rawPitch = (float) (-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
        float curYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : player().getYRot();
        float curPitch = RotationHandler.isActive() ? RotationHandler.getServerPitch() : player().getXRot();
        float dYaw = Mth.wrapDegrees(rawYaw - curYaw);
        float dPitch = rawPitch - curPitch;
        // Slightly random convergence per call so rotation speed
        // is never identical twice in a row (anti-pattern for detectors).
        float lerp = 0.85F + random.nextFloat() * 0.10F;
        RotationHandler.setRotation(curYaw + dYaw * lerp, Mth.clamp(curPitch + dPitch * lerp, -90.0F, 90.0F));
    }

    private boolean canReachTarget(LivingEntity t) {
        float range = attackRange.getValue();
        if (!isCandidate(t, selectAimDist())
                || t.getBoundingBox().distanceToSqr(player().getEyePosition()) > range * range) return false;
        float yaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : player().getYRot();
        float pitch = RotationHandler.isActive() ? RotationHandler.getServerPitch() : player().getXRot();
        return RayTraceUtils.isLookingAt(t, range, yaw, pitch, noHitThroughWalls.getValue());
    }

    private boolean canUseHand() {
        if (notWhileEating.getValue() && isEating()) return false;
        if (shieldWait > 0) return false;
        if (player().isBlocking()) {
            if (releaseShield.getValue()) {
                mc.gameMode.releaseUsingItem(player());
                shieldWait = Math.round(shieldTicks.getValue());
            }
            return false;
        }
        return true;
    }

    private boolean readyToHit(LivingEntity t) {
        if (!canReachTarget(t) || !canUseHand()) return false;
        if (onlyTools.getValue() && !AttackHandler.isWeapon(player().getMainHandItem())) return false;
        ItemStack hand = player().getMainHandItem();
        if (!hand.isEmpty() && player().getCooldowns().isOnCooldown(hand)) return false;

        float strength = player().getAttackStrengthScale(0.5F);
        boolean groundNow = player().onGround() && !alwaysHittable();
        if (groundNow) {
            // На земле бьём только при заряде ровно 100%
            if (mode.is("Только криты") || strength < 1.0F) {
                groundReadyAtMs = 0;
                return false;
            }
            // ...и не раньше, чем через 30-100 мс после полного заряда
            long now = System.currentTimeMillis();
            if (groundReadyAtMs == 0) {
                groundReadyAtMs = now + 30L + random.nextInt(71);
            }
            return now >= groundReadyAtMs;
        }
        // В воздухе (падение): порог 86.000-93.000% с тысячными, а при 93%+ —
        // удар сразу, рандомный порог роли не играет.
        groundReadyAtMs = 0;
        int charge = Math.round(strength * 100000.0F);
        return AuraAttackTiming.canAttack(charge, airThresholdThousandths,
                player().onGround(), mode.is("Только криты"), alwaysHittable());
    }

    private void randomizeCooldown() {
        // Порог удара в падении: 86.000% - 93.000%, с точностью до тысячных процента
        airThresholdThousandths = 86_000 + random.nextInt(7_001);
    }

    private int findAxeSlot() {
        int selected = player().getInventory().getSelectedSlot();
        if (player().getInventory().getItem(selected).is(ItemTags.AXES)) return selected;
        for (int i = 0; i < 36; i++) {
            if (player().getInventory().getItem(i).is(ItemTags.AXES)) return i;
        }
        return -1;
    }

    private boolean prepareAxe(int axeSlot) {
        int prev = player().getInventory().getSelectedSlot();
        if (axeSlot == prev) return false;
        if (axeSlot < 9) {
            axeReturnSlot = prev;
            axeHotbarSlot = axeSlot;
            switchToSlot(axeSlot);
            return true;
        }
        if (player().containerMenu != player().inventoryMenu || mc.getConnection() == null) return false;
        int hotbarSlot = prev;
        for (int i = 0; i < 9; i++) {
            if (player().getInventory().getItem(i).isEmpty()) {
                hotbarSlot = i;
                break;
            }
        }
        mc.gameMode.handleContainerInput(player().inventoryMenu.containerId, axeSlot, hotbarSlot, ContainerInput.SWAP, player());
        if (!player().getInventory().getItem(hotbarSlot).is(ItemTags.AXES)) return false;
        axeReturnSlot = prev;
        axeHotbarSlot = hotbarSlot;
        axeInvSlot = axeSlot;
        switchToSlot(hotbarSlot);
        return true;
    }

    private void switchToSlot(int slot) {
        if (player().getInventory().getSelectedSlot() == slot) return;
        player().getInventory().setSelectedSlot(slot);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    private void restoreAxeSlot() {
        if (player() != null && mc.gameMode != null) {
            if (axeInvSlot >= 0 && axeHotbarSlot >= 0) {
                var menu = player().containerMenu;
                var source = menu.findSlot(player().getInventory(), axeInvSlot);
                // An open container has its own numbering for the player's inventory slots.
                if (source.isEmpty() || mc.getConnection() == null) return;
                if (player().getInventory().getItem(axeHotbarSlot).is(ItemTags.AXES)) {
                    mc.gameMode.handleContainerInput(menu.containerId, source.getAsInt(), axeHotbarSlot, ContainerInput.SWAP, player());
                }
            }
            if (axeReturnSlot >= 0) switchToSlot(axeReturnSlot);
        }
        axeReturnSlot = -1;
        axeHotbarSlot = -1;
        axeInvSlot = -1;
    }

    private boolean isEating() {
        if (!player().isUsingItem()) return false;
        ItemUseAnimation anim = player().getUseItem().getUseAnimation();
        return anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK;
    }

    public boolean isSprintBlocked() {
        return pendingHit;
    }

    private boolean hasLowHeadroom() {
        AABB box = player().getBoundingBox();
        AABB jumpSpace = new AABB(box.minX + 0.001, box.maxY, box.minZ + 0.001,
                box.maxX - 0.001, box.maxY + 0.5, box.maxZ - 0.001);
        return !mc.level.noBlockCollision(player(), jumpSpace);
    }

    /** Когда криты не нужны вообще — можно бить и без падения. */
    private boolean alwaysHittable() {
        return player().getPose() == Pose.SWIMMING
                || hasLowHeadroom()
                || player().getAbilities().flying
                || player().hasEffect(MobEffects.LEVITATION)
                || player().hasEffect(MobEffects.SLOW_FALLING)
                || player().hasEffect(MobEffects.BLINDNESS)
                || player().isInWater()
                || player().isUnderWater()
                || player().isInLava()
                || player().isFallFlying()
                || player().isPassenger()
                || player().onClimbable()
                || AttackHandler.isInCobweb(player());
    }

    /** Смотрит ли цель на нас (|Δyaw| < 90°) — щит ломаём только «в лоб». */
    private boolean targetFacingMe(LivingEntity t) {
        Vec3 toPlayer = player().getEyePosition().subtract(t.getEyePosition());
        float angleToPlayer = (float) (Math.toDegrees(Math.atan2(toPlayer.z, toPlayer.x)) - 90.0);
        return Math.abs(Mth.wrapDegrees(t.getYRot() - angleToPlayer)) < 90.0F;
    }
}
