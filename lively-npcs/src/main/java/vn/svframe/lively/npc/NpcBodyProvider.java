package vn.svframe.lively.npc;

@FunctionalInterface
public interface NpcBodyProvider {
    NpcBody create(NpcDefinition definition);
}
