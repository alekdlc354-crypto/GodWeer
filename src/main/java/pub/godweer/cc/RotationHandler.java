package pub.godweer.cc;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;


/**
 * Create by daun kvass
 */
public final class RotationHandler {
    private static final Minecraft mc = Minecraft.getInstance();
    private static final float MOUSE_TURN_SCALE = 0.15F;

    public enum State {
        IDLE,
        ACTIVE,
        DISENGAGING
    }

    @Getter private static State state = State.IDLE;
    @Getter private static float freeYaw;
    @Getter private static float freePitch;
    private static boolean hasLastAngle;

    @Getter private static float serverYaw;
    @Getter private static float serverPitch;

    private static float lastYaw;
    private static float lastPitch;
    private static float lastHeadYaw;
    private static float lastBodyYaw;

    public static boolean isActive() {
        return state != State.IDLE;
    }


    public static void onPlayerTick(LocalPlayer player) {
        if (player == null) {
            clear();
            return;
        }

        if (state == State.DISENGAGING) {
            float deltaYaw = Mth.wrapDegrees(freeYaw - serverYaw);
            float deltaPitch = freePitch - serverPitch;

            if (Math.abs(deltaYaw) < 2.0F && Math.abs(deltaPitch) < 2.0F) {
                clear();
            } else {
                rememberRenderAngles(player);
                serverYaw += deltaYaw * 0.35F;
                serverPitch = Mth.clamp(serverPitch + deltaPitch * 0.35F, -90.0F, 90.0F);

                player.setYRot(serverYaw);
                player.setXRot(serverPitch);
                player.yHeadRot = serverYaw;
                player.yBodyRot = serverYaw;
            }
        }
    }

    public static void apply(float yawDelta, float pitchDelta) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (state == State.IDLE) {
            freeYaw = player.getYRot();
            freePitch = player.getXRot();
            serverYaw = player.getYRot();
            serverPitch = player.getXRot();
        }
        state = State.ACTIVE;

        rememberRenderAngles(player);

        serverYaw = serverYaw + yawDelta;
        serverPitch = Mth.clamp(serverPitch + pitchDelta, -90.0F, 90.0F);

        player.setYRot(serverYaw);
        player.setXRot(serverPitch);
        player.yHeadRot = serverYaw;
        player.yBodyRot = serverYaw;
    }

    public static void setRotation(float yaw, float pitch) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (state == State.IDLE) {
            freeYaw = player.getYRot();
            freePitch = player.getXRot();
            serverYaw = player.getYRot();
            serverPitch = player.getXRot();
        }
        state = State.ACTIVE;

        float targetPitch = Mth.clamp(pitch, -90.0F, 90.0F);
        float deltaYaw = Mth.wrapDegrees(yaw - serverYaw);
        float deltaPitch = targetPitch - serverPitch;

        double sens = mc.options.sensitivity().get() * 0.6D + 0.2D;
        double gcdStep = sens * sens * sens * 1.2D;
        if (gcdStep >= 1.0E-4D) {
            deltaYaw = (float) (Math.round((double) deltaYaw / gcdStep) * gcdStep);
            deltaPitch = (float) (Math.round((double) deltaPitch / gcdStep) * gcdStep);
        }

        apply(deltaYaw, deltaPitch);
    }

    public static void syncFreeLook(float yaw, float pitch) {
        if (isActive()) return;
        freeYaw = yaw;
        freePitch = pitch;
    }

    public static boolean onMouseTurn(double yawInput, double pitchInput) {
        if (!isActive()) return false;

        freeYaw += (float) yawInput * MOUSE_TURN_SCALE;
        freePitch = Mth.clamp(freePitch + (float) pitchInput * MOUSE_TURN_SCALE, -90.0F, 90.0F);
        return true;
    }

    public static void applyRenderInterpolation() {
        if (!isActive() || !hasLastAngle) return;
        LocalPlayer player = mc.player;
        if (player == null) {
            hasLastAngle = false;
            return;
        }

        player.yRotO = lastYaw;
        player.xRotO = lastPitch;
        player.yHeadRotO = lastHeadYaw;
        player.yBodyRotO = lastBodyYaw;
    }


    public static void disengage(String mode) {
        if (state == State.IDLE) return;

        if (mode.equalsIgnoreCase("Instant")) {
            clear();
        } else {
            state = State.DISENGAGING;
        }
    }

    public static void clear() {
        LocalPlayer player = mc.player;
        state = State.IDLE;
        hasLastAngle = false;

        if (player != null) {
            serverYaw = player.getYRot();
            serverPitch = player.getXRot();
            freeYaw = player.getYRot();
            freePitch = player.getXRot();
        }
    }

    private static void rememberRenderAngles(LocalPlayer player) {
        lastYaw = player.getYRot();
        lastPitch = player.getXRot();
        lastHeadYaw = player.yHeadRot;
        lastBodyYaw = player.yBodyRot;
        hasLastAngle = true;
    }
}