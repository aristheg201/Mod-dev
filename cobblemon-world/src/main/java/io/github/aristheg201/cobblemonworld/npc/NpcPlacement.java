package io.github.aristheg201.cobblemonworld.npc;

public record NpcPlacement(
        String id,
        String dimension,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String entityUuid
) {
    public NpcPlacement { pitch = 0; yaw = net.minecraft.util.Mth.wrapDegrees(yaw); }
}
