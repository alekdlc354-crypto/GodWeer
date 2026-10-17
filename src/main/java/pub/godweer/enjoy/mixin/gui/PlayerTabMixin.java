package pub.godweer.enjoy.mixin.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.hud.ServerGui;

/**
 * Vanilla tab list is replaced by the custom server GUI.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onExtractRenderState(GuiGraphicsExtractor extractor, int i, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if (ServerGui.isEnabled()) {
            ci.cancel();
        }
    }
}
