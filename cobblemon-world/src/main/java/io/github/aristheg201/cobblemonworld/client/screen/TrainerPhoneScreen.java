package io.github.aristheg201.cobblemonworld.client.screen;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.network.PhoneActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

public final class TrainerPhoneScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int W = 131;
    private static final int H = 207;
    private static final int PER_PAGE = 6;
    private static final int TILE = 36;
    private static final int INK = 0xFF17313A;
    private static final int MUTED = 0xFF526A70;
    private static final int ACCENT = 0xFF1D7E8A;
    private static final int BG = 0xFFE4F8F7;
    private static final int HEADER = 0xFFB9E9EA;
    private static final int CHIP = 0xFFD3EFF0;
    private static final int CHIP_HOVER = 0xFFAADFE2;

    private static final List<App> APPS = List.of(
            new App("Trainer Card", "trainer_card"),
            new App("Objective", "objective"),
            new App("Story", "story"),
            new App("Side Quests", "side_quests"),
            new App("Level Cap", "level_cap"),
            new App("Badges", "badges"),
            new App("Contacts", "contacts"),
            new App("Messages", "messages"),
            new App("League", "league"),
            new App("Faction", "faction")
    );

    private final CWorldNetworking.PhoneSnapshot snapshot;
    private String selected = "home";
    private int homePage;
    private int messageIndex;
    private int contactIndex;
    private int questIndex;
    private int left;
    private int top;
    private EditBox factionName;
    private EditBox factionMember;

    public TrainerPhoneScreen(String json) {
        super(Component.literal("Trainer Phone"));
        snapshot = GSON.fromJson(json, CWorldNetworking.PhoneSnapshot.class);
        messageIndex = newestUnreadIndex(snapshot.messages());
    }

    @Override
    protected void init() {
        clearWidgets();
        left = (width - W) / 2;
        top = (height - H) / 2;

        factionName = new EditBox(font, left + 21, top + 119, 66, 14, Component.literal("Faction name"));
        factionName.setHint(Component.literal("name"));
        factionName.setBordered(false);
        factionName.setMaxLength(24);
        addRenderableWidget(factionName);

        factionMember = new EditBox(font, left + 21, top + 119, 66, 14, Component.literal("Player name"));
        factionMember.setHint(Component.literal("player"));
        factionMember.setBordered(false);
        factionMember.setMaxLength(24);
        addRenderableWidget(factionMember);
        updateFields();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Do not call renderBackground: phone should sit cleanly over the world, not blur/darken it.
        renderFrame(g);
        if ("home".equals(selected)) renderHome(g, mouseX, mouseY);
        else renderApp(g, mouseX, mouseY);
        // Screen.render() calls renderBackground() in 1.21.1, which applies the vanilla
        // full-screen blur. Render our only widgets directly so the in-world background stays crisp.
        if (factionName != null) factionName.render(g, mouseX, mouseY, partialTick);
        if (factionMember != null) factionMember.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty: Cobblemon Smartphone-style overlay, not a menu screen.
    }

    private void renderFrame(GuiGraphics g) {
        g.fill(left + 5, top, left + W - 5, top + H, 0xFF17191E);
        g.fill(left + 2, top + 7, left + W - 2, top + H - 7, 0xFF17191E);
        g.fill(left + 5, top + 3, left + W - 5, top + H - 3, 0xFF6F1723);
        g.fill(left + 3, top + 10, left + W - 3, top + H - 10, 0xFF6F1723);
        g.fill(left + 7, top + 5, left + W - 7, top + H - 5, 0xFFB82F3A);
        g.fill(left + 6, top + 11, left + 9, top + H - 13, 0xFFE65A5E);
        g.fill(left + W - 10, top + 11, left + W - 7, top + H - 13, 0xFF6F1723);
        g.fill(left + 13, top + 17, left + 118, top + 185, 0xFF0C2027);
        g.fill(left + 15, top + 19, left + 116, top + 183, BG);
        g.fill(left + 51, top + 9, left + 80, top + 11, 0xFF3B1017);
        g.fill(left + 57, top + 194, left + 74, top + 197, 0xFF57131E);
        g.fill(left - 1, top + 46, left + 3, top + 68, 0xFF17191E);
        g.fill(left + W - 3, top + 62, left + W + 1, top + 86, 0xFF17191E);
    }

    private void renderHome(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(left + 15, top + 19, left + 116, top + 36, HEADER);
        text(g, "TRAINER", left + 20, top + 24, 0.72F, INK);
        textRight(g, "Lv." + snapshot.levelCap(), left + 111, top + 24, 0.72F, ACCENT);

        int from = homePage * PER_PAGE;
        int to = Math.min(APPS.size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            int local = i - from;
            int x = left + 22 + (local % 2) * 51;
            int y = top + 43 + (local / 2) * 44;
            App app = APPS.get(i);
            boolean hover = inside(mouseX, mouseY, x - 2, y - 2, 40, 40);
            g.fill(x - 2, y - 2, x + 38, y + 38, hover ? CHIP_HOVER : CHIP);
            g.fill(x, y, x + TILE, y + TILE, 0xFFF4FFFF);
            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(
                    CobblemonWorldMod.MOD_ID, "textures/gui/icons/" + app.id + ".png");
            g.blit(icon, x + 10, y + 4, 0, 0.0F, 0.0F, 16, 16, 16, 16);
            center(g, shortLabel(app.label), x + 18, y + 25, 0.53F, INK);
            if ("messages".equals(app.id)) {
                int unread = unread();
                if (unread > 0) {
                    g.fill(x + 26, y + 1, x + 35, y + 10, 0xFFD33142);
                    center(g, unread > 9 ? "9+" : Integer.toString(unread), x + 30, y + 2, 0.46F, 0xFFFFFFFF);
                }
            }
        }

        int pages = Math.max(1, (APPS.size() + PER_PAGE - 1) / PER_PAGE);
        int start = left + W / 2 - (pages * 5 - 2) / 2;
        for (int i = 0; i < pages; i++) {
            g.fill(start + i * 5, top + 174, start + i * 5 + 3, top + 177,
                    i == homePage ? ACCENT : 0xFF8AA8AC);
        }
        text(g, "‹", left + 24, top + 169, 0.9F, homePage > 0 ? INK : 0xFF9DB0B2);
        textRight(g, "›", left + 108, top + 169, 0.9F, homePage + 1 < pages ? INK : 0xFF9DB0B2);
    }

    private void renderApp(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(left + 15, top + 19, left + 116, top + 39, HEADER);
        text(g, "‹", left + 19, top + 23, 0.9F, ACCENT);
        text(g, label(selected), left + 31, top + 25, 0.62F, INK);
        g.fill(left + 19, top + 42, left + 112, top + 43, 0xFFADD9DA);

        int x = left + 20;
        int y = top + 48;
        int w = 91;

        switch (selected) {
            case "trainer_card" -> {
                text(g, snapshot.trainerName(), x, y, 0.76F, ACCENT);
                kv(g, "Level Cap", "Lv." + snapshot.levelCap(), x, y + 19, w);
                kv(g, "Badges", Integer.toString(snapshot.badges().size()), x, y + 34, w);
                kv(g, "League", snapshot.leagueTier(), x, y + 49, w);
                kv(g, "Faction", snapshot.faction().name(), x, y + 64, w);
                if (snapshot.rpg() != null && snapshot.rpg().available()) {
                    kv(g, "RPG", snapshot.rpg().classId() + " Lv." + snapshot.rpg().level(), x, y + 79, w);
                    text(g, "STA " + whole(snapshot.rpg().stamina()) + "/" + whole(snapshot.rpg().maxStamina()),
                            x, y + 97, 0.5F, MUTED);
                    text(g, "MANA " + whole(snapshot.rpg().mana()) + "/" + whole(snapshot.rpg().maxMana()),
                            x, y + 110, 0.5F, MUTED);
                }
            }
            case "objective" -> {
                text(g, "CURRENT OBJECTIVE", x, y, 0.53F, ACCENT);
                g.fill(x, y + 13, x + w, y + 15, ACCENT);
                wrap(g, safe(snapshot.story().objective(), "No tracked objective"), x, y + 24, w, 0.62F, 10, INK);
            }
            case "story" -> {
                text(g, snapshot.story().title(), x, y, 0.65F, ACCENT);
                text(g, human(snapshot.story().id()), x, y + 16, 0.48F, MUTED);
                g.fill(x, y + 28, x + w, y + 29, 0xFFB4DCDD);
                wrap(g, safe(snapshot.story().objective(), "Story complete."), x, y + 38, w, 0.58F, 9, INK);
            }
            case "side_quests" -> renderQuest(g, x, y, w, mouseX, mouseY);
            case "level_cap" -> {
                center(g, "Lv." + snapshot.levelCap(), x + w / 2, y + 2, 1.15F, ACCENT);
                g.fill(x + 12, y + 25, x + w - 12, y + 27, ACCENT);
                wrap(g, "Pokémon above your cap keep their real level, but cannot spawn naturally, be caught, sent out, gain XP, or enter battle until the cap catches up.",
                        x, y + 37, w, 0.52F, 12, INK);
            }
            case "badges" -> {
                text(g, snapshot.badges().size() + " BADGES", x, y, 0.66F, ACCENT);
                if (snapshot.badges().isEmpty()) text(g, "No badges yet.", x, y + 20, 0.56F, MUTED);
                else {
                    int yy = y + 19;
                    for (String badge : snapshot.badges()) {
                        if (yy > top + 161) break;
                        g.fill(x, yy, x + 8, yy + 8, 0xFFE8B83B);
                        text(g, human(badge), x + 13, yy, 0.48F, INK);
                        yy += 13;
                    }
                }
            }
            case "contacts" -> renderContact(g, x, y, w, mouseX, mouseY);
            case "messages" -> renderMessage(g, x, y, w, mouseX, mouseY);
            case "league" -> {
                center(g, snapshot.leagueTier(), x + w / 2, y + 4, 0.82F, ACCENT);
                center(g, snapshot.leaguePoints() + " LP", x + w / 2, y + 24, 0.62F, INK);
                g.fill(x + 12, y + 42, x + w - 12, y + 44, 0xFFB4DCDD);
                wrap(g, "Main-story milestones award League Points. Eight major badges or 800 LP reaches Champion tier.",
                        x, y + 54, w, 0.54F, 8, INK);
            }
            case "faction" -> renderFaction(g, x, y, w, mouseX, mouseY);
            default -> select("home");
        }
    }

    private void renderQuest(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        var q = quest();
        if (q == null) {
            text(g, "No side quests yet.", x, y, 0.58F, MUTED);
            return;
        }
        text(g, (q.completed() ? "✓ " : "") + q.title(), x, y, 0.6F, q.completed() ? 0xFF397A4A : ACCENT);
        text(g, q.giver() + "  " + q.progress() + "/" + q.required(), x, y + 16, 0.49F, MUTED);
        wrap(g, q.description(), x, y + 31, w, 0.53F, 7, INK);
        pager(g, questIndex, snapshot.quests().size(), mouseX, mouseY);
        if (!q.completed()) chip(g, x + 17, top + 149, 57, 15, "Track Quest", mouseX, mouseY);
    }

    private void renderContact(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        var c = contact();
        if (c == null) {
            text(g, "No contacts yet.", x, y, 0.58F, MUTED);
            return;
        }
        g.fill(x, y, x + 24, y + 24, 0xFFB7DDE0);
        center(g, initials(c.displayName()), x + 12, y + 7, 0.64F, ACCENT);
        text(g, c.displayName(), x + 30, y + 1, 0.58F, INK);
        text(g, c.unread() > 0 ? c.unread() + " unread" : "Up to date", x + 30, y + 14, 0.46F,
                c.unread() > 0 ? 0xFFD33142 : MUTED);
        g.fill(x, y + 34, x + w, y + 35, 0xFFB4DCDD);
        wrap(g, "Story contacts unlock after major battles and investigations.", x, y + 44, w, 0.53F, 7, INK);
        pager(g, contactIndex, snapshot.contacts().size(), mouseX, mouseY);
    }

    private void renderMessage(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        var m = message();
        if (m == null) {
            text(g, "No messages yet.", x, y, 0.58F, MUTED);
            return;
        }
        text(g, m.sender(), x + 2, y, 0.62F, ACCENT);
        if (m.unread()) g.fill(x + w - 6, y + 2, x + w, y + 8, 0xFFD33142);

        int bubbleY = y + 17;
        int bubbleH = m.responses().isEmpty() ? 82 : 58;
        g.fill(x, bubbleY, x + w, bubbleY + bubbleH, 0xFFF5FFFF);
        g.fill(x, bubbleY, x + 2, bubbleY + bubbleH, ACCENT);
        wrap(g, m.text(), x + 6, bubbleY + 7, w - 11, 0.53F, m.responses().isEmpty() ? 11 : 7, INK);

        if (m.unread() && !m.responses().isEmpty()) {
            for (int i = 0; i < Math.min(2, m.responses().size()); i++) {
                chip(g, x, top + 132 + i * 20, w, 16, m.responses().get(i), mouseX, mouseY);
            }
        }
        pager(g, messageIndex, snapshot.messages().size(), mouseX, mouseY);
    }

    private void renderFaction(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        var f = snapshot.faction();
        boolean none = "No Faction".equals(f.name());
        if (none) {
            text(g, "NO FACTION", x, y, 0.62F, MUTED);
            wrap(g, "Create a faction or accept an invitation.", x, y + 17, w, 0.52F, 4, INK);
            chip(g, left + 91, top + 118, 20, 15, "Create", mouseX, mouseY);
            if (f.pendingInvites() != null && !f.pendingInvites().isEmpty()) {
                text(g, "Invite: " + f.pendingInvites().get(0), x, top + 141, 0.48F, MUTED);
                chip(g, x, top + 153, w, 15, "Accept Invite", mouseX, mouseY);
            }
            return;
        }

        text(g, f.name(), x, y, 0.65F, ACCENT);
        text(g, f.role() + "  •  " + f.memberCount() + " members", x, y + 15, 0.48F, MUTED);
        kv(g, "Island", f.phase(), x, y + 33, w);
        kv(g, "Affinity", f.affinity().toUpperCase(Locale.ROOT), x, y + 47, w);
        kv(g, "Gate", f.gateScore() + (f.qualified() ? " ✓" : ""), x, y + 61, w);
        kv(g, "Control", f.controlPoints() + "/5", x, y + 75, w);
        boolean staff = "OWNER".equals(f.role()) || "OFFICER".equals(f.role());
        if (staff) chip(g, left + 91, top + 118, 20, 15, "Invite", mouseX, mouseY);
        chip(g, x, top + 144, 43, 15, "Join Island", mouseX, mouseY);
        chip(g, x + 48, top + 144, 43, 15, "OWNER".equals(f.role()) ? "Disband" : "Leave", mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (!inside(mx, my, left, top, W, H)) return false;

        if ("home".equals(selected)) {
            int from = homePage * PER_PAGE;
            int to = Math.min(APPS.size(), from + PER_PAGE);
            for (int i = from; i < to; i++) {
                int local = i - from;
                int x = left + 22 + (local % 2) * 51;
                int y = top + 43 + (local / 2) * 44;
                if (inside(mx, my, x - 2, y - 2, 40, 40)) {
                    select(APPS.get(i).id);
                    return true;
                }
            }
            int pages = Math.max(1, (APPS.size() + PER_PAGE - 1) / PER_PAGE);
            if (inside(mx, my, left + 16, top + 164, 30, 22) && homePage > 0) homePage--;
            else if (inside(mx, my, left + 85, top + 164, 30, 22) && homePage + 1 < pages) homePage++;
            return true;
        }

        if (inside(mx, my, left + 16, top + 19, 20, 20)) {
            select("home");
            return true;
        }

        if ("messages".equals(selected)) {
            var m = message();
            if (m != null && m.unread()) {
                for (int i = 0; i < Math.min(2, m.responses().size()); i++) {
                    if (inside(mx, my, left + 20, top + 132 + i * 20, 91, 16)) {
                        ClientPlayNetworking.send(new PhoneActionPayload("respond", m.key(), Integer.toString(i)));
                        return true;
                    }
                }
            }
            if (pagerClick(mx, my, snapshot.messages().size())) {
                messageIndex = Math.floorMod(messageIndex + (mx < left + W / 2 ? -1 : 1), snapshot.messages().size());
                return true;
            }
        }

        if ("contacts".equals(selected) && pagerClick(mx, my, snapshot.contacts().size())) {
            contactIndex = Math.floorMod(contactIndex + (mx < left + W / 2 ? -1 : 1), snapshot.contacts().size());
            return true;
        }

        if ("side_quests".equals(selected)) {
            var q = quest();
            if (q != null && !q.completed() && inside(mx, my, left + 37, top + 149, 57, 15)) {
                ClientPlayNetworking.send(new PhoneActionPayload("track_quest", q.id(), ""));
                return true;
            }
            if (pagerClick(mx, my, snapshot.quests().size())) {
                questIndex = Math.floorMod(questIndex + (mx < left + W / 2 ? -1 : 1), snapshot.quests().size());
                return true;
            }
        }

        if ("faction".equals(selected)) {
            var f = snapshot.faction();
            boolean none = "No Faction".equals(f.name());
            if (none) {
                if (inside(mx, my, left + 91, top + 118, 20, 15)) {
                    ClientPlayNetworking.send(new PhoneActionPayload("faction_create", factionName.getValue(), ""));
                    return true;
                }
                if (f.pendingInvites() != null && !f.pendingInvites().isEmpty()
                        && inside(mx, my, left + 20, top + 153, 91, 15)) {
                    ClientPlayNetworking.send(new PhoneActionPayload("faction_accept", f.pendingInvites().get(0), ""));
                    return true;
                }
            } else {
                boolean staff = "OWNER".equals(f.role()) || "OFFICER".equals(f.role());
                if (staff && inside(mx, my, left + 91, top + 118, 20, 15)) {
                    ClientPlayNetworking.send(new PhoneActionPayload("faction_invite", factionMember.getValue(), ""));
                    return true;
                }
                if (inside(mx, my, left + 20, top + 144, 43, 15)) {
                    ClientPlayNetworking.send(new PhoneActionPayload("faction_island_join", "", ""));
                    return true;
                }
                if (inside(mx, my, left + 68, top + 144, 43, 15)) {
                    ClientPlayNetworking.send(new PhoneActionPayload(
                            "OWNER".equals(f.role()) ? "faction_disband" : "faction_leave", "", ""));
                    return true;
                }
            }
        }

        return true;
    }

    private void select(String id) {
        selected = id == null || id.isBlank() ? "home" : id;
        updateFields();
    }

    private void updateFields() {
        if (factionName == null || factionMember == null) return;
        boolean faction = "faction".equals(selected);
        boolean none = "No Faction".equals(snapshot.faction().name());
        boolean staff = "OWNER".equals(snapshot.faction().role()) || "OFFICER".equals(snapshot.faction().role());
        factionName.setVisible(faction && none);
        factionMember.setVisible(faction && !none && staff);
    }

    private boolean pagerClick(int x, int y, int size) {
        return size > 1 && (inside(x, y, left + 22, top + 168, 18, 12)
                || inside(x, y, left + 91, top + 168, 18, 12));
    }

    private void pager(GuiGraphics g, int index, int size, int mouseX, int mouseY) {
        if (size <= 1) return;
        chip(g, left + 22, top + 168, 18, 12, "‹", mouseX, mouseY);
        center(g, (index + 1) + "/" + size, left + W / 2, top + 170, 0.46F, MUTED);
        chip(g, left + 91, top + 168, 18, 12, "›", mouseX, mouseY);
    }

    private void chip(GuiGraphics g, int x, int y, int w, int h, String label, int mouseX, int mouseY) {
        g.fill(x, y, x + w, y + h, inside(mouseX, mouseY, x, y, w, h) ? CHIP_HOVER : CHIP);
        g.fill(x, y + h - 1, x + w, y + h, 0xFF95C8CB);
        center(g, label, x + w / 2, y + 4, 0.46F, INK);
    }

    private void kv(GuiGraphics g, String key, String value, int x, int y, int w) {
        text(g, key, x, y, 0.5F, MUTED);
        textRight(g, safe(value, "—"), x + w, y, 0.5F, INK);
        g.fill(x, y + 11, x + w, y + 12, 0xFFD0E9EA);
    }

    private void wrap(GuiGraphics g, String value, int x, int y, int width, float scale, int max, int color) {
        int virtual = Math.max(1, (int) (width / scale));
        var lines = font.split(Component.literal(safe(value, "")), virtual);
        int count = Math.min(max, lines.size());
        for (int i = 0; i < count; i++) {
            g.pose().pushPose();
            g.pose().translate(x, y + i * (font.lineHeight * scale + 1.0F), 0.0F);
            g.pose().scale(scale, scale, 1.0F);
            g.drawString(font, lines.get(i), 0, 0, color, false);
            g.pose().popPose();
        }
    }

    private void text(GuiGraphics g, String value, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, safe(value, ""), 0, 0, color, false);
        g.pose().popPose();
    }

    private void textRight(GuiGraphics g, String value, int x, int y, float scale, int color) {
        String v = safe(value, "");
        text(g, v, x - Math.round(font.width(v) * scale), y, scale, color);
    }

    private void center(GuiGraphics g, String value, int x, int y, float scale, int color) {
        String v = safe(value, "");
        text(g, v, x - Math.round(font.width(v) * scale / 2.0F), y, scale, color);
    }

    private CWorldNetworking.MessageView message() {
        if (snapshot.messages() == null || snapshot.messages().isEmpty()) return null;
        messageIndex = Math.floorMod(messageIndex, snapshot.messages().size());
        return snapshot.messages().get(messageIndex);
    }

    private CWorldNetworking.ContactView contact() {
        if (snapshot.contacts() == null || snapshot.contacts().isEmpty()) return null;
        contactIndex = Math.floorMod(contactIndex, snapshot.contacts().size());
        return snapshot.contacts().get(contactIndex);
    }

    private CWorldNetworking.QuestView quest() {
        if (snapshot.quests() == null || snapshot.quests().isEmpty()) return null;
        questIndex = Math.floorMod(questIndex, snapshot.quests().size());
        return snapshot.quests().get(questIndex);
    }

    private int unread() {
        int count = 0;
        if (snapshot.messages() != null) for (var m : snapshot.messages()) if (m.unread()) count++;
        return count;
    }

    private static int newestUnreadIndex(List<CWorldNetworking.MessageView> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        for (int i = messages.size() - 1; i >= 0; i--) if (messages.get(i).unread()) return i;
        return messages.size() - 1;
    }

    private static String shortLabel(String value) {
        return switch (value) {
            case "Trainer Card" -> "Trainer";
            case "Side Quests" -> "Quests";
            case "Level Cap" -> "Cap";
            default -> value;
        };
    }

    private static String label(String id) {
        for (App app : APPS) if (app.id.equals(id)) return app.label;
        return "Trainer Phone";
    }

    private static String initials(String value) {
        if (value == null || value.isBlank()) return "?";
        String[] p = value.trim().split("\\s+");
        if (p.length == 1) return p[0].substring(0, Math.min(2, p[0].length())).toUpperCase(Locale.ROOT);
        return (p[0].substring(0, 1) + p[p.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
    }

    private static String human(String value) {
        if (value == null || value.isBlank()) return "";
        StringBuilder out = new StringBuilder();
        for (String word : value.replace('-', '_').split("_")) {
            if (word.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static boolean inside(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int whole(double value) {
        return (int) Math.round(value);
    }

    public void qaSelectApp(String id) {
        select(id == null || id.isBlank() ? "home" : id);
    }

    public String qaSelectedApp() {
        return selected;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record App(String label, String id) {}
}
