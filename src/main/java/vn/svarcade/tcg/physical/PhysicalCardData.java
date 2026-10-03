package vn.svarcade.tcg.physical;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import vn.svarcade.tcg.data.CardIdentityResolver;
import vn.svarcade.tcg.data.Catalog;
import java.util.*;

/** Immutable provenance. Only cardId participates in Card Worlds gameplay. */
public record PhysicalCardData(int version,UUID physicalId,String cardId,String finish,String origin,
        String finderUuid,String finderName,String capturedSpecies,List<String> capturedAspects,
        int capturedLevel,boolean shiny,long createdAt) {
    public static final Set<String> FINISHES=Set.of("Normal","Holo","Reverse Holo","Full Art","Secret","World Found","Underground");
    public PhysicalCardData { capturedAspects=List.copyOf(capturedAspects); }
    public static final Codec<PhysicalCardData> CODEC=RecordCodecBuilder.<PhysicalCardData>create(i->i.group(
        Codec.INT.fieldOf("version").forGetter(PhysicalCardData::version),
        net.minecraft.util.Uuids.STRING_CODEC.fieldOf("physical_id").forGetter(PhysicalCardData::physicalId),
        Codec.STRING.fieldOf("card_id").forGetter(PhysicalCardData::cardId),
        Codec.STRING.fieldOf("finish").forGetter(PhysicalCardData::finish),
        Codec.STRING.fieldOf("origin").forGetter(PhysicalCardData::origin),
        Codec.STRING.fieldOf("finder_uuid").forGetter(PhysicalCardData::finderUuid),
        Codec.STRING.fieldOf("finder_name").forGetter(PhysicalCardData::finderName),
        Codec.STRING.fieldOf("captured_species").forGetter(PhysicalCardData::capturedSpecies),
        Codec.STRING.listOf().fieldOf("captured_aspects").forGetter(PhysicalCardData::capturedAspects),
        Codec.INT.fieldOf("captured_level").forGetter(PhysicalCardData::capturedLevel),
        Codec.BOOL.fieldOf("shiny").forGetter(PhysicalCardData::shiny),
        Codec.LONG.fieldOf("created_at").forGetter(PhysicalCardData::createdAt)
    ).apply(i,PhysicalCardData::new)).validate(d->{try{d.validate();return DataResult.success(d);}catch(RuntimeException e){return DataResult.error(()->"Invalid physical card: "+e.getMessage());}});
    public static final PacketCodec<RegistryByteBuf,PhysicalCardData> PACKET_CODEC=PacketCodec.of((d,b)->{
        d.validate();b.writeVarInt(d.version);b.writeUuid(d.physicalId);b.writeString(d.cardId,128);
        b.writeString(d.finish,32);b.writeString(d.origin,32);b.writeString(d.finderUuid,36);b.writeString(d.finderName,64);
        b.writeString(d.capturedSpecies,128);b.writeVarInt(d.capturedAspects.size());
        d.capturedAspects.forEach(a->b.writeString(a,96));b.writeVarInt(d.capturedLevel);b.writeBoolean(d.shiny);b.writeLong(d.createdAt);
    },b->{int version=b.readVarInt();UUID token=b.readUuid();String card=b.readString(128),finish=b.readString(32),origin=b.readString(32),finder=b.readString(36),name=b.readString(64),species=b.readString(128);
        int n=b.readVarInt();if(n<0||n>64)throw new IllegalArgumentException("Invalid aspect count");
        List<String> aspects=new ArrayList<>();for(int k=0;k<n;k++)aspects.add(b.readString(96));
        var d=new PhysicalCardData(version,token,card,finish,origin,finder,name,species,aspects,b.readVarInt(),b.readBoolean(),b.readLong());d.validate();return d;
    });
    public void validate() {
        if(version!=1||physicalId==null||physicalId.equals(new UUID(0,0)))throw new IllegalArgumentException("Invalid token/version");
        if(cardId==null||!cardId.matches("[a-z0-9_./:-]{1,128}"))throw new IllegalArgumentException("Invalid card ID");
        if(!FINISHES.contains(finish)||!Set.of("BLANK_CAPTURE","CHEST_LOOT","ADMIN_GRANT").contains(origin))throw new IllegalArgumentException("Invalid finish/origin");
        if(finderUuid==null||finderName==null||finderName.length()>64||finderName.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Invalid finder");
        if(!finderUuid.isEmpty())UUID.fromString(finderUuid);
        if(!capturedSpecies.isEmpty())CardIdentityResolver.canonicalSpecies(capturedSpecies);
        CardIdentityResolver.validateAspects(capturedAspects);
        if(capturedLevel<0||capturedLevel>10000||createdAt<=0)throw new IllegalArgumentException("Invalid provenance");
        if(origin.equals("BLANK_CAPTURE")&&(finderUuid.isEmpty()||capturedSpecies.isEmpty()||capturedLevel<1))throw new IllegalArgumentException("Missing capture provenance");
    }
    public void validate(Catalog catalog){validate();catalog.card(cardId);}
    public static PhysicalCardData create(Catalog catalog,String id,String finish,String origin,String finderUuid,String finderName) {
        var card=catalog.card(id);
        var data=new PhysicalCardData(1,UUID.randomUUID(),id,finish,origin,finderUuid,finderName,
            card.category().equals("pokemon")?CardIdentityResolver.canonicalSpecies(card.species()):"",card.aspects(),0,false,System.currentTimeMillis());
        data.validate(catalog);return data;
    }
}
