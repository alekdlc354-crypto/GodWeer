package pub.godweer.kitty.module.impl.render;

import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import pub.godweer.enjoy.event.EventTarget;
import pub.godweer.enjoy.event.list.AttackEvent;
import pub.godweer.enjoy.event.list.PacketEvent;
import pub.godweer.enjoy.event.list.Render3DEvent;
import pub.godweer.kitty.module.Category;
import pub.godweer.kitty.module.Module;
import pub.godweer.kitty.module.settings.impl.CheckBox;
import pub.godweer.kitty.module.settings.impl.ColorSetting;
import pub.godweer.kitty.module.settings.impl.ModeSetting;
import pub.godweer.kitty.module.settings.impl.SliderSetting;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.world.module.PopEffectRenderer;

/**
 * Incredible PopEffect - stunning visual effects for hits, kills and totem pops
 * Created for pure aesthetic pleasure
 */
public final class PopEffect extends Module {

    // === General ===
    public final CheckBox onlyPlayers = checkbox("Only Players", true);
    public final CheckBox themeColor = checkbox("Theme Color", true);

    // === Hit Effect ===
    public final ModeSetting hitEffect = mode("Hit Effect", "Explosion", "Explosion", "Lightning", "Off");
    public final ColorSetting hitColor = color("Hit Color", ColorUtil.rgba(255, 80, 140, 255)).visible(() -> !themeColor.getValue());
    public final SliderSetting hitCount = slider("Hit Particles", 40.0F, 10.0F, 150.0F, 5.0F).visible(() -> !isHitOff());
    public final SliderSetting hitSize = slider("Hit Size", 0.3F, 0.1F, 1.0F, 0.05F).visible(() -> !isHitOff());
    public final SliderSetting hitLifetime = slider("Hit Lifetime", 1.0F, 0.3F, 3.0F, 0.1F).visible(() -> !isHitOff());

    // === Kill Effect ===
    public final ModeSetting killEffect = mode("Kill Effect", "Beam", "Beam", "Explosion", "Off");
    public final ColorSetting killColor = color("Kill Color", ColorUtil.rgba(255, 50, 50, 255)).visible(() -> !themeColor.getValue());
    public final SliderSetting killCount = slider("Kill Particles", 60.0F, 15.0F, 200.0F, 5.0F).visible(() -> !isKillOff());
    public final SliderSetting killSize = slider("Kill Size", 0.4F, 0.1F, 1.2F, 0.05F).visible(() -> !isKillOff());
    public final SliderSetting killLifetime = slider("Kill Lifetime", 2.2F, 0.5F, 5.0F, 0.1F).visible(() -> !isKillOff());

    // === Totem Pop Effect ===
    public final ModeSetting totemEffect = mode("Totem Effect", "Spiral", "Spiral", "Beam", "Explosion", "Off");
    public final SliderSetting totemCount = slider("Totem Particles", 80.0F, 20.0F, 250.0F, 5.0F).visible(() -> !isTotemOff());
    public final SliderSetting totemSize = slider("Totem Size", 0.35F, 0.1F, 1.0F, 0.05F).visible(() -> !isTotemOff());
    public final SliderSetting totemLifetime = slider("Totem Lifetime", 2.5F, 0.5F, 5.0F, 0.1F).visible(() -> !isTotemOff());

    // === Global ===
    public final SliderSetting speed = slider("Speed", 1.0F, 0.2F, 3.0F, 0.1F);
    public final SliderSetting brightness = slider("Brightness", 3.5F, 1.0F, 8.0F, 0.5F);
    public final CheckBox throughWalls = checkbox("Through Walls", true);

    private final PopEffectRenderer renderer = new PopEffectRenderer();

    public PopEffect() {
        super("PopEffect", "Incredible visual effects for hits, kills and totem pops", Category.RENDER);
    }

    public PopEffectRenderer getRenderer() {
        return this.renderer;
    }

    public boolean hasActiveEffects() {
        return this.renderer.hasActive();
    }

    // === Color getters ===
    public int getHitColor() {
        return this.themeColor.getValue() ? Theme.getAccentColor() : this.hitColor.getValue();
    }

    public int getKillColor() {
        return this.themeColor.getValue() ? Theme.getAccentColor() : this.killColor.getValue();
    }

    // === Visibility predicates ===
    private boolean isHitOff() {
        return "Off".equalsIgnoreCase(hitEffect.getValue());
    }

    private boolean isKillOff() {
        return "Off".equalsIgnoreCase(killEffect.getValue());
    }

    private boolean isTotemOff() {
        return "Off".equalsIgnoreCase(totemEffect.getValue());
    }

    // === Render ===
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null) return;
        this.renderer.render(this, event.getDeltaTracker().getGameTimeDeltaPartialTick(false));
    }

    // === Attack (Hit) ===
    @EventTarget
    public void onAttack(AttackEvent event) {
        if (mc.level == null) return;
        Entity target = event.getTarget();
        if (target == null) return;
        if (!(target instanceof LivingEntity living)) return;
        if (this.onlyPlayers.getValue() && !(living instanceof Player)) return;

        String mode = this.hitEffect.getValue();
        if ("Off".equalsIgnoreCase(mode)) return;

        Vec3 pos = living.position().add(0, living.getBbHeight() * 0.5, 0);
        this.renderer.spawnHitEffect(pos, this);
    }

    // === Totem Pop (Packet 35) ===
    @EventTarget
    public void onPacket(PacketEvent event) {
        if (mc.level == null) return;

        if (event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() == 35) {
                Entity entity = packet.getEntity(mc.level);
                if (entity != null) {
                    onTotemPop(entity);
                }
            }
        }
    }

    public void onTotemPop(Entity entity) {
        if (entity == null) return;
        if (this.onlyPlayers.getValue() && !(entity instanceof Player)) return;

        String mode = this.totemEffect.getValue();
        if ("Off".equalsIgnoreCase(mode)) return;

        this.renderer.spawnTotemPop(entity, this);
    }

    // === Kill ===
    public void onEntityKill(Entity entity) {
        if (entity == null) return;
        if (this.onlyPlayers.getValue() && !(entity instanceof Player)) return;

        String mode = this.killEffect.getValue();
        if ("Off".equalsIgnoreCase(mode)) return;

        Vec3 pos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
        this.renderer.spawnKillEffect(pos, this);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.renderer.clear();
    }
}
