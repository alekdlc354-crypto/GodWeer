package pub.godweer.cc;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

import static pub.godweer.IMinecraft.mc;

/**
 * Create by daun kvass
 */
public final class RayTraceUtils {

    private RayTraceUtils() {}


    public static Vec3 getLookVec(float yaw, float pitch) {
        float f = pitch * ((float) Math.PI / 180F);
        float f1 = -yaw * ((float) Math.PI / 180F);
        float cosYaw = Mth.cos(f1);
        float sinYaw = Mth.sin(f1);
        float cosPitch = Mth.cos(f);
        float sinPitch = Mth.sin(f);
        return new Vec3(sinYaw * cosPitch, -sinPitch, cosYaw * cosPitch);
    }

    public static boolean isLookingAt(Entity target, double maxRange, float yaw, float pitch) {
        return isLookingAt(target, maxRange, yaw, pitch, true);
    }

    public static boolean isLookingAt(Entity target, double maxRange, float yaw, float pitch, boolean checkWalls) {
        if (mc.player == null || mc.level == null || target == null) return false;

        Vec3 eyes = mc.player.getEyePosition();
        Vec3 reachVec = eyes.add(getLookVec(yaw, pitch).scale(maxRange));
        AABB box = target.getBoundingBox().inflate(target.getPickRadius() + 0.05);
        Optional<Vec3> hit = box.clip(eyes, reachVec);
        if (!box.contains(eyes) && hit.isEmpty()) return false;
        Vec3 hitPoint = box.contains(eyes) ? eyes : hit.get();
        if (!checkWalls) return true;

        BlockHitResult blockHit = mc.level.clip(new ClipContext(
                eyes, reachVec,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return blockHit.getType() == HitResult.Type.MISS
                || eyes.distanceToSqr(hitPoint) < eyes.distanceToSqr(blockHit.getLocation());
    }


    public static boolean canSeePoint(Vec3 from, Vec3 to) {
        if (mc.level == null) return false;
        HitResult hit = mc.level.clip(new ClipContext(
                from, to,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        return hit.getType() == HitResult.Type.MISS;
    }
}