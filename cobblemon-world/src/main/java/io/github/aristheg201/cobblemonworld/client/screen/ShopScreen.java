package io.github.aristheg201.cobblemonworld.client.screen;

import com.google.gson.Gson;
import io.github.aristheg201.cobblemonworld.network.ShopBuyPayload;
import io.github.aristheg201.cobblemonworld.shop.ShopEntry;
import io.github.aristheg201.cobblemonworld.shop.ShopSnapshot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.util.List;

/** Merchant board in its own screen; all catalog metadata comes from the server. */
public final class ShopScreen extends Screen {
    private static final Gson GSON = new Gson();
    private static final int W = 560, H = 340, GRID_X = 24, GRID_Y = 109, COLS = 5, CARD_W = 64, CARD_H = 64, ROWS = 3;
    private static final int INK = 0xFF3B291F, GOLD = 0xFFD1A652, RED = 0xFF8C3540;
    private static final ResourceLocation BOARD = ResourceLocation.fromNamespaceAndPath("cobblemonworld", "textures/gui/shop_board.png");
    private ShopSnapshot snapshot;
    private String category = "all", selected = "";
    private int row, quantity = 1;
    private float scale;
    private int originX, originY;
    private boolean buying, dragging;
    private long requestAt;

    public ShopScreen(String json) {
        super(Component.translatable("shop.cobblemonworld.title"));
        snapshot = GSON.fromJson(json, ShopSnapshot.class);
        if (!snapshot.entries().isEmpty()) selected = snapshot.entries().getFirst().id();
    }
    public void update(String json) {
        ShopSnapshot next = GSON.fromJson(json, ShopSnapshot.class);
        if (!next.id().equals(snapshot.id())) return;
        snapshot = next; buying = false;
        row = Math.min(row, maxRow());
        if (entry() == null && !filtered().isEmpty()) selected = filtered().getFirst().id();
        if ("success".equals(next.result())) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.1f));
    }
    protected void init() {
        scale = Math.min(1.0f, Math.min((width - 12) / (float) W, (height - 12) / (float) H));
        originX = (int) ((width - W * scale) / 2); originY = (int) ((height - H * scale) / 2);
    }
    private List<ShopEntry> filtered() { return snapshot.entries().stream().filter(e -> category.equals("all") || e.categories().contains(category)).toList(); }
    private ShopEntry entry() { return snapshot.entries().stream().filter(e -> e.id().equals(selected)).findFirst().orElse(null); }
    private int maxRow() { return Math.max(0, (filtered().size() + COLS - 1) / COLS - ROWS); }
    private boolean affordable(ShopEntry e) {
        if (e == null || !e.available() || !snapshot.economyAvailable() || "access".equals(snapshot.result())) return false;
        try { return new BigDecimal(snapshot.balance()).compareTo(BigDecimal.valueOf((long) e.price() * quantity)) >= 0; }
        catch (NumberFormatException ex) { return false; }
    }
    private static Component tr(String suffix) { return Component.translatable("shop.cobblemonworld." + suffix); }
    private ItemStack stack(ShopEntry e) {
        var id = ResourceLocation.tryParse(e.item());
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(id), e.quantity());
    }
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xA0140D09);
        int mx = (int) ((mouseX - originX) / scale), my = (int) ((mouseY - originY) / scale);
        g.pose().pushPose(); g.pose().translate(originX, originY, 0); g.pose().scale(scale, scale, 1);
        g.blit(BOARD, 0, 0, 0, 0, W, H, W, H);
        g.drawString(font, Component.translatable(snapshot.titleKey()), 29, 22, 0xFFF9E7BD, true);
        g.drawString(font, snapshot.merchant(), 29, 42, 0xFFE4C583, false);
        g.drawString(font, Component.translatable(snapshot.descriptionKey()), 29, 61, INK, false);
        String balance = tr("balance").getString() + ": " + (snapshot.economyAvailable() ? snapshot.balance() + " BC" : "—");
        g.drawString(font, balance, W - 32 - font.width(balance), 26, 0xFFFFE6A7, true);
        control(g, 514, 46, 22, 18, Component.literal("×"), mx, my, true, false);
        int tx = 24;
        for (var cat : snapshot.categories()) {
            int tw = Math.max(54, font.width(tr("category." + cat)) + 18);
            control(g, tx, 80, tw, 21, tr("category." + cat), mx, my, true, category.equals(cat)); tx += tw + 4;
        }
        List<ShopEntry> rows = filtered();
        ShopEntry hovered = null;
        for (int local = 0; local < ROWS * COLS; local++) {
            int index = row * COLS + local;
            if (index >= rows.size()) break;
            var e = rows.get(index);
            int x = GRID_X + (local % COLS) * CARD_W, y = GRID_Y + (local / COLS) * CARD_H;
            boolean hover = hit(mx, my, x, y, CARD_W - 4, CARD_H - 4);
            panel(g, x, y, CARD_W - 4, CARD_H - 4, e.id().equals(selected) ? 0xFFFFE6AF : hover ? 0xFFF7EACA : 0xFFE4D4AE,
                    e.id().equals(selected) ? RED : hover ? GOLD : 0xFFAD9263);
            ItemStack item = stack(e);
            if (!item.isEmpty()) {
                g.pose().pushPose(); g.pose().translate(x + 19, y + 5, 0); g.pose().scale(1.4f, 1.4f, 1);
                g.renderItem(item, 0, 0); g.pose().popPose();
                var nameLines = font.split(item.getHoverName(), CARD_W - 10);
                for (int line = 0; line < Math.min(2, nameLines.size()); line++)
                    g.drawString(font, nameLines.get(line), x + 4, y + 29 + line * 9, INK, false);
            } else g.drawString(font, "—", x + 25, y + 14, INK, false);
            String price = e.price() + " BC";
            g.drawString(font, price, x + 4, y + 49, !e.available() ? 0xFF8D7765 : affordable(e) ? INK : RED, false);
            if (e.quantity() > 1) g.drawString(font, "×" + e.quantity(), x + 38, y + 49, INK, false);
            if (!e.available()) g.fill(x + 4, y + 26, x + 56, y + 27, RED);
            if (hover) hovered = e;
        }
        // Track and proportional thumb, both clickable/draggable.
        panel(g, 346, GRID_Y, 8, 188, 0xFF907449, 0xFF654629);
        int allRows = Math.max(ROWS, (rows.size() + COLS - 1) / COLS);
        int thumbH = Math.max(18, 186 * ROWS / allRows);
        int thumbY = GRID_Y + 1 + (maxRow() == 0 ? 0 : row * (186 - thumbH) / maxRow());
        panel(g, 347, thumbY, 6, thumbH, GOLD, 0xFF704523);
        g.drawString(font, Component.translatable("shop.cobblemonworld.catalog_count", rows.size()), 26, 311, INK, false);
        var chosen = entry();
        if (chosen != null) {
            ItemStack item = stack(chosen);
            if (!item.isEmpty()) {
                g.pose().pushPose(); g.pose().translate(414, 113, 0); g.pose().scale(3.4f, 3.4f, 1);
                g.renderItem(item, 0, 0); g.pose().popPose();
                int line = 0;
                for (var text : font.split(item.getHoverName(), 152)) {
                    g.drawString(font, text, 374, 175 + line * 11, INK, false); if (++line == 3) break;
                }
            }
            g.drawString(font, tr("category." + chosen.categories().getFirst()), 374, 211, 0xFF785D41, false);
            g.drawString(font, Component.translatable("shop.cobblemonworld.bundle", chosen.quantity()), 374, 226, INK, false);
            g.drawString(font, ((long) chosen.price() * quantity) + " BeastCoin", 374, 244, INK, false);
            control(g, 374, 262, 24, 20, Component.literal("−"), mx, my, quantity > 1, false);
            g.drawString(font, Integer.toString(quantity), 417, 269, INK, false);
            control(g, 443, 262, 24, 20, Component.literal("+"), mx, my, quantity < 16, false);
            if (buying && System.currentTimeMillis() - requestAt > 5000) buying = false;
            control(g, 374, 289, 152, 25, tr(buying ? "waiting" : "buy"), mx, my, affordable(chosen) && !buying, true);
        }
        String status = snapshot.result();
        if (!snapshot.economyAvailable() && (status == null || status.isEmpty())) status = "economy";
        if (status != null && !status.isEmpty()) {
            String text = font.plainSubstrByWidth(tr("result." + status).getString(), 510);
            g.drawString(font, text, 25, 326, status.equals("success") ? 0xFF46723B : RED, false);
        }
        g.pose().popPose();
        if (hovered != null && !stack(hovered).isEmpty()) g.renderTooltip(font, stack(hovered), mouseX, mouseY);
    }
    private void panel(GuiGraphics g, int x, int y, int w, int h, int color, int edge) {
        g.fill(x + 1, y + 2, x + w + 1, y + h + 2, 0x66302010);
        g.fill(x, y, x + w, y + h, edge); g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, 0x55FFFFFF);
        g.fill(x + 2, y + h - 3, x + w - 2, y + h - 2, 0x33402A10);
    }
    private void control(GuiGraphics g, int x, int y, int w, int h, Component label, int mx, int my, boolean enabled, boolean chosen) {
        boolean hover = enabled && hit(mx, my, x, y, w, h);
        panel(g, x, y, w, h, !enabled ? 0xFFB4A282 : chosen ? hover ? 0xFFA64542 : RED : hover ? 0xFFFFE8BC : 0xFFE1C594, 0xFF775128);
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, !enabled ? 0xFF6B614E : chosen ? 0xFFFFEDC2 : INK);
    }
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0) return super.mouseClicked(x, y, button);
        int mx = (int) ((x - originX) / scale), my = (int) ((y - originY) / scale);
        if (hit(mx, my, 514, 46, 22, 18)) { onClose(); return true; }
        int tx = 24;
        for (var cat : snapshot.categories()) {
            int tw = Math.max(54, font.width(tr("category." + cat)) + 18);
            if (hit(mx, my, tx, 80, tw, 21)) { category = cat; row = 0; if (!filtered().isEmpty() && filtered().stream().noneMatch(e -> e.id().equals(selected))) selected = filtered().getFirst().id(); return true; } tx += tw + 4;
        }
        if (hit(mx, my, 346, GRID_Y, 8, 188)) { dragging = true; scrollTo(my); return true; }
        var rows = filtered();
        for (int i = 0; i < ROWS * COLS && row * COLS + i < rows.size(); i++) {
            if (hit(mx, my, GRID_X + i % COLS * CARD_W, GRID_Y + i / COLS * CARD_H, CARD_W - 4, CARD_H - 4)) {
                selected = rows.get(row * COLS + i).id(); quantity = 1; return true;
            }
        }
        if (hit(mx, my, 374, 262, 24, 20)) { quantity = Math.max(1, quantity - 1); return true; }
        if (hit(mx, my, 443, 262, 24, 20)) { quantity = Math.min(16, quantity + 1); return true; }
        if (hit(mx, my, 374, 289, 152, 25) && affordable(entry()) && !buying) {
            buying = true; requestAt = System.currentTimeMillis();
            ClientPlayNetworking.send(new ShopBuyPayload(snapshot.id(), selected, quantity));
            return true;
        }
        return true;
    }
    private void scrollTo(int my) { row = Math.max(0, Math.min(maxRow(), Math.round((my - GRID_Y) / 188f * maxRow()))); }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (dragging) { scrollTo((int) ((y - originY) / scale)); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    public boolean mouseReleased(double x, double y, int button) { dragging = false; return super.mouseReleased(x, y, button); }
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        row = Math.max(0, Math.min(maxRow(), row + (vertical < 0 ? (int) Math.max(1, -vertical) : -(int) Math.max(1, vertical)))); return true;
    }
    public boolean isPauseScreen() { return false; }
    private static boolean hit(int x, int y, int left, int top, int w, int h) { return x >= left && x < left + w && y >= top && y < top + h; }
    public void qaClickCategory(String value) {
        int tx = 24;
        for (var cat : snapshot.categories()) {
            int tw = Math.max(54, font.width(tr("category." + cat)) + 18);
            if (cat.equals(value)) { mouseClicked(originX + (tx + 3) * scale, originY + 84 * scale, 0); return; }
            tx += tw + 4;
        }
        throw new IllegalArgumentException("Missing category " + value);
    }
    public void qaClickEntry(String id) {
        qaClickCategory("all");
        var rows = filtered();
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id().equals(id)) {
            mouseScrolled(0, 0, 0, -(i / COLS));
            int visible = i - row * COLS;
            mouseClicked(originX + (GRID_X + visible % COLS * CARD_W + 8) * scale,
                    originY + (GRID_Y + visible / COLS * CARD_H + 8) * scale, 0);
            return;
        }
        throw new IllegalArgumentException("Missing QA catalog item " + id);
    }
    public void qaClickBuy() { mouseClicked(originX + 410 * scale, originY + 300 * scale, 0); }
    public ShopSnapshot qaSnapshot() { return snapshot; }
    public void qaCategory(String value) { category = value; row = 0; if (!filtered().isEmpty()) selected = filtered().getFirst().id(); }
    public void qaScroll(int value) { row = Math.min(maxRow(), Math.max(0, value)); }
}
