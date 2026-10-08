package io.github.aristheg201.cobblemonworld.npc;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import io.github.aristheg201.cobblemonworld.shop.ShopService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class NpcService {
    private NpcService() {}
    public static boolean dispatch(NPCEntity npc, ServerPlayer player, String id) {
        if (ShopService.open(player, npc, id)) return true;
        if (!"daycare_mira".equals(id)) return false;
        if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) {
            player.sendSystemMessage(Component.translatable("service.cobblemonworld.heal.battle"));
            return true;
        }
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int healed = 0;
        for (var pokemon : party) { pokemon.heal(); healed++; }
        player.sendSystemMessage(Component.translatable(healed == 0 ? "service.cobblemonworld.heal.empty" : "service.cobblemonworld.heal.success"));
        return true;
    }
}
