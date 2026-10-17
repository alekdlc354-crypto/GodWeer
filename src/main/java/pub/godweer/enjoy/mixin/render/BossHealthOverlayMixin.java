package pub.godweer.enjoy.mixin.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.hud.ServerGui;

import java.util.Map;
import java.util.UUID;

/**
 * Create by daun kvass
 */
@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {

    @Shadow
    @Final
    private Map<UUID, LerpingBossEvent> events;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void onExtractRenderState(GuiGraphicsExtractor extractor, CallbackInfo ci) {
        if (ServerGui.isEnabled()) {
            ci.cancel();
            if (!this.events.isEmpty()) {
                ServerGui.renderBossBars(this.events);
            }
        }
    }
}