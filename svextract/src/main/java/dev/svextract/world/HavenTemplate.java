package dev.svextract.world;

import com.google.gson.*;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Imported author geometry is included only by the explicitly requested private build profile. */
public record HavenTemplate(List<StationBlueprint.Change> changes,Map<String,Object> provenance) {
    public static HavenTemplate load() throws IOException {
        String base="/data/svextract/structures/private/vault_legacy_haven";
        try(InputStream input=HavenTemplate.class.getResourceAsStream(base+".nbt")) {
            if(input==null)return new HavenTemplate(StationBlueprint.haven(),Map.of("id","original_haven9","externalMapApplied",false));
            byte[] compressed=input.readNBytes(2*1024*1024+1);
            if(compressed.length>2*1024*1024)throw new IOException("Private map exceeds the compressed source budget");
            Map<String,Object> provenance=new LinkedHashMap<>();
            try(InputStream metadata=HavenTemplate.class.getResourceAsStream(base+".json")) {
                if(metadata==null)throw new IOException("Private map provenance is missing");
                JsonObject json=JsonParser.parseReader(new InputStreamReader(metadata,StandardCharsets.UTF_8)).getAsJsonObject();
                json.entrySet().forEach(e->provenance.put(e.getKey(),e.getValue().getAsString()));
            }
            try {
                String checksum=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(compressed));
                if(!checksum.equals(provenance.get("sha256")))throw new IOException("Private map source checksum differs from its provenance");
            }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
            ImportedStructure.Template template=ImportedStructure.read(new ByteArrayInputStream(compressed));
            if(template.x()!=47||template.y()!=47||template.z()!=47)throw new IOException("Vault Haven geometry must be the validated 47x47x47 authored room");
            LinkedHashMap<BlockPos,net.minecraft.block.BlockState> cells=new LinkedHashMap<>();
            for(var c:ImportedStructure.resolve(template,new BlockPos(-23,45,-23)))cells.put(c.position(),c.state());
            // Spawn and mission-control paths are deliberately safe; the surrounding authored room remains intact.
            for(int x=-4;x<=4;x++)for(int z=-14;z<=8;z++) {
                cells.put(new BlockPos(x,64,z),Blocks.STONE_BRICKS.getDefaultState());
                for(int y=65;y<=68;y++)cells.put(new BlockPos(x,y,z),Blocks.AIR.getDefaultState());
            }
            for(int x=-1;x<=1;x++)cells.put(new BlockPos(x,65,-10),Blocks.OAK_PLANKS.getDefaultState());
            provenance.put("externalMapApplied",true);provenance.put("sourceCells",template.cells().size());provenance.put("omittedEntityPayloads",template.omittedPayloads());
            provenance.put("placement",List.of(-23,45,-23));
            return new HavenTemplate(cells.entrySet().stream().map(e->new StationBlueprint.Change(e.getKey(),e.getValue())).toList(),Map.copyOf(provenance));
        }
    }
}
