package pub.godweer.kitty.display.mainmenu;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerSkinWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.ClientAsset;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import pub.godweer.kitty.sosiski.account.AccountManager;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AltManagerScreen extends Screen {
    private static final String[] WORDS = {
            "Wolf", "Fox", "Bear", "Hawk", "Eagle", "Tiger", "Lion", "Panda", "Koala", "Lynx",
            "Puma", "Cobra", "Viper", "Shark", "Whale", "Orca", "Dolphin", "Otter", "Beaver", "Badger",
            "Raven", "Crow", "Owl", "Falcon", "Gecko", "Turtle", "Rabbit", "Moose", "Bison", "Camel",
            "Llama", "Zebra", "Rhino", "Hippo", "Gorilla", "Monkey", "Parrot", "Toucan", "Swan", "Goose",
            "Duck", "Eel", "Squid", "Crab", "Lobster", "Moth", "Beetle", "Spider", "Hornet", "Wasp",
            "Bee", "Ant", "Frog", "Toad", "Newt", "Seal", "Walrus", "Yak", "Ram", "Boar",
            "Stag", "Horse", "Pony", "Mule", "Goat", "Sheep", "Cow", "Bull", "Dragon", "Phoenix",
            "Griffin", "Hydra", "Kraken", "Golem", "Troll", "Ogre", "Goblin", "Wizard", "Witch", "Mage",
            "Knight", "Archer", "Rogue", "Ninja", "Pirate", "Viking", "Samurai", "Ghost", "Zombie", "Skeleton",
            "Creeper", "Ender", "Slime", "Blaze", "Cool", "Silent", "Angry", "Happy", "Swift", "Dark",
            "Lucky", "Wild", "Tiny", "Brave", "Mighty", "Sneaky", "Crazy", "Lazy", "Funny", "Shiny",
            "Sunny", "Cloudy", "Frosty", "Fiery", "Icy", "Stormy", "Windy", "Rusty", "Golden", "Silver",
            "Bronze", "Iron", "Steel", "Stone", "Crystal", "Diamond", "Emerald", "Ruby", "Amber", "Jade",
            "Pearl", "Onyx", "Cosmic", "Lunar", "Solar", "Stellar", "Atomic", "Nuclear", "Turbo", "Mega",
            "Ultra", "Super", "Hyper", "Giga", "Nano", "Micro", "Royal", "Noble", "Wicked", "Evil",
            "Holy", "Sacred", "Ancient", "Mystic", "Magic", "Secret", "Hidden", "Lost", "Fallen", "Rising",
            "Burning", "Frozen", "Toxic", "Sticky", "Fuzzy", "Fluffy", "Spicy", "Sweet", "Sour", "Salty",
            "Bitter", "Crispy", "Smooth", "Rough", "Sharp", "Blunt", "Heavy", "Light", "Rapid", "Quick",
            "Slow", "Loud", "Quiet", "Calm", "Fierce", "Gentle", "Proud", "Humble", "Clever", "Smart",
            "Sly", "Bold", "Shy", "Merry", "Jolly", "Grumpy", "Sleepy", "Hungry", "Thirsty", "Dizzy",
            "Pixel", "Blocky", "Nether", "Cursed", "Lonely", "Rocky", "Sandy", "Muddy", "Mossy", "Sword",
            "Shield", "Axe", "Bow", "Arrow", "Spear", "Dagger", "Hammer", "Pickaxe", "Shovel", "Helmet",
            "Armor", "Crown", "Throne", "Castle", "Tower", "Bridge", "Forest", "Jungle", "Desert", "Ocean",
            "River", "Lake", "Island", "Mountain", "Valley", "Canyon", "Cave", "Volcano", "Comet", "Meteor",
            "Planet", "Galaxy", "Nebula", "Orbit", "Rocket", "Laser", "Plasma", "Photon", "Quantum", "Matrix",
            "Cipher", "Byte", "Glitch", "Virus", "Robot", "Cyborg", "Droid", "Engine", "Thunder", "Tornado",
            "Blizzard", "Cyclone", "Eclipse", "Shadow", "Phantom", "Spirit", "Soul", "Spark", "Flame", "Ember",
            "Frost", "Snow", "Rain", "Mist", "Fog", "Dawn", "Dusk", "Night", "Moon", "Star",
            "Sun", "Sky", "Cloud", "Wind", "Wave", "Tide", "Coral", "Shell", "Anchor", "Cookie",
            "Muffin", "Waffle", "Pancake", "Pickle", "Potato", "Tomato", "Carrot", "Pepper", "Banana", "Mango",
            "Lemon", "Cherry", "Berry", "Peach", "Apple", "Melon", "Candy", "Sugar", "Honey", "Cocoa",
            "Coffee", "Toast", "Bacon", "Pizza", "Taco", "Burger", "Noodle", "Ramen", "Sushi", "Boba",
            "Cheese", "Butter", "Jelly", "Donut", "Nugget", "Biscuit", "Pretzel", "Bagel"
    };

    private static final int ROW_HEIGHT = 18;
    private static final int LIST_WIDTH = 200;
    private static final int LIST_TOP = 42;
    private static final long DOUBLE_CLICK_MS = 400L;

    private static final Random RND = new Random();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    private static final Map<String, PlayerSkin> SKINS = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static List<String> COMBOS;

    private final Screen parent;
    private final AccountManager accounts;

    private EditBox nameBox;
    private PlayerSkinWidget skinWidget;
    private List<String> rows = new ArrayList<>();
    private String selected;
    private int scroll;
    private long lastClick;

    public AltManagerScreen(Screen parent) {
        super(Component.literal("Alt Manager"));
        this.parent = parent;
        this.accounts = AccountManager.getInstance();
    }

    public static void hookTitleScreen() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen)) {
                return;
            }

            List<AbstractWidget> buttons = Screens.getWidgets(screen);
            for (AbstractWidget button : new ArrayList<>(buttons)) {
                if (!button.getMessage().equals(Component.translatable("menu.online"))) {
                    continue;
                }

                int index = buttons.indexOf(button);
                buttons.remove(index);
                buttons.add(index, Button.builder(Component.literal("Alt Manager"),
                                pressed -> client.setScreenAndShow(new AltManagerScreen(screen)))
                        .bounds(button.getX(), button.getY(), button.getWidth(), button.getHeight()).build());
            }
        });
    }

    @Override
    protected void init() {
        this.rows = new ArrayList<>(accounts.getSortedAccounts());
        if (selected != null && !rows.contains(selected)) {
            selected = null;
        }

        int listX = Math.max(4, width / 2 - LIST_WIDTH - 10);
        int right = Math.min(width / 2 + 55, Math.max(listX + LIST_WIDTH + 10, width - 90));

        nameBox = new EditBox(font, listX, 18, 150, 18, Component.literal("Nick"));
        nameBox.setMaxLength(16);
        addRenderableWidget(nameBox);
        addButton("Add", listX + 158, 18, 55, () -> {
            String name = nameBox.getValue().trim();
            if (!name.isEmpty() && !accounts.getAccounts().contains(name)) {
                accounts.addAccount(name);
                refreshRows();
            }

            nameBox.setValue("");
            click();
        });

        skinWidget = new PlayerSkinWidget(80, 120, minecraft.getEntityModels(), this::currentSkin);
        skinWidget.setX(right);
        skinWidget.setY(LIST_TOP + 8);
        skinWidget.visible = selected != null;
        addRenderableWidget(skinWidget);

        int rowY = height - 88;
        addButton("Random", centered(70), rowY, 70, () -> {
            String name = randomName();
            accounts.addAccount(name);
            selected = name;
            refreshRows();
            click();
        });
        addButton("Delete", centered(70) + 74, rowY, 70, () -> {
            if (selected != null) {
                accounts.removeAccount(selected);
                selected = null;
                refreshRows();
            }

            click();
        });
        addButton("Fav", centered(70) + 148, rowY, 70, () -> {
            if (selected != null) {
                accounts.toggleFavorite(selected);
                refreshRows();
            }

            click();
        });

        int bottomY = height - 62;
        addButton("Login", centered(100), bottomY, 100, () -> {
            if (selected != null) {
                login(selected);
            }

            click();
        });
        addButton("Back", centered(100) + 104, bottomY, 100, () -> {
            click();
            minecraft.setScreenAndShow(parent);
        });
    }

    private void addButton(String label, int x, int y, int width, Runnable action) {
        addRenderableWidget(Button.builder(Component.literal(label), pressed -> action.run())
                .bounds(x, y, width, 18).build());
    }

    private int centered(int width) {
        return this.width / 2 - width / 2;
    }

    private void refreshRows() {
        this.rows = new ArrayList<>(accounts.getSortedAccounts());
        if (selected != null && !rows.contains(selected)) {
            selected = null;
        }

        if (skinWidget != null) {
            skinWidget.visible = selected != null;
        }
    }

    private void click() {
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void login(String name) {
        this.selected = name;
        accounts.relogin(name);
    }

    private PlayerSkin currentSkin() {
        if (selected == null) {
            return DefaultPlayerSkin.get(UUID.nameUUIDFromBytes("OfflinePlayer:".getBytes(StandardCharsets.UTF_8)));
        }

        return skinFor(selected);
    }

    private static PlayerSkin skinFor(String name) {
        PlayerSkin cached = SKINS.get(name);
        if (cached != null) {
            return cached;
        }

        if (LOADING.add(name)) {
            POOL.submit(() -> {
                try {
                    PlayerSkin skin = downloadSkin(name);
                    if (skin != null) {
                        SKINS.put(name, skin);
                    }
                } catch (Exception ignored) {
                } finally {
                    LOADING.remove(name);
                }
            });
        }

        return DefaultPlayerSkin.get(offlineUuid(name));
    }

    private static PlayerSkin downloadSkin(String name) throws Exception {
        try (InputStream in = URI.create("https://minotar.net/skin/" + name).toURL().openStream()) {
            NativeImage image = NativeImage.read(in);
            NativeImage modern = toModern(image);
            ClientAsset.ResourceTexture texture = new ClientAsset.ResourceTexture(
                    Identifier.fromNamespaceAndPath("godweer", "alts/skin/" + name.toLowerCase(Locale.ROOT)));

            Minecraft.getInstance().execute(() -> Minecraft.getInstance().getTextureManager()
                    .register(texture.texturePath(), new DynamicTexture(() -> "godweer_alt_skin", modern)));

            return PlayerSkin.insecure(texture, null, null, PlayerModelType.WIDE);
        }
    }

    private static NativeImage toModern(NativeImage image) {
        if (image.getWidth() == 64 && image.getHeight() == 64) {
            return image;
        }

        NativeImage out = new NativeImage(NativeImage.Format.RGBA, 64, 64, true);
        copy(image, out, 0, 0, 0, 0, 64, 32);
        copy(image, out, 40, 16, 32, 48, 16, 16);
        copy(image, out, 0, 16, 16, 48, 16, 16);
        image.close();
        return out;
    }

    private static void copy(NativeImage from, NativeImage to, int sx, int sy, int tx, int ty, int w, int h) {
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                to.setPixel(tx + x, ty + y, from.getPixel(sx + x, sy + y));
            }
        }
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static synchronized List<String> combos() {
        if (COMBOS == null) {
            COMBOS = new ArrayList<>();
            for (int i = 0; i < WORDS.length; i++) {
                for (int j = 0; j < WORDS.length; j++) {
                    if (i == j) {
                        continue;
                    }

                    String name = WORDS[i] + WORDS[j];
                    if (name.length() <= 16) {
                        COMBOS.add(name);
                    }
                }
            }
        }

        return COMBOS;
    }

    private static String randomName() {
        List<String> taken = AccountManager.getInstance().getAccounts();
        List<String> free = new ArrayList<>();
        for (String name : combos()) {
            if (!taken.contains(name)) {
                free.add(name);
            }
        }

        if (!free.isEmpty()) {
            return free.get(RND.nextInt(free.size()));
        }

        for (int i = 0; i < 1000; i++) {
            String name = WORDS[RND.nextInt(WORDS.length)] + (1000 + RND.nextInt(9000));
            if (name.length() <= 16 && !taken.contains(name)) {
                return name;
            }
        }

        return "Alt" + System.currentTimeMillis();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        double mx = event.x();
        double my = event.y();
        int listX = width / 2 - LIST_WIDTH - 10;
        if (mx < listX || mx > listX + LIST_WIDTH || my < LIST_TOP || my >= height - 70) {
            return false;
        }

        int index = scroll + (int) ((my - LIST_TOP) / ROW_HEIGHT);
        if (index < 0 || index >= rows.size()) {
            return false;
        }

        String name = rows.get(index);
        long now = System.currentTimeMillis();
        if (name.equals(selected) && now - lastClick < DOUBLE_CLICK_MS) {
            login(name);
        }

        selected = name;
        lastClick = now;
        refreshRows();
        click();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int visible = Math.max(1, (height - LIST_TOP - 78) / ROW_HEIGHT);
        int max = Math.max(0, rows.size() - visible);
        scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(scrollY)));
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        String active = accounts.getActiveAccount();
        int listX = width / 2 - LIST_WIDTH - 10;
        int listBottom = height - 70;

        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);

        if (rows.isEmpty()) {
            graphics.centeredText(font, "no accounts", listX + LIST_WIDTH / 2, LIST_TOP + 24, 0xFF888888);
        }

        for (int i = scroll; i < rows.size(); i++) {
            int y = LIST_TOP + (i - scroll) * ROW_HEIGHT;
            if (y + ROW_HEIGHT > listBottom) {
                break;
            }

            String name = rows.get(i);
            boolean isSelected = name.equals(selected);
            boolean isActive = name.equalsIgnoreCase(active);

            if (isSelected) {
                graphics.fill(listX, y, listX + LIST_WIDTH, y + ROW_HEIGHT - 1, 0x55FFFFFF);
            } else if (isActive) {
                graphics.fill(listX, y, listX + LIST_WIDTH, y + ROW_HEIGHT - 1, 0x5500FF00);
            }

            String label = (accounts.isFavorite(name) ? "* " : "  ") + name;
            int color = isActive ? 0xFF55FF55 : (isSelected ? 0xFFFFFFFF : 0xFFBBBBBB);
            graphics.text(font, label, listX + 4, y + 4, color);
        }

        graphics.text(font, "double click to login", listX, listBottom + 6, 0xFF777777);
    }
}
