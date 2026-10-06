package io.github.aristheg201.cobblemonworld.item;

import io.github.aristheg201.cobblemonworld.progression.LevelCapService;
import io.github.aristheg201.cobblemonworld.progression.PlayerProgression;
import io.github.aristheg201.cobblemonworld.progression.ProgressionStore;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TrainerPhoneItem extends Item {
    public TrainerPhoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            PlayerProgression progression = ProgressionStore.INSTANCE.getOrCreate(serverPlayer.getUUID());
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.cobblemonworld.phone.bootstrap",
                    LevelCapService.getCap(serverPlayer),
                    progression.currentStory));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
