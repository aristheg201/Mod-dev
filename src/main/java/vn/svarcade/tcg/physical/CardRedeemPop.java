package vn.svarcade.tcg.physical;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server-only notification: there is deliberately no C2S redeem payload. */
public record CardRedeemPop(ItemStack stack) implements CustomPayload {
    public static final Id<CardRedeemPop> ID=new Id<>(Identifier.of("svarcade_tcg","card_redeem_pop"));
    public static final PacketCodec<RegistryByteBuf,CardRedeemPop> CODEC=ItemStack.PACKET_CODEC.xmap(CardRedeemPop::new,CardRedeemPop::stack);
    public Id<? extends CustomPayload> getId(){return ID;}
}
