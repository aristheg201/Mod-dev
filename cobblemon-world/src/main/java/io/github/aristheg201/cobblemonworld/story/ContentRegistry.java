package io.github.aristheg201.cobblemonworld.story;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.aristheg201.cobblemonworld.CobblemonWorldMod;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ContentRegistry {
    private static final Gson GSON = new GsonBuilder().create();
    public static final ContentRegistry INSTANCE = new ContentRegistry();

    private static final String[] CONTACTS = {
            "mysterious", "mara_voss", "dr_orin", "rook", "selene_kade", "aurelia"
    };
    private static final String[] QUESTS = {
            "first_signal", "voss_echo", "orin_archive", "rook_black_card", "selene_stalemate", "aurelia_below"
    };
    private static final String[] CHAPTERS = {
            "prologue", "chapter_01_signal", "chapter_02_under_mountain", "chapter_03_house_of_cards",
            "chapter_04_kings_gambit", "chapter_05_league_fault", "chapter_06_seventh_lock",
            "chapter_07_false_victory", "battle_tower", "royal_league", "school_of_wolf", "chapter_08_last_person"
    };

    private final Map<String, ContactDefinition> contacts = new LinkedHashMap<>();
    private final Map<String, QuestDefinition> quests = new LinkedHashMap<>();
    private final Map<String, StoryChapterDefinition> chapters = new LinkedHashMap<>();

    private ContentRegistry() {}

    public void loadBuiltIns() {
        contacts.clear();
        quests.clear();
        chapters.clear();

        for (String id : CONTACTS) {
            load("data/cobblemonworld/contacts/" + id + ".json", ContactDefinition.class, d -> contacts.put(d.id(), d));
        }
        for (String id : QUESTS) {
            load("data/cobblemonworld/quests/" + id + ".json", QuestDefinition.class, d -> quests.put(d.id(), d));
        }
        for (String id : CHAPTERS) {
            load("data/cobblemonworld/story/" + id + ".json", StoryChapterDefinition.class, d -> chapters.put(d.id(), d));
        }

        CobblemonWorldMod.LOGGER.info("Loaded {} contacts, {} quests and {} story chapters.",
                contacts.size(), quests.size(), chapters.size());
    }

    private <T> void load(String path, Class<T> type, java.util.function.Consumer<T> sink) {
        try (var stream = ContentRegistry.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing built-in content: " + path);
            sink.accept(GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), type));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + path, e);
        }
    }

    public ContactDefinition contact(String id) { return contacts.get(id); }
    public QuestDefinition quest(String id) { return quests.get(id); }
    public StoryChapterDefinition chapter(String id) { return chapters.get(id); }
    public Collection<ContactDefinition> contacts() { return contacts.values(); }

    public MessageNode message(String contactId, String messageId) {
        ContactDefinition contact = contact(contactId);
        if (contact == null || contact.messages() == null) return null;
        for (MessageNode node : contact.messages()) {
            if (messageId.equals(node.id())) return node;
        }
        return null;
    }

    public record ContactDefinition(String id, String displayName, String icon, MessageNode[] messages) {}
    public record MessageNode(String id, String triggerFlag, String text, String[] responses, String questUnlock, String setFlag, ResponseChoice[] choices) {}
    public record ResponseChoice(String text, String nextNode, String questUnlock, String setFlag) {}
    public record QuestDefinition(String id, String title, String giver, String description, Objective[] objectives, String completionFlag) {}
    public record Objective(String id, String type, String target, int amount) {}
    public record StoryChapterDefinition(
            String id,
            String title,
            String objective,
            String[] requiredFlags,
            String[] completionFlags,
            int levelCapOnComplete,
            String nextChapter,
            String badge
    ) {}
}
