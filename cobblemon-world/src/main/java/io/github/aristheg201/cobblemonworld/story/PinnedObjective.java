package io.github.aristheg201.cobblemonworld.story;

/** Save identity independently of wording, locale and current NPC placement. */
public record PinnedObjective(String questId, String objectiveId, String targetType, String npcId,
                              String dimension, String textKey) {}
