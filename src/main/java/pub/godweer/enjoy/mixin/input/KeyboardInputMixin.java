package pub.godweer.enjoy.mixin.input;

import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pub.godweer.Client;
import pub.godweer.enjoy.event.list.PlayerInputEvent;
import pub.godweer.kitty.module.impl.misc.FreeCam;
import pub.godweer.kitty.module.impl.player.GuiWalk;
import pub.godweer.auxiliary.player.MoveBlockUtility;

/**
 * Create by daun kvass
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("HEAD"))
    private void onInputTickHead(CallbackInfo ci) {
        MoveBlockUtility.onTick();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onPlayerInputTick(CallbackInfo ci) {
        if (FreeCam.INSTANCE != null && FreeCam.INSTANCE.isEnabled()) {
            this.keyPresses = new Input(false, false, false, false, false, false, false);
            this.moveVector = new Vec2(0.0F, 0.0F);
            return;
        }

        Input screenInput = GuiWalk.screenInput();
        if (screenInput != null) {
            this.keyPresses = screenInput;
            this.moveVector = new Vec2(
                    refrection(screenInput.left(), screenInput.right()),
                    refrection(screenInput.forward(), screenInput.backward())
            );
        }

        if (MoveBlockUtility.isFrozen()) {
            this.moveVector = new Vec2(0.0F, 0.0F);
            this.keyPresses = new Input(false, false, false, false, false, this.keyPresses.shift(), false);
        } else if (MoveBlockUtility.isSprintBlocked()) {
            Input cur = this.keyPresses;
            this.keyPresses = new Input(cur.forward(), cur.backward(), cur.left(), cur.right(), false, cur.shift(), false);
        }

        PlayerInputEvent event = new PlayerInputEvent((KeyboardInput) (Object) this, this.keyPresses, this.getMoveVector());
        Client.getInstance().getEventManager().call(event);
        this.keyPresses = event.getKeyPresses();
        this.moveVector = event.getMoveVector();
    }

    private static float refrection(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        }
        return positive ? 1.0F : -1.0F;
    }
}