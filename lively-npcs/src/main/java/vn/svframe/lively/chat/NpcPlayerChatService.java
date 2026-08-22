package vn.svframe.lively.chat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import vn.svframe.lively.model.NpcSnapshot;
import vn.svframe.lively.npc.NpcDefinition;
import vn.svframe.lively.npc.NpcRuntime;
import vn.svframe.lively.persistence.NpcStateRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Reactive local chat for NPCs. One player message can schedule at most one NPC reply.
 * There is no periodic ambient text spam and no fake signed-chat identity.
 */
public final class NpcPlayerChatService {
    private static final double HEAR_RANGE_SQ = 20D * 20D;
    private static final double CASUAL_RANGE_SQ = 9D * 9D;
    private static final long NPC_COOLDOWN = 220L;
    private static final long PLAYER_COOLDOWN = 120L;
    private static final long GLOBAL_COOLDOWN = 35L;
    private static final int MAX_PENDING = 64;

    private record Pending(long dueTick, UUID npcId, UUID playerId, String text) {}
    private record Candidate(NpcDefinition definition, double distanceSq, double score, boolean named) {}

    private final NpcRuntime npcs;
    private final NpcStateRegistry states;
    private final ConcurrentHashMap<UUID, Long> npcCooldown = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> playerCooldown = new ConcurrentHashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private long globalCooldown;

    public NpcPlayerChatService(NpcRuntime npcs, NpcStateRegistry states) {
        this.npcs = npcs;
        this.states = states;
    }

    public void onPlayerChat(ServerPlayerEntity player, String rawMessage) {
        if (player == null || rawMessage == null) return;
        String message = rawMessage.trim();
        if (message.isEmpty() || message.length() > 300) return;
        long tick = player.getServer().getTicks();
        if (tick < playerCooldown.getOrDefault(player.getUuid(), 0L) || tick < globalCooldown) return;

        String world = player.getServerWorld().getRegistryKey().getValue().toString();
        Vec3d playerPos = player.getPos();
        String lower = message.toLowerCase(Locale.ROOT);
        List<Candidate> candidates = npcs.snapshot().values().stream()
                .filter(NpcDefinition::spawned)
                .filter(NpcDefinition::aiEnabled)
                .filter(npc -> world.equals(npcs.worldKey(npc.id()).orElse(npc.world())))
                .map(npc -> candidate(npc, playerPos, lower, tick))
                .filter(candidate -> candidate != null)
                .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparingDouble(Candidate::distanceSq))
                .toList();
        if (candidates.isEmpty()) return;

        Candidate chosen = candidates.getFirst();
        double chance = replyChance(lower, chosen);
        if (ThreadLocalRandom.current().nextDouble() > chance) return;

        String reply = replyFor(chosen.definition(), player, message, lower);
        if (reply == null || reply.isBlank()) return;
        long delay = chosen.named() ? ThreadLocalRandom.current().nextLong(8L, 22L)
                : ThreadLocalRandom.current().nextLong(16L, 38L);
        synchronized (pending) {
            if (pending.size() >= MAX_PENDING) return;
            pending.add(new Pending(tick + delay, chosen.definition().id(), player.getUuid(), reply));
        }
        npcCooldown.put(chosen.definition().id(), tick + NPC_COOLDOWN);
        playerCooldown.put(player.getUuid(), tick + PLAYER_COOLDOWN);
        globalCooldown = tick + GLOBAL_COOLDOWN;
    }

    public void tick(MinecraftServer server, long tick) {
        List<Pending> ready = new ArrayList<>();
        synchronized (pending) {
            for (int i = pending.size() - 1; i >= 0; i--) {
                Pending value = pending.get(i);
                if (value.dueTick() <= tick) ready.add(pending.remove(i));
            }
        }
        ready.stream().sorted(Comparator.comparingLong(Pending::dueTick)).limit(4).forEach(value -> {
            NpcDefinition npc = npcs.get(value.npcId()).orElse(null);
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(value.playerId());
            if (npc == null || player == null || !npc.spawned()) return;
            String world = npcs.worldKey(npc.id()).orElse(npc.world());
            Vec3d position = npcs.position(npc.id()).orElse(new Vec3d(npc.x(), npc.y(), npc.z()));
            if (!player.getServerWorld().getRegistryKey().getValue().toString().equals(world)
                    || player.getPos().squaredDistanceTo(position) > HEAR_RANGE_SQ) return;
            NpcDefinition current = npc.withPosition(world, position.x, position.y, position.z, npc.yaw(), npc.pitch());
            if (NpcChatOutput.sendNearby(server, current, value.text(), 48D)) {
                states.get(npc.id()).ifPresent(state -> state.remember("player_chat_reply",
                        Map.of("player", player.getUuid().toString()), .12D, 1D));
            }
        });
        if (tick % 600L == 0L) {
            npcCooldown.entrySet().removeIf(entry -> entry.getValue() < tick - 1200L);
            playerCooldown.entrySet().removeIf(entry -> entry.getValue() < tick - 1200L);
        }
    }

    public boolean sayNow(MinecraftServer server, UUID npcId, String text) {
        NpcDefinition npc = npcs.get(npcId).orElse(null);
        if (npc == null || !npc.spawned()) return false;
        Vec3d position = npcs.position(npc.id()).orElse(new Vec3d(npc.x(), npc.y(), npc.z()));
        String world = npcs.worldKey(npc.id()).orElse(npc.world());
        return NpcChatOutput.sendNearby(server,
                npc.withPosition(world, position.x, position.y, position.z, npc.yaw(), npc.pitch()), text, 48D);
    }

    private Candidate candidate(NpcDefinition npc, Vec3d playerPos, String message, long tick) {
        if (tick < npcCooldown.getOrDefault(npc.id(), 0L)) return null;
        Vec3d position = npcs.position(npc.id()).orElse(new Vec3d(npc.x(), npc.y(), npc.z()));
        double distanceSq = position.squaredDistanceTo(playerPos);
        if (distanceSq > HEAR_RANGE_SQ) return null;
        String name = npc.name().toLowerCase(Locale.ROOT);
        boolean named = !name.isBlank() && containsName(message, name);
        boolean question = message.contains("?") || startsQuestion(message);
        boolean greeting = containsAny(message, "chào", "hello", " hi", "hi ", "hey", "yo", "alo", "ê ", "ê,");
        if (!named && !question && !greeting && distanceSq > CASUAL_RANGE_SQ) return null;
        double score = named ? 10D : 0D;
        if (question) score += 3D;
        if (greeting) score += 2D;
        score += Math.max(0D, 3D - Math.sqrt(distanceSq) * .2D);
        return new Candidate(npc, distanceSq, score, named);
    }

    private double replyChance(String message, Candidate candidate) {
        if (candidate.named()) return .92D;
        if (message.contains("?") || startsQuestion(message)) return candidate.distanceSq() <= CASUAL_RANGE_SQ ? .48D : .25D;
        if (containsAny(message, "chào", "hello", " hi", "hi ", "hey", "yo", "alo")) return .38D;
        return .08D;
    }

    private String replyFor(NpcDefinition npc, ServerPlayerEntity player, String original, String message) {
        NpcSnapshot state = states.snapshot(npc.id()).orElse(null);
        double friendly = state == null ? .5D : state.trait("friendly");
        double brave = state == null ? .5D : state.trait("brave");
        String role = npc.role() == null ? "" : npc.role().toLowerCase(Locale.ROOT);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (containsAny(message, "chào", "hello", " hi", "hi ", "hey", "yo", "alo")) {
            return pick(random, friendly > .55D ? List.of("chào.", "ừ, chào.", "có gì không?", "yo.")
                    : List.of("ừ.", "chào.", "gì đấy?"));
        }
        if (containsAny(message, "ở đâu", "where", "nhà", "home")) {
            String home = npc.metadata().get("home.structure");
            String work = npc.metadata().get("work.structure");
            if (containsAny(message, "làm", "work", "shop", "cửa hàng") && work != null && !work.isBlank()) return "tôi làm ở " + work + ".";
            if (home != null && !home.isBlank()) return "tôi thường ở " + home + ".";
            if (work != null && !work.isBlank()) return "thường thì tôi ở " + work + ".";
            return "không cố định lắm.";
        }
        if (containsAny(message, "đấu", "battle", "fight", "đánh nhau")) {
            return brave > .62D ? pick(random, List.of("muốn đấu thì lại đây.", "được, thử xem.", "tùy, tôi không ngại."))
                    : pick(random, List.of("không rảnh.", "thôi, bỏ đi.", "tìm người khác đi."));
        }
        if (containsAny(message, "mua", "bán", "shop", "trade", "giá")) {
            if (containsAny(role, "merchant", "shop", "vendor", "seller", "trader")) return pick(random, List.of("cần mua gì?", "xem hàng đi.", "có tiền thì nói chuyện tiếp."));
            return "hỏi nhầm người rồi.";
        }
        if (containsAny(message, "giúp", "help", "cứu")) return friendly > .5D ? "cần gì?" : "chuyện gì?";
        if (message.contains("?")) return pick(random, List.of("không chắc.", "có thể.", "hỏi cụ thể hơn đi.", "tôi cũng đang nghĩ vụ đó."));
        return pick(random, List.of("gì đấy?", "hử?", "tôi nghe.", "nói đi."));
    }

    private static boolean startsQuestion(String message) {
        String value = message.stripLeading();
        return containsAny(value, "sao ", "tại sao", "vì sao", "ai ", "gì ", "đâu ", "khi nào", "how ", "why ", "what ", "where ", "who ");
    }

    private static boolean containsName(String message, String name) {
        if (message.contains(name)) return true;
        String[] parts = name.split("\\s+");
        return parts.length > 1 && parts[0].length() >= 4 && message.contains(parts[0]);
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String pick(ThreadLocalRandom random, List<String> values) {
        return values.get(random.nextInt(values.size()));
    }
}
