package vn.svframe.lively.dialogue;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import vn.svframe.lively.api.LivelyApi;
import vn.svframe.lively.model.NpcState;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Chat is the rendering surface. Active dialogue input is captured and never broadcast globally. */
public final class DialogueService {
    private static final Duration SESSION_TTL = Duration.ofMinutes(3);
    private static final long INPUT_COOLDOWN_MS = 180L;

    private final ConcurrentHashMap<UUID, DialogueSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastInputMillis = new ConcurrentHashMap<>();
    private final NluEngine nlu = new NluEngine();

    public void install() {
        LivelyApi.installDialogueService(this);
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            DialogueSession session = sessions.get(sender.getUuid());
            if (session == null) return true;
            if (session.expired()) { close(sender, "Hội thoại đã kết thúc."); return true; }
            if (!allowInput(sender.getUuid())) return false;
            handleFreeText(sender, session, message.getSignedContent());
            return false;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                literal("livelydialogue")
                        .then(literal("choose")
                                .then(argument("session", StringArgumentType.word())
                                        .then(argument("nonce", LongArgumentType.longArg(1L))
                                                .then(argument("choice", IntegerArgumentType.integer(0, 64))
                                                        .executes(ctx -> {
                                                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                            if (player == null) return 0;
                                                            return choose(player, UUID.fromString(StringArgumentType.getString(ctx, "session")),
                                                                    LongArgumentType.getLong(ctx, "nonce"),
                                                                    IntegerArgumentType.getInteger(ctx, "choice")) ? 1 : 0;
                                                        })))))
                        .then(literal("leave").executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) return 0;
                            close(player, "Bạn rời cuộc trò chuyện."); return 1;
                        }))));
    }

    public DialogueSession start(ServerPlayerEntity player, UUID npcId, String npcName) {
        return start(player, npcId, npcName, "npc");
    }

    public DialogueSession start(ServerPlayerEntity player, UUID npcId, String npcName, String role) {
        if (LivelyApi.states() != null) {
            NpcState state = LivelyApi.states().getOrCreate(npcId, npcName, role);
            state.remember("conversation_started", Map.of("player", player.getUuid().toString()), 0.20D, 1D);
        }
        DialogueSession session = new DialogueSession(player.getUuid(), npcId, npcName, DialogueSession.Mode.HYBRID, 12,
                Instant.now().plus(SESSION_TTL));
        session.setChoices(defaultChoices()); sessions.put(player.getUuid(), session);
        session.record(false, "Có chuyện gì?"); render(player, session, "Có chuyện gì?"); return session;
    }

    public Optional<DialogueSession> session(UUID playerId) {
        DialogueSession session = sessions.get(playerId);
        if (session != null && session.expired()) { sessions.remove(playerId, session); return Optional.empty(); }
        return Optional.ofNullable(session);
    }

    private boolean choose(ServerPlayerEntity player, UUID sessionId, long nonce, int choiceId) {
        DialogueSession session = sessions.get(player.getUuid());
        if (session == null || !session.sessionId().equals(sessionId) || session.expired()) return false;
        DialogueSession.Choice choice = session.choices().stream().filter(c -> c.id() == choiceId).findFirst().orElse(null);
        if (choice == null || !session.consumeChoice(nonce, choiceId)) return false;
        if (choice.semanticAction().equals("leave")) { close(player, "Bạn rời cuộc trò chuyện."); return true; }

        session.record(true, choice.label());
        rememberChoice(player, session, choice);
        String reply = switch (choice.semanticAction()) {
            case "ask_work" -> "Việc thì lúc nào cũng có. Vấn đề là việc nào đáng làm.";
            case "ask_problem" -> "Có vài chuyện chưa ổn. Tôi đang tự cân nhắc xem có nên nhờ cậu không.";
            case "challenge" -> "Muốn đấu thì nói thẳng thế nghe còn tử tế hơn vòng vo.";
            case "free_text" -> "Cứ nói đi. Tôi đang nghe.";
            default -> "Tôi hiểu.";
        };
        session.record(false, reply); session.setChoices(defaultChoices()); render(player, session, reply); return true;
    }

    private void handleFreeText(ServerPlayerEntity player, DialogueSession session, String raw) {
        if (raw.length() > 320) { player.sendMessage(Text.literal("[Lively] Câu đó dài quá để coi là một lượt hội thoại."), false); return; }
        session.record(true, raw);
        NluEngine.Meaning meaning = nlu.parse(raw);
        updateNpcMemory(player, session, raw, meaning);
        String reply = replyFor(meaning);
        session.record(false, reply); session.setChoices(defaultChoices()); render(player, session, reply);
    }

    private void updateNpcMemory(ServerPlayerEntity player, DialogueSession session, String raw, NluEngine.Meaning meaning) {
        if (LivelyApi.states() == null) return;
        NpcState state = LivelyApi.states().get(session.npcId()).orElseGet(() ->
                LivelyApi.states().getOrCreate(session.npcId(), session.npcName(), "npc"));
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("player", player.getUuid().toString()); facts.put("intent", meaning.intent().name());
        facts.put("utterance", raw); facts.putAll(meaning.slots());
        double importance = switch (meaning.intent()) {
            case ASSERT_INFORMATION, OFFER_HELP, CHALLENGE -> 0.65D;
            case TRADE, ASK_INFORMATION -> 0.45D;
            default -> 0.25D;
        };
        state.remember("player_dialogue", facts, importance, meaning.confidence());

        if (meaning.intent() == NluEngine.Intent.ASSERT_INFORMATION) {
            String subject = meaning.slots().getOrDefault("subject", "claim");
            String location = meaning.slots().get("location");
            String key = location == null ? "reported_claim." + normalizeKey(subject) : "reported_location." + normalizeKey(subject);
            String value = location == null ? raw : location;
            double trust = state.snapshot(1).relationship(player.getUuid()).trust();
            state.updateBelief(key, value, Math.max(0.25D, Math.min(0.95D, 0.55D + trust * 0.25D)), player.getUuid());
        } else if (meaning.intent() == NluEngine.Intent.OFFER_HELP) {
            state.updateRelationship(player.getUuid(), 0.02D, 0.03D, -0.01D, 0D);
        }
    }

    private void rememberChoice(ServerPlayerEntity player, DialogueSession session, DialogueSession.Choice choice) {
        if (LivelyApi.states() == null) return;
        LivelyApi.states().get(session.npcId()).ifPresent(state -> state.remember("dialogue_choice",
                Map.of("player", player.getUuid().toString(), "choice", choice.semanticAction()), 0.25D, 1D));
    }

    private String replyFor(NluEngine.Meaning meaning) {
        return switch (meaning.intent()) {
            case ASSERT_INFORMATION -> {
                String subject = meaning.slots().getOrDefault("subject", "chuyện đó"); String location = meaning.slots().get("location");
                yield location == null ? "Tôi sẽ nhớ chuyện cậu vừa nói về " + subject + "."
                        : "Được. Tôi sẽ ghi nhớ rằng " + subject + " có thể liên quan tới " + location + ".";
            }
            case ASK_INFORMATION -> "Tôi chỉ nói những gì mình thực sự biết. Tin đồn thì để dân chợ lo.";
            case OFFER_HELP -> "Nếu cậu nghiêm túc thì tốt. Tôi sẽ nói khi có việc phù hợp.";
            case CHALLENGE -> "Được. Nhưng chuyện đánh nhau sẽ do luật trận đấu quyết định, không phải cái miệng của tôi.";
            case TRADE -> "Nếu có giao dịch, giá và hàng sẽ được hệ thống kiểm tra. Tôi không tự bịa kho hàng.";
            case GREETING -> "Chào. Có chuyện gì?";
            case GOODBYE -> "Đi cẩn thận.";
            case UNKNOWN -> "Tôi chưa hiểu ý đó đủ rõ. Nói cụ thể hơn một chút.";
        };
    }

    private void render(ServerPlayerEntity player, DialogueSession session, String line) {
        player.sendMessage(Text.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").formatted(Formatting.DARK_GRAY), false);
        player.sendMessage(Text.literal(" " + session.npcName()).formatted(Formatting.GOLD, Formatting.BOLD), false);
        player.sendMessage(Text.literal(" " + line).formatted(Formatting.WHITE), false); player.sendMessage(Text.literal(" "), false);
        for (DialogueSession.Choice choice : session.choices()) {
            String command = "/livelydialogue choose " + session.sessionId() + " " + session.nonce() + " " + choice.id();
            MutableText option = Text.literal(" ◆ " + choice.label()).setStyle(Style.EMPTY.withColor(Formatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
            player.sendMessage(option, false);
        }
        player.sendMessage(Text.literal(" ✕ Rời đi").setStyle(Style.EMPTY.withColor(Formatting.GRAY)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/livelydialogue leave"))), false);
        player.sendMessage(Text.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").formatted(Formatting.DARK_GRAY), false);
    }

    private void close(ServerPlayerEntity player, String message) {
        sessions.remove(player.getUuid()); lastInputMillis.remove(player.getUuid());
        player.sendMessage(Text.literal("[Lively] " + message).formatted(Formatting.GRAY), false);
    }

    private boolean allowInput(UUID playerId) {
        long now = System.currentTimeMillis(); Long previous = lastInputMillis.put(playerId, now);
        return previous == null || now - previous >= INPUT_COOLDOWN_MS;
    }

    private static String normalizeKey(String value) { return value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_:-]+", "_"); }
    private static List<DialogueSession.Choice> defaultChoices() {
        return List.of(new DialogueSession.Choice(1, "Có việc gì không?", "ask_problem"),
                new DialogueSession.Choice(2, "Ông đang làm gì?", "ask_work"),
                new DialogueSession.Choice(3, "Thách đấu", "challenge"),
                new DialogueSession.Choice(4, "Nói điều khác...", "free_text"));
    }
}
