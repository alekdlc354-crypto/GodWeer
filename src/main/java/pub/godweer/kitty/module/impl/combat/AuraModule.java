package pub.godweer.kitty.module.impl.combat;

import lombok.Getter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import pub.godweer.cc.*;
import pub.godweer.cc.UBoxPoints.TargetPoint;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.PlayerInputEvent;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.kitty.builder.BuilderPoints;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.*;
import pub.godweer.auxiliary.client.clients.Targets;
import pub.godweer.auxiliary.player.MoveUtility;

/**
 * Create by daun kvass
 */
@Getter
public class AuraModule extends Module {
    public static AuraModule INSTANCE;

    private final SliderSetting attackRange = slider("Attack Range", 3.0f, 1.0f, 6.0f, 0.1f);
    private final SliderSetting aimRange = slider("Aim Range", 4.5f, 1.0f, 8.0f, 0.1f);

    private final ModeSetting rotMode = mode("Rotation", RotationRegistry.getNames()[0], RotationRegistry.getNames());
    private final ModeSetting moveFix = mode("Movement Correction", "Silent", "Silent", "Current");
    private final ModeSetting disengageMode = mode("Disengage", "Smooth", "Smooth", "Instant");
    private final ModeSetting sprintReset = mode("Sprint Reset", "Legit", "None", "Legit", "Packet");
    private final MultiModeSetting targets = multiMode("Targets", "Players", "Mobs", "Animals", "Naked", "Friends", "Villagers");

    private final HeaderSetting elis = header("Elytra");
    private final CheckBox elytraPredict = checkbox("Elytra Predict", true);
    private final ModeSetting predictType = mode("Predict Type", "Default", "Default", "Limit").visible(elytraPredict::getValue);
    private final SliderSetting distancelytra = slider("Elytra Distance", 15.0f, 1.0f, 40.0f, 1.0f).visible(elytraPredict::getValue);

    private final HeaderSetting dopa = header("Settings");
    private final CheckBox raytrace = checkbox("Raytrace", true);
    private final CheckBox aimThroughWalls = checkbox("Aim Through Walls", false);
    private final CheckBox throughWalls = checkbox("Through Walls", false);
    private final CheckBox onlyCrits = checkbox("Only Crits", true);
    private final CheckBox smartCrits = checkbox("Smart Crits", true).visible(onlyCrits::getValue);
    private final CheckBox randomFallDistance = checkbox("Random Fall Distance", false).visible(onlyCrits::getValue);
    private final CheckBox pauseEating = checkbox("Pause while Eating", true);

    private final ModeSetting targetEsp = mode("Target ESP", "Marker", "None", "Marker", "Ghosts", "Circle", "Cube");

    private LivingEntity target = null;
    private Vec3 predictedElytraPos = null;

    public AuraModule() {
        super("Aura", "ффф", Category.COMBAT);
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        target = null;
        predictedElytraPos = null;
        RotationHandler.disengage(disengageMode.getValue());
        AttackHandler.reset();
        Targets.reset();
    }

    public PredictUtils.Type getPredictType() {
        return predictType.getValue().equalsIgnoreCase("Limit") ? PredictUtils.Type.LIMIT : PredictUtils.Type.DEFAULT;
    }

    public float getElytraRangeBonus() {
        if (player() != null && player().getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && player().isFallFlying()) {
            return distancelytra.getValue();
        }
        return 0.0f;
    }

    public boolean isEating() {
        if (player() == null || !player().isUsingItem()) return false;
        ItemUseAnimation anim = player().getUseItem().getUseAnimation();
        return anim == ItemUseAnimation.EAT || anim == ItemUseAnimation.DRINK;
    }

    private boolean shouldWaitForPostPacketCrit() {
        if (Criticals.INSTANCE == null || !Criticals.INSTANCE.isEnabled()) return false;
        if (!player().hasEffect(MobEffects.SLOW_FALLING)) return false;
        if (AttackHandler.isInCobweb(player())) return false;

        return !player().onGround();
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!inGame() || player() == null || mc.gameMode == null) return;

        if (event.getPhase() == PlayerTickEvent.Phase.PRE) {
            float maxFindDist = aimRange.getValue() + getElytraRangeBonus();
            target = Targets.findTarget(maxFindDist, targets);

            if (target == null) {
                predictedElytraPos = null;
                RotationHandler.disengage(disengageMode.getValue());
                AttackHandler.reset();
                return;
            }

            Vec3 aimPos;
            boolean isVisible;

            if (player().isFallFlying() && target.isFallFlying() && elytraPredict.getValue()) {
                predictedElytraPos = PredictUtils.realPredict(target, getPredictType());
                aimPos = predictedElytraPos;
                isVisible = RayTraceUtils.canSeePoint(player().getEyePosition(), aimPos);
            } else {
                predictedElytraPos = null;
                TargetPoint targetPoint;
                if (rotMode.getValue().equalsIgnoreCase("Builder")) {
                    targetPoint = BuilderPoints.getBestPoint(player(), target);
                } else {
                    targetPoint = UBoxPoints.getBestPoint(target);
                }

                if (targetPoint == null) {
                    RotationHandler.disengage(disengageMode.getValue());
                    return;
                }
                aimPos = targetPoint.point();
                isVisible = targetPoint.isVisible();
            }

            boolean canAimThroughWalls = this.aimThroughWalls.getValue() || this.throughWalls.getValue();

            if (!canAimThroughWalls && !isVisible) {
                RotationHandler.disengage(disengageMode.getValue());
                return;
            }

            AuraRotation rotation = RotationRegistry.get(rotMode.getValue());
            boolean canAttackNow = AttackHandler.shouldAttack(target, this) && !(pauseEating.getValue() && isEating());

            rotation.tick(player(), target, aimPos, canAttackNow);

            if (!shouldWaitForPostPacketCrit()) {
                processAttack(isVisible, rotation);
            }
        }

        if (event.getPhase() == PlayerTickEvent.Phase.POST) {
            if (shouldWaitForPostPacketCrit() && player().fallDistance > 0 && player().fallDistance < 1) {
                if (target != null) {
                    boolean isVisible = RayTraceUtils.canSeePoint(player().getEyePosition(), target.position());
                    AuraRotation rotation = RotationRegistry.get(rotMode.getValue());
                    processAttack(isVisible, rotation);
                }
            }
        }
    }

    private void processAttack(boolean isVisible, AuraRotation rotation) {
        if (target == null) return;

        if (pauseEating.getValue() && isEating()) return;

        if (!AttackHandler.shouldAttack(target, this)) return;

        boolean isIntersecting = player().getBoundingBox().intersects(target.getBoundingBox());

        float checkYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : player().getYRot();
        float checkPitch = RotationHandler.isActive() ? RotationHandler.getServerPitch() : player().getXRot();
        boolean isAimed = isIntersecting || !raytrace.getValue() || RayTraceUtils.isLookingAt(target, attackRange.getValue(), checkYaw, checkPitch);
        if (!isAimed) return;

        boolean canHit = isIntersecting || this.throughWalls.getValue() || isVisible;
        if (!canHit) return;

        AttackHandler.attack(target, this, rotation);
    }

    @EventTarget
    public void onInput(PlayerInputEvent event) {
        if (!inGame() || player() == null) return;

        AttackHandler.handleInput(event, this);

        if (target != null && RotationHandler.isActive()) {
            if (moveFix.getValue().equalsIgnoreCase("Silent")) {
                MoveUtility.fixMovement(event, RotationHandler.getFreeYaw());
            }
        }
    }
    public boolean usesCubesTargetEsp() { return targetEsp.getValue().equalsIgnoreCase("Cube"); }
    public boolean hasTargetEsp() { return !targetEsp.getValue().equalsIgnoreCase("None"); }
    public boolean usesMarkerTargetEsp() { return targetEsp.getValue().equalsIgnoreCase("Marker"); }
    public boolean usesGhostTargetEsp() { return targetEsp.getValue().equalsIgnoreCase("Ghosts"); }
    public boolean usesCircleTargetEsp() { return targetEsp.getValue().equalsIgnoreCase("Circle"); }
}