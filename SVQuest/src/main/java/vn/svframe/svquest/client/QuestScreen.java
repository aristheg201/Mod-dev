package vn.svframe.svquest.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.util.ArrayList;
import java.util.List;

/** Native SVFrame quest + feature hub. The quest rail browses the full beta.5 catalog. */
public final class QuestScreen extends Screen {
    private static final Identifier LOGO = Identifier.of(SVQuest.MOD_ID, "textures/gui/server_logo.png");
    private static final int LOGO_W = 128, LOGO_H = 89;
    private static final int BG = 0xF20A0E16, HEADER = 0xFA111827, PANEL = 0xF217202D;
    private static final int CARD = 0xFA202B3B, CARD_HOVER = 0xFA29384C, BORDER = 0xFF35445B;
    private static final int MAGENTA = 0xFFFF4FD1, CYAN = 0xFF56DDF2, GREEN = 0xFF75E690;
    private static final int GOLD = 0xFFFFCF5A, RED = 0xFFFF6B7A, TEXT = 0xFFF5F7FB;
    private static final int MUTED = 0xFF9EAABD, DIM = 0xFF667085;

    private enum Tab { PROGRESS, ACTIVITIES, POKEMON, SHOPS, SERVICES }
    private Tab tab = Tab.PROGRESS;
    private int selectedQuest = -1;
    private final List<Hit> hits = new ArrayList<>();

    private int railScroll, railMaxScroll;
    private int railTrackX, railTrackY, railTrackH, railThumbY, railThumbH;
    private int railBoundsX, railBoundsY, railBoundsW, railBoundsH;
    private boolean railDragging;
    private double railGrabOffset;

    public QuestScreen() { super(Text.literal("SVQuest")); }
    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        super.render(ctx, mouseX, mouseY, delta);
        ctx.fill(0, 0, width, height, 0xB9000000);

        int w = Math.min(1240, Math.max(720, width - 24));
        int h = Math.min(720, Math.max(440, height - 24));
        int x = (width - w) / 2, y = (height - h) / 2;
        panel(ctx, x, y, x + w, y + h, BG, 0xFF2E3B50);
        header(ctx, x, y, w, mouseX, mouseY);
        int contentY = y + 103;
        if (tab == Tab.PROGRESS) progress(ctx, x + 14, contentY, w - 28, h - 117, mouseX, mouseY);
        else featureGrid(ctx, x + 14, contentY, w - 28, h - 117, mouseX, mouseY);
    }

    private void header(DrawContext ctx, int x, int y, int w, int mx, int my) {
        ctx.fill(x + 1, y + 1, x + w - 1, y + 76, HEADER);
        int logoH = 58, logoW = Math.round(logoH * (LOGO_W / (float) LOGO_H));
        try { ctx.drawTexture(LOGO, x + 15, y + 8, 0, 0, logoW, logoH, LOGO_W, LOGO_H); }
        catch (Throwable ignored) { ctx.drawText(textRenderer, "SVFRAME", x + 18, y + 30, MAGENTA, true); }
        int tx = x + 26 + logoW;
        ctx.drawText(textRenderer, "SVFRAME", tx, y + 20, TEXT, true);
        ctx.drawText(textRenderer, "SVQuest", tx, y + 40, CYAN, false);

        int closeX = x + w - 29;
        boolean closeHover = inside(mx, my, closeX, y + 10, 18, 18);
        panel(ctx, closeX, y + 10, closeX + 18, y + 28, closeHover ? 0xFF6B2733 : 0xFF263144, closeHover ? RED : BORDER);
        drawCentered(ctx, "×", closeX, y + 15, 18, closeHover ? 0xFFFFFFFF : MUTED);
        hits.add(new Hit(closeX, y + 10, 18, 18, "close"));

        String[] names = {"TIẾN TRÌNH", "HOẠT ĐỘNG", "POKÉMON", "CỬA HÀNG", "DỊCH VỤ"};
        Tab[] tabs = Tab.values();
        int navX = x + 14, navY = y + 78, gap = 5, bw = (w - 28 - gap * 4) / 5;
        for (int i = 0; i < names.length; i++) {
            boolean active = tab == tabs[i], hover = inside(mx, my, navX, navY, bw, 22);
            panel(ctx, navX, navY, navX + bw, navY + 22,
                    active ? 0xFF762766 : hover ? 0xFF2A3649 : 0xFF1A2331, active ? MAGENTA : BORDER);
            drawCentered(ctx, names[i], navX, navY + 7, bw, active ? 0xFFFFFFFF : 0xFFC9D1DE);
            hits.add(new Hit(navX, navY, bw, 22, "tab:" + tabs[i].name()));
            navX += bw + gap;
        }
    }

    private void progress(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        if (selectedQuest < 0 || selectedQuest >= QuestCatalog.QUESTS.size()) {
            selectedQuest = firstActiveIndex();
            railScroll = Math.max(0, selectedQuest - 2);
        }
        int railW = Math.max(230, Math.min(330, w / 4)), gap = 10;
        journeyRail(ctx, x, y, railW, h, mx, my);
        questDetail(ctx, x + railW + gap, y, w - railW - gap, h, mx, my);
    }

    private int firstActiveIndex() {
        for (int i = 0; i < QuestCatalog.QUESTS.size(); i++) {
            QuestCatalog.Quest q = QuestCatalog.QUESTS.get(i);
            if (!SVQuestClient.STATE.claimed(q.id()) && unlocked(q)) return i;
        }
        return Math.max(0, QuestCatalog.QUESTS.size() - 1);
    }

    private boolean unlocked(QuestCatalog.Quest q) {
        return QuestCatalog.unlocked(SVQuestClient.STATE.claimedView(), q);
    }

    private boolean complete(QuestCatalog.Quest q) {
        if (!unlocked(q)) return false;
        if (q.objectives().isEmpty()) return false;
        for (int i = 0; i < q.objectives().size(); i++) {
            if (SVQuestClient.STATE.progress(QuestCatalog.progressKey(q, i)) < q.objectives().get(i).amount()) return false;
        }
        return true;
    }

    private void journeyRail(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        panel(ctx, x, y, x + w, y + h, PANEL, BORDER);
        railBoundsX = x; railBoundsY = y; railBoundsW = w; railBoundsH = h;
        ctx.drawText(textRenderer, "NHIỆM VỤ SVFRAME", x + 12, y + 11, TEXT, true);
        String count = SVQuestClient.STATE.claimedCount() + "/" + QuestCatalog.QUESTS.size();
        ctx.drawText(textRenderer, count, x + w - 16 - textRenderer.getWidth(count), y + 11, CYAN, false);

        int listTop = y + 34, listBottom = y + h - 8, rowH = 44;
        int visibleRows = Math.max(1, (listBottom - listTop) / rowH);
        railMaxScroll = Math.max(0, QuestCatalog.QUESTS.size() - visibleRows);
        railScroll = clamp(railScroll, 0, railMaxScroll);
        int contentRight = x + w - (railMaxScroll > 0 ? 14 : 7);

        for (int slot = 0; slot < visibleRows; slot++) {
            int i = railScroll + slot;
            if (i >= QuestCatalog.QUESTS.size()) break;
            QuestCatalog.Quest q = QuestCatalog.QUESTS.get(i);
            int ry = listTop + slot * rowH;
            boolean selected = i == selectedQuest;
            boolean done = SVQuestClient.STATE.claimed(q.id());
            boolean open = unlocked(q);
            boolean ready = !done && complete(q);
            boolean hover = inside(mx, my, x + 7, ry, contentRight - (x + 7), rowH - 4);
            panel(ctx, x + 7, ry, contentRight, ry + rowH - 4,
                    selected ? 0xFF2C3A50 : hover ? 0xFF222D3E : 0xFF171F2B,
                    selected ? CYAN : 0xFF263247);
            int stateColor = done ? GREEN : ready ? CYAN : open ? GOLD : DIM;
            ctx.fill(x + 12, ry + 8, x + 15, ry + rowH - 12, stateColor);
            ctx.drawText(textRenderer, done ? "✓" : open ? "▶" : "◆", x + 20, ry + 7, stateColor, true);
            ctx.drawText(textRenderer, trim(q.title(), Math.max(16, (w - 60) / 6)), x + 34, ry + 7,
                    selected ? TEXT : open ? 0xFFC5CDDA : DIM, false);
            ctx.drawText(textRenderer, q.phase(), x + 34, ry + 22, stateColor, false);
            hits.add(new Hit(x + 7, ry, contentRight - (x + 7), rowH - 4, "quest:" + i));
        }

        if (railMaxScroll > 0) {
            railTrackX = x + w - 8; railTrackY = listTop; railTrackH = visibleRows * rowH - 4;
            ctx.fill(railTrackX, railTrackY, railTrackX + 3, railTrackY + railTrackH, 0xFF101722);
            railThumbH = Math.max(22, Math.round(railTrackH * (visibleRows / (float) QuestCatalog.QUESTS.size())));
            int travel = Math.max(1, railTrackH - railThumbH);
            railThumbY = railTrackY + Math.round(travel * (railScroll / (float) railMaxScroll));
            boolean thumbHover = inside(mx, my, railTrackX - 4, railThumbY, 11, railThumbH);
            ctx.fill(railTrackX - 1, railThumbY, railTrackX + 4, railThumbY + railThumbH,
                    thumbHover || railDragging ? CYAN : 0xFF65758D);
        } else { railTrackH = railThumbH = 0; }
    }

    private void questDetail(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        QuestCatalog.Quest q = QuestCatalog.byIndex(selectedQuest);
        boolean done = SVQuestClient.STATE.claimed(q.id());
        boolean open = unlocked(q);
        boolean ready = !done && complete(q);
        panel(ctx, x, y, x + w, y + h, PANEL, BORDER);

        int badgeW = Math.min(180, textRenderer.getWidth(q.phase()) + 20);
        panel(ctx, x + 16, y + 13, x + 16 + badgeW, y + 34,
                done ? 0xFF1F4931 : open ? 0xFF61461D : 0xFF273348,
                done ? GREEN : open ? GOLD : BORDER);
        drawCentered(ctx, q.phase(), x + 16, y + 20, badgeW, done ? GREEN : open ? GOLD : MUTED);
        String stateText = done ? "HOÀN THÀNH" : ready ? "HOÀN TẤT" : open ? "ĐANG THỰC HIỆN" : "CHƯA MỞ KHÓA";
        int stateColor = done ? GREEN : ready ? CYAN : open ? GOLD : DIM;
        ctx.drawText(textRenderer, stateText, x + w - 16 - textRenderer.getWidth(stateText), y + 20, stateColor, true);
        ctx.drawText(textRenderer, q.title(), x + 16, y + 46, TEXT, true);
        drawWrapped(ctx, q.description(), x + 16, y + 63, w - 32, MUTED, 2);

        int oy = y + 100;
        if (!open) {
            ctx.drawText(textRenderer, "YÊU CẦU", x + 16, oy, GOLD, true); oy += 16;
            String req = prerequisiteText(q);
            panel(ctx, x + 16, oy, x + w - 16, oy + 34, 0xFF181E29, BORDER);
            drawWrapped(ctx, req, x + 27, oy + 10, w - 54, MUTED, 2);
            oy += 49;
        }

        ctx.drawText(textRenderer, "MỤC TIÊU", x + 16, oy, CYAN, true); oy += 17;
        for (int i = 0; i < q.objectives().size(); i++) {
            QuestCatalog.Objective o = q.objectives().get(i);
            long value = SVQuestClient.STATE.progress(QuestCatalog.progressKey(q, i));
            boolean objectiveDone = done || value >= o.amount();
            int cardH = !o.featureId().isBlank() && open && !objectiveDone ? 50 : 40;
            panel(ctx, x + 16, oy, x + w - 16, oy + cardH, CARD, objectiveDone ? 0xFF376449 : BORDER);
            ctx.drawText(textRenderer, objectiveDone ? "✓" : "○", x + 26, oy + 8, objectiveDone ? GREEN : MUTED, true);
            String label = o.label().isBlank() ? o.type() : o.label();
            ctx.drawText(textRenderer, trim(label, Math.max(24, (w - 180) / 6)), x + 44, oy + 7, TEXT, false);
            String n = Math.min(value, o.amount()) + " / " + o.amount();
            ctx.drawText(textRenderer, n, x + w - 27 - textRenderer.getWidth(n), oy + 7, objectiveDone ? GREEN : MUTED, false);
            int bx = x + 44, by = oy + 25, barW = Math.max(30, w - 88 - (cardH == 50 ? 115 : 0));
            ctx.fill(bx, by, bx + barW, by + 5, 0xFF0D131C);
            int pw = o.amount() <= 0 ? barW : Math.round(barW * Math.min(1f, value / (float) o.amount()));
            ctx.fill(bx, by, bx + pw, by + 5, objectiveDone ? GREEN : CYAN);
            if (cardH == 50) actionButton(ctx, x + w - 124, oy + 22, 96, 19, "MỞ HỆ", "feature:" + o.featureId(), mx, my);
            oy += cardH + 7;
            if (oy > y + h - 90) break;
        }

        if (oy < y + h - 45) {
            oy += 4;
            ctx.drawText(textRenderer, "PHẦN THƯỞNG", x + 16, oy, GOLD, true); oy += 16;
            int chipX = x + 16;
            for (QuestCatalog.Reward reward : q.rewards()) {
                String rewardText = rewardText(reward);
                int cw = Math.min(w - 32, textRenderer.getWidth(rewardText) + 16);
                if (chipX + cw > x + w - 16) { chipX = x + 16; oy += 23; }
                if (oy > y + h - 25) break;
                panel(ctx, chipX, oy, chipX + cw, oy + 19, 0xFF293421, 0xFF607543);
                ctx.drawText(textRenderer, trim(rewardText, Math.max(10, cw / 6)), chipX + 8, oy + 6, 0xFFE6F5CA, false);
                chipX += cw + 6;
            }
        }
    }

    private String prerequisiteText(QuestCatalog.Quest q) {
        if (q.prerequisites().isEmpty()) return "Không có yêu cầu.";
        StringBuilder b = new StringBuilder("Hoàn thành: ");
        for (int i = 0; i < q.prerequisites().size(); i++) {
            if (i > 0) b.append(", ");
            QuestCatalog.Quest req = QuestCatalog.byId(q.prerequisites().get(i));
            b.append(req == null ? q.prerequisites().get(i) : req.title());
        }
        return b.toString();
    }

    private String rewardText(QuestCatalog.Reward r) {
        if (!r.label().isBlank()) return r.label();
        return switch (r.type()) {
            case "ITEM" -> Math.max(1, r.count()) + " " + r.item().replace("cobblemon:", "");
            case "COBBLEDOLLARS" -> r.amount() + " CobbleDollars";
            case "BEASTCOIN" -> r.amount() + " BeastCoin";
            case "HUNTERCOIN" -> r.amount() + " HunterCoin";
            case "SKILL_POINT" -> r.amount() + " Skill Point";
            case "COMMAND" -> "Phần thưởng server";
            default -> r.type();
        };
    }

    private void featureGrid(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        panel(ctx, x, y, x + w, y + h, PANEL, BORDER);
        String title = switch (tab) { case ACTIVITIES -> "HOẠT ĐỘNG"; case POKEMON -> "POKÉMON"; case SHOPS -> "CỬA HÀNG"; case SERVICES -> "DỊCH VỤ"; default -> ""; };
        ctx.drawText(textRenderer, title, x + 16, y + 13, TEXT, true);
        ctx.drawText(textRenderer, "Mở trực tiếp hệ thống server.", x + 16, y + 30, MUTED, false);
        String[][] cards = cards(tab);
        int cols = w >= 900 ? 4 : 3, gap = 9, cw = (w - 32 - gap * (cols - 1)) / cols, ch = 78, startY = y + 53;
        for (int i = 0; i < cards.length; i++) {
            int col = i % cols, row = i / cols, cx = x + 16 + col * (cw + gap), cy = startY + row * (ch + gap);
            if (cy + ch > y + h - 12) break;
            boolean hover = inside(mx, my, cx, cy, cw, ch);
            panel(ctx, cx, cy, cx + cw, cy + ch, hover ? CARD_HOVER : CARD, hover ? CYAN : BORDER);
            ctx.fill(cx + 9, cy + 10, cx + 13, cy + ch - 10, categoryColor(tab));
            ctx.drawText(textRenderer, cards[i][0], cx + 22, cy + 14, TEXT, true);
            drawWrapped(ctx, cards[i][1], cx + 22, cy + 31, cw - 34, MUTED, 2);
            ctx.drawText(textRenderer, hover ? "MỞ  ›" : "MỞ", cx + 22, cy + 59, hover ? CYAN : DIM, true);
            hits.add(new Hit(cx, cy, cw, ch, "feature:" + cards[i][2]));
        }
    }

    private String[][] cards(Tab tab) {
        return switch (tab) {
            case ACTIVITIES -> new String[][]{{"NovaRaids","Raid boss, lịch raid và phần thưởng.","raids"},{"Ranked","PvP xếp hạng.","ranked"},{"Battle Tower","Mở terminal Battle Tower gần bạn.","battle_tower"},{"Battle Factory","Battle với đội hình rental.","battle_factory"},{"Hunts","Săn Pokémon theo mục tiêu.","hunts"},{"Expeditions","Gửi Pokémon đi expedition.","expeditions"},{"Showcase","Trưng bày và thi Pokémon.","showcase"},{"Daily","Phần thưởng hằng ngày.","daily"},{"Battle Pass","Tiến trình mùa.","battle_pass"}};
            case POKEMON -> new String[][]{{"Pokémon Skills","Mua, bind và build Pokémon Skill.","pokemon_skills"},{"Breeding","Mở trực tiếp SoulBreeding Daycare.","breeding"},{"Research","Research Tasks và Pokédex.","research"},{"WonderTrade","Đổi Pokémon ngẫu nhiên.","wonder_trade"},{"STS","Trade/storage utility.","sts"},{"GTS","Marketplace Pokémon.","gts"},{"Fusion / Potara","Fusion endgame.","fusion"},{"Skins","Kho và trang phục Pokémon.","skins"}};
            case SHOPS -> new String[][]{{"Shop chính","Poké Ball, berry, medicine, resource.","shop"},{"Skin Shop","Skin Pokémon.","skins"},{"GTS","Mua bán Pokémon.","gts"},{"Hunter Shop","Nội dung HunterCoin.","hunter_shop"},{"Tera Lab","Tài nguyên Tera.","tera_lab"},{"Gacha","Crate / gacha content.","gacha"},{"Rank Shop","Quyền lợi server.","rank_shop"}};
            case SERVICES -> new String[][]{{"Home","Quản lý home cá nhân.","homes"},{"Warp","Khu chức năng.","warps"},{"Waypoint / GPS","Dẫn đường.","waypoints"},{"Claim","Bảo vệ vùng đất.","claims"},{"GTS","Marketplace Pokémon.","gts"},{"Skin Inventory","Kho skin.","skin_inventory"},{"Exchange","Đổi tiền/tài nguyên.","exchange"}};
            default -> new String[0][0];
        };
    }

    private int categoryColor(Tab tab) { return switch (tab) { case ACTIVITIES -> GOLD; case POKEMON -> CYAN; case SHOPS -> GREEN; case SERVICES -> MAGENTA; default -> BORDER; }; }

    private void actionButton(DrawContext ctx, int x, int y, int w, int h, String label, String action, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h);
        panel(ctx, x, y, x + w, y + h, hover ? 0xFF8A3378 : 0xFF68265E, hover ? 0xFFFF82DF : MAGENTA);
        drawCentered(ctx, label, x, y + Math.max(5, (h - 8) / 2), w, 0xFFFFFFFF);
        hits.add(new Hit(x, y, w, h, "action:" + action));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && railTrackH > 0 && inside(mouseX, mouseY, railTrackX - 5, railThumbY, 13, railThumbH)) {
            railDragging = true; railGrabOffset = mouseY - railThumbY; return true;
        }
        if (button == 0 && railTrackH > 0 && inside(mouseX, mouseY, railTrackX - 5, railTrackY, 13, railTrackH)) {
            setScrollFromThumb(mouseY - railThumbH / 2.0); return true;
        }
        if (button == 0) for (Hit hit : new ArrayList<>(hits)) {
            if (!inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) continue;
            String a = hit.action;
            if (a.equals("close")) { close(); return true; }
            if (a.startsWith("tab:")) { tab = Tab.valueOf(a.substring(4)); return true; }
            if (a.startsWith("quest:")) { selectedQuest = Integer.parseInt(a.substring(6)); return true; }
            if (a.startsWith("feature:")) { SVQuestClient.action(a); return true; }
            if (a.startsWith("action:")) { SVQuestClient.action(a.substring(7)); return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && railDragging) { setScrollFromThumb(mouseY - railGrabOffset); return true; }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && railDragging) { railDragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tab == Tab.PROGRESS && inside(mouseX, mouseY, railBoundsX, railBoundsY, railBoundsW, railBoundsH)) {
            if (verticalAmount != 0) railScroll = clamp(railScroll - (int) Math.signum(verticalAmount) * 3, 0, railMaxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void setScrollFromThumb(double desiredTop) {
        int travel = Math.max(1, railTrackH - railThumbH);
        double local = Math.max(0, Math.min(travel, desiredTop - railTrackY));
        railScroll = clamp((int) Math.round((local / travel) * railMaxScroll), 0, railMaxScroll);
    }

    private void drawWrapped(DrawContext ctx, String text, int x, int y, int maxWidth, int color, int maxLines) {
        if (text == null || text.isBlank()) return;
        String[] words = text.split(" "); StringBuilder line = new StringBuilder(); int lines = 0;
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textRenderer.getWidth(candidate) > maxWidth && !line.isEmpty()) {
                ctx.drawText(textRenderer, line.toString(), x, y + lines * 11, color, false); lines++;
                if (lines >= maxLines) return; line.setLength(0); line.append(word);
            } else { if (!line.isEmpty()) line.append(' '); line.append(word); }
        }
        if (!line.isEmpty() && lines < maxLines) ctx.drawText(textRenderer, line.toString(), x, y + lines * 11, color, false);
    }

    private void panel(DrawContext ctx, int x1, int y1, int x2, int y2, int fill, int border) {
        ctx.fill(x1,y1,x2,y2,fill); ctx.fill(x1,y1,x2,y1+1,border); ctx.fill(x1,y2-1,x2,y2,border);
        ctx.fill(x1,y1,x1+1,y2,border); ctx.fill(x2-1,y1,x2,y2,border);
    }
    private void drawCentered(DrawContext ctx, String s, int x, int y, int w, int color) { ctx.drawText(textRenderer, s, x + (w - textRenderer.getWidth(s)) / 2, y, color, false); }
    private String trim(String s, int chars) { if (s == null) return ""; return s.length() <= chars ? s : s.substring(0, Math.max(1, chars - 1)) + "…"; }
    private int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private boolean inside(double px, double py, int x, int y, int w, int h) { return px >= x && px < x + w && py >= y && py < y + h; }
    private record Hit(int x, int y, int w, int h, String action) {}
}
