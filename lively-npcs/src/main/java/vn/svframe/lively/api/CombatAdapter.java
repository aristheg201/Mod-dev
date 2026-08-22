package vn.svframe.lively.api;

import vn.svframe.lively.combat.CombatCortex;

import java.util.UUID;

/** External combat systems translate their battle into this stable core contract. */
public interface CombatAdapter {
    String id();
    boolean supports(Object battleHandle);
    CombatCortex.CombatState snapshot(Object battleHandle, UUID npcId);
    CombatCortex.Simulator simulator(Object battleHandle, UUID npcId);
    void apply(Object battleHandle, UUID npcId, CombatCortex.Decision decision);
}
