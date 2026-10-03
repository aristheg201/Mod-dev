package vn.svarcade.tcg.physical;

import net.minecraft.component.ComponentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.world.World;
import java.util.List;

public final class CardItems {
    public static final ComponentType<PhysicalCardData> DATA=Registry.register(Registries.DATA_COMPONENT_TYPE,
        Identifier.of("svarcade_tcg","physical_card_data"),ComponentType.<PhysicalCardData>builder().codec(PhysicalCardData.CODEC).packetCodec(PhysicalCardData.PACKET_CODEC).build());
    public static final Item PHYSICAL_CARD=Registry.register(Registries.ITEM,Identifier.of("svarcade_tcg","physical_card"),new PhysicalItem());
    public static final Item BLANK_CARD=Registry.register(Registries.ITEM,Identifier.of("svarcade_tcg","blank_card"),new BlankItem());
    public static void initialize(){net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.register((player,world,hand,target,hit)->{
        if(!player.getStackInHand(hand).isOf(BLANK_CARD))return ActionResult.PASS;
        if(world.isClient)return ActionResult.SUCCESS;
        if(!(target instanceof LivingEntity living))return ActionResult.FAIL;
        return BlankCapture.attempt((ServerPlayerEntity)player,living,hand)?ActionResult.SUCCESS:ActionResult.FAIL;
    });}
    public static ItemStack physical(PhysicalCardData data){data.validate();ItemStack stack=new ItemStack(PHYSICAL_CARD);stack.set(DATA,data);return stack;}
    private static class PhysicalItem extends Item {
        PhysicalItem(){super(new Settings().maxCount(1));}
        @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity player,Hand hand){
            ItemStack stack=player.getStackInHand(hand);
            if(world.isClient)return TypedActionResult.success(stack);
            return PhysicalCards.redeem((ServerPlayerEntity)player,hand)?TypedActionResult.success(stack):TypedActionResult.fail(stack);
        }
        @Override public Text getName(ItemStack stack){
            var data=stack.get(DATA);var card=PhysicalCards.definition(data==null?"":data.cardId());
            return card==null?super.getName(stack):Text.translatable("item.svarcade_tcg.physical_card.named",PhysicalCards.cardName(card));
        }
        @Override public void appendTooltip(ItemStack stack,TooltipContext context,List<Text> lines,TooltipType type){
            lines.add(Text.translatable("cardworlds.physical.kind").formatted(Formatting.GRAY));
            var d=stack.get(DATA);if(d==null){lines.add(Text.translatable("cardworlds.physical.invalid").formatted(Formatting.RED));return;}
            var card=PhysicalCards.definition(d.cardId());
            if(card!=null)lines.add(Text.translatable("cardworlds.physical.rarity",Text.translatable("cardworlds.rarity."+card.rarity().toLowerCase(java.util.Locale.ROOT).replace(' ','_'))));
            if(!d.capturedSpecies().isEmpty()){
                String[] species=d.capturedSpecies().split(":",2);
                lines.add(Text.translatable("cardworlds.physical.species",Text.translatable("cobblemon.species."+species[1]+".name")));
                lines.add(Text.translatable("cardworlds.physical.form",d.capturedAspects().isEmpty()?Text.translatable("cardworlds.physical.base"):Text.literal(String.join(", ",d.capturedAspects()))));
                lines.add(Text.translatable("cardworlds.physical.aspects",d.capturedAspects().isEmpty()?Text.translatable("cardworlds.physical.none"):Text.literal(String.join(", ",d.capturedAspects()))));
            }
            lines.add(Text.translatable("cardworlds.physical.finish",Text.translatable("cardworlds.physical.finish."+d.finish().toLowerCase(java.util.Locale.ROOT).replace(' ','_'))));
            lines.add(Text.translatable("cardworlds.physical.origin",Text.translatable("cardworlds.physical.origin."+d.origin().toLowerCase(java.util.Locale.ROOT))));
            if(d.capturedLevel()>0)lines.add(Text.translatable("cardworlds.physical.level",d.capturedLevel()));
            if(!d.finderName().isEmpty())lines.add(Text.translatable("cardworlds.physical.finder",d.finderName()));
            if(d.shiny())lines.add(Text.translatable("cardworlds.physical.shiny").formatted(Formatting.GOLD));
            lines.add(Text.translatable("cardworlds.physical.redeem_hint").formatted(Formatting.GRAY));
            if(type.isAdvanced())lines.add(Text.literal(d.physicalId().toString()).formatted(Formatting.DARK_GRAY));
        }
    }
    private static class BlankItem extends Item {
        BlankItem(){super(new Settings().maxCount(64));}
        @Override public ActionResult useOnEntity(ItemStack stack,PlayerEntity player,LivingEntity entity,Hand hand){
            if(player.getWorld().isClient)return ActionResult.SUCCESS;
            return BlankCapture.attempt((ServerPlayerEntity)player,entity,hand)?ActionResult.SUCCESS:ActionResult.FAIL;
        }
        @Override public void appendTooltip(ItemStack stack,TooltipContext context,List<Text> lines,TooltipType type){
            lines.add(Text.translatable("cardworlds.blank.kind").formatted(Formatting.GRAY));
            lines.add(Text.translatable("cardworlds.blank.use"));lines.add(Text.translatable("cardworlds.blank.consumed").formatted(Formatting.GRAY));
        }
    }
    private CardItems(){}
}
