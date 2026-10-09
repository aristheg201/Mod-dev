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

        var narrative = io.github.aristheg201.cobblemonworld.narrative.NarrativeRegistry.INSTANCE;
        for (var actor : narrative.data.actors()) {
            var messages = new java.util.ArrayList<MessageNode>();
            var old = contacts.get(actor.id());
            if (old != null && old.messages() != null) messages.addAll(java.util.List.of(old.messages()));
            for (var stage : narrative.data.campaign()) if (stage.target().equals(actor.id())) {
                var scene = narrative.scenes.get(stage.scene());
                var facts = narrative.node(scene, "facts");
                if(facts==null)facts=narrative.node(scene,"accepted");
                if(stage.type().equals("battle")) {
                    var outcome=narrative.scenes.get("outcome."+actor.id()+".win");
                    if(outcome!=null)facts=narrative.node(outcome,outcome.start());
                }
                if (facts != null) {
                    var replies = new ResponseChoice[3];
                    var opening = narrative.node(scene, scene.start());
                    var question=java.util.Arrays.stream(opening.choices()).filter(c->"ask".equals(c.id())).findFirst().orElse(null);
                    var answer=narrative.node(scene,"answer");
                    String[] texts={question==null?"narrative.phone.serious":question.text(),"narrative.phone.read","narrative.phone.later"};
                    String[] answers={answer==null?facts.text():answer.text(),"narrative.phone.read_ack","narrative.phone.later_ack"};
                    for (int i=0;i<3;i++) {
                        String branchId="follow_answer_"+stage.id()+"_"+i;
                        replies[i]=new ResponseChoice(texts[i],branchId,"","phone_reply_"+stage.id()+"_"+i);
                        messages.add(new MessageNode(branchId,"",answers[i],new String[0],"","",new ResponseChoice[0]));
                    }
                    messages.add(new MessageNode("follow_"+stage.id(),"narrative_stage_"+stage.id(),facts.text(),
                            texts,"","phone_answer_"+stage.id(),replies));
                    // Readable legacy destination from the first narrative prototype.
                    messages.add(new MessageNode("follow_answer_"+stage.id(),"","narrative.actor."+actor.id()+".greeting",new String[0],"","",new ResponseChoice[0]));
                }
            }
            contacts.put(actor.id(), new ContactDefinition(actor.id(), actor.name(), old == null ? "trainer" : old.icon(), messages.toArray(MessageNode[]::new)));
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
