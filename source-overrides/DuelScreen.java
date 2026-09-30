package vn.svarcade.tcg.client.screens;

import vn.svarcade.tcg.client.CardWorldsScreen;
import vn.svarcade.tcg.client.component.Rect;
import vn.svarcade.tcg.client.component.Ui;
import vn.svarcade.tcg.client.card.CardRenderer;
import vn.svarcade.tcg.client.render.DuelWorldScene;
import vn.svarcade.tcg.duel.Duel;
import vn.svarcade.tcg.data.Catalog;

import java.util.*;

/** HUD and interaction layer over the real 3D DuelWorldScene. */
public final class DuelScreen implements Page {
    public boolean dismissed;
    private String source = "", intent = "", pile = "HAND";
    private final LinkedHashSet<String> summonMaterials = new LinkedHashSet<>();
    private List<String> resolving = List.of();
    private long resolvedAt, attackAt;
    private String attacking = "";
    private int handPage;
    private final DuelWorldScene scene = new DuelWorldScene();

    public void observe(Duel.View old, Duel.View next) {
        if (next == null) {
            dismissed = false;
            source = "";
            intent = "";
            summonMaterials.clear();
            scene.close();
            return;
        }
        if (old == null) dismissed = false;
        if (old != null && !old.chain().isEmpty() && next.chain().isEmpty()) {
            resolving = new ArrayList<>(old.chain());
            Collections.reverse(resolving);
            resolvedAt = System.currentTimeMillis();
        }
    }

    public void closeScene() { scene.close(); }
    public boolean freeLookDrag(int button, double dx, double dy) {
        if (button != 1) return false;
        scene.orbit(dx, dy);
        return true;
    }
    public boolean freeLookScroll(double amount) {
        scene.zoom(amount);
        return true;
    }
    public void resetCamera() { scene.resetView(); }

    public void render(CardWorldsScreen a, Ui u, Rect b) {
        Duel.View v = a.state.duel();
        if (v == null) { scene.close(); return; }
        if (!v.winner().isBlank()) {
            // The authoritative duel is over: destroy the 3D arena immediately.
            scene.close();
        } else {
            scene.setAttack(attacking, attackAt);
            scene.sync(v, a.state.rules().pokemonZones());
        }

        u.fill(new Rect(0, 0, b.w(), 88), 0xA006111C);
        u.fill(new Rect(0, 88, 188, b.h() - 88), 0x9906111C);
        u.fill(new Rect(b.w() - 228, 88, 228, b.h() - 88), 0x9906111C);
        u.fill(new Rect(188, b.h() - 182, b.w() - 416, 182), 0xB006111C);

        profile(u, new Rect(20, 14, 320, 64), a.state.playerName(), v.life().get(v.you()), a.state.rules().life(), Ui.CYAN);
        profile(u, new Rect(b.w() - 340, 14, 320, 64), a.state.opponentName(), v.life().get(1 - v.you()), a.state.rules().life(), 0xFFE97172);
        Rect phase = new Rect(b.w() / 2 - 128, 12, 256, 68);
        u.panel(phase);
        u.text("Turn " + v.turn(), phase.x() + 98, phase.y() + 10, 15, Ui.MUTED);
        u.text(phase(v.phase()), phase.x() + 44, phase.y() + 33, 21, Ui.WHITE);

        int py = 116;
        for (String p : List.of("DRAW", "STANDBY", "MAIN1", "BATTLE", "MAIN2", "END")) {
            Rect r = new Rect(20, py, 148, 31);
            if (v.phase().equals(p)) { u.panel(r); u.frame(r, Ui.CYAN); }
            u.text(phase(p), r.x() + 10, py + 8, 13, v.phase().equals(p) ? Ui.WHITE : Ui.MUTED);
            py += 37;
        }
        pile(u, "DECK", v.deckCounts().get(v.you()), new Rect(20, b.h() - 150, 145, 48));
        pile(u, "DISCARD", count(v, Duel.Zone.DISCARD), new Rect(20, b.h() - 94, 145, 48));
        pile(u, "EXTRA", count(v, Duel.Zone.EXTRA), new Rect(b.w() - 210, 102, 190, 43));
        pile(u, "BANISHED", count(v, Duel.Zone.BANISHED), new Rect(b.w() - 210, 153, 190, 43));

        for (Duel.VisibleCard card : v.cards().stream().filter(c -> c.zone() == Duel.Zone.FIELD).toList()) {
            Rect hit = scene.hitBox(card.token(), 1280, b.h());
            if (hit == null) continue;
            boolean target = targetAllowed(a, card), selected = card.token().equals(source), material = summonMaterials.contains(card.token());
            int edge = material ? Ui.GOLD : target ? Ui.GOLD : selected ? Ui.CYAN : card.controller() == v.you() ? 0xAA49C9F5 : 0xAAE97172;
            int labelW = Math.max(104, Math.min(176, hit.w() + 48));
            Rect label = new Rect(hit.x() + hit.w() / 2 - labelW / 2, hit.bottom() - 4, labelW, 24);
            u.fill(label, 0xB0091724);
            u.frame(label, edge);
            var fieldDef = definition(a, card);
            String fieldLabel = (material ? "TRIBUTE • " : "") + card.name() + (fieldDef == null ? "" : "  ★" + fieldDef.level()) + "  " + card.power();
            u.fit(fieldLabel, label.inset(5), 12, selected || target || material ? Ui.WHITE : Ui.MUTED);
            u.click(hit, () -> choose(a, card));
        }

        int bx = 225;
        for (String z : List.of("HAND", "SUPPORT", "STADIUM")) {
            String selectedPile = z;
            u.chip(z.substring(0, 1) + z.substring(1).toLowerCase(), new Rect(bx, b.h() - 39, 91, 27), pile.equals(z), () -> { pile = selectedPile; handPage = 0; });
            bx += 101;
        }

        var hand = v.cards().stream().filter(c -> c.controller() == v.you() && c.zone().name().equals(pile)).toList();
        int cap = 8;
        int off = Math.min(handPage, Math.max(0, (hand.size() - 1) / cap)) * cap;
        int total = Math.min(cap, hand.size() - off);
        int hw = 92;
        int hx = 205 + Math.max(0, (790 - total * 98) / 2);
        for (int i = 0; i < total; i++) {
            var card = hand.get(i + off);
            var def = definition(a, card);
            if (def == null) continue;
            int lift = card.token().equals(source) ? 18 : 0;
            Rect r = new Rect(hx + i * 98, b.h() - 166 - lift, hw, 130);
            CardRenderer.draw(u, def, r, 0, card.token().equals(source), true, "hand-" + card.token());
            if (targetAllowed(a, card)) u.frame(r.inset(-2), Ui.GOLD);
            u.click(r, () -> choose(a, card));
        }
        u.button("<", new Rect(1008, b.h() - 93, 30, 28), false, off > 0, () -> handPage--);
        u.button(">", new Rect(1045, b.h() - 93, 30, 28), false, off + cap < hand.size(), () -> handPage++);

        int x = b.w() - 210, y = 228;
        var src = source(v);
        var def = src == null ? null : definition(a, src);
        String actionPrompt = intent.isBlank() ? (v.priority() == v.you() ? "YOUR RESPONSE" : "OPPONENT'S RESPONSE") :
            intent.equals("summon") ? summonPrompt(a, v, def) : "CHOOSE A 3D TARGET";
        u.text(actionPrompt, x, y - 24, 13, Ui.GOLD);
        boolean priority = v.priority() == v.you() && v.winner().isBlank();
        u.button(def == null ? "Summon" : "Summon  ★" + def.level(), new Rect(x, y, 190, 36), true,
            priority && v.open() && v.turnPlayer() == v.you() && src != null && src.category().equals("pokemon") &&
                (src.zone() == Duel.Zone.HAND || src.zone() == Duel.Zone.EXTRA) && (v.phase().equals("MAIN1") || v.phase().equals("MAIN2")),
            () -> perform(a, "play"));
        u.button("Activate", new Rect(x, y + 45, 190, 36), false,
            priority && def != null && def.effect() != null && def.effect().phases().contains(v.phase()) &&
                (def.effect().speed() > 1 || v.open() && v.turnPlayer() == v.you()) && !(src.category().equals("pokemon") && src.zone() == Duel.Zone.HAND),
            () -> perform(a, "activate"));
        u.button("Attack", new Rect(x, y + 90, 190, 36), false,
            priority && v.open() && v.turnPlayer() == v.you() && v.phase().equals("BATTLE") && v.turn() > 1 && src != null && src.zone() == Duel.Zone.FIELD,
            () -> perform(a, "attack"));
        u.button(v.open() ? "Next Phase" : "Pass", new Rect(x, y + 135, 190, 36), false, priority,
            () -> a.send("duel", v.open() ? "next" : "pass", "", ""));
        u.button("Surrender", new Rect(x, y + 182, 190, 30), false, v.winner().isBlank(), () -> a.send("duel", "concede", "", ""));
        u.button("Reset View", new Rect(x, y + 220, 190, 30), false, v.winner().isBlank(), scene::resetView);
        u.fit("RMB drag: free look  |  Wheel: zoom", new Rect(386, 88, 508, 22), 12, Ui.MUTED);

        List<String> chain = v.chain();
        boolean anim = System.currentTimeMillis() - resolvedAt < resolving.size() * 550L;
        if (anim) chain = resolving;
        int cy = 101;
        for (int i = 0; i < chain.size(); i++) {
            Rect cr = new Rect(452, cy, 376, 30);
            u.panel(cr);
            u.fit(chain.get(i).replace("Chain", "CHAIN"), cr.inset(7), 14,
                anim && i == (System.currentTimeMillis() - resolvedAt) / 550 ? Ui.GOLD : Ui.WHITE);
            cy += 35;
        }

        if (!v.winner().isBlank()) {
            Rect r = new Rect(430, 224, 420, 150);
            u.panel(r);
            u.text(v.winner().equals(Integer.toString(v.you())) ? "VICTORY" : "DUEL COMPLETE", r.x() + 80, r.y() + 25, 33, Ui.GOLD);
            u.button("Continue", new Rect(r.x() + 100, r.y() + 88, 220, 37), true, true, () -> {
                scene.close();
                dismissed = true;
                a.navigate("Play");
                a.send("refresh");
            });
        }
    }

    private int count(Duel.View v, Duel.Zone zone) { return (int)v.cards().stream().filter(c -> c.controller() == v.you() && c.zone() == zone).count(); }

    private void choose(CardWorldsScreen a, Duel.VisibleCard c) {
        if (intent.equals("summon") && targetAllowed(a, c)) {
            var v = a.state.duel();
            var src = source(v);
            var d = src == null ? null : definition(a, src);
            var materialDef = definition(a, c);
            if (d == null || materialDef == null) { clearSummon(); return; }

            boolean evolution = d.evolvesFrom() != null && !d.evolvesFrom().isBlank() && materialDef.id().equals(d.evolvesFrom());
            if (evolution) {
                a.send("duel", "play", source, c.token());
                clearSummon();
                return;
            }

            if (d.extra()) return;
            if (!summonMaterials.add(c.token())) summonMaterials.remove(c.token());
            int required = d.tributeCount();
            if (required > 0 && summonMaterials.size() == required) {
                a.send("duel", "play", source, String.join(",", summonMaterials));
                clearSummon();
            }
            return;
        }

        if (!intent.isBlank() && targetAllowed(a, c)) {
            if (intent.equals("attack")) {
                attacking = source;
                attackAt = System.currentTimeMillis();
                scene.setAttack(attacking, attackAt);
            }
            a.send("duel", intent, source, c.token());
            intent = "";
            summonMaterials.clear();
            return;
        }
        if (c.controller() == a.state.duel().you()) {
            source = c.token();
            intent = "";
            summonMaterials.clear();
        }
    }

    private void perform(CardWorldsScreen a, String action) {
        var v = a.state.duel();
        var src = source(v);
        if (src == null) return;
        var d = definition(a, src);
        if (d == null) return;

        if (action.equals("play")) {
            summonMaterials.clear();
            boolean canEvolve = d.evolvesFrom() != null && !d.evolvesFrom().isBlank() &&
                v.cards().stream().filter(c -> c.controller() == v.you() && c.zone() == Duel.Zone.FIELD)
                    .map(c -> definition(a, c)).filter(Objects::nonNull).anyMatch(x -> x.id().equals(d.evolvesFrom()));
            if (d.extra() || d.tributeCount() > 0 || canEvolve) {
                intent = "summon";
                return;
            }
            a.send("duel", "play", source, "");
            intent = "";
            return;
        }

        intent = action;
        if (action.equals("activate") && d.effect() != null && d.effect().target().equals("none") ||
            action.equals("attack") && v.cards().stream().noneMatch(c -> c.controller() != v.you() && c.zone() == Duel.Zone.FIELD)) {
            if (action.equals("attack")) { attacking = source; attackAt = System.currentTimeMillis(); scene.setAttack(attacking, attackAt); }
            a.send("duel", action, source, "");
            intent = "";
        } else if (action.equals("activate") && d.effect() != null && d.effect().target().equals("chain")) {
            a.send("duel", action, source, Integer.toString(v.chain().size()));
            intent = "";
        } else if (action.equals("activate") && d.effect() != null && d.effect().target().equals("grave")) {
            pile = "DISCARD";
            handPage = 0;
        }
    }

    private boolean targetAllowed(CardWorldsScreen a, Duel.VisibleCard t) {
        var v = a.state.duel();
        var src = source(v);
        if (src == null || intent.isBlank()) return false;
        var d = definition(a, src);
        if (d == null) return false;
        if (intent.equals("attack")) return t.zone() == Duel.Zone.FIELD && t.controller() != v.you();
        if (intent.equals("summon")) {
            if (t.controller() != v.you() || t.zone() != Duel.Zone.FIELD) return false;
            var other = definition(a, t);
            if (other == null) return false;
            boolean evolution = d.evolvesFrom() != null && !d.evolvesFrom().isBlank() && other.id().equals(d.evolvesFrom());
            if (d.extra()) return evolution;
            return evolution || d.tributeCount() > 0;
        }
        if (d.effect() == null) return false;
        return switch (d.effect().target()) {
            case "ally" -> t.controller() == v.you() && t.zone() == Duel.Zone.FIELD;
            case "enemy" -> t.controller() != v.you() && t.zone() == Duel.Zone.FIELD;
            case "grave" -> t.controller() == v.you() && t.zone() == Duel.Zone.DISCARD && t.category().equals("pokemon");
            default -> false;
        };
    }

    private String summonPrompt(CardWorldsScreen a, Duel.View v, Catalog.Card d) {
        if (d == null) return "CHOOSE SUMMON MATERIAL";
        if (d.extra()) return "CHOOSE EVOLUTION MATERIAL";
        int required = d.tributeCount();
        int left = Math.max(0, required - summonMaterials.size());
        boolean canEvolve = d.evolvesFrom() != null && !d.evolvesFrom().isBlank() &&
            v.cards().stream().filter(c -> c.controller() == v.you() && c.zone() == Duel.Zone.FIELD)
                .map(c -> definition(a, c)).filter(Objects::nonNull).anyMatch(x -> x.id().equals(d.evolvesFrom()));
        if (canEvolve && required > 0) return "EVOLVE OR CHOOSE " + left + (left == 1 ? " TRIBUTE" : " TRIBUTES");
        if (canEvolve) return "CHOOSE EVOLUTION MATERIAL";
        return "CHOOSE " + left + (left == 1 ? " TRIBUTE" : " TRIBUTES");
    }

    private void clearSummon() {
        intent = "";
        summonMaterials.clear();
    }

    private Duel.VisibleCard source(Duel.View v) { return v.cards().stream().filter(c -> c.token().equals(source)).findFirst().orElse(null); }
    private Catalog.Card definition(CardWorldsScreen a, Duel.VisibleCard c) { return a.state.definitions().values().stream().filter(d -> d.name().equals(c.name())).findFirst().orElse(null); }

    private void profile(Ui u, Rect r, String name, int life, int max, int color) {
        u.panel(r); u.text(name, r.x() + 15, r.y() + 12, 21, Ui.WHITE); u.text("LP " + life, r.right() - 95, r.y() + 14, 17, Ui.WHITE);
        u.meter(new Rect(r.x() + 15, r.y() + 44, r.w() - 30, 8), life / (double)max, color);
    }

    private void pile(Ui u, String name, int count, Rect r) {
        u.panel(r);
        u.text(name.equals("DISCARD") ? "Graveyard" : name.equals("BANISHED") ? "Banished" : name.equals("EXTRA") ? "Extra Deck" : "Deck", r.x() + 10, r.y() + 8, 13, Ui.WHITE);
        u.text(Integer.toString(count), r.x() + 10, r.y() + 26, 17, Ui.GOLD);
        if (!name.equals("DECK")) u.click(r, () -> { pile = name; handPage = 0; });
    }

    private static String phase(String p) {
        return switch (p) {
            case "DRAW" -> "Draw Phase";
            case "STANDBY" -> "Standby Phase";
            case "MAIN1" -> "Main Phase 1";
            case "MAIN2" -> "Main Phase 2";
            case "BATTLE" -> "Battle Phase";
            default -> "End Phase";
        };
    }
}
