package vn.svarcade.tcg.client.screens;

import vn.svarcade.tcg.client.CardWorldsScreen;
import vn.svarcade.tcg.client.component.Rect;
import vn.svarcade.tcg.client.component.Ui;
import vn.svarcade.tcg.economy.CardStore;

import java.util.List;

/**
 * Play hub with strict separation between PvE bot duels and human PvP.
 * Bot difficulty never leaks into the PvP challenge flow.
 */
public final class PlayScreen implements Page {
    @Override
    public void render(CardWorldsScreen a, Ui u, Rect b) {
        u.text("PLAY", b.x(), b.y(), 29, Ui.WHITE);
        u.text("Choose a duel mode", b.x(), b.y() + 34, 16, Ui.MUTED);

        boolean deckReady = a.legality().isEmpty();
        String deck = selectedDeck(a);

        Rect deckBar = new Rect(b.x(), b.y() + 62, b.w(), 58);
        u.panel(deckBar);
        u.text("ACTIVE DECK", deckBar.x() + 16, deckBar.y() + 10, 12, Ui.CYAN);
        u.fit(deck, new Rect(deckBar.x() + 16, deckBar.y() + 29, 280, 20), 16, Ui.WHITE);
        if (!deckReady) {
            List<String> errors = a.legality();
            u.fit(errors.isEmpty() ? "Deck is not legal." : errors.getFirst(),
                new Rect(deckBar.x() + 306, deckBar.y() + 20, 365, 24), 12, 0xFFE97172);
        } else {
            u.text("LEGAL", deckBar.x() + 306, deckBar.y() + 24, 13, Ui.GOLD);
        }
        u.button("Change Deck", new Rect(deckBar.right() - 248, deckBar.y() + 10, 112, 38), false, true, () -> cycleDeck(a));
        u.button("Deck Builder", new Rect(deckBar.right() - 126, deckBar.y() + 10, 110, 38), false, true, () -> a.navigate("Decks"));

        int gap = 18;
        int columnW = (b.w() - gap) / 2;
        int top = b.y() + 138;
        int height = b.h() - 148;
        Rect botPanel = new Rect(b.x(), top, columnW, height);
        Rect pvpPanel = new Rect(b.x() + columnW + gap, top, columnW, height);
        u.panel(botPanel);
        u.panel(pvpPanel);

        u.text("DUEL VS BOT", botPanel.x() + 18, botPanel.y() + 16, 22, Ui.WHITE);
        u.text("PvE • same Duel Engine • no hidden-info cheating", botPanel.x() + 18, botPanel.y() + 44, 12, Ui.MUTED);

        difficulty(u, a, botPanel, 0, "EASY",
            "Forgiving AI. Legal moves are mostly randomized.", "NO CURRENCY", deckReady);
        difficulty(u, a, botPanel, 1, "NORMAL",
            "Balanced AI. Prioritizes sensible summons, effects and attacks.", "NO CURRENCY", deckReady);
        difficulty(u, a, botPanel, 2, "HARD",
            "Competitive AI. Scores trades, removal, chains and lethal pressure.", "ECONOMY REWARD", deckReady);

        u.fill(new Rect(botPanel.x() + 18, botPanel.bottom() - 58, botPanel.w() - 36, 1), Ui.LINE);
        u.fit("Only a victory on HARD can pay server economy currency. Reward provider and amount are server-configured.",
            new Rect(botPanel.x() + 18, botPanel.bottom() - 48, botPanel.w() - 36, 36), 11, Ui.MUTED);

        u.text("PVP DUEL", pvpPanel.x() + 18, pvpPanel.y() + 16, 22, Ui.WHITE);
        u.text("Human vs Human only • bots never fill this mode", pvpPanel.x() + 18, pvpPanel.y() + 44, 12, Ui.MUTED);

        boolean ranked = "Ranked".equalsIgnoreCase(a.mode);
        u.chip("Casual", new Rect(pvpPanel.x() + 18, pvpPanel.y() + 70, 92, 28), !ranked, () -> {
            a.mode = "Casual";
            a.format = "CASUAL";
        });
        u.chip("Ranked", new Rect(pvpPanel.x() + 120, pvpPanel.y() + 70, 92, 28), ranked, () -> {
            a.mode = "Ranked";
            a.format = "RANKED";
        });

        int y = pvpPanel.y() + 116;
        u.text("ONLINE DUELISTS", pvpPanel.x() + 18, y, 13, Ui.CYAN);
        y += 26;
        int shown = 0;
        for (String player : a.state.players()) {
            if (y + 34 > pvpPanel.bottom() - 58) break;
            String target = player;
            u.button("Challenge  " + target, new Rect(pvpPanel.x() + 18, y, pvpPanel.w() - 36, 32),
                false, deckReady, () -> a.send("challenge", target, deck, Boolean.toString("Ranked".equalsIgnoreCase(a.mode))));
            y += 38;
            shown++;
        }
        if (shown == 0) {
            u.text("No other duelists online.", pvpPanel.x() + 18, y + 4, 13, Ui.MUTED);
        }

        if (!a.state.challenge().isBlank()) {
            u.button("Accept challenge from " + a.state.challenge(),
                new Rect(pvpPanel.x() + 18, pvpPanel.bottom() - 46, pvpPanel.w() - 36, 34),
                true, deckReady, () -> a.send("accept", deck));
        } else {
            u.fit(ranked ? "Ranked uses the server ranked format and result tracking." : "Casual does not affect ranked rating.",
                new Rect(pvpPanel.x() + 18, pvpPanel.bottom() - 43, pvpPanel.w() - 36, 28), 11, Ui.MUTED);
        }
    }

    private static void difficulty(Ui u, CardWorldsScreen a, Rect panel, int index,
                                   String name, String description, String reward, boolean enabled) {
        int y = panel.y() + 112 + index * 104;
        Rect row = new Rect(panel.x() + 18, y, panel.w() - 36, 88);
        u.fill(row, 0xB0091724);
        u.frame(row, index == 2 ? Ui.GOLD : Ui.LINE);
        u.text(name, row.x() + 14, row.y() + 11, 18, index == 2 ? Ui.GOLD : Ui.WHITE);
        u.fit(description, new Rect(row.x() + 14, row.y() + 37, row.w() - 154, 34), 11, Ui.MUTED);
        u.text(reward, row.right() - 126, row.y() + 12, 10, index == 2 ? Ui.GOLD : Ui.MUTED);
        u.button("DUEL", new Rect(row.right() - 118, row.y() + 40, 102, 32), index == 2, enabled,
            () -> a.send("pve", selectedDeck(a), name));
    }

    private static String selectedDeck(CardWorldsScreen a) {
        return a.deckName == null || a.deckName.isBlank() ? "Starter" : a.deckName;
    }

    private static void cycleDeck(CardWorldsScreen a) {
        List<CardStore.Deck> decks = a.state.savedDecks();
        if (decks.isEmpty()) {
            a.navigate("Decks");
            return;
        }
        int current = 0;
        for (int i = 0; i < decks.size(); i++) {
            if (decks.get(i).name().equals(a.deckName)) {
                current = i;
                break;
            }
        }
        a.loadDeck(decks.get((current + 1) % decks.size()).name());
    }
}
