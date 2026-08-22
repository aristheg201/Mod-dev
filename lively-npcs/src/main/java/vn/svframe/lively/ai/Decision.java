package vn.svframe.lively.ai;

import java.util.UUID;

public record Decision(
        UUID npcId,
        long npcRevision,
        long worldRevision,
        Goal goal,
        AiAction action,
        double score
) {}
