package vn.svframe.lively.chat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import vn.svframe.lively.npc.NpcDefinition;

/** Plain chat-looking output. This intentionally does not forge signed player chat packets. */
public final class NpcChatOutput {
    private NpcChatOutput() {}

    public static boolean sendNearby(MinecraftServer server, NpcDefinition npc, String message, double range) {
        if (server == null || npc == null) return false;
        String clean = sanitize(message);
        if (clean.isEmpty()) return false;
        double rangeSq = Math.max(8D, Math.min(128D, range));
        rangeSq *= rangeSq;
        Text line = Text.literal("<" + npc.name() + "> " + clean);
        boolean sent = false;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!player.getServerWorld().getRegistryKey().getValue().toString().equals(npc.world())) continue;
            if (player.getPos().squaredDistanceTo(new net.minecraft.util.math.Vec3d(npc.x(), npc.y(), npc.z())) > rangeSq) continue;
            player.sendMessage(line, false);
            sent = true;
        }
        return sent;
    }

    private static String sanitize(String value) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').replaceAll("[\\p{Cntrl}&&[^\\t]]", " ").trim();
        if (clean.length() > 180) clean = clean.substring(0, 180);
        return clean;
    }
}
