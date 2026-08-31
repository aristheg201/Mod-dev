package vn.svframe.svquest.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import vn.svframe.svquest.SVQuest;
import vn.svframe.svquest.quest.QuestCatalog;

import java.util.ArrayList;
import java.util.List;

public final class QuestScreen extends Screen {
    private static final Identifier LOGO = Identifier.of(SVQuest.MOD_ID, "textures/gui/server_logo.png");
    private static final int LOGO_W = 512;
    private static final int LOGO_H = 355;

    private static final int BG = 0xE70A0E16;
    private static final int PANEL = 0xE8161D2A;
    private static final int PANEL_2 = 0xF0202938;
    private static final int BORDER = 0xFF334156;
    private static final int ACCENT = 0xFFFF4FD1;
    private static final int CYAN = 0xFF55DDF2;
    private static final int GREEN = 0xFF72E58B;
    private static final int GOLD = 0xFFFFCF5A;
    private static final int TEXT = 0xFFF3F6FA;
    private static final int MUTED = 0xFFA9B3C5;
    private static final int LOCKED = 0xFF6B7280;

    private enum Tab { PROGRESS, ACTIVITIES, POKEMON, SHOPS, SERVICES }
    private Tab tab = Tab.PROGRESS;
    private final List<Hit> hits = new ArrayList<>();

    public QuestScreen() {
        super(Text.literal("SVQuest"));
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        hits.clear();
        ctx.fill(0, 0, width, height, 0xB0000000);

        int maxW = Math.min(1180, width - 28);
        int maxH = Math.min(680, height - 28);
        int x = (width - maxW) / 2;
        int y = (height - maxH) / 2;
        int right = x + maxW;
        int bottom = y + maxH;

        panel(ctx, x, y, right, bottom, BG, 0xFF252F42);
        drawHeader(ctx, x, y, maxW, mouseX, mouseY);

        int contentY = y + 104;
        if (tab == Tab.PROGRESS) drawProgress(ctx, x + 18, contentY, maxW - 36, maxH - 122, mouseX, mouseY);
        else drawFeatureGrid(ctx, x + 18, contentY, maxW - 36, maxH - 122, mouseX, mouseY);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawHeader(DrawContext ctx, int x, int y, int w, int mx, int my) {
        int logoH = 76;
        int logoW = (int) (logoH * (LOGO_W / (float) LOGO_H));
        try {
            ctx.drawTexture(LOGO, x + 18, y + 10, 0, 0, logoW, logoH, LOGO_W, LOGO_H);
        } catch (Throwable ignored) {
            ctx.drawText(textRenderer, "SVFRAME COBBLEMON", x + 22, y + 38, ACCENT, true);
        }

        int titleX = x + 18 + logoW + 16;
        ctx.drawText(textRenderer, "SVQUEST", titleX, y + 19, TEXT, true);
        ctx.drawText(textRenderer, "Tiến trình gameplay • launcher tính năng • không cần nhớ command", titleX, y + 36, MUTED, false);
        String status = SVQuestClient.STATE.serverAvailable() ? "● Đã đồng bộ server" : "● Chưa nhận dữ liệu server";
        ctx.drawText(textRenderer, status, titleX, y + 55, SVQuestClient.STATE.serverAvailable() ? GREEN : GOLD, false);

        String[] labels = {"TIẾN TRÌNH", "HOẠT ĐỘNG", "POKÉMON", "CỬA HÀNG", "DỊCH VỤ"};
        Tab[] tabs = Tab.values();
        int navY = y + 82;
        int gap = 6;
        int navX = x + 18;
        int available = w - 36;
        int bw = (available - gap * (labels.length - 1)) / labels.length;
        for (int i = 0; i < labels.length; i++) {
            boolean active = tab == tabs[i];
            boolean hover = inside(mx, my, navX, navY, bw, 20);
            int fill = active ? 0xFF7A236B : hover ? 0xFF303B50 : 0xFF232C3C;
            panel(ctx, navX, navY, navX + bw, navY + 20, fill, active ? ACCENT : BORDER);
            drawCentered(ctx, labels[i], navX, navY + 6, bw, active ? 0xFFFFFFFF : 0xFFD3DBE8);
            hits.add(new Hit(navX, navY, bw, 20, "tab:" + tabs[i].name()));
            navX += bw + gap;
        }
    }

    private void drawProgress(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        int leftW = Math.max(190, Math.min(255, w / 4));
        int gap = 12;
        drawJourneyRail(ctx, x, y, leftW, h, mx, my);
        drawQuestDetails(ctx, x + leftW + gap, y, w - leftW - gap, h, mx, my);
    }

    private void drawJourneyRail(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        panel(ctx, x, y, x + w, y + h, PANEL, BORDER);
        ctx.drawText(textRenderer, "LỘ TRÌNH GAMEPLAY", x + 12, y + 12, TEXT, true);
        ctx.drawText(textRenderer, "Biết mình đang ở đâu và còn gì phía trước.", x + 12, y + 28, MUTED, false);

        int current = Math.max(0, Math.min(SVQuestClient.STATE.questIndex(), QuestCatalog.QUESTS.size() - 1));
        int rowY = y + 50;
        int rowH = Math.max(34, Math.min(48, (h - 62) / QuestCatalog.QUESTS.size()));
        for (int i = 0; i < QuestCatalog.QUESTS.size(); i++) {
            var q = QuestCatalog.QUESTS.get(i);
            boolean done = i < current;
            boolean active = i == current;
            boolean hover = inside(mx, my, x + 8, rowY, w - 16, rowH - 4);
            int fill = active ? 0xFF29384F : hover ? 0xFF202B3C : 0xFF171F2B;
            panel(ctx, x + 8, rowY, x + w - 8, rowY + rowH - 4, fill, active ? CYAN : 0xFF263247);
            ctx.fill(x + 13, rowY + 9, x + 17, rowY + rowH - 13, done ? GREEN : active ? CYAN : LOCKED);
            ctx.drawText(textRenderer, q.phase(), x + 24, rowY + 7, done ? GREEN : active ? CYAN : MUTED, false);
            ctx.drawText(textRenderer, trim(q.title(), Math.max(12, (w - 40) / 6)), x + 24, rowY + 20, active ? TEXT : 0xFFC1C8D4, false);
            rowY += rowH;
        }
    }

    private void drawQuestDetails(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        int current = Math.max(0, Math.min(SVQuestClient.STATE.questIndex(), QuestCatalog.QUESTS.size() - 1));
        var q = QuestCatalog.byIndex(current);
        panel(ctx, x, y, x + w, y + h, PANEL, BORDER);

        ctx.drawText(textRenderer, q.phase(), x + 18, y + 16, CYAN, true);
        ctx.drawText(textRenderer, q.title(), x + 18, y + 34, TEXT, true);
        ctx.drawText(textRenderer, q.description(), x + 18, y + 52, MUTED, false);

        int actionW = Math.min(180, w / 3);
        int actionX = x + w - actionW - 18;
        int actionY = y + 14;
        panel(ctx, actionX, actionY, actionX + actionW, actionY + 25, 0xFF7A236B, ACCENT);
        drawCentered(ctx, "ĐỒNG BỘ TIẾN TRÌNH", actionX, actionY + 8, actionW, 0xFFFFFFFF);
        hits.add(new Hit(actionX, actionY, actionW, 25, "action:sync"));

        int oy = y + 86;
        ctx.drawText(textRenderer, "MỤC TIÊU", x + 18, oy, GOLD, true);
        oy += 19;
        for (var o : q.objectives()) {
            int value = SVQuestClient.STATE.progress(o.key());
            boolean done = value >= o.target();
            panel(ctx, x + 18, oy, x + w - 18, oy + 40, PANEL_2, done ? GREEN : BORDER);
            ctx.drawText(textRenderer, done ? "✓" : "○", x + 28, oy + 8, done ? GREEN : MUTED, true);
            ctx.drawText(textRenderer, o.label(), x + 46, oy + 7, TEXT, false);
            String valueText = Math.min(value, o.target()) + " / " + o.target();
            ctx.drawText(textRenderer, valueText, x + w - 18 - textRenderer.getWidth(valueText) - 10, oy + 7, done ? GREEN : MUTED, false);
            int bx = x + 46;
            int by = oy + 25;
            int bw = w - 92;
            ctx.fill(bx, by, bx + bw, by + 5, 0xFF101722);
            int progressW = o.target() <= 0 ? bw : (int) (bw * Math.min(1f, value / (float) o.target()));
            ctx.fill(bx, by, bx + progressW, by + 5, done ? GREEN : CYAN);
            oy += 48;
        }

        oy += 2;
        ctx.drawText(textRenderer, "PHẦN THƯỞNG → NUÔI QUEST KẾ TIẾP", x + 18, oy, GOLD, true);
        oy += 17;
        int chipX = x + 18;
        for (String reward : q.rewards()) {
            int cw = textRenderer.getWidth(reward) + 18;
            if (chipX + cw > x + w - 18) { chipX = x + 18; oy += 24; }
            panel(ctx, chipX, oy, chipX + cw, oy + 19, 0xFF2B3422, 0xFF657A40);
            ctx.drawText(textRenderer, reward, chipX + 9, oy + 6, 0xFFE6F4C8, false);
            chipX += cw + 7;
        }

        int quickY = Math.max(oy + 38, y + h - 118);
        ctx.drawText(textRenderer, "MỞ NHANH HỆ LIÊN QUAN", x + 18, quickY, CYAN, true);
        quickY += 17;
        String[][] quick = switch (current) {
            case 0, 1, 2 -> new String[][]{{"Pokémon Skills", "pokemon_skills"}, {"Shop", "shop"}, {"GTS", "gts"}};
            case 3, 4 -> new String[][]{{"Pokémon Skills", "pokemon_skills"}, {"Ranked", "ranked"}, {"Hunts", "hunts"}};
            case 5 -> new String[][]{{"Shop", "shop"}, {"GTS", "gts"}, {"Skin Shop", "skins"}};
            case 6 -> new String[][]{{"Raids", "raids"}, {"Ranked", "ranked"}, {"Battle Tower", "battle_tower"}};
            default -> new String[][]{{"Research", "research"}, {"Fusion", "fusion"}, {"Raids", "raids"}};
        };
        int bw = (w - 36 - 16) / 3;
        for (int i = 0; i < quick.length; i++) {
            int bx = x + 18 + i * (bw + 8);
            button(ctx, bx, quickY, bw, 29, quick[i][0], quick[i][1], mx, my);
        }

        if (current < QuestCatalog.QUESTS.size() - 1) {
            var next = QuestCatalog.byIndex(current + 1);
            int ny = y + h - 48;
            ctx.drawText(textRenderer, "TIẾP THEO", x + 18, ny, MUTED, true);
            ctx.drawText(textRenderer, next.title() + "  🔒", x + 90, ny, 0xFFD7DEEA, false);
        }
    }

    private void drawFeatureGrid(DrawContext ctx, int x, int y, int w, int h, int mx, int my) {
        panel(ctx, x, y, x + w, y + h, PANEL, BORDER);
        String title = switch (tab) {
            case ACTIVITIES -> "HOẠT ĐỘNG";
            case POKEMON -> "POKÉMON";
            case SHOPS -> "CỬA HÀNG";
            case SERVICES -> "DỊCH VỤ";
            default -> "TIẾN TRÌNH";
        };
        ctx.drawText(textRenderer, title, x + 16, y + 14, TEXT, true);
        ctx.drawText(textRenderer, "Click để mở trực tiếp. Command chỉ là implementation detail bên dưới.", x + 16, y + 31, MUTED, false);

        String[][] cards = switch (tab) {
            case ACTIVITIES -> new String[][]{
                    {"NovaRaids", "Raid boss & lịch raid", "raids"}, {"Ranked", "PvP xếp hạng", "ranked"},
                    {"Battle Tower", "Chuỗi battle PvE", "battle_tower"}, {"Battle Factory", "Đội hình rental", "battle_factory"},
                    {"Hunts", "Săn mục tiêu", "hunts"}, {"Expeditions", "Hoạt động expedition", "expeditions"},
                    {"Showcase", "Trưng bày Pokémon", "showcase"}, {"Daily", "Daily rewards", "daily"},
                    {"Battle Pass", "Tiến trình mùa", "battle_pass"}
            };
            case POKEMON -> new String[][]{
                    {"Pokémon Skills", "936+ skill / build", "pokemon_skills"}, {"Breeding", "Trứng & lai tạo", "breeding"},
                    {"Research", "Research tasks", "research"}, {"WonderTrade", "Đổi Pokémon ngẫu nhiên", "wonder_trade"},
                    {"STS", "Trade / storage utility", "sts"}, {"GTS", "Marketplace Pokémon", "gts"},
                    {"Fusion", "Potara / Fusion endgame", "fusion"}, {"Skins", "Skin Pokémon", "skins"}
            };
            case SHOPS -> new String[][]{
                    {"Shop chính", "Vật phẩm cơ bản", "shop"}, {"Skin Shop", "Trang phục Pokémon", "skins"},
                    {"GTS", "Player marketplace", "gts"}, {"Hunter", "HunterCoin content", "hunts"},
                    {"Pokémon Skills", "Mua skill bằng BeastCoin", "pokemon_skills"}, {"Raid", "Tài nguyên raid", "raids"}
            };
            case SERVICES -> new String[][]{
                    {"Homes", "Nhà của bạn", "homes"}, {"Warps", "Điểm dịch chuyển", "warps"},
                    {"Waypoints", "GPS / dẫn đường", "waypoints"}, {"Claims", "Bảo vệ khu vực", "claims"},
                    {"GTS", "Giao dịch Pokémon", "gts"}, {"Skin Inventory", "Kho skin", "skins"}
            };
            default -> new String[0][0];
        };

        int cols = w >= 900 ? 3 : 2;
        int gap = 10;
        int cardW = (w - 32 - gap * (cols - 1)) / cols;
        int cardH = 74;
        int startY = y + 54;
        for (int i = 0; i < cards.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = x + 16 + col * (cardW + gap);
            int cy = startY + row * (cardH + gap);
            featureCard(ctx, cx, cy, cardW, cardH, cards[i][0], cards[i][1], cards[i][2], mx, my);
        }
    }

    private void featureCard(DrawContext ctx, int x, int y, int w, int h, String title, String desc, String id, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h);
        panel(ctx, x, y, x + w, y + h, hover ? 0xFF28354A : PANEL_2, hover ? CYAN : BORDER);
        ctx.fill(x + 9, y + 10, x + 13, y + h - 10, hover ? CYAN : 0xFF52657E);
        ctx.drawText(textRenderer, title, x + 23, y + 13, TEXT, true);
        ctx.drawText(textRenderer, desc, x + 23, y + 31, MUTED, false);
        String open = "MỞ  ›";
        ctx.drawText(textRenderer, open, x + w - textRenderer.getWidth(open) - 16, y + h - 18, hover ? CYAN : 0xFFD9E0EB, true);
        hits.add(new Hit(x, y, w, h, "action:feature:" + id));
    }

    private void button(DrawContext ctx, int x, int y, int w, int h, String label, String id, int mx, int my) {
        boolean hover = inside(mx, my, x, y, w, h);
        panel(ctx, x, y, x + w, y + h, hover ? 0xFF31516A : 0xFF26384B, hover ? CYAN : 0xFF3C5974);
        drawCentered(ctx, label, x, y + 10, w, hover ? 0xFFFFFFFF : 0xFFE6EDF7);
        hits.add(new Hit(x, y, w, h, "action:feature:" + id));
    }

    private void panel(DrawContext ctx, int x1, int y1, int x2, int y2, int fill, int border) {
        ctx.fill(x1, y1, x2, y2, border);
        ctx.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, fill);
    }

    private void drawCentered(DrawContext ctx, String s, int x, int y, int w, int color) {
        ctx.drawText(textRenderer, s, x + (w - textRenderer.getWidth(s)) / 2, y, color, false);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static String trim(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, Math.max(1, max - 1)) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Hit hit : hits) {
                if (!hit.contains(mouseX, mouseY)) continue;
                if (hit.action.startsWith("tab:")) {
                    try { tab = Tab.valueOf(hit.action.substring(4)); }
                    catch (Exception ignored) {}
                    return true;
                }
                if (hit.action.startsWith("action:")) {
                    SVQuestClient.action(hit.action.substring(7));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private record Hit(int x, int y, int w, int h, String action) {
        boolean contains(double mx, double my) { return inside(mx, my, x, y, w, h); }
    }
}
