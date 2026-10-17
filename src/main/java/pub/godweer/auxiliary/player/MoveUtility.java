package pub.godweer.auxiliary.player;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import pub.godweer.cc.RotationHandler;
import pub.godweer.enjoy.event.list.PlayerInputEvent;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */

public final class MoveUtility {

    public static void fixMovement(PlayerInputEvent event, float targetYaw) {
        if (mc.player == null) return;

        Vec2 moveVec = event.getMoveVector();
        float forward = moveVec.y;
        float strafe = moveVec.x;

        if (forward == 0.0F && strafe == 0.0F) return;

        double angle = Mth.wrapDegrees((float) Math.toDegrees(direction(targetYaw, forward, strafe)));
        float serverYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : mc.player.getYRot();

        float closestForward = 0.0F, closestStrafe = 0.0F;
        double closestDiff = Double.MAX_VALUE;

        for (float predF = -1.0F; predF <= 1.0F; predF += 1.0F) {
            for (float predS = -1.0F; predS <= 1.0F; predS += 1.0F) {
                if (predS == 0.0F && predF == 0.0F) continue;
                double predAngle = Mth.wrapDegrees((float) Math.toDegrees(direction(serverYaw, predF, predS)));
                double diff = Math.abs(angle - predAngle);
                if (diff < closestDiff) {
                    closestDiff = diff;
                    closestForward = predF;
                    closestStrafe = predS;
                }
            }
        }

        event.setMoveVector(new Vec2(closestStrafe, closestForward));
        Input in = event.getKeyPresses();
        event.setKeyPresses(new Input(
                closestForward > 0.0F, closestForward < 0.0F,
                closestStrafe > 0.0F, closestStrafe < 0.0F,
                in.jump(), in.shift(), in.sprint()
        ));
    }

    /**
     * Focus-коррекция (вжимание в противника): только физика (moveVector),
     * клавиши не трогаем. Аналог одного EntityMixin из гайда без KeyboardInputMixin.
     */
    public static void fixMovementFocus(PlayerInputEvent event, float targetYaw) {
        if (mc.player == null) return;

        Vec2 moveVec = event.getMoveVector();
        float forward = moveVec.y;
        float strafe = moveVec.x;

        if (forward == 0.0F && strafe == 0.0F) return;

        double angle = Mth.wrapDegrees((float) Math.toDegrees(direction(targetYaw, forward, strafe)));
        float serverYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : mc.player.getYRot();

        float closestForward = 0.0F, closestStrafe = 0.0F;
        double closestDiff = Double.MAX_VALUE;

        for (float predF = -1.0F; predF <= 1.0F; predF += 1.0F) {
            for (float predS = -1.0F; predS <= 1.0F; predS += 1.0F) {
                if (predS == 0.0F && predF == 0.0F) continue;
                double predAngle = Mth.wrapDegrees((float) Math.toDegrees(direction(serverYaw, predF, predS)));
                double diff = Math.abs(angle - predAngle);
                if (diff < closestDiff) {
                    closestDiff = diff;
                    closestForward = predF;
                    closestStrafe = predS;
                }
            }
        }

        event.setMoveVector(new Vec2(closestStrafe, closestForward));
    }

    /**
     * Focus-вжимание: держим W в сторону цели — игрок сам идёт на противника.
     */
    public static void pressIntoTarget(PlayerInputEvent event, float targetYaw) {
        if (mc.player == null) return;
        float serverYaw = RotationHandler.isActive() ? RotationHandler.getServerYaw() : mc.player.getYRot();
        float need = Mth.wrapDegrees(targetYaw - serverYaw);
        Input in = event.getKeyPresses();
        if (in == null) return;
        if (Math.abs(need) <= 67.5F) {
            event.setMoveVector(new Vec2(0.0F, 1.0F));
            event.setKeyPresses(new Input(true, false, false, false, in.jump(), in.shift(), in.sprint()));
        } else if (need > 67.5F && need <= 157.5F) {
            event.setMoveVector(new Vec2(-1.0F, 0.0F));
            event.setKeyPresses(new Input(false, false, false, true, in.jump(), in.shift(), in.sprint()));
        } else if (need < -67.5F && need >= -157.5F) {
            event.setMoveVector(new Vec2(1.0F, 0.0F));
            event.setKeyPresses(new Input(false, false, true, false, in.jump(), in.shift(), in.sprint()));
        } else {
            event.setMoveVector(new Vec2(0.0F, 1.0F));
            event.setKeyPresses(new Input(true, false, false, false, in.jump(), in.shift(), in.sprint()));
        }
    }

    private static double direction(float yaw, double forward, double strafe) {
        if (forward < 0.0D) yaw += 180.0F;
        float f = 1.0F;
        if (forward < 0.0D) f = -0.5F;
        else if (forward > 0.0D) f = 0.5F;

        if (strafe > 0.0D) yaw -= 90.0F * f;
        if (strafe < 0.0D) yaw += 90.0F * f;
        return Math.toRadians(yaw);
    }
}