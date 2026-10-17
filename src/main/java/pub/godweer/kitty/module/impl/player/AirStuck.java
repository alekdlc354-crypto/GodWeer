package pub.godweer.kitty.module.impl.player;

import lombok.Getter;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.EventTravel;
import pub.godweer.enjoy.event.list.PacketEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.ModeSetting;

/**
 * Create by daun kvass
 */
@Getter
public class AirStuck extends Module {

    public final ModeSetting mode = mode("Mode", "Motion", "Motion", "CancelPacket");

    public AirStuck() {
        super("AirStuck", "Замораживает тебя как глыбу", Category.PLAYER);
    }

    @EventTarget
    public void onTravel(EventTravel event) {
        event.cancel();
    }

    @EventTarget
    public void onPacket(PacketEvent event) {
        if (!event.isSend()) return;

        if (event.is(ServerboundMovePlayerPacket.class) && mode.getValue().equalsIgnoreCase("CancelPacket")) {
            event.cancel();
        }
    }
}