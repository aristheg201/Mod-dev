package vn.svframe.lively.admin;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.npc.NpcDefinition;

import java.util.Comparator;
import java.util.UUID;

/** Click-driven server-side administration surface built entirely from validated /lively commands. */
public final class NpcAdminWizard {
    private NpcAdminWizard() {}

    public static int show(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return error(source, "Wizard is player-only");
        if (LivelyApi.npcs() == null) return error(source, "Lively NPC runtime is not active");

        line(player, "━━━━━━━━ Lively NPC Wizard ━━━━━━━━", Formatting.DARK_AQUA, true);
        player.sendMessage(Text.literal("Tạo NPC ngay vị trí hiện tại:").formatted(Formatting.GRAY), false);
        row(player,
                button("[PLAYER]", "/lively npc create player NPC npc", "Player-model NPC. Đổi tên/role/skin sau.", Formatting.AQUA),
                button("[VILLAGER]", "/lively npc create vanilla minecraft:villager NPC npc", "Vanilla villager body.", Formatting.GREEN),
                button("[POKÉMON]", "/lively npc create pokemon Pokemon npc pikachu", "Cần Cobblemon Integration; properties có thể sửa sau.", Formatting.LIGHT_PURPLE));
        player.sendMessage(Text.literal("NPC hiện có, bấm để chỉnh:").formatted(Formatting.GRAY), false);
        LivelyApi.npcs().snapshot().values().stream()
                .sorted(Comparator.comparing(NpcDefinition::name).thenComparing(n -> n.id().toString()))
                .limit(40)
                .forEach(npc -> player.sendMessage(button(
                        " • " + npc.name() + "  [" + npc.bodyType() + "] " + shortId(npc.id()),
                        "/lively npc wizard " + npc.id(),
                        "role=" + npc.role() + " | spawned=" + npc.spawned() + " | ai=" + npc.aiEnabled(),
                        npc.spawned() ? Formatting.WHITE : Formatting.DARK_GRAY), false));
        player.sendMessage(Text.literal("Nếu có hơn 40 NPC thì dùng /lively npc list rồi mở wizard bằng UUID. Chat cũng có giới hạn, loài người cuối cùng cũng gặp một giới hạn hợp lý.").formatted(Formatting.DARK_GRAY), false);
        line(player, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━", Formatting.DARK_AQUA, false);
        return 1;
    }

    public static int show(ServerCommandSource source, UUID id) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return error(source, "Wizard is player-only");
        NpcDefinition npc = LivelyApi.npcs() == null ? null : LivelyApi.npcs().get(id).orElse(null);
        if (npc == null) return error(source, "Unknown NPC");

        line(player, "━━━━ " + npc.name() + " · " + shortId(id) + " ━━━━", Formatting.GOLD, true);
        player.sendMessage(Text.literal("Role: " + npc.role() + " | Body: " + npc.bodyType() + (npc.bodyKey().isBlank() ? "" : " · " + npc.bodyKey()))
                .formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal("World: " + npc.world() + " @ " + round(npc.x()) + ", " + round(npc.y()) + ", " + round(npc.z()))
                .formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("Skin: " + (npc.skinName().isBlank() ? "default" : abbreviate(npc.skinName(), 96))).formatted(Formatting.GRAY), false);

        row(player,
                button(npc.spawned() ? "[DESPAWN]" : "[SPAWN]", "/lively npc " + (npc.spawned() ? "despawn " : "spawn ") + id,
                        "Chỉ đổi body runtime; cognition vẫn giữ nguyên.", npc.spawned() ? Formatting.RED : Formatting.GREEN),
                button("[TP HERE]", "/lively npc tp " + id, "Đưa NPC tới vị trí của bạn.", Formatting.AQUA),
                button("[LOOK]", "/lively npc look " + id, "Cho NPC nhìn về phía bạn.", Formatting.YELLOW));

        row(player,
                button(npc.aiEnabled() ? "[AI OFF]" : "[AI ON]", "/lively npc set " + id + " flag ai " + !npc.aiEnabled(),
                        "Bật/tắt cognition/autonomy cho NPC.", npc.aiEnabled() ? Formatting.RED : Formatting.GREEN),
                button("[PLAYER BODY]", "/lively npc set " + id + " body player", "Đổi sang player-model body.", Formatting.AQUA),
                button("[VILLAGER BODY]", "/lively npc set " + id + " body vanilla minecraft:villager", "Đổi nhanh sang villager.", Formatting.GREEN));

        row(player,
                suggest("[SKIN MOJANG]", "/lively npc skin " + id + " mojang ", "Nhập username Mojang.", Formatting.AQUA),
                suggest("[SKIN URL]", "/lively npc skin " + id + " url https://", "Dán URL PNG hoặc trang skin được allowlist.", Formatting.LIGHT_PURPLE),
                button("[SKIN DEFAULT]", "/lively npc skin " + id + " default", "Xóa skin tùy chỉnh.", Formatting.GRAY));

        row(player,
                button("[NAV HERE]", "/lively npc nav here " + id, "Pathfind tới vị trí của bạn.", Formatting.GREEN),
                button("[NAV STOP]", "/lively npc nav stop " + id, "Dừng navigation task hiện tại.", Formatting.RED),
                button("[NAV STATUS]", "/lively npc nav status " + id, "Xem navigation state.", Formatting.YELLOW));

        player.sendMessage(Text.literal("Chỉnh sâu:").formatted(Formatting.GRAY), false);
        player.sendMessage(suggest("  • tên", "/lively npc set " + id + " name ", "Nhập tên mới", Formatting.WHITE), false);
        player.sendMessage(suggest("  • role", "/lively npc set " + id + " role ", "Nhập role mới", Formatting.WHITE), false);
        player.sendMessage(suggest("  • trait", "/lively npc set " + id + " trait ", "Cú pháp: <trait> <0..1>", Formatting.WHITE), false);
        player.sendMessage(suggest("  • need", "/lively npc set " + id + " need ", "Cú pháp: <need> <0..1>", Formatting.WHITE), false);
        player.sendMessage(suggest("  • metadata", "/lively npc set " + id + " meta ", "Ví dụ home.structure, work.structure, business.name", Formatting.WHITE), false);
        player.sendMessage(suggest("  • schedule", "/lively npc schedule add " + id + " ", "Cú pháp: <startMinute> <endMinute> <activity> <structure> <priority>", Formatting.WHITE), false);
        player.sendMessage(button("← Danh sách NPC", "/lively npc wizard", "Quay lại wizard chính.", Formatting.DARK_AQUA), false);
        line(player, "━━━━━━━━━━━━━━━━━━━━━━━━━━━━", Formatting.GOLD, false);
        return 1;
    }

    private static MutableText button(String label, String command, String hover, Formatting color) {
        return Text.literal(label).setStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover))));
    }

    private static MutableText suggest(String label, String command, String hover, Formatting color) {
        return Text.literal(label).setStyle(Style.EMPTY.withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(hover))));
    }

    private static void row(ServerPlayerEntity player, MutableText... parts) {
        MutableText line = Text.empty();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) line.append(Text.literal("  "));
            line.append(parts[i]);
        }
        player.sendMessage(line, false);
    }

    private static void line(ServerPlayerEntity player, String value, Formatting color, boolean bold) {
        MutableText text = Text.literal(value).formatted(color);
        if (bold) text.formatted(Formatting.BOLD);
        player.sendMessage(text, false);
    }

    private static String shortId(UUID id) { return id.toString().substring(0, 8); }
    private static String round(double value) { return String.format(java.util.Locale.ROOT, "%.1f", value); }
    private static String abbreviate(String value, int max) { return value.length() <= max ? value : value.substring(0, max - 1) + "…"; }
    private static int error(ServerCommandSource source, String message) { source.sendError(Text.literal(message)); return 0; }
}
