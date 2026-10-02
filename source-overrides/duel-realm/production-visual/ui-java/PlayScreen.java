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
        u.text("cardworlds.ui.play_42c5c", b.x(), b.y(), 29, Ui.WHITE);
        u.text("cardworlds.ui.choose_a_duel_mode", b.x(), b.y() + 34, 16, Ui.MUTED);

        boolean deckReady = a.legality().isEmpty();
        String deck = selectedDeck(a);

        Rect deckBar = new Rect(b.x(), b.y() + 62, b.w(), 58);
        u.panel(deckBar);
        u.text("cardworlds.ui.active_deck", deckBar.x() + 16, deckBar.y() + 10, 12, Ui.CYAN);
        u.fit(deck, new Rect(deckBar.x() + 16, deckBar.y() + 29, 280, 20), 16, Ui.WHITE);
        if (!deckReady) {
            List<String> errors = a.legality();
            u.fit(errors.isEmpty() ? "Deck is not legal." : errors.getFirst(),
                new Rect(deckBar.x() + 306, deckBar.y() + 20, 365, 24), 12, 0xFFE97172);
        } else {
            u.text("cardworlds.ui.legal", deckBar.x() + 306, deckBar.y() + 24, 13, Ui.GOLD);
        }
        u.button("cardworlds.ui.change_deck", new Rect(deckBar.right() - 248, deckBar.y() + 10, 112, 38), false, true, () -> cycleDeck(a));
        u.button("cardworlds.ui.deck_builder", new Rect(deckBar.right() - 126, deckBar.y() + 10, 110, 38), false, true, () -> a.navigate("Decks"));

        int gap = 18;
        int columnW = (b.w() - gap) / 2;
        int top = b.y() + 138;
        int height = b.h() - 148;
        Rect botPanel = new Rect(b.x(), top, columnW, height);
        Rect pvpPanel = new Rect(b.x() + columnW + gap, top, columnW, height);
        u.panel(botPanel);
        u.panel(pvpPanel);

        u.text("cardworlds.ui.duel_vs_bot", botPanel.x() + 18, botPanel.y() + 16, 22, Ui.WHITE);
        u.text("cardworlds.ui.pve_same_duel_engine_no_hidden_info_cheating", botPanel.x() + 18, botPanel.y() + 44, 12, Ui.MUTED);

        Rect botRows=new Rect(botPanel.x()+18,botPanel.y()+104,botPanel.w()-36,Math.max(60,botPanel.h()-174));
        var botScroll=a.beginScroll(u,"play/bots",botRows,312);Rect botContent=new Rect(botPanel.x(),botPanel.y()-botScroll.offset(),botPanel.w()-14,botPanel.h());
        difficulty(u, a, botContent, 0, "EASY",
            "Forgiving AI. Legal moves are mostly randomized.", "PRACTICE - NO REWARD", deckReady);
        difficulty(u, a, botContent, 1, "NORMAL",
            "Balanced AI. Prioritizes sensible summons, effects and attacks.", "WIN +15 BEAST COIN - LOSS 0", deckReady);
        difficulty(u, a, botContent, 2, "HARD",
            "Competitive AI. Scores trades, removal, chains and lethal pressure.", "WIN +20 BEAST COIN - LOSS +5", deckReady);

        a.endScroll(u,botScroll);
        u.fill(new Rect(botPanel.x() + 18, botPanel.bottom() - 58, botPanel.w() - 36, 1), Ui.LINE);
        u.text("Beast Coin only. Hunter Coin is NEVER a battle reward.",
            botPanel.x() + 18, botPanel.bottom() - 42, 12, Ui.MUTED);

        u.text("cardworlds.ui.pvp_duel", pvpPanel.x() + 18, pvpPanel.y() + 16, 22, Ui.WHITE);
        u.text("cardworlds.ui.human_vs_human_only_bots_never_fill_this_mode", pvpPanel.x() + 18, pvpPanel.y() + 44, 12, Ui.MUTED);

        boolean ranked = "Ranked".equalsIgnoreCase(a.mode);
        u.chip("cardworlds.ui.casual", new Rect(pvpPanel.x() + 18, pvpPanel.y() + 70, 92, 28), !ranked, () -> {
            a.mode = "Casual";
            a.format = "CASUAL";
        });
        u.chip("cardworlds.ui.ranked", new Rect(pvpPanel.x() + 120, pvpPanel.y() + 70, 92, 28), ranked, () -> {
            a.mode = "Ranked";
            a.format = "RANKED";
        });

        u.text("Casual PvP - WIN +30 BEAST COIN - LOSS +15",
            pvpPanel.x() + 18, pvpPanel.y() + 114, 14, Ui.CYAN);
        u.text("Ranked - WIN +50 BEAST COIN - LOSS +25",
            pvpPanel.x() + 18, pvpPanel.y() + 140, 14, Ui.GOLD);
        int y = pvpPanel.y() + 182;
        u.text("cardworlds.ui.online_duelists", pvpPanel.x() + 18, y, 13, Ui.CYAN);
        y += 26;
        Rect players=new Rect(pvpPanel.x()+18,y,pvpPanel.w()-36,Math.max(50,pvpPanel.bottom()-58-y));
        var playerScroll=a.beginScroll(u,"play/players",players,a.state.players().size()*38);y-=playerScroll.offset();
        int shown = 0;
        for (String player : a.state.players()) {
            if(y+34<=players.y()||y>=players.bottom()){y+=38;continue;}
            String target = player;
            u.button("cardworlds.ui.challenge" + target, new Rect(pvpPanel.x() + 18, y, pvpPanel.w() - 50, 32),
                false, deckReady, () -> a.send("challenge", target, deck, Boolean.toString("Ranked".equalsIgnoreCase(a.mode))));
            y += 38;
            shown++;
        }
        a.endScroll(u,playerScroll);
        if (a.state.players().isEmpty()) {
            u.text("cardworlds.ui.no_other_duelists_online", pvpPanel.x() + 18, y + 4, 13, Ui.MUTED);
        }

        if (!a.state.challenge().isBlank()) {
            u.button("cardworlds.ui.accept_challenge_from" + a.state.challenge(),
                new Rect(pvpPanel.x() + 18, pvpPanel.bottom() - 46, pvpPanel.w() - 36, 34),
                true, deckReady, () -> a.send("accept", deck));
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
        u.fit(reward, new Rect(row.x() + 14, row.y() + 65, row.w() - 140, 16), 14, index == 2 ? Ui.GOLD : Ui.MUTED);
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
