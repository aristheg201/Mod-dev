package io.github.aristheg201.cobblemonworld.client.screen;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.network.PhoneActionPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private final List<Button> responseButtons = new ArrayList<>();
    private String selected = "home";
    private CWorldNetworking.MessageView activeMessage;

    public TrainerPhoneScreen(String json) {
        super(Component.literal("Trainer Phone"));
        this.snapshot = GSON.fromJson(json, CWorldNetworking.PhoneSnapshot.class);
        this.activeMessage = chooseActiveMessage(snapshot.messages());
    }

    @Override
    protected void init() {
        clearWidgets();
        tiles.clear();
        responseButtons.clear();

        int panelWidth = Math.min(360, width - 32);
        int panelHeight = Math.min(250, height - 24);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;
        int buttonWidth = (panelWidth - 18) / 2;
        int y = top + 38;

        for (int i = 0; i < APPS.size(); i++) {
            App app = APPS.get(i);
            int col = i % 2;
            int row = i / 2;
            int x = left + 6 + col * (buttonWidth + 6);
            int by = y + row * 28;
            tiles.add(new AppTile(app, x, by));

            addRenderableWidget(Button.builder(Component.literal("     " + app.label()), b -> selectApp(app.id()))
                    .bounds(x, by, buttonWidth, 22).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Home"), b -> selectApp("home"))
                .bounds(left + panelWidth - 58, top + 5, 52, 20).build());

        if (activeMessage != null && activeMessage.responses() != null) {
            int responseY = top + panelHeight - 30;
            int count = Math.min(2, activeMessage.responses().size());
            int gap = 6;
            int widthEach = count <= 1 ? panelWidth - 32 : (panelWidth - 38) / 2;
            for (int i = 0; i < count; i++) {
                final int responseIndex = i;
                int x = left + 16 + i * (widthEach + gap);
                Button button = Button.builder(Component.literal(activeMessage.responses().get(i)), b -> {
                            ClientPlayNetworking.send(new PhoneActionPayload(
                                    "respond", activeMessage.key(), Integer.toString(responseIndex)));
                        })
                        .bounds(x, responseY, widthEach, 20)
                        .build();
                button.visible = false;
                responseButtons.add(button);
                addRenderableWidget(button);
            }
        }
    }

    private void selectApp(String id) {
        selected = id;
        boolean showResponses = "messages".equals(id) && activeMessage != null && activeMessage.unread();
        for (Button button : responseButtons) button.visible = showResponses;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int panelWidth = Math.min(380, width - 24);
        int panelHeight = Math.min(250, height - 24);
        int left = (width - panelWidth) / 2;
        int top = (height - panelHeight) / 2;

        graphics.fill(left, top, left + panelWidth, top + panelHeight, 0xE610141C);
        graphics.fill(left + 3, top + 3, left + panelWidth - 3, top + 27, 0xFF202936);
        graphics.drawString(font, "TRAINER PHONE", left + 10, top + 11, 0xFFFFFFFF, false);
        graphics.drawString(font, "Lv. Cap " + snapshot.levelCap(), left + panelWidth - 78, top + 11, 0xFFFFD75A, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        for (AppTile tile : tiles) {
            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(
                    CobblemonWorldMod.MOD_ID,
                    "textures/gui/icons/" + tile.app().id() + ".png"
            );
            graphics.blit(icon, tile.x() + 5, tile.y() + 3, 0, 0.0F, 0.0F, 16, 16, 16, 16);
        }

        int infoY = top + 176;
        int infoBottom = top + panelHeight - (responseButtons.isEmpty() || !"messages".equals(selected) ? 8 : 34);
        graphics.fill(left + 8, infoY, left + panelWidth - 8, infoBottom, 0xC010151D);
        renderSelected(graphics, left + 16, infoY + 9);
    }

    private void renderSelected(GuiGraphics g, int x, int y) {
        switch (selected) {
            case "trainer_card" -> {
                g.drawString(font, "Trainer: " + snapshot.trainerName(), x, y, 0xFFFFFFFF, false);
                g.drawString(font, "League: " + snapshot.leagueTier() + " (" + snapshot.leaguePoints() + ")", x, y + 13, 0xFFBFD7FF, false);
            }
            case "objective" -> drawWrap(g, "Objective: " + safe(snapshot.currentObjective(), "No tracked objective"), x, y, 4);
            case "story" -> drawWrap(g, "Current Story: " + safe(snapshot.currentStory(), "Prologue"), x, y, 4);
            case "side_quests" -> drawWrap(g, "Active: " + join(snapshot.activeSideQuests()) + " | Completed: " + snapshot.completedSideQuests().size(), x, y, 4);
            case "level_cap" -> drawWrap(g, "Only Pokemon at Lv." + snapshot.levelCap() + " or below may spawn naturally, be captured, or be used.", x, y, 4);
            case "badges" -> drawWrap(g, "Badges: " + join(snapshot.badges()), x, y, 4);
            case "contacts" -> drawWrap(g, "Contacts: " + join(snapshot.contacts()), x, y, 4);
            case "messages" -> renderMessage(g, x, y);
            case "league" -> drawWrap(g, "League: " + snapshot.leagueTier() + " • " + snapshot.leaguePoints() + " pts", x, y, 4);
            case "faction" -> drawWrap(g, "Faction: " + snapshot.factionName(), x, y, 4);
            default -> {
                g.drawString(font, "Welcome, " + snapshot.trainerName(), x, y, 0xFFFFFFFF, false);
                g.drawString(font, "Open an app above.", x, y + 13, 0xFFB8C2D0, false);
            }
        }
    }

    private void renderMessage(GuiGraphics g, int x, int y) {
        if (activeMessage == null) {
            g.drawString(font, "No messages yet.", x, y, 0xFFB8C2D0, false);
            return;
        }
        String prefix = activeMessage.unread() ? "NEW • " : "";
        g.drawString(font, prefix + activeMessage.sender(), x, y, 0xFFFFD75A, false);
        drawWrap(g, activeMessage.text(), x, y + 13, responseButtons.isEmpty() ? 3 : 2);
    }

    private void drawWrap(GuiGraphics g, String text, int x, int y, int maxLines) {
        int line = 0;
        for (var seq : font.split(Component.literal(text), Math.max(120, width / 3))) {
            g.drawString(font, seq, x, y + line * 11, 0xFFE8EEF7, false);
            line++;
            if (line >= maxLines) break;
        }
    }

    private static CWorldNetworking.MessageView chooseActiveMessage(List<CWorldNetworking.MessageView> messages) {
        if (messages == null || messages.isEmpty()) return null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).unread()) return messages.get(i);
        }
        return messages.get(messages.size() - 1);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String join(List<String> values) {
        return values == null || values.isEmpty() ? "None" : String.join(", ", values);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record App(String label, String id) {}
    private record AppTile(App app, int x, int y) {}
}
