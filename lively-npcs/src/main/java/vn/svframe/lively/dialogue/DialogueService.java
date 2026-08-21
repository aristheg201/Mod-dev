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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/** Chat-based conversation surface isolated from normal player chat. */
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
            if (session.expired()) {
                close(sender, "Hội thoại đã kết thúc.");
                return true;
            }
            String raw = message.getSignedContent();
            if (!allowInput(sender.getUuid())) return false;
            handleFreeText(sender, session, raw);
            return false;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("livelydialogue")
                    .then(literal("choose")
                            .then(argument("session", StringArgumentType.word())
                                    .then(argument("nonce", LongArgumentType.longArg(1L))
                                            .then(argument("choice", IntegerArgumentType.integer(0, 64))
                                                    .executes(ctx -> {
                                                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                                                        if (player == null) return 0;
                                                        return choose(
                                                                player,
                                                                UUID.fromString(StringArgumentType.getString(ctx, "session")),
                                                                LongArgumentType.getLong(ctx, "nonce"),
                                                                IntegerArgumentType.getInteger(ctx, "choice")) ? 1 : 0;
                                                    })))))
                    .then(literal("leave").executes(ctx -> {
                        ServerPlayerEntity player = ctx.getSource().getPlayer();
                        if (player == null) return 0;
                        close(player, "Bạn rời cuộc trò chuyện.");
                        return 1;
                    })));
        });
    }

    public DialogueSession start(ServerPlayerEntity player, UUID npcId, String npcName) {
        DialogueSession session = new DialogueSession(
                player.getUuid(), npcId, npcName, DialogueSession.Mode.HYBRID, 12,
                Instant.now().plus(SESSION_TTL));
        session.setChoices(defaultChoices());
        sessions.put(player.getUuid(), session);
        session.record(false, "Có chuyện gì?");
        render(player, session, "Có chuyện gì?");
        return session;
    }

    public Optional<DialogueSession> session(UUID playerId) {
        DialogueSession session = sessions.get(playerId);
        if (session != null && session.expired()) {
            sessions.remove(playerId, session);
            return Optional.empty();
        }
        return Optional.ofNullable(session);
    }

    private boolean choose(ServerPlayerEntity player, UUID sessionId, long nonce, int choiceId) {
        DialogueSession session = sessions.get(player.getUuid());
        if (session == null || !session.sessionId().equals(sessionId) || session.expired()) return false;
        DialogueSession.Choice choice = session.choices().stream()
                .filter(c -> c.id() == choiceId).findFirst().orElse(null);
        if (choice == null || !session.consumeChoice(nonce, choiceId)) return false;

        if (choice.semanticAction().equals("leave")) {
            close(player, "Bạn rời cuộc trò chuyện.");
            return true;
        }

        session.record(true, choice.label());
        String reply = switch (choice.semanticAction()) {
            case "ask_work" -> "Việc thì lúc nào cũng có. Vấn đề là việc nào đáng làm.";
            case "ask_problem" -> "Có vài chuyện chưa ổn. Tôi đang tự cân nhắc xem có nên nhờ cậu không.";
            case "challenge" -> "Muốn đấu thì nói thẳng thế nghe còn tử tế hơn vòng vo.";
            case "free_text" -> "Cứ nói đi. Tôi đang nghe.";
            default -> "Tôi hiểu.";
        };
        session.record(false, reply);
        session.setChoices(defaultChoices());
        render(player, session, reply);
        return true;
    }

    private void handleFreeText(ServerPlayerEntity player, DialogueSession session, String raw) {
        if (raw.length() > 320) {
            player.sendMessage(Text.literal("[Lively] Câu đó dài quá để coi là một lượt hội thoại."), false);
            return;
        }
        session.record(true, raw);
        NluEngine.Meaning meaning = nlu.parse(raw);
        String reply = replyFor(meaning);
        session.record(false, reply);
        session.setChoices(defaultChoices());
        render(player, session, reply);
    }

    private String replyFor(NluEngine.Meaning meaning) {
        return switch (meaning.intent()) {
            case ASSERT_INFORMATION -> {
                String subject = meaning.slots().getOrDefault("subject", "chuyện đó");
                String location = meaning.slots().get("location");
                yield location == null
                        ? "Tôi sẽ nhớ chuyện cậu vừa nói về " + subject + "."
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
        player.sendMessage(Text.literal(" " + line).formatted(Formatting.WHITE), false);
        player.sendMessage(Text.literal(" "), false);

        for (DialogueSession.Choice choice : session.choices()) {
            String command = "/livelydialogue choose " + session.sessionId() + " " + session.nonce() + " " + choice.id();
            MutableText option = Text.literal(" ◆ " + choice.label())
                    .setStyle(Style.EMPTY.withColor(Formatting.AQUA)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
            player.sendMessage(option, false);
        }
        MutableText leave = Text.literal(" ✕ Rời đi")
                .setStyle(Style.EMPTY.withColor(Formatting.GRAY)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/livelydialogue leave")));
        player.sendMessage(leave, false);
        player.sendMessage(Text.literal("━━━━━━━━━━━━━━━━━━━━━━━━━━━━").formatted(Formatting.DARK_GRAY), false);
    }

    private void close(ServerPlayerEntity player, String message) {
        sessions.remove(player.getUuid());
        lastInputMillis.remove(player.getUuid());
        player.sendMessage(Text.literal("[Lively] " + message).formatted(Formatting.GRAY), false);
    }

    private boolean allowInput(UUID playerId) {
        long now = System.currentTimeMillis();
        Long previous = lastInputMillis.put(playerId, now);
        return previous == null || now - previous >= INPUT_COOLDOWN_MS;
    }

    private static List<DialogueSession.Choice> defaultChoices() {
        return List.of(
                new DialogueSession.Choice(1, "Có việc gì không?", "ask_problem"),
                new DialogueSession.Choice(2, "Ông đang làm gì?", "ask_work"),
                new DialogueSession.Choice(3, "Thách đấu", "challenge"),
                new DialogueSession.Choice(4, "Nói điều khác...", "free_text")
        );
    }
}
