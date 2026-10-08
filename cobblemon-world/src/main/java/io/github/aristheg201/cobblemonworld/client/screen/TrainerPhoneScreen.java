package io.github.aristheg201.cobblemonworld.client.screen;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.network.PhoneActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;

public final class TrainerPhoneScreen extends Screen {
    private static final Gson GSON = new Gson();

    // Original Cobblemon Smartphone geometry.
    private static final int SMALL_W = 131;
    private static final int LARGE_W = 211;
    private static final int H = 207;
    private static final int GRID_COLUMNS = 2;
    private static final int GRID_ROWS = 3;
    private static final int PER_PAGE = GRID_COLUMNS * GRID_ROWS;
    private static final int GRID_START_X = 26;
    private static final int GRID_START_Y = 37;
    private static final int BUTTON_SPACING = 43;
    private static final int BUTTON_SIZE = 36;
    private static final int FOOTER_PREV_X = 36;
    private static final int FOOTER_HOME_X = 62;
    private static final int FOOTER_NEXT_X = 88;
    private static final int FOOTER_Y = 187;
    private static final int FOOTER_SIZE = 7;
    private static final int DOT_CENTER_X = SMALL_W / 2;
    private static final int DOT_Y = 169;
    private static final int DOT_SIZE = 9;
    private static final int DOT_SPACING = 2;

    // Original large-screen content geometry/palette.
    private static final int BACK_X = 20;
    private static final int BACK_Y = 14;
    private static final int CONTENT_X = 20;
    private static final int CONTENT_Y = 31;
    private static final int CONTENT_W = 171;
    private static final int CONTENT_BOTTOM = 194;
    private static final int SECTION_TITLE_BG = 0xFF3A96B6;
    private static final int SECTION_CONTENT_BG = 0xFFEFFDFF;
    private static final int SECTION_CONTENT_ALT = 0xFFDCEFF2;
    private static final int CONTENT_TEXT = 0xFF1A1A2E;
    private static final int CONTENT_DIM = 0xFF555555;
    private static final int CONTENT_GOLD = 0xFFB8860B;
    private static final int HOVER_CYAN = 0xFF4FB4D6;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GOLD = 0xFFFFD700;
    private static final int DANGER = 0xFFD03030;

    private static final ResourceLocation SMALL_FRAME = tex("textures/gui/smartphone_red.png");
    private static final ResourceLocation HOME_SCREEN = tex("textures/gui/home_screen.png");
    private static final ResourceLocation LARGE_FRAME = tex("textures/gui/large_smartphone_red.png");
    private static final ResourceLocation LARGE_SCREEN = tex("textures/gui/large_screen.png");
    private static final ResourceLocation PREV_BUTTON = tex("textures/gui/elements/prev_button.png");
    private static final ResourceLocation HOME_BUTTON = tex("textures/gui/elements/home_button.png");
    private static final ResourceLocation NEXT_BUTTON = tex("textures/gui/elements/next_button.png");
    private static final ResourceLocation DOT_ON = tex("textures/gui/elements/page_dot_on.png");
    private static final ResourceLocation DOT_OFF = tex("textures/gui/elements/page_dot_off.png");

    private static final List<App> APPS = List.of(
            new App("ui.cobblemonworld.trainer_card", "trainer_card", "trainer"),
            new App("ui.cobblemonworld.objective", "objective", "gps"),
            new App("ui.cobblemonworld.current_story", "story", "patchouli"),
            new App("ui.cobblemonworld.side_quests", "side_quests", "structure_compass"),
            new App("ui.cobblemonworld.level_cap", "level_cap", "pokeinfo"),
            new App("ui.cobblemonworld.badges", "badges", "pokedex"),
            new App("ui.cobblemonworld.contacts", "contacts", "social"),
            new App("ui.cobblemonworld.messages", "messages", "cloud"),
            new App("ui.cobblemonworld.league", "league", "cobbledollars"),
            new App("ui.cobblemonworld.faction", "faction", "waystone")
    );

    private CWorldNetworking.PhoneSnapshot snapshot;
    private String selected = "home";
    private int homePage;
    private int messageIndex;
    private int responseScroll;
    private int transcriptScroll = Integer.MAX_VALUE;
    private int contactIndex;
    private int questIndex;
    private int smallX;
    private int largeX;
    private int top;
    private EditBox factionName;
    private EditBox factionMember;

    public TrainerPhoneScreen(String json) {
        super(Component.translatable("ui.cobblemonworld.trainer_phone"));
        snapshot = GSON.fromJson(json, CWorldNetworking.PhoneSnapshot.class);
        messageIndex = newestUnreadIndex(snapshot.messages());
    }

    public void qaClickReply(int index) {
        qaSelectApp("messages"); responseScroll = Math.max(0, index - 1);
        mouseClicked(largeX + CONTENT_X + 18, top + 141 + (index - responseScroll) * 18, 0);
    }
    public void qaPinQuest() {
        qaSelectApp("side_quests");
        mouseClicked(largeX + CONTENT_X + 20, top + 165, 0);
    }
    public void qaPinQuest(String id) {
        qaSelectApp("side_quests");
        for(int i=0;i<snapshot.quests().size();i++){
            if(quest()!=null && quest().id().equals(id)){qaPinQuest();return;}
            mouseClicked(largeX+165,top+182,0);
        }
        throw new IllegalStateException("Quest unavailable in actual phone: "+id);
    }
    public void update(String json) {
        String contact = message() == null ? "" : message().contactId();
        snapshot = GSON.fromJson(json, CWorldNetworking.PhoneSnapshot.class);
        for (int i = 0; i < snapshot.messages().size(); i++) if (snapshot.messages().get(i).contactId().equals(contact)) messageIndex = i;
        responseScroll = 0; transcriptScroll = Integer.MAX_VALUE;
        updateFields();
    }

    @Override
    protected void init() {
        clearWidgets();
        smallX = (width - SMALL_W) / 2;
        largeX = (width - LARGE_W) / 2;
        top = (height - H) / 2;

        factionName = new EditBox(font, largeX + 35, top + 108, 111, 14, Component.translatable("ui.cobblemonworld.faction_name"));
        factionName.setHint(Component.translatable("ui.cobblemonworld.faction_name"));
        factionName.setBordered(false);
        factionName.setMaxLength(24);
        addRenderableWidget(factionName);

        factionMember = new EditBox(font, largeX + 35, top + 108, 111, 14, Component.translatable("ui.cobblemonworld.player_name"));
        factionMember.setHint(Component.translatable("ui.cobblemonworld.player_name"));
        factionMember.setBordered(false);
        factionMember.setMaxLength(24);
        addRenderableWidget(factionMember);
        updateFields();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if ("home".equals(selected)) {
            renderHome(g, mouseX, mouseY);
        } else {
            renderApp(g, mouseX, mouseY);
        }

        // Do not call Screen.render(): in 1.21.1 that path applies the vanilla menu blur.
        if (factionName != null && factionName.visible) factionName.render(g, mouseX, mouseY, partialTick);
        if (factionMember != null && factionMember.visible) factionMember.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Deliberately empty. Cobblemon Smartphone is an in-world handheld overlay.
    }

    private void renderHome(GuiGraphics g, int mouseX, int mouseY) {
        blit(g, SMALL_FRAME, smallX, top, SMALL_W, H);
        blit(g, HOME_SCREEN, smallX, top, SMALL_W, H);
        renderWorldTime(g, smallX);

        int from = homePage * PER_PAGE;
        int to = Math.min(APPS.size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            int local = i - from;
            int bx = smallX + GRID_START_X + (local % GRID_COLUMNS) * BUTTON_SPACING;
            int by = top + GRID_START_Y + (local / GRID_COLUMNS) * BUTTON_SPACING;
            App app = APPS.get(i);
            boolean hovered = inside(mouseX, mouseY, bx, by, BUTTON_SIZE, BUTTON_SIZE);
            ResourceLocation icon = buttonTexture(app.icon(), hovered);
            blit(g, icon, bx, by, BUTTON_SIZE, BUTTON_SIZE);

            if ("messages".equals(app.id()) && unread() > 0) {
                renderBadge(g, unread(), bx, by);
            }
        }

        renderPageDots(g);
        renderFooterButtons(g, mouseX, mouseY);
        renderHomeTooltip(g, mouseX, mouseY);
    }

    private void renderWorldTime(GuiGraphics g, int x) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long ticksToday = Math.floorMod(mc.level.getDayTime(), 24_000L);
        long totalMinutes = ((ticksToday * 1_440L / 24_000L) + 360L) % 1_440L;
        String value = "%02d:%02d".formatted(totalMinutes / 60L, totalMinutes % 60L);

        g.pose().pushPose();
        g.pose().translate(x + 20.0, top + 20.0, 0.0);
        g.pose().scale(0.6F, 0.6F, 1.0F);
        g.drawString(font, value, 0, 0, 0xFFE6FFFF, false);
        g.pose().popPose();
    }

    private void renderPageDots(GuiGraphics g) {
        int pages = Math.max(1, (APPS.size() + PER_PAGE - 1) / PER_PAGE);
        if (pages <= 1) return;

        int dotCount = Math.min(3, pages);
        int active = pages <= 3 ? homePage : (homePage == 0 ? 0 : homePage == pages - 1 ? dotCount - 1 : 1);
        int startX = DOT_CENTER_X - ((dotCount * DOT_SIZE + (dotCount - 1) * DOT_SPACING) / 2);
        for (int i = 0; i < dotCount; i++) {
            ResourceLocation dot = i == active ? DOT_ON : DOT_OFF;
            int yOffset = i == active ? 0 : 1;
            blit(g, dot, smallX + startX + i * (DOT_SIZE + DOT_SPACING), top + DOT_Y + yOffset, DOT_SIZE, DOT_SIZE);
        }
    }

    private void renderFooterButtons(GuiGraphics g, int mouseX, int mouseY) {
        renderFooterButton(g, PREV_BUTTON, FOOTER_PREV_X, mouseX, mouseY);
        renderFooterButton(g, HOME_BUTTON, FOOTER_HOME_X, mouseX, mouseY);
        renderFooterButton(g, NEXT_BUTTON, FOOTER_NEXT_X, mouseX, mouseY);
    }

    private void renderFooterButton(GuiGraphics g, ResourceLocation texture, int relativeX, int mouseX, int mouseY) {
        int x = smallX + relativeX;
        int y = top + FOOTER_Y;
        boolean hovered = inside(mouseX, mouseY, x, y, FOOTER_SIZE, FOOTER_SIZE);
        float v = hovered ? FOOTER_SIZE : 0.0F;
        g.blit(texture, x, y, 0, 0.0F, v, FOOTER_SIZE, FOOTER_SIZE, FOOTER_SIZE, FOOTER_SIZE * 2);
    }

    private void renderBadge(GuiGraphics g, int count, int bx, int by) {
        String label = count > 99 ? "99+" : Integer.toString(count);
        int badgeW = Math.max(10, font.width(ui(label)) / 2 + 4);
        int badgeX = bx + BUTTON_SIZE - badgeW;
        g.fill(badgeX, by, bx + BUTTON_SIZE, by + 10, DANGER);
        scaledText(g, label, badgeX + 2, by + 2, 0.5F, WHITE);
    }

    private void renderHomeTooltip(GuiGraphics g, int mouseX, int mouseY) {
        int from = homePage * PER_PAGE;
        int to = Math.min(APPS.size(), from + PER_PAGE);
        for (int i = from; i < to; i++) {
            int local = i - from;
            int bx = smallX + GRID_START_X + (local % GRID_COLUMNS) * BUTTON_SPACING;
            int by = top + GRID_START_Y + (local / GRID_COLUMNS) * BUTTON_SPACING;
            if (inside(mouseX, mouseY, bx, by, BUTTON_SIZE, BUTTON_SIZE)) {
                g.renderTooltip(font, Component.translatable(APPS.get(i).label()), mouseX, mouseY);
                return;
            }
        }
    }

    private void renderApp(GuiGraphics g, int mouseX, int mouseY) {
        blit(g, LARGE_FRAME, largeX, top, LARGE_W, H);
        blit(g, LARGE_SCREEN, largeX, top, LARGE_W, H);

        boolean backHover = inside(mouseX, mouseY, largeX + BACK_X - 2, top + BACK_Y - 2, 34, 12);
        g.drawString(font, ui("ui.cobblemonworld.back"), largeX + BACK_X, top + BACK_Y, backHover ? GOLD : WHITE, false);

        String appTitle = ui(appLabel(selected));
        g.drawString(font, appTitle, largeX + (LARGE_W - font.width(appTitle)) / 2, top + BACK_Y, WHITE, false);

        int x = largeX + CONTENT_X;
        int y = top + CONTENT_Y;

        switch (selected) {
            case "trainer_card" -> renderTrainerCard(g, x, y);
            case "objective" -> renderObjective(g, x, y);
            case "story" -> renderStory(g, x, y);
            case "side_quests" -> renderQuest(g, x, y, mouseX, mouseY);
            case "level_cap" -> renderLevelCap(g, x, y);
            case "badges" -> renderBadges(g, x, y);
            case "contacts" -> renderContact(g, x, y, mouseX, mouseY);
            case "messages" -> renderMessage(g, x, y, mouseX, mouseY);
            case "league" -> renderLeague(g, x, y);
            case "faction" -> renderFaction(g, x, y, mouseX, mouseY);
            default -> select("home");
        }
    }

    private void renderTrainerCard(GuiGraphics g, int x, int y) {
        section(g, x, y, CONTENT_W, 18, "ui.cobblemonworld.trainer");
        int cy = y + 18;
        surface(g, x, cy, CONTENT_W, 118);
        g.drawString(font, snapshot.trainerName(), x + 8, cy + 8, CONTENT_TEXT, false);
        field(g, "ui.cobblemonworld.level_cap", "Lv." + snapshot.levelCap(), x + 8, cy + 27, CONTENT_W - 16);
        field(g, "ui.cobblemonworld.badges", Integer.toString(snapshot.badges().size()), x + 8, cy + 43, CONTENT_W - 16);
        field(g, "ui.cobblemonworld.league", snapshot.leagueTier(), x + 8, cy + 59, CONTENT_W - 16);
        field(g, "ui.cobblemonworld.faction", snapshot.faction().name(), x + 8, cy + 75, CONTENT_W - 16);
        if (snapshot.rpg() != null && snapshot.rpg().available()) {
            field(g, "RPG", snapshot.rpg().classId() + " Lv." + snapshot.rpg().level(), x + 8, cy + 91, CONTENT_W - 16);
            String meters = "STA " + whole(snapshot.rpg().stamina()) + "/" + whole(snapshot.rpg().maxStamina())
                    + "   MANA " + whole(snapshot.rpg().mana()) + "/" + whole(snapshot.rpg().maxMana());
            g.drawString(font, meters, x + 8, cy + 107, CONTENT_DIM, false);
        }
    }

    private void renderObjective(GuiGraphics g, int x, int y) {
        section(g, x, y, CONTENT_W, 18, "ui.cobblemonworld.current_objective");
        surface(g, x, y + 18, CONTENT_W, 126);
        wrapped(g, safe(snapshot.story().objective(), "ui.cobblemonworld.no_tracked_objective"), x + 8, y + 30, CONTENT_W - 16, 12, CONTENT_TEXT);
    }

    private void renderStory(GuiGraphics g, int x, int y) {
        section(g, x, y, CONTENT_W, 18, snapshot.story().title());
        surface(g, x, y + 18, CONTENT_W, 126);

        g.fill(x + 8, y + 42, x + CONTENT_W - 8, y + 43, 0xFFB4DCDD);
        wrapped(g, safe(snapshot.story().objective(), "ui.cobblemonworld.story_complete"), x + 8, y + 51, CONTENT_W - 16, 10, CONTENT_TEXT);
    }

    private void renderQuest(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        var q = quest();
        section(g, x, y, CONTENT_W, 18, "ui.cobblemonworld.side_quests");
        surface(g, x, y + 18, CONTENT_W, 126);
        if (q == null) {
            g.drawString(font, ui("ui.cobblemonworld.no_side_quests_yet"), x + 8, y + 31, CONTENT_DIM, false);
            return;
        }
        g.drawString(font, (q.completed() ? ui("ui.cobblemonworld.completed_prefix") : "") + ui(q.title()), x + 8, y + 29,
                q.completed() ? 0xFF397A4A : CONTENT_TEXT, false);
        g.drawString(font, q.giver() + "   " + q.progress() + "/" + q.required(), x + 8, y + 43, CONTENT_DIM, false);
        wrapped(g, q.description(), x + 8, y + 58, CONTENT_W - 16, 7, CONTENT_TEXT);
        renderPager(g, questIndex, snapshot.quests().size(), mouseX, mouseY);
        if (!q.completed()) button(g, x + 8, top + 161, 155, 16, "ui.cobblemonworld.pin_to_objective", mouseX, mouseY);
    }

    private void renderLevelCap(GuiGraphics g, int x, int y) {
        section(g, x, y, CONTENT_W, 18, "ui.cobblemonworld.level_cap");
        surface(g, x, y + 18, CONTENT_W, 126);
        String cap = "Lv." + snapshot.levelCap();
        g.drawString(font, cap, x + (CONTENT_W - font.width(cap)) / 2, y + 30, CONTENT_GOLD, false);
        g.fill(x + 28, y + 46, x + CONTENT_W - 28, y + 48, SECTION_TITLE_BG);
        wrapped(g,
                "ui.cobblemonworld.cap_explanation",
                x + 8, y + 58, CONTENT_W - 16, 9, CONTENT_TEXT);
    }

    private void renderBadges(GuiGraphics g, int x, int y) {
        section(g, x, y, CONTENT_W, 18, "ui.cobblemonworld.badges");
        surface(g, x, y + 18, CONTENT_W, 126);
        if (snapshot.badges().isEmpty()) {
            g.drawString(font, ui("ui.cobblemonworld.no_badges_yet"), x + 8, y + 31, CONTENT_DIM, false);
            return;
        }
        int cy = y + 29;
        for (String badge : snapshot.badges()) {
            if (cy > top + CONTENT_BOTTOM - 13) break;
            g.fill(x + 8, cy + 1, x + 16, cy + 9, CONTENT_GOLD);
            g.drawString(font, ui("badge.cobblemonworld." + badge), x + 22, cy, CONTENT_TEXT, false);
            cy += 14;
        }
    }

    private void renderContact(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        var c = contact();
        section(g, x, y, CONTENT_W, 18, "ui.cobblemonworld.contacts");
        surface(g, x, y + 18, CONTENT_W, 126);
        if (c == null) {
            g.drawString(font, ui("ui.cobblemonworld.no_contacts_yet"), x + 8, y + 31, CONTENT_DIM, false);
            return;
        }
        g.fill(x + 8, y + 28, x + 34, y + 54, SECTION_CONTENT_ALT);
        String initials = initials(c.displayName());
        g.drawString(font, initials, x + 21 - font.width(initials) / 2, y + 37, SECTION_TITLE_BG, false);
        g.drawString(font, c.displayName(), x + 42, y + 30, CONTENT_TEXT, false);
        g.drawString(font, c.unread() > 0 ? Component.translatable("ui.cobblemonworld.unread", c.unread()).getString() : ui("ui.cobblemonworld.up_to_date"), x + 42, y + 43,
                c.unread() > 0 ? DANGER : CONTENT_DIM, false);
        g.fill(x + 8, y + 62, x + CONTENT_W - 8, y + 63, 0xFFB4DCDD);
        wrapped(g, "ui.cobblemonworld.story_contacts_unlock_after_major_battles_and_investigations",
                x + 8, y + 72, CONTENT_W - 16, 6, CONTENT_TEXT);
        renderPager(g, contactIndex, snapshot.contacts().size(), mouseX, mouseY);
    }

    private void renderMessage(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        var m = message();
        section(g, x, y, CONTENT_W, 18, "ui.cobblemonworld.messages");
        surface(g, x, y + 18, CONTENT_W, 126);
        if (m == null) { g.drawString(font, ui("ui.cobblemonworld.no_messages_yet"), x + 8, y + 31, CONTENT_DIM, false); return; }
        var turns = snapshot.messages().stream().filter(t -> t.contactId().equals(m.contactId())).toList();
        var reply = activeReply(m.contactId());
        int bodyBottom = reply == null ? top + 172 : top + 132;
        int cy = y + 23;
        java.util.List<TranscriptLine> lines = new java.util.ArrayList<>();
        for (var turn : turns) {
            lines.add(new TranscriptLine(turn.sender(), true));
            for (var text : font.split(Component.translatable(turn.text()), CONTENT_W - 18))
                lines.add(new TranscriptLine(text, false));
            lines.add(new TranscriptLine("", false));
        }
        int visible = Math.max(1, (bodyBottom - cy) / 11);
        int maxScroll = Math.max(0, lines.size() - visible);
        transcriptScroll = Math.min(transcriptScroll, maxScroll);
        for (int i = transcriptScroll; i < Math.min(lines.size(), transcriptScroll + visible); i++) {
            var line = lines.get(i);
            int ly = cy + (i - transcriptScroll) * 11;
            if (line.value() instanceof net.minecraft.util.FormattedCharSequence seq) g.drawString(font, seq, x + 8, ly, CONTENT_TEXT, false);
            else g.drawString(font, line.value().toString(), x + 8, ly, line.sender() ? SECTION_TITLE_BG : CONTENT_TEXT, false);
        }
        if (maxScroll > 0) g.fill(x + CONTENT_W - 4, cy + transcriptScroll * (bodyBottom - cy - 8) / Math.max(1,maxScroll), x + CONTENT_W - 2, cy + 8 + transcriptScroll * (bodyBottom - cy - 8) / Math.max(1,maxScroll), SECTION_TITLE_BG);
        if (reply != null) {
            responseScroll = Math.min(responseScroll, Math.max(0, reply.responses().size() - 2));
            for (int i = 0; i < 2 && i + responseScroll < reply.responses().size(); i++)
                button(g, x + 8, top + 136 + i * 18, CONTENT_W - 16, 16, reply.responses().get(i + responseScroll), mouseX, mouseY);
            if (reply.responses().size() > 2) g.drawString(font, "↕", x + CONTENT_W - 8, top + 145, CONTENT_DIM, false);
        }
        renderPager(g, messageIndex, snapshot.messages().size(), mouseX, mouseY);
    }
    private record TranscriptLine(Object value, boolean sender) {}
    private CWorldNetworking.MessageView activeReply(String contact) {
        CWorldNetworking.MessageView reply = null;
        for (var m : snapshot.messages()) if (m.contactId().equals(contact) && !m.responses().isEmpty()) reply = m;
        return reply;
    }

    private void renderLeague(GuiGraphics g, int x, int y) {
        section(g, x, y, CONTENT_W, 18, "ui.cobblemonworld.league");
        surface(g, x, y + 18, CONTENT_W, 126);
        String tier = ui("league.cobblemonworld.tier." + snapshot.leagueTier().toLowerCase(Locale.ROOT));
        g.drawString(font, tier, x + (CONTENT_W - font.width(tier)) / 2, y + 31, CONTENT_GOLD, false);
        String points = Component.translatable("league.cobblemonworld.points", snapshot.leaguePoints()).getString();
        g.drawString(font, points, x + (CONTENT_W - font.width(points)) / 2, y + 48, CONTENT_TEXT, false);
        g.fill(x + 28, y + 66, x + CONTENT_W - 28, y + 68, SECTION_TITLE_BG);
        wrapped(g, "ui.cobblemonworld.main_story_milestones_award_league_points__eight_major_badges_or_800_points_reaches_champion_tier",
                x + 8, y + 78, CONTENT_W - 16, 6, CONTENT_TEXT);
    }

    private void renderFaction(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        var f = snapshot.faction();
        section(g, x, y, CONTENT_W, 18, "ui.cobblemonworld.faction");
        surface(g, x, y + 18, CONTENT_W, 126);

        boolean none = "No Faction".equals(f.name());
        if (none) {
            g.drawString(font, ui("ui.cobblemonworld.no_faction_yet"), x + 8, y + 30, CONTENT_TEXT, false);
            g.drawString(font, ui("ui.cobblemonworld.create_one_or_accept_an_invitation"), x + 8, y + 44, CONTENT_DIM, false);
            fieldBox(g, largeX + 31, top + 104, 119, 20);
            button(g, largeX + 155, top + 105, 35, 18, "ui.cobblemonworld.create", mouseX, mouseY);
            if (f.pendingInvites() != null && !f.pendingInvites().isEmpty()) {
                g.drawString(font, Component.translatable("ui.cobblemonworld.invitation", f.pendingInvites().get(0)).getString(), x + 8, top + 135, CONTENT_GOLD, false);
                button(g, x + 8, top + 150, CONTENT_W - 16, 18, "ui.cobblemonworld.accept_invite", mouseX, mouseY);
            }
            return;
        }

        g.drawString(font, f.name(), x + 8, y + 29, CONTENT_TEXT, false);
        g.drawString(font, Component.translatable("ui.cobblemonworld.faction_members", Component.translatable("faction.cobblemonworld.role." + f.role().toLowerCase(Locale.ROOT)), f.memberCount()).getString(), x + 8, y + 43, CONTENT_DIM, false);
        field(g, "ui.cobblemonworld.island", ui("faction.cobblemonworld.phase." + f.phase().toLowerCase(Locale.ROOT)), x + 8, y + 59, CONTENT_W - 16);
        field(g, "ui.cobblemonworld.affinity", ui("faction.cobblemonworld.affinity." + f.affinity().toLowerCase(Locale.ROOT)), x + 8, y + 75, CONTENT_W - 16);
        field(g, "ui.cobblemonworld.gate", f.gateScore() + (f.qualified() ? " - " + ui("ui.cobblemonworld.qualified") : ""), x + 8, y + 91, CONTENT_W - 16);
        field(g, "ui.cobblemonworld.control", f.controlPoints() + "/5", x + 8, y + 107, CONTENT_W - 16);

        boolean staff = "OWNER".equals(f.role()) || "OFFICER".equals(f.role());
        if (staff) {
            fieldBox(g, largeX + 31, top + 137, 119, 20);
            button(g, largeX + 155, top + 138, 35, 18, "ui.cobblemonworld.invite", mouseX, mouseY);
        }
        button(g, x + 8, top + 166, 72, 18, "ui.cobblemonworld.join_island", mouseX, mouseY);
        button(g, x + 91, top + 166, 72, 18, "OWNER".equals(f.role()) ? "ui.cobblemonworld.disband" : "ui.cobblemonworld.leave", mouseX, mouseY);
    }

    private static String ui(String text) {
        return Component.translatable(text).getString();
    }

    private void section(GuiGraphics g, int x, int y, int w, int h, String title) {
        g.fill(x, y, x + w, y + h, SECTION_TITLE_BG);
        String shown = font.plainSubstrByWidth(ui(safe(title, "")), w - 12);
        g.drawString(font, shown, x + 6, y + 5, WHITE, false);
    }

    private void surface(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SECTION_CONTENT_BG);
    }

    private void field(GuiGraphics g, String label, String value, int x, int y, int w) {
        g.drawString(font, ui(label), x, y, CONTENT_DIM, false);
        String shown = font.plainSubstrByWidth(ui(safe(value, "-")), Math.max(20, w - font.width(ui(label)) - 12));
        g.drawString(font, shown, x + w - font.width(shown), y, CONTENT_TEXT, false);
        g.fill(x, y + 11, x + w, y + 12, 0xFFD0E9EA);
    }

    private void fieldBox(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SECTION_CONTENT_ALT);
        g.fill(x, y, x + w, y + 1, SECTION_TITLE_BG);
        g.fill(x, y + h - 1, x + w, y + h, SECTION_TITLE_BG);
        g.fill(x, y, x + 1, y + h, SECTION_TITLE_BG);
        g.fill(x + w - 1, y, x + w, y + h, SECTION_TITLE_BG);
    }

    private void wrapped(GuiGraphics g, String text, int x, int y, int width, int maxLines, int color) {
        int line = 0;
        for (var seq : font.split(Component.translatable(safe(text, "")), width)) {
            g.drawString(font, seq, x, y + line * 11, color, false);
            if (++line >= maxLines) break;
        }
    }

    private void renderPager(GuiGraphics g, int index, int size, int mouseX, int mouseY) {
        if (size <= 1) return;
        button(g, largeX + 24, top + 177, 28, 14, "<", mouseX, mouseY);
        String page = (index + 1) + "/" + size;
        g.drawString(font, page, largeX + (LARGE_W - font.width(page)) / 2, top + 180, CONTENT_DIM, false);
        button(g, largeX + 159, top + 177, 28, 14, ">", mouseX, mouseY);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, int mouseX, int mouseY) {
        boolean hover = inside(mouseX, mouseY, x, y, w, h);
        int bg = hover ? HOVER_CYAN : SECTION_TITLE_BG;
        g.fill(x, y, x + w, y + h, bg);
        String shown = font.plainSubstrByWidth(ui(label), w - 8);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - font.lineHeight) / 2, WHITE, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        int mx = (int) mouseX;
        int my = (int) mouseY;

        if ("home".equals(selected)) {
            int from = homePage * PER_PAGE;
            int to = Math.min(APPS.size(), from + PER_PAGE);
            for (int i = from; i < to; i++) {
                int local = i - from;
                int bx = smallX + GRID_START_X + (local % GRID_COLUMNS) * BUTTON_SPACING;
                int by = top + GRID_START_Y + (local / GRID_COLUMNS) * BUTTON_SPACING;
                if (inside(mx, my, bx, by, BUTTON_SIZE, BUTTON_SIZE)) {
                    select(APPS.get(i).id());
                    return true;
                }
            }

            int pages = Math.max(1, (APPS.size() + PER_PAGE - 1) / PER_PAGE);
            if (footerHit(mx, my, FOOTER_PREV_X)) {
                homePage = Math.max(0, homePage - 1);
                return true;
            }
            if (footerHit(mx, my, FOOTER_HOME_X)) {
                homePage = 0;
                return true;
            }
            if (footerHit(mx, my, FOOTER_NEXT_X)) {
                homePage = Math.min(pages - 1, homePage + 1);
                return true;
            }
            return inside(mx, my, smallX, top, SMALL_W, H);
        }

        if (inside(mx, my, largeX + BACK_X - 2, top + BACK_Y - 2, 34, 12)) {
            select("home");
            return true;
        }

        if ("messages".equals(selected)) {
            var m = message();
            var reply = m == null ? null : activeReply(m.contactId());
            if (reply != null) {
                for (int i = 0; i < 2 && i + responseScroll < reply.responses().size(); i++) {
                    if (inside(mx, my, largeX + CONTENT_X + 8, top + 136 + i * 18, CONTENT_W - 16, 16)) {
                        ClientPlayNetworking.send(new PhoneActionPayload("respond", reply.key(), Integer.toString(i + responseScroll)));
                        return true;
                    }
                }
            }
            int delta = pagerDelta(mx, my, snapshot.messages().size());
            if (delta != 0) {
                messageIndex = Math.floorMod(messageIndex + delta, snapshot.messages().size());
                responseScroll = 0; transcriptScroll = Integer.MAX_VALUE;
                return true;
            }
        }

        if ("contacts".equals(selected)) {
            int delta = pagerDelta(mx, my, snapshot.contacts().size());
            if (delta != 0) {
                contactIndex = Math.floorMod(contactIndex + delta, snapshot.contacts().size());
                return true;
            }
        }

        if ("side_quests".equals(selected)) {
            var q = quest();
            if (q != null && !q.completed()
                    && inside(mx, my, largeX + CONTENT_X + 8, top + 161, 155, 16)) {
                ClientPlayNetworking.send(new PhoneActionPayload("pin_objective", q.id(), ""));
                return true;
            }
            int delta = pagerDelta(mx, my, snapshot.quests().size());
            if (delta != 0) {
                questIndex = Math.floorMod(questIndex + delta, snapshot.quests().size());
                return true;
            }
        }

        if ("faction".equals(selected)) {
            var f = snapshot.faction();
            boolean none = "No Faction".equals(f.name());
            if (none) {
                if (inside(mx, my, largeX + 155, top + 105, 35, 18)) {
                    ClientPlayNetworking.send(new PhoneActionPayload("faction_create", factionName.getValue(), ""));
                    return true;
                }
                if (f.pendingInvites() != null && !f.pendingInvites().isEmpty()
                        && inside(mx, my, largeX + CONTENT_X + 8, top + 150, CONTENT_W - 16, 18)) {
                    ClientPlayNetworking.send(new PhoneActionPayload("faction_accept", f.pendingInvites().get(0), ""));
                    return true;
                }
            } else {
                boolean staff = "OWNER".equals(f.role()) || "OFFICER".equals(f.role());
                if (staff && inside(mx, my, largeX + 155, top + 138, 35, 18)) {
                    ClientPlayNetworking.send(new PhoneActionPayload("faction_invite", factionMember.getValue(), ""));
                    return true;
                }
                if (inside(mx, my, largeX + CONTENT_X + 8, top + 166, 72, 18)) {
                    ClientPlayNetworking.send(new PhoneActionPayload("faction_island_join", "", ""));
                    return true;
                }
                if (inside(mx, my, largeX + CONTENT_X + 91, top + 166, 72, 18)) {
                    ClientPlayNetworking.send(new PhoneActionPayload(
                            "OWNER".equals(f.role()) ? "faction_disband" : "faction_leave", "", ""));
                    return true;
                }
            }
        }

        return inside(mx, my, largeX, top, LARGE_W, H);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if ("messages".equals(selected) && verticalAmount != 0) {
            var m = message(); var reply = m == null ? null : activeReply(m.contactId());
            if (mouseY >= top + 133 && reply != null) responseScroll = Math.max(0, Math.min(Math.max(0, reply.responses().size() - 2), responseScroll + (verticalAmount < 0 ? 1 : -1)));
            else transcriptScroll = Math.max(0, transcriptScroll + (verticalAmount < 0 ? 2 : -2));
            return true;
        }
        if (!"home".equals(selected) || verticalAmount == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
        int pages = Math.max(1, (APPS.size() + PER_PAGE - 1) / PER_PAGE);
        int next = homePage + (verticalAmount < 0.0 ? 1 : -1);
        int clamped = Math.max(0, Math.min(pages - 1, next));
        if (clamped == homePage) return false;
        homePage = clamped;
        return true;
    }

    private boolean footerHit(int x, int y, int relativeX) {
        return inside(x, y, smallX + relativeX, top + FOOTER_Y, FOOTER_SIZE, FOOTER_SIZE);
    }

    private int pagerDelta(int x, int y, int size) {
        if (size <= 1) return 0;
        if (inside(x, y, largeX + 24, top + 177, 28, 14)) return -1;
        if (inside(x, y, largeX + 159, top + 177, 28, 14)) return 1;
        return 0;
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
        if (snapshot.messages() != null) {
            for (var m : snapshot.messages()) if (m.unread()) count++;
        }
        return count;
    }

    private static int newestUnreadIndex(List<CWorldNetworking.MessageView> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        for (int i = messages.size() - 1; i >= 0; i--) if (messages.get(i).unread()) return i;
        return messages.size() - 1;
    }

    private static String appLabel(String id) {
        for (App app : APPS) if (app.id().equals(id)) return ui(app.label());
        return "ui.cobblemonworld.trainer_phone";
    }

    private static String initials(String value) {
        if (value == null || value.isBlank()) return "?";
        String[] parts = value.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase(Locale.ROOT);
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

    private static int whole(double value) {
        return (int) Math.round(value);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean inside(int px, int py, int x, int y, int w, int h) {
        return px >= x && px < x + w && py >= y && py < y + h;
    }

    private static ResourceLocation tex(String path) {
        // These assets are vendored unchanged from Cobblemon Smartphone and retain
        // their upstream namespace so their pixel layout is used exactly as authored.
        return ResourceLocation.fromNamespaceAndPath("cobblemon_smartphone", path);
    }

    private static ResourceLocation buttonTexture(String icon, boolean hovered) {
        return tex("textures/gui/buttons/" + icon + (hovered ? "_hover" : "") + ".png");
    }

    private static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, int w, int h) {
        g.blit(texture, x, y, 0, 0.0F, 0.0F, w, h, w, h);
    }

    private void scaledText(GuiGraphics g, String value, int x, int y, float scale, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.drawString(font, value, 0, 0, color, false);
        g.pose().popPose();
    }

    public void qaSelectApp(String id) {
        String target = id == null || id.isBlank() ? "home" : id;
        if (selected.equals(target)) return;
        if (!selected.equals("home")) mouseClicked(largeX + BACK_X, top + BACK_Y, 0);
        if (target.equals("home")) return;
        for (int i = 0; i < APPS.size(); i++) if (APPS.get(i).id().equals(target)) {
            int page = i / PER_PAGE;
            while (homePage < page) mouseClicked(smallX + FOOTER_NEXT_X + 2, top + FOOTER_Y + 2, 0);
            while (homePage > page) mouseClicked(smallX + FOOTER_PREV_X + 2, top + FOOTER_Y + 2, 0);
            int local = i % PER_PAGE;
            mouseClicked(smallX + GRID_START_X + local % GRID_COLUMNS * BUTTON_SPACING + 8,
                    top + GRID_START_Y + local / GRID_COLUMNS * BUTTON_SPACING + 8, 0);
            if (!selected.equals(target)) throw new IllegalStateException("Phone app click did not open " + target);
            return;
        }
        throw new IllegalArgumentException("Unknown phone app " + target);
    }

    public String qaSelectedApp() {
        return selected;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record App(String label, String id, String icon) {}
}
