package pub.godweer.enjoy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.rendertype.RenderType;
import pub.godweer.cc.Angle;
import pub.godweer.cc.PredictUtils;
import pub.godweer.kitty.module.impl.combat.AuraModule;
import pub.godweer.kitty.module.impl.render.ChamsHook;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends EntityRenderer<T, S> {

    protected LivingEntityRendererMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Unique private final StopWatch elytraRotationTimer = new StopWatch();
    @Unique private final StopWatch elytraGraceTimer = new StopWatch();
    @Unique private boolean elytraFacingTarget = false;
    @Unique private boolean elytraActive = false;
    @Unique private boolean elytraVisualInitialized = false;
    @Unique private float elytraVisualYaw;
    @Unique private float elytraVisualPitch;
    @Unique private float elytraVisualHeadYaw;
    @Unique private long elytraLastFrameTime = System.currentTimeMillis();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void applyElytraVisualRotation(T livingEntity, S renderState, float partialTicks, CallbackInfo ci) {
        LocalPlayer self = Minecraft.getInstance().player;
        if (self == null || livingEntity != self) return;

        float deltaSeconds = getFrameDeltaSeconds();
        float baseBodyYaw = renderState.bodyRot;
        float baseRelativeHeadYaw = renderState.yRot;
        float basePitch = renderState.xRot;

        AuraModule aura = AuraModule.INSTANCE;
        boolean enabled = aura != null && aura.isEnabled();
        LivingEntity target = enabled ? aura.getTarget() : null;

        boolean shouldRotate = false;
        float targetYaw = baseBodyYaw;
        float targetPitch = basePitch;

        if (enabled && livingEntity.isFallFlying() && target != null && target.isAlive() && target.isFallFlying()) {
            Vec3 playerPos = livingEntity.getPosition(partialTicks);
            Vec3 targetPos = target.getPosition(partialTicks);
            Vec3 targetLook = target.getViewVector(partialTicks).normalize();
            Vec3 targetToPlayer = playerPos.subtract(targetPos);
            double dot = targetToPlayer.dot(targetLook);

            PredictUtils.Type type = aura.getPredictType();
            Vec3 predict = PredictUtils.realPredict(target, type);
            double distToPredict = playerPos.distanceTo(predict);

            if (dot > 0.0 && distToPredict < 6.0) {
                shouldRotate = true;
                Angle rotation = Angle.fromVec(playerPos.add(0, self.getEyeHeight(), 0), targetPos.add(0.0, target.getBbHeight() * 0.5, 0.0));
                targetYaw = rotation.getYaw();
                targetPitch = rotation.getPitch();
            }
        }

        if (shouldRotate) {
            elytraGraceTimer.reset();

            if (!elytraActive) {
                elytraActive = true;
                elytraFacingTarget = true;
                elytraRotationTimer.reset();
                if (!elytraVisualInitialized) {
                    elytraVisualYaw = baseBodyYaw;
                    elytraVisualPitch = basePitch;
                    elytraVisualHeadYaw = baseRelativeHeadYaw;
                    elytraVisualInitialized = true;
                }
            } else if (elytraRotationTimer.finished(300)) {
                elytraFacingTarget = !elytraFacingTarget;
                elytraRotationTimer.reset();
            }
        } else {
            if (elytraActive) {
                if (elytraGraceTimer.finished(150)) {
                    elytraActive = false;
                    elytraFacingTarget = false;
                }
            }
        }

        if (elytraVisualInitialized) {
            float desiredYaw = elytraFacingTarget ? targetYaw : baseBodyYaw;
            float desiredPitch = elytraFacingTarget ? targetPitch : basePitch;
            float desiredHeadYaw = elytraFacingTarget ? 0.0F : baseRelativeHeadYaw;
            float smoothStep = Mth.clamp(deltaSeconds * 25.0F, 0.0F, 1.0F);

            elytraVisualYaw = Mth.rotLerp(smoothStep, elytraVisualYaw, desiredYaw);
            elytraVisualPitch = Mth.lerp(smoothStep, elytraVisualPitch, desiredPitch);
            elytraVisualHeadYaw = Mth.lerp(smoothStep, elytraVisualHeadYaw, desiredHeadYaw);

            renderState.bodyRot = elytraVisualYaw;
            renderState.xRot = elytraVisualPitch;
            renderState.yRot = elytraVisualHeadYaw;

            if (!elytraFacingTarget && !elytraActive) {
                float yawDiff = Math.abs(Mth.wrapDegrees(elytraVisualYaw - baseBodyYaw));
                float pitchDiff = Math.abs(elytraVisualPitch - basePitch);
                if (yawDiff < 1.0F && pitchDiff < 1.0F) {
                    elytraVisualInitialized = false;
                }
            }
        }
    }

    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void godweerChamsRenderType(LivingEntityRenderState renderState, boolean b1, boolean b2, boolean b3,
                                        CallbackInfoReturnable<RenderType> cir) {
        RenderType chams = ChamsHook.renderType(renderState);
        if (chams != null) cir.setReturnValue(chams);
    }

    @Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
    private void godweerChamsTint(LivingEntityRenderState renderState, CallbackInfoReturnable<Integer> cir) {
        Integer tint = ChamsHook.tint(renderState);
        if (tint != null) cir.setReturnValue(tint);
    }

    /**
     * Through-walls ghost pass of the chams body: wraps the vanilla
     * submitModel call and, when Through Walls is on, emits an extra
     * depth-ignoring draw before the normal depth-tested one. The main
     * render type is always the normal (depth-tested) chams type.
     */
    @WrapOperation(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/UvMapping;I)V"
            )
    )
    @SuppressWarnings({"unchecked", "rawtypes", "RedundantCast"})
    private void godweerChamsBodySecondPass(SubmitNodeCollector collector,
                                            Model model,
                                            Object state,
                                            com.mojang.blaze3d.vertex.PoseStack poseStack,
                                            RenderType renderType,
                                            int i1,
                                            int i2,
                                            int i3,
                                            net.minecraft.client.renderer.texture.UvMapping uvMapping,
                                            int outline,
                                            Operation<Void> original) {
        if (state instanceof LivingEntityRenderState living) {
            RenderType through = ChamsHook.throughType(living);
            Integer throughTint = ChamsHook.throughTint(living);
            if (through != null && throughTint != null) {
                collector.order(living.outlineColor).submitModel(
                        model,
                        living,
                        poseStack,
                        through,
                        i1,
                        i2,
                        throughTint,
                        null,
                        outline
                );
            }
        }
        original.call(collector, model, state, poseStack, renderType, i1, i2, i3, uvMapping, outline);
    }

    @Unique
    private float getFrameDeltaSeconds() {
        long now = System.currentTimeMillis();
        float deltaSeconds = Mth.clamp((now - elytraLastFrameTime) / 1000.0F, 0.0F, 0.1F);
        elytraLastFrameTime = now;
        return deltaSeconds;
    }

    @Unique
    private static class StopWatch {
        private long time = System.currentTimeMillis();
        public boolean finished(long ms) { return System.currentTimeMillis() - time >= ms; }
        public void reset() { time = System.currentTimeMillis(); }
    }
}