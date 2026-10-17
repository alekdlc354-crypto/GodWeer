package pub.godweer.kitty.module.impl.movement;

import net.minecraft.world.effect.MobEffects;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.PlayerTickEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.impl.combat.AttackAura;

/**
 * Create by daun kvass
 */
public class Sprint extends Module {

    public Sprint() {
        super("Sprint", "Само за тебя Стантит", Category.MOVEMENT);
    }

    @EventTarget
    public void onTick(PlayerTickEvent event) {
        if (!inGame() || player() == null) return;
        AttackAura aura = AttackAura.INSTANCE;
        if (aura != null && aura.isEnabled() && aura.isSprintBlocked()) {
            // Во время остановки перед ударом спринт не форсим —
            // ваниль сам снимет его из-за отсутствия движения.
            return;
        }
        boolean canSprint = mc.options.keyUp.isDown()
                && !player().isShiftKeyDown()
                && !player().hasEffect(MobEffects.BLINDNESS)
                && player().getFoodData().getFoodLevel() > 6;

        if (canSprint) {
            player().setSprinting(true);
        }
    }
}
