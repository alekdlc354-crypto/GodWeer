package pub.godweer.hud;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import pub.godweer.IMinecraft;
import pub.godweer.enjoy.event.list.Render2DEvent;
import pub.godweer.auxiliary.client.clients.ColorUtil;
import pub.godweer.auxiliary.client.clients.Theme;
import pub.godweer.auxiliary.render.Render2D;
import pub.godweer.auxiliary.render.renders.font.Fonts;
import pub.godweer.auxiliary.render.renders.font.IconUse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Staff online list: head, nick, role tag (Moder/Admin/Curator/Owner
 * matched via scoreboard team), SPEC/PLAY badge. Row animations.
 */
public final class StaffHud extends HudElement implements IMinecraft {

    private static final float PAD = 6.0F;
    private static final float HEADER_H = 16.0F;
    private static final float ROW_H = 16.0F;
    private static final float HEAD = 12.0F;
    private static final int BG = ColorUtil.rgba(8, 8, 12, 228);

    private static final String[] ROLES = {"owner", "admin", "curator", "moder"};

    private record StaffEntry(String name, String tag, int tagColor, boolean spec, Identifier skin) {
    }

    private final Map<String, Float> anims = new HashMap<>();
    private float animatedW = -1.0F;

    public StaffHud() {
        super("staff", "Staff", 160.0F, 60.0F, 120.0F, 40.0F);
    }

    private static int roleColor(String role) {
        return switch (role) {
            case "owner" -> ColorUtil.rgba(255, 85, 85, 255);
            case "admin" -> ColorUtil.rgba(255, 170, 45, 255);
            case "curator" -> ColorUtil.rgba(180, 130, 255, 255);
            default -> ColorUtil.rgba(110, 220, 130, 255);
        };
    }

    private static String prettyRole(String role) {
        if (role == null || role.isEmpty()) return role;
        return role.substring(0, 1).toUpperCase(Locale.ROOT) + role.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Finds a staff role for a nick via scoreboard team, else via tab-list display name. */
    private static String staffRole(String name) {
        if (name == null || name.isEmpty()) return null;
        try {
            if (mc.level != null) {
                PlayerTeam team = mc.level.getScoreboard().getPlayersTeam(name);
                if (team != null) {
                    String hay = team.getName() + " " + team.getDisplayName().getString() + " "
                            + team.getPlayerPrefix().getString() + " " + team.getPlayerSuffix().getString();
                    String hit = matchRole(hay);
                    if (hit != null) return hit;
                }
            }
        } catch (Exception ignored) {
        }
        try {
            if (mc.getConnection() != null) {
                for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                    String n;
                    try {
                        n = info.getProfile().name();
                    } catch (Exception e) {
                        continue;
                    }
                    if (!name.equalsIgnoreCase(n)) continue;
                    try {
                        var tab = info.getTabListDisplayName();
                        if (tab != null) {
                            String hit = matchRole(tab.getString());
                            if (hit != null) return hit;
                        }
                    } catch (Exception ignored) {
                    }
                    break;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String matchRole(String hay) {
        if (hay == null) return null;
        String low = hay.toLowerCase(Locale.ROOT);
        for (String role : ROLES) {
            if (low.contains(role)) return role;
        }
        return null;
    }

    private static boolean isSpectating(PlayerInfo info, String name) {
        try {
            if (info != null && info.getGameMode() == GameType.SPECTATOR) return true;
        } catch (Exception ignored) {
        }
        try {
            if (mc.level != null && name != null) {
                var e = mc.level.getPlayerByUUID(info != null ? info.getProfile().id() : null);
                if (e != null) return e.isSpectator();
            }
        } catch (Exception ignored) {
        }
        try {
            if (mc.level != null && name != null) {
                for (AbstractClientPlayer p : mc.level.players()) {
                    try {
                        if (p.getGameProfile().name().equalsIgnoreCase(name)) return p.isSpectator();
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static Identifier skinOf(PlayerInfo info, String name) {
        try {
            if (mc.level != null && info != null) {
                var e = mc.level.getPlayerByUUID(info.getProfile().id());
                if (e instanceof AbstractClientPlayer ap) return ap.getSkin().body().texturePath();
            }
        } catch (Exception ignored) {
        }
        try {
            if (mc.level != null && name != null) {
                for (AbstractClientPlayer p : mc.level.players()) {
                    try {
                        if (p.getGameProfile().name().equalsIgnoreCase(name)) {
                            return p.getSkin().body().texturePath();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<StaffEntry> collect() {
        List<StaffEntry> out = new ArrayList<>();
        try {
            if (mc.getConnection() != null && mc.player != null) {
                String self = mc.player.getGameProfile().name();
                for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                    String name;
                    try {
                        name = info.getProfile().name();
                    } catch (Exception e) {
                        continue;
                    }
                    if (name.equalsIgnoreCase(self)) continue;
                    String role = staffRole(name);
                    if (role == null) continue;
                    out.add(new StaffEntry(name, prettyRole(role), roleColor(role),
                            isSpectating(info, name), skinOf(info, name)));
                    if (out.size() >= 10) break;
                }
            }
        } catch (Exception ignored) {
        }
        out.sort((a, b) -> {
            if (a.spec() != b.spec()) return a.spec() ? -1 : 1;
            return a.name().compareToIgnoreCase(b.name());
        });
        return out;
    }

    @Override
    public boolean shouldRender() {
        if (!enabled) return false;
        if (HudManager.editMode()) return true;
        if (mc.player == null || mc.getConnection() == null) return false;
        try {
            String self = mc.player.getGameProfile().name();
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                String name = info.getProfile().name();
                if (!name.equalsIgnoreCase(self) && staffRole(name) != null) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public void draw(Render2DEvent event) {
        List<StaffEntry> staff = collect();
        if (staff.isEmpty() && HudManager.editMode()) {
            staff.add(new StaffEntry("AdminSteve", "Admin", roleColor("admin"), true, null));
            staff.add(new StaffEntry("ModerAlex", "Moder", roleColor("moder"), false, null));
        }

        for (StaffEntry s : staff) {
            anims.putIfAbsent(s.name().toLowerCase(Locale.ROOT), 0.0F);
        }
        float listH = 0.0F;
        float maxW = Fonts.SF_MEDIUM.getWidth("Staff online", 8.0F) + 26.0F;
        for (StaffEntry s : staff) {
            float a = anims.getOrDefault(s.name().toLowerCase(Locale.ROOT), 0.0F);
            a += (1.0F - a) * 0.2F;
            anims.put(s.name().toLowerCase(Locale.ROOT), a);
            listH += ROW_H * a;
            if (a > 0.05F) maxW = Math.max(maxW, rowWidth(s));
        }
        anims.keySet().removeIf(k -> {
            for (StaffEntry s : staff) {
                if (s.name().toLowerCase(Locale.ROOT).equals(k)) return false;
            }
            return true;
        });

        float targetW = Math.max(110.0F, maxW + PAD * 2.0F);
        if (animatedW < 0.0F) animatedW = targetW;
        animatedW += (targetW - animatedW) * 0.25F;

        this.width = Math.max(targetW, animatedW);
        this.height = PAD * 2.0F + HEADER_H + Math.max(ROW_H, listH);

        Render2D.drawBlur(x, y, width, height, 8.0F, BG, 1.0F);

        int accent = Theme.getAccentColor();
        Render2D.drawRoundedRect(x + PAD, y + PAD + 1.0F, 12.0F, 12.0F, 4.0F,
                ColorUtil.withAlpha(accent, 45));
        Fonts.drawIcon(IconUse.STAFF, x + PAD + 2.0F, y + PAD + 3.0F, 8.0F,
                ColorUtil.withAlpha(accent, 255));
        Fonts.drawString(Fonts.SF_MEDIUM, "Staff online (" + staff.size() + ")",
                x + PAD + 16.0F, y + PAD + 2.5F, 8.0F, ColorUtil.withAlpha(accent, 255));

        float cy = y + PAD + HEADER_H;
        int white = ColorUtil.rgba(240, 242, 248, 255);
        for (StaffEntry s : staff) {
            float a = anims.getOrDefault(s.name().toLowerCase(Locale.ROOT), 1.0F);
            float rh = ROW_H * a;
            if (a <= 0.04F) continue;
            float alpha = a;
            float slide = (1.0F - a) * -5.0F;

            float hy = cy + (rh - HEAD) / 2.0F;
            if (s.skin() != null) {
                Render2D.drawHead(s.skin(), x + PAD + slide, hy, HEAD, 3.0F);
            } else {
                Render2D.drawRoundedRect(x + PAD + slide, hy, HEAD, HEAD, 3.0F,
                        ColorUtil.rgba(255, 255, 255, 14));
                String letter = s.name().isEmpty() ? "?" : s.name().substring(0, 1).toUpperCase(Locale.ROOT);
                Fonts.drawCenteredString(Fonts.SF_MEDIUM, letter, x + PAD + slide + HEAD / 2.0F,
                        hy + 2.0F, 7.5F, ColorUtil.multiplyAlpha(white, alpha));
            }

            float nx = x + PAD + HEAD + 4.0F + slide;
            float nameW = Fonts.SF_MEDIUM.getWidth(s.name(), 7.5F);
            float nameAvail = width - PAD * 2.0F - (HEAD + 4.0F) - 44.0F
                    - Fonts.SF_MEDIUM.getWidth(s.tag(), 6.5F);
            String shown = s.name();
            float drawnNameW = nameW;
            if (nameW > nameAvail && nameAvail > 12.0F) {
                shown = ServerGui.ellipsize(s.name(), nameAvail, 7.5F);
                drawnNameW = Fonts.SF_MEDIUM.getWidth(shown, 7.5F);
            }
            Fonts.drawString(Fonts.SF_MEDIUM, shown, nx, cy + 1.0F, 7.5F,
                    ColorUtil.multiplyAlpha(white, alpha));

            float tagW = Fonts.SF_MEDIUM.getWidth(s.tag(), 6.5F) + 8.0F;
            float tx = nx + drawnNameW + 4.0F;
            Render2D.drawRoundedRect(tx, cy + 1.5F, tagW, 9.0F, 4.5F,
                    ColorUtil.multiplyAlpha(ColorUtil.withAlpha(s.tagColor(), 50), alpha));
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, s.tag(), tx + tagW / 2.0F, cy + 2.5F, 6.5F,
                    ColorUtil.multiplyAlpha(s.tagColor(), alpha));

            String mode = s.spec() ? "SPEC" : "PLAY";
            int modeCol = s.spec() ? ColorUtil.rgba(255, 95, 95, 255) : ColorUtil.rgba(120, 220, 140, 255);
            float mw = Fonts.SF_MEDIUM.getWidth(mode, 6.5F) + 8.0F;
            float mx = x + width - PAD - mw;
            Render2D.drawRoundedRect(mx, cy + (rh - 9.0F) / 2.0F, mw, 9.0F, 4.5F,
                    ColorUtil.multiplyAlpha(ColorUtil.withAlpha(modeCol, 50), alpha));
            Fonts.drawCenteredString(Fonts.SF_MEDIUM, mode, mx + mw / 2.0F, cy + (rh - 9.0F) / 2.0F + 1.0F,
                    6.5F, ColorUtil.multiplyAlpha(modeCol, alpha));

            cy += rh;
        }
    }

    private static float rowWidth(StaffEntry s) {
        return HEAD + 4.0F + Fonts.SF_MEDIUM.getWidth(s.name(), 7.5F) + 4.0F
                + Fonts.SF_MEDIUM.getWidth(s.tag(), 6.5F) + 8.0F + 6.0F
                + Fonts.SF_MEDIUM.getWidth(s.spec() ? "SPEC" : "PLAY", 6.5F) + 8.0F;
    }
}
