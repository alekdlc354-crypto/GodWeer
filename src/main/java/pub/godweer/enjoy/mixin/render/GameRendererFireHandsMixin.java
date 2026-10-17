package pub.godweer.enjoy.mixin.render;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.kitty.module.impl.render.FireHands;

/**
 * FireHands Mixin - hooks into renderItemInHand for fire effect
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererFireHandsMixin {

    @Inject(method = "renderItemInHand", at = @At("HEAD"))
    private void firehands$before(CallbackInfo ci) {
        FireHands hands = Client.INSTANCE.moduleManager.getModule(FireHands.class);
        if (hands != null && hands.isEnabled()) {
            FireHands.onBeforeHands();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("TAIL"))
    private void firehands$after(CallbackInfo ci) {
        FireHands hands = Client.INSTANCE.moduleManager.getModule(FireHands.class);
        if (hands != null && hands.isEnabled()) {
            FireHands.onAfterHands();
        }
    }
}
