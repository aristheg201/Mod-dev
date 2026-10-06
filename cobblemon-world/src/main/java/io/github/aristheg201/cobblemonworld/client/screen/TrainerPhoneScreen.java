package io.github.aristheg201.cobblemonworld.client.screen;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;
import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
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
    private String selected = "home";

    public TrainerPhoneScreen(String json) {
        super(Component.literal("Trainer Phone"));
        this.snapshot = GSON.fromJson(json, CWorldNetworking.PhoneSnapshot.class);
    }

    @Override
    protected void init() {
        clearWidgets();
        tiles.clear();

        int panelWidth = Math.min(360, width - 32);
        int left = (width - panelWidth) / 2;
        int buttonWidth = (panelWidth - 18) / 2;
        int y = 62;

        for (int i = 0; i < APPS.size(); i++) {
            App app = APPS.get(i);
            int col = i % 2;
            int row = i / 2;
            int x = left + 6 + col * (buttonWidth + 6);
            int by = y + row * 28;
            tiles.add(new AppTile(app, x, by));

            addRenderableWidget(Button.builder(Component.literal("     " + app.label()), b -> selected = app.id())
                    .bounds(x, by, buttonWidth, 22).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Home"), b -> selected = "home")
                .bounds(left + panelWidth - 58, 34, 52, 20).build());
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
        graphics.fill(left + 8, infoY, left + panelWidth - 8, top + panelHeight - 8, 0xC010151D);
        renderSelected(graphics, left + 16, infoY + 9);
    }

    private void renderSelected(GuiGraphics g, int x, int y) {
        switch (selected) {
            case "trainer_card" -> {
                g.drawString(font, "Trainer: " + snapshot.trainerName(), x, y, 0xFFFFFFFF, false);
                g.drawString(font, "League: " + snapshot.leagueTier() + " (" + snapshot.leaguePoints() + ")", x, y + 13, 0xFFBFD7FF, false);
            }
            case "objective" -> drawWrap(g, "Objective: " + safe(snapshot.currentObjective(), "No tracked objective"), x, y);
            case "story" -> drawWrap(g, "Current Story: " + safe(snapshot.currentStory(), "Prologue"), x, y);
            case "side_quests" -> drawWrap(g, "Active: " + snapshot.activeSideQuests().size() + " | Completed: " + snapshot.completedSideQuests().size(), x, y);
            case "level_cap" -> drawWrap(g, "Only Pokemon at Lv." + snapshot.levelCap() + " or below may spawn naturally, be captured, or be used.", x, y);
            case "badges" -> drawWrap(g, "Badges: " + join(snapshot.badges()), x, y);
            case "contacts" -> drawWrap(g, "Contacts: " + join(snapshot.contacts()), x, y);
            case "messages" -> drawWrap(g, "Unread: " + snapshot.unreadMessages().size(), x, y);
            case "league" -> drawWrap(g, "League: " + snapshot.leagueTier() + " • " + snapshot.leaguePoints() + " pts", x, y);
            case "faction" -> drawWrap(g, "Faction: " + snapshot.factionName(), x, y);
            default -> {
                g.drawString(font, "Welcome, " + snapshot.trainerName(), x, y, 0xFFFFFFFF, false);
                g.drawString(font, "Open an app above.", x, y + 13, 0xFFB8C2D0, false);
            }
        }
    }

    private void drawWrap(GuiGraphics g, String text, int x, int y) {
        int line = 0;
        for (var seq : font.split(Component.literal(text), Math.max(120, width / 3))) {
            g.drawString(font, seq, x, y + line * 11, 0xFFE8EEF7, false);
            line++;
            if (line >= 4) break;
        }
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
