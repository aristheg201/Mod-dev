package io.github.aristheg201.cobblemonworld.story;

import io.github.aristheg201.cobblemonworld.network.CWorldNetworking;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.minecraft.server.level.ServerPlayer;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class DialogueService {
    public record Turn(String contact, String node, int choice, String playerName, boolean player, String legacyText) {}
    private DialogueService() {}
    public static String contactName(PlayerProgression p, String id) {
        if (id.equals("mysterious") && p.storyFlags.contains("toba_identity_revealed")) return "TOBA";
        var contact = ContentRegistry.INSTANCE.contact(id);
        return contact == null ? "" : contact.displayName();
    }
    public static ContentRegistry.ResponseChoice choice(ContentRegistry.MessageNode node, int index) {
        if (node.choices() != null && index >= 0 && index < node.choices().length) return node.choices()[index];
        if (node.responses() == null || index < 0 || index >= node.responses().length) return null;
        String[] raw = node.responses()[index].split("\u001f", -1);
        return new ContentRegistry.ResponseChoice(raw[0], raw.length > 1 ? raw[1] : "", raw.length > 2 ? raw[2] : node.questUnlock(), raw.length > 3 ? raw[3] : node.setFlag());
    }
    private static int choiceCount(ContentRegistry.MessageNode node) {
        return node.choices() != null ? node.choices().length : node.responses() == null ? 0 : node.responses().length;
    }
    public static boolean respond(ServerPlayer player, String contact, String nodeId, int index) {
        var p = ProgressionStore.INSTANCE.getOrCreate(player.getUUID());
        String key = contact + ":" + nodeId;
        var node = ContentRegistry.INSTANCE.message(contact, nodeId);
        if (node == null || (!p.unreadMessages.contains(key) && !p.readMessages.contains(key))) return false;
        if (p.dialogueHistory.stream().anyMatch(t -> t.player() && contact.equals(t.contact()) && nodeId.equals(t.node()))) return false;
        var choice = choice(node, index);
        if (choice == null) return false;
        // Store transcript before effects so re-entrant triggers preserve NPC/player/NPC order.
        ensureNpcTurn(p, contact, nodeId);
        p.unreadMessages.remove(key); p.readMessages.add(key);
        p.dialogueHistory.add(new Turn(contact, nodeId, index, player.getGameProfile().getName(), true, ""));
        if(nodeId.startsWith("follow_") && !nodeId.startsWith("follow_answer_") && index>=0 && index<3)
            p.narrative.personality.merge(new String[]{"serious","sarcastic","polite"}[index],1,(a,b)->Math.min(1000,a+b));
        ProgressionStore.INSTANCE.save();
        String quest = first(choice.questUnlock(), node.questUnlock());
        String flag = first(choice.setFlag(), node.setFlag());
        if (!quest.isBlank()) CampaignService.activateQuest(player, quest);
        if (!flag.isBlank()) CampaignService.setFlag(player, flag);
        if (choice.nextNode() != null && !choice.nextNode().isBlank()) CampaignService.queueMessage(player, contact, choice.nextNode());
        ProgressionStore.INSTANCE.save();
        return true;
    }
    private static String first(String choice, String fallback) { return choice != null && !choice.isBlank() ? choice : fallback == null ? "" : fallback; }
    public static void ensureNpcTurn(PlayerProgression p, String contact, String node) {
        if (p.dialogueHistory.stream().noneMatch(t -> !t.player() && contact.equals(t.contact()) && node.equals(t.node())))
            p.dialogueHistory.add(new Turn(contact, node, -1, "", false, ""));
    }
    public static List<CWorldNetworking.MessageView> views(PlayerProgression p) {
        // Preserve old direct-patch replies, including actual player names. New saves use typed turns.
        for (String key : p.readMessages) {
            if (key.startsWith("@reply:")) {
                if (p.dialogueHistory.stream().anyMatch(t -> key.equals(t.node()))) continue;
                try {
                    String[] parts = key.substring(7).split(":", -1);
                    if (parts.length == 3) p.dialogueHistory.add(new Turn(decode(parts[0]), key, -1, decode(parts[1]), true, decode(parts[2])));
                } catch (IllegalArgumentException e) {
                    io.github.aristheg201.cobblemonworld.CobblemonWorldMod.LOGGER.warn("Skipped malformed legacy dialogue reply");
                }
            } else migrateNpc(p, key);
        }
        for (String key : p.unreadMessages) migrateNpc(p, key);
        List<CWorldNetworking.MessageView> output = new ArrayList<>();
        for (Turn t : p.dialogueHistory) {
            var contact = ContentRegistry.INSTANCE.contact(t.contact());
            var node = ContentRegistry.INSTANCE.message(t.contact(), t.node());
            if (t.player()) {
                String text = t.legacyText();
                if (node != null && choice(node, t.choice()) != null) text = choice(node, t.choice()).text();
                if (text != null && !text.isBlank()) output.add(new CWorldNetworking.MessageView("@player:" + output.size(), t.contact(), t.playerName(), text, List.of(), false));
            } else if (contact != null && node != null) {
                String key = t.contact() + ":" + t.node();
                boolean answered = p.dialogueHistory.stream().anyMatch(turn -> turn.player() && t.contact().equals(turn.contact()) && t.node().equals(turn.node()));
                List<String> options = new ArrayList<>();
                if (!answered) for (int i = 0; i < choiceCount(node); i++) options.add(choice(node, i).text());
                output.add(new CWorldNetworking.MessageView(key, t.contact(), contactName(p, t.contact()), node.text(), List.copyOf(options), p.unreadMessages.contains(key)));
            }
        }
        return List.copyOf(output);
    }
    private static void migrateNpc(PlayerProgression p, String key) {
        int split = key.indexOf(':');
        if (split < 1) return;
        String contact = key.substring(0, split), node = key.substring(split + 1);
        if (ContentRegistry.INSTANCE.message(contact, node) != null) ensureNpcTurn(p, contact, node);
    }
    private static String decode(String s) { return new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8); }
}
