package io.github.aristheg201.cobblemonworld.client.screen;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.network.PhoneActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class TrainerPhoneScreen extends Screen {
    private static final Gson GSON = new Gson();
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
    private final List<AppTile> tiles = new ArrayList<>();

    private String selected = "home";
    private int messageIndex;
    private int contactIndex;
    private int questIndex;

    private Button previousButton;
    private Button nextButton;
    private Button responseOne;
    private Button responseTwo;
    private Button trackQuestButton;
    private Button createFactionButton;
    private Button joinIslandButton;
    private EditBox factionName;

    public TrainerPhoneScreen(String json) {
        super(Component.literal("Trainer Phone"));
        this.snapshot = GSON.fromJson(json, CWorldNetworking.PhoneSnapshot.class);
        this.messageIndex = newestUnreadIndex(snapshot.messages());
    }

    @Override
    protected void init() {
        clearWidgets();
        tiles.clear();

        int panelWidth = Math.min(520, width - 16);
        int panelHeight = Math.min(310, height - 16);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int appAreaWidth = Math.min(210, Math.max(160, panelWidth / 2));
        int appButtonWidth = (appAreaWidth - 18) / 2;

        for (int i = 0; i < APPS.size(); i++) {
            App app = APPS.get(i);
            int col = i % 2;
            int row = i / 2;
            int x = left + 6 + col * (appButtonWidth + 6);
            int y = top + 38 + row * 42;
            tiles.add(new AppTile(app, x, y));
            addRenderableWidget(Button.builder(Component.literal("     " + app.label()), b -> selectApp(app.id()))
                    .bounds(x, y, appButtonWidth, 34).build());
        }

        int detailLeft = left + appAreaWidth + 8;
        int detailWidth = Math.max(90, panelWidth - appAreaWidth - 16);
        int controlsY = top + panelHeight - 28;

        previousButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> previous())
                .bounds(detailLeft, controlsY, 24, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> next())
                .bounds(detailLeft + 28, controlsY, 24, 20).build());

        responseOne = addRenderableWidget(Button.builder(Component.literal("Reply 1"), b -> respond(0))
                .bounds(detailLeft + 58, controlsY, Math.max(70, (detailWidth - 64) / 2), 20).build());
        responseTwo = addRenderableWidget(Button.builder(Component.literal("Reply 2"), b -> respond(1))
                .bounds(detailLeft + 62 + Math.max(70, (detailWidth - 64) / 2), controlsY,
                        Math.max(70, (detailWidth - 64) / 2), 20).build());

        trackQuestButton = addRenderableWidget(Button.builder(Component.literal("Track Quest"), b -> trackQuest())
                .bounds(detailLeft + 58, controlsY, Math.min(120, detailWidth - 62), 20).build());

        factionName = new EditBox(font, detailLeft, controlsY - 24, Math.min(150, detailWidth), 20,
                Component.literal("Faction name"));
        factionName.setHint(Component.literal("Faction name"));
        addRenderableWidget(factionName);

        createFactionButton = addRenderableWidget(Button.builder(Component.literal("Create"), b ->
                        ClientPlayNetworking.send(new PhoneActionPayload("faction_create", factionName.getValue(), "")))
                .bounds(detailLeft + Math.min(154, detailWidth - 54), controlsY - 24, 52, 20).build());

        joinIslandButton = addRenderableWidget(Button.builder(Component.literal("Join Island"), b ->
                        ClientPlayNetworking.send(new PhoneActionPayload("faction_island_join", "", "")))
                .bounds(detailLeft + 58, controlsY, Math.min(110, detailWidth - 62), 20).build());

        updateControls();
    }

    private void selectApp(String id) {
        selected = id;
        updateControls();
    }

    private void previous() {
        if ("messages".equals(selected) && !snapshot.messages().isEmpty()) {
            messageIndex = Math.floorMod(messageIndex - 1, snapshot.messages().size());
        } else if ("contacts".equals(selected) && !snapshot.contacts().isEmpty()) {
            contactIndex = Math.floorMod(contactIndex - 1, snapshot.contacts().size());
        } else if ("side_quests".equals(selected) && !snapshot.quests().isEmpty()) {
            questIndex = Math.floorMod(questIndex - 1, snapshot.quests().size());
        }
        updateControls();
    }

    private void next() {
        if ("messages".equals(selected) && !snapshot.messages().isEmpty()) {
            messageIndex = Math.floorMod(messageIndex + 1, snapshot.messages().size());
        } else if ("contacts".equals(selected) && !snapshot.contacts().isEmpty()) {
            contactIndex = Math.floorMod(contactIndex + 1, snapshot.contacts().size());
        } else if ("side_quests".equals(selected) && !snapshot.quests().isEmpty()) {
            questIndex = Math.floorMod(questIndex + 1, snapshot.quests().size());
        }
        updateControls();
    }

    private void respond(int index) {
        CWorldNetworking.MessageView message = currentMessage();
        if (message == null || !message.unread() || index >= message.responses().size()) return;
        ClientPlayNetworking.send(new PhoneActionPayload("respond", message.key(), Integer.toString(index)));
    }

    private void trackQuest() {
        CWorldNetworking.QuestView quest = currentQuest();
        if (quest == null || quest.completed()) return;
        ClientPlayNetworking.send(new PhoneActionPayload("track_quest", quest.id(), ""));
    }

    private void updateControls() {
        boolean pageable = "messages".equals(selected) || "contacts".equals(selected) || "side_quests".equals(selected);
        previousButton.visible = pageable;
        nextButton.visible = pageable;

        CWorldNetworking.MessageView message = currentMessage();
        boolean replies = "messages".equals(selected) && message != null && message.unread();
        responseOne.visible = replies && message.responses().size() >= 1;
        responseTwo.visible = replies && message.responses().size() >= 2;
        if (responseOne.visible) responseOne.setMessage(Component.literal(message.responses().get(0)));
        if (responseTwo.visible) responseTwo.setMessage(Component.literal(message.responses().get(1)));

        CWorldNetworking.QuestView quest = currentQuest();
        trackQuestButton.visible = "side_quests".equals(selected) && quest != null && !quest.completed();

        boolean faction = "faction".equals(selected);
        factionName.setVisible(faction && "No Faction".equals(snapshot.faction().name()));
        createFactionButton.visible = faction && "No Faction".equals(snapshot.faction().name())
                && snapshot.faction().integrationAvailable();
        joinIslandButton.visible = faction && !"No Faction".equals(snapshot.faction().name())
                && !"DORMANT".equals(snapshot.faction().phase());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int panelWidth = Math.min(520, width - 16);
        int panelHeight = Math.min(310, height - 16);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int appAreaWidth = Math.min(210, Math.max(160, panelWidth / 2));
        int detailLeft = left + appAreaWidth + 8;

        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xF010141C);
        graphics.fill(left + 3, top + 3, left + panelWidth - 3, top + 30, 0xFF202936);
        graphics.fill(detailLeft - 4, top + 36, left + panelWidth - 6, top + panelHeight - 34, 0xC00C1118);
        graphics.drawString(font, "TRAINER PHONE", left + 10, top + 12, 0xFFFFFFFF, false);
        graphics.drawString(font, "Lv." + snapshot.levelCap(), left + panelWidth - 48, top + 12, 0xFFFFD75A, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        for (AppTile tile : tiles) {
            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(
                    CobblemonWorldMod.MOD_ID, "textures/gui/icons/" + tile.app().id() + ".png");
            graphics.blit(icon, tile.x() + 5, tile.y() + 9, 0, 0.0F, 0.0F, 16, 16, 16, 16);
            if ("messages".equals(tile.app().id())) {
                int unread = 0;
                for (var m : snapshot.messages()) if (m.unread()) unread++;
                if (unread > 0) graphics.drawString(font, Integer.toString(unread), tile.x() + 22, tile.y() + 4, 0xFFFF6666, true);
            }
        }

        renderSelected(graphics, detailLeft + 4, top + 45, Math.max(80, panelWidth - appAreaWidth - 22));
    }

    private void renderSelected(GuiGraphics g, int x, int y, int wrapWidth) {
        switch (selected) {
            case "trainer_card" -> {
                title(g, "Trainer Card", x, y);
                line(g, "Trainer: " + snapshot.trainerName(), x, y + 18);
                line(g, "Level Cap: " + snapshot.levelCap(), x, y + 31);
                line(g, "Badges: " + snapshot.badges().size(), x, y + 44);
                line(g, "League: " + snapshot.leagueTier(), x, y + 57);
                line(g, "Faction: " + snapshot.faction().name(), x, y + 70);
            }
            case "objective" -> {
                title(g, "Current Objective", x, y);
                wrap(g, safe(snapshot.story().objective(), "No tracked objective"), x, y + 18, wrapWidth, 10);
            }
            case "story" -> {
                title(g, snapshot.story().title(), x, y);
                line(g, "Chapter: " + snapshot.story().id(), x, y + 18);
                wrap(g, safe(snapshot.story().objective(), "Story complete."), x, y + 36, wrapWidth, 9);
            }
            case "side_quests" -> renderQuest(g, x, y, wrapWidth);
            case "level_cap" -> {
                title(g, "Level Cap • Lv." + snapshot.levelCap(), x, y);
                wrap(g, "Only Pokemon at or below your cap may spawn naturally, be captured, be sent out, or enter a Cobblemon battle. Existing over-cap Pokemon keep their real level and unlock when your cap catches up.",
                        x, y + 18, wrapWidth, 11);
            }
            case "badges" -> {
                title(g, "Badges • " + snapshot.badges().size(), x, y);
                wrap(g, humanJoin(snapshot.badges()), x, y + 18, wrapWidth, 11);
            }
            case "contacts" -> renderContact(g, x, y, wrapWidth);
            case "messages" -> renderMessage(g, x, y, wrapWidth);
            case "league" -> {
                title(g, "League", x, y);
                line(g, "Tier: " + snapshot.leagueTier(), x, y + 18);
                line(g, "League Points: " + snapshot.leaguePoints(), x, y + 31);
                wrap(g, "Main-story milestones award League points automatically. Eight major badges or 800 points reaches Champion tier.",
                        x, y + 51, wrapWidth, 8);
            }
            case "faction" -> renderFaction(g, x, y, wrapWidth);
            default -> {
                title(g, "Welcome, " + snapshot.trainerName(), x, y);
                line(g, snapshot.story().title(), x, y + 20);
                wrap(g, safe(snapshot.story().objective(), "Open an app."), x, y + 36, wrapWidth, 9);
            }
        }
    }

    private void renderQuest(GuiGraphics g, int x, int y, int wrapWidth) {
        CWorldNetworking.QuestView q = currentQuest();
        title(g, "Side Quests • " + snapshot.quests().size(), x, y);
        if (q == null) {
            line(g, "No side quests yet.", x, y + 18);
            return;
        }
        line(g, (q.completed() ? "COMPLETE • " : "") + q.title(), x, y + 18);
        line(g, "From: " + q.giver() + " • " + q.progress() + "/" + q.required(), x, y + 31);
        wrap(g, q.description(), x, y + 49, wrapWidth, 8);
    }

    private void renderContact(GuiGraphics g, int x, int y, int wrapWidth) {
        CWorldNetworking.ContactView c = currentContact();
        title(g, "Contacts • " + snapshot.contacts().size(), x, y);
        if (c == null) {
            line(g, "Only ??? is expected at the beginning.", x, y + 18);
            return;
        }
        line(g, c.displayName(), x, y + 18);
        line(g, c.unread() > 0 ? c.unread() + " unread message(s)" : "No unread messages", x, y + 34);
        wrap(g, "Contacts are unlocked by defeating major story trainers and completing their follow-up investigations.",
                x, y + 54, wrapWidth, 7);
    }

    private void renderMessage(GuiGraphics g, int x, int y, int wrapWidth) {
        CWorldNetworking.MessageView m = currentMessage();
        title(g, "Messages • " + snapshot.messages().size(), x, y);
        if (m == null) {
            line(g, "No messages yet.", x, y + 18);
            return;
        }
        line(g, (m.unread() ? "NEW • " : "") + m.sender(), x, y + 18);
        wrap(g, m.text(), x, y + 36, wrapWidth, 10);
    }

    private void renderFaction(GuiGraphics g, int x, int y, int wrapWidth) {
        var f = snapshot.faction();
        title(g, "Faction", x, y);
        if (!f.integrationAvailable()) {
            wrap(g, "Factions 2.8.0 is not installed. The rest of Cobblemon World remains functional.",
                    x, y + 18, wrapWidth, 6);
            return;
        }
        line(g, "Faction: " + f.name(), x, y + 18);
        line(g, "Island: " + f.phase() + " • " + f.affinity().toUpperCase(java.util.Locale.ROOT), x, y + 31);
        line(g, "Owner: " + (f.owner().isBlank() ? "None" : f.owner()), x, y + 44);
        line(g, "Gate: " + f.gateScore() + (f.qualified() ? " • QUALIFIED" : ""), x, y + 57);
        line(g, "Control: " + f.controlPoints() + "/5 • Built: " + (f.islandBuilt() ? "Yes" : "No"), x, y + 70);
    }

    private CWorldNetworking.MessageView currentMessage() {
        if (snapshot.messages() == null || snapshot.messages().isEmpty()) return null;
        messageIndex = Math.floorMod(messageIndex, snapshot.messages().size());
        return snapshot.messages().get(messageIndex);
    }

    private CWorldNetworking.ContactView currentContact() {
        if (snapshot.contacts() == null || snapshot.contacts().isEmpty()) return null;
        contactIndex = Math.floorMod(contactIndex, snapshot.contacts().size());
        return snapshot.contacts().get(contactIndex);
    }

    private CWorldNetworking.QuestView currentQuest() {
        if (snapshot.quests() == null || snapshot.quests().isEmpty()) return null;
        questIndex = Math.floorMod(questIndex, snapshot.quests().size());
        return snapshot.quests().get(questIndex);
    }

    private static int newestUnreadIndex(List<CWorldNetworking.MessageView> messages) {
        if (messages == null || messages.isEmpty()) return 0;
        for (int i = messages.size() - 1; i >= 0; i--) if (messages.get(i).unread()) return i;
        return messages.size() - 1;
    }

    private void title(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x, y, 0xFFFFD75A, false);
    }

    private void line(GuiGraphics g, String text, int x, int y) {
        g.drawString(font, text, x, y, 0xFFE8EEF7, false);
    }

    private void wrap(GuiGraphics g, String text, int x, int y, int width, int maxLines) {
        int line = 0;
        for (var seq : font.split(Component.literal(safe(text, "")), width)) {
            g.drawString(font, seq, x, y + line * 11, 0xFFE8EEF7, false);
            if (++line >= maxLines) break;
        }
    }

    private static String humanJoin(List<String> values) {
        if (values == null || values.isEmpty()) return "None";
        List<String> out = new ArrayList<>();
        for (String value : values) out.add(value.replace('_', ' '));
        return String.join(", ", out);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record App(String label, String id) {}
    private record AppTile(App app, int x, int y) {}
}
