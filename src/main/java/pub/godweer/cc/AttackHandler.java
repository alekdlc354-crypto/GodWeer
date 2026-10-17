package pub.godweer.cc;

import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import pub.godweer.enjoy.event.list.PlayerInputEvent;
import pub.godweer.kitty.module.impl.combat.AuraModule;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */
public class AttackHandler {

    private static long lastAttackTime = 0L;

    public static void reset() {
    }

    public static boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.PICKAXES)
                || stack.is(ItemTags.SHOVELS)
                || stack.is(ItemTags.HOES)
                || stack.is(Items.TRIDENT)
                || stack.is(Items.MACE);
    }

    public static boolean isInAttackRange(Player player, LivingEntity target, AuraModule aura) {
        if (player.getBoundingBox().intersects(target.getBoundingBox())) {
            return true;
        }

        if (player.isFallFlying() && target.isFallFlying() && aura.getElytraPredict().getValue()) {
            Vec3 predPos = aura.getPredictedElytraPos() != null
                    ? aura.getPredictedElytraPos()
                    : PredictUtils.realPredict(target, aura.getPredictType());
            return player.getEyePosition().distanceTo(predPos) <= 3.5f;
        }
        return player.distanceTo(target) <= aura.getAttackRange().getValue();
    }

    public static boolean isInCobweb(Player player) {
        if (mc.level == null || player == null) return false;
        return mc.level.getBlockState(player.blockPosition()).is(Blocks.COBWEB)
                || mc.level.getBlockState(player.blockPosition().above()).is(Blocks.COBWEB);
    }


    public static boolean canCritical(Player player, AuraModule aura) {
        if (!aura.getOnlyCrits().getValue()) return true;

        boolean bypassCrit = player.getAbilities().flying
                || player.hasEffect(MobEffects.LEVITATION)
                || player.hasEffect(MobEffects.BLINDNESS)
                || player.hasEffect(MobEffects.SLOW_FALLING)
                || player.isInWater()
                || player.isUnderWater()
                || player.isInLava()
                || player.onClimbable()
                || isInCobweb(player);

        if (bypassCrit) {
            return true;
        }

        if (aura.getSmartCrits().getValue()&& player.onGround() && !mc.options.keyJump.isDown()) {
            return true;
        }

        float minFall = 0.0f;
        if (aura.getRandomFallDistance().getValue()) {
            minFall = (float) (Math.random() > 0.75 ? (0.35 + Math.random() * 0.35) : (Math.random() * 0.08));
        }

        return !player.onGround() && (player.fallDistance > minFall || player.getDeltaMovement().y < -0.0784);
    }


    public static void                handleInput(PlayerInputEvent event, AuraModule aura) {
        Player player = mc.player;
        if (player == null || aura == null) return;

        LivingEntity target = aura.getTarget();
        boolean readyToHit = target != null
                && isInAttackRange(player, target, aura)
                && player.getAttackStrengthScale(0.5f) >= 0.92f;

        String sprintMode = aura.getSprintReset().getValue();
        if (sprintMode.equalsIgnoreCase("Legit") && !player.isInLava() && !player.isInWater()) {
            boolean shouldResetLegit = readyToHit && !player.onGround() && player.getDeltaMovement().y < 0;

            if (shouldResetLegit && event.getKeyPresses() != null && event.getKeyPresses().forward()) {
                Input in = event.getKeyPresses();
                event.setKeyPresses(new Input(
                        false,
                        in.backward(),
                        in.left(),
                        in.right(),
                        in.jump(),
                        in.shift(),
                        false
                ));

                if (event.getMoveVector() != null) {
                    event.setMoveVector(new Vec2(0.0f, event.getMoveVector().y));
                }

                player.setSprinting(false);
            }
        }
    }

    public static boolean shouldAttack(LivingEntity target, AuraModule aura) {
        Player player = mc.player;
        if (player == null || target == null) return false;

        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && player.getCooldowns().isOnCooldown(mainHand)) {
            return false;
        }

        if (!isInAttackRange(player, target, aura)) {
            return false;
        }

        boolean onElytra = player.isFallFlying() && target.isFallFlying();
        float cooldown = player.getAttackStrengthScale(0.5f);

        if (onElytra) {
            if (cooldown < 0.90f) return false;
        } else {
            boolean hasWeapon = isWeapon(mainHand);
            if (!hasWeapon) {
                if (System.currentTimeMillis() - lastAttackTime < 600L || cooldown < 1.0f) {
                    return false;
                }
            } else {
                float reqCooldown = player.onGround() ? 0.95f : 0.92f;
                if (cooldown < reqCooldown) {
                    return false;
                }
            }
        }

        return canCritical(player, aura);
    }

    public static void attack(LivingEntity target, AuraModule aura, AuraRotation rotation) {
        Player player = mc.player;
        if (player == null || mc.gameMode == null || mc.getConnection() == null) return;

        lastAttackTime = System.currentTimeMillis();

        String sprintMode = aura.getSprintReset().getValue();
        boolean isPacket = sprintMode.equalsIgnoreCase("Packet");
        boolean wasSprinting = player.isSprinting();

        if (isPacket && wasSprinting) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        }

        player.setSprinting(false);

        mc.gameMode.attack(player, target);
        player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);

        if (isPacket && wasSprinting) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
            player.setSprinting(true);
        }

        if (rotation != null) {
            rotation.onAttack();
        }

        player.resetAttackStrengthTicker();
    }
}