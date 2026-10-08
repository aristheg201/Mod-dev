package io.github.aristheg201.cobblemonworld.npc;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import io.github.aristheg201.cobblemonworld.shop.ShopService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class NpcService {
    private NpcService() {}
    private static void rememberService(ServerPlayer player,String id) {
        var definition=NpcDefinitionRegistry.INSTANCE.get(id);if(definition==null)return;
        if(definition.flagsOnInteract()!=null)for(String flag:definition.flagsOnInteract())io.github.aristheg201.cobblemonworld.story.CampaignService.setFlag(player,flag);
        if(definition.interactionFlag()!=null && !definition.interactionFlag().isBlank())io.github.aristheg201.cobblemonworld.story.CampaignService.setFlag(player,definition.interactionFlag());
    }
    public static boolean dispatch(NPCEntity npc, ServerPlayer player, String id) {
        if (ShopService.open(player, npc, id)) { rememberService(player,id); return true; }
        if (!"daycare_mira".equals(id)) return false;
        if (Cobblemon.INSTANCE.getBattleRegistry().getBattleByParticipatingPlayer(player) != null) {
            player.sendSystemMessage(Component.translatable("service.cobblemonworld.heal.battle"));
            return true;
        }
        var party = Cobblemon.INSTANCE.getStorage().getParty(player);
        int healed = 0;
        for (var pokemon : party) { pokemon.heal(); healed++; }
        io.github.aristheg201.cobblemonworld.network.CWorldNetworking.toast(player, "story", "narrative.cobblemonworld.conversation", healed == 0 ? "service.cobblemonworld.heal.empty" : "service.cobblemonworld.heal.success");
        if (healed > 0) rememberService(player,id);
        if (healed > 0) io.github.aristheg201.cobblemonworld.narrative.NarrativeEngine.event(player, "heal", id);
        return true;
    }
}
