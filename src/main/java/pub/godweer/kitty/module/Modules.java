package pub.godweer.kitty.module;

import lombok.Getter;
import com.mojang.blaze3d.platform.InputConstants;
import pub.godweer.kitty.module.impl.combat.*;
import pub.godweer.kitty.module.impl.misc.*;
import pub.godweer.kitty.module.impl.movement.*;
import pub.godweer.kitty.module.impl.player.*;
import pub.godweer.kitty.module.impl.render.*;
import pub.godweer.kitty.module.impl.render.GlowHands;
import pub.godweer.kitty.module.impl.render.ESP;
import pub.godweer.kitty.module.impl.render.StorageESP;
import pub.godweer.kitty.module.impl.render.Tracers;
import pub.godweer.kitty.module.impl.render.Trajectories;
import pub.godweer.kitty.module.impl.render.HoleESP;
import pub.godweer.kitty.module.impl.misc.XRay;
import pub.godweer.kitty.module.impl.misc.Zoom;
import pub.godweer.kitty.module.impl.misc.FovChanger;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Create by daun kvass
 */
@Getter
public class Modules {
    private final List<Module> modules = new ArrayList<>();
    public ClickGui clickGui;
    public Sprint sprint;
    public ElytraHelper elytraHelper;
    public AuraModule auraModule;
    public NameTags nameTags;
    public Interface anInterface;
    public GuiWalk guiWalk;
    public Removals removals;
    public Rain rain;
    public Ambience ambience;
    public ShaderSky shaderSky;
    public AirStuck airStuck;
    public ServerHelper serverHelper;
    public WaterSpeed waterSpeed;
    public Timer timer;
    public BetterMinecraft betterMinecraft;
    public FreeLook freeLook;
    public ShaderHands shaderHands;
    public GlowHands glowHands;
    public Chams chams;
    public ViewModel viewModel;
    public SwingAnimation swingAnimation;
    public PopEffect popEffect;
    public AutoTotem autoTotem;
    public BlockHighlight blockHighlight;
    public JumpCircles jumpCircles;
    public FreeCam freeCam;
    public ItemScroller itemScroller;
    public FullBright fullBright;
    public AHHelper ahHelper;
    public AntiPush antiPush;
    public AutoTool autoTool;
    public Particles particles;
    public AttackAura attackAura;
    public Criticals criticals;
    public TargetEsp targetEsp;
    public Trails trails;
    public ESP esp;
    public StorageESP storageEsp;
    public Tracers tracers;
    public Trajectories trajectories;
    public HoleESP holeEsp;
    public XRay xray;
    public Zoom zoom;
    public FovChanger fovChanger;

    public void init() {
        // Only HUD + ClickGUI stay functional. Everything else is instantiated
        // for null-safety (mixins/commands reference the fields) but never registered,
        // so it can't be enabled, bound, saved or shown in ClickGUI.
        this.clickGui = new ClickGui();
        this.anInterface = new Interface();

        this.sprint = new Sprint();
        this.elytraHelper = new ElytraHelper();
        this.fullBright = new FullBright();
        this.attackAura = new AttackAura();
        this.criticals = new Criticals();
        this.ahHelper = new AHHelper();
        this.antiPush = new AntiPush();
        this.autoTool = new AutoTool();
        this.popEffect = new PopEffect();
        this.itemScroller = new ItemScroller();
        this.waterSpeed = new WaterSpeed();
        this.jumpCircles = new JumpCircles();
        this.freeCam = new FreeCam();
        this.swingAnimation = new SwingAnimation();
        this.viewModel = new ViewModel();
        this.blockHighlight = new BlockHighlight();
        this.autoTotem = new AutoTotem();
        this.shaderHands = new ShaderHands();
        this.glowHands = new GlowHands();
        this.chams = new Chams();
        this.betterMinecraft = new BetterMinecraft();
        this.guiWalk = new GuiWalk();
        this.removals = new Removals();
        this.freeLook = new FreeLook();
        this.serverHelper = new ServerHelper();
        this.timer = new Timer();
        this.airStuck = new AirStuck();
        this.ambience = new Ambience();
        this.shaderSky = new ShaderSky();
        this.nameTags = new NameTags();
        this.auraModule = new AuraModule();
        this.rain = new Rain();
        this.particles = new Particles();
        this.targetEsp = new TargetEsp();
        this.trails = new Trails();
        this.esp = new ESP();
        this.storageEsp = new StorageESP();
        this.tracers = new Tracers();
        this.trajectories = new Trajectories();
        this.holeEsp = new HoleESP();
        this.xray = new XRay();
        this.zoom = new Zoom();
        this.fovChanger = new FovChanger();

        register(
                this.clickGui,
                this.anInterface,
                this.shaderSky,
                this.shaderHands,
                this.glowHands,
                this.chams,
                this.rain,
                this.targetEsp,
                this.attackAura,
                this.sprint,
                this.elytraHelper,
                this.popEffect,
                this.trails,
                this.particles,
                this.esp,
                this.storageEsp,
                this.tracers,
                this.trajectories,
                this.holeEsp,
                this.xray,
                this.zoom,
                this.fovChanger,
                this.freeCam
        );
    }

    public void register(Module... mods) {
        for (Module mod : mods) {
            modules.add(mod);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> clazz) {
        for (Module module : modules) {
            if (module.getClass() == clazz) {
                return (T) module;
            }
        }
        return null;
    }

    public Module getModule(String name) {
        for (Module module : modules) {
            if (module.getName().equalsIgnoreCase(name)) {
                return module;
            }
        }
        return null;
    }

    public List<Module> getByCategory(Category category) {
        return modules.stream()
                .filter(m -> m.getCategory() == category)
                .collect(Collectors.toList());
    }

    public void onKey(int key) {
        if (key == 0 || key == 0) return;
        for (Module module : modules) {
            if (module.getBind().matches(key)) {
                module.toggle();
            }
        }
    }

    public void onMouse(int button) {
        for (Module module : modules) {
            if (module.getBind().matchesMouse(button)) {
                module.toggle();
            }
        }
    }
}
