package dev.svextract.world;

import dev.svextract.SvExtract;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

/** Optional author terrain lives as server map data, never as an embedded third-party mod. */
public final class MapTemplates {
    public static final String VAULT_SHA256="46b45b7c4572073809499dae39ecb12ff588df9b08492cf1ccf7e8593f6a0f03";
    private MapTemplates(){}
    public static List<StationBlueprint.Change> blackglass(Path config)throws IOException{
        Path template=config.resolve("svextract/templates/vault_legacy_haven.nbt");
        if(!Files.exists(template)){
            try(var bundled=MapTemplates.class.getResourceAsStream("/data/svextract/structures/vault_legacy_chamber.nbt")){
                if(bundled!=null){
                    byte[] bytes=bundled.readNBytes(2*1024*1024+1);
                    if(bytes.length>2*1024*1024)throw new IOException("Bundled Vault terrain exceeds 2 MiB");
                    Files.createDirectories(template.getParent());Files.write(template,bytes,StandardOpenOption.CREATE_NEW);
                }
            }
        }
        List<StationBlueprint.Change> result=new ArrayList<>();
        if(Files.isRegularFile(template)){
            if(Files.size(template)>2*1024*1024)throw new IOException(template+": compressed author template exceeds 2 MiB");
            try{
                String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(template)));
                if(!hash.equals(VAULT_SHA256))throw new IOException(template+": expected the pinned legacy Vault-public big1.nbt (SHA-256 "+VAULT_SHA256+"); received "+hash);
            }catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
            ImportedStructure.Template imported;
            try(var input=Files.newInputStream(template)){imported=ImportedStructure.read(input);}
            if(imported.x()>96||imported.z()>96||imported.y()>48)throw new IOException(template+": author room does not fit the bounded Blackglass chamber (96x48x96)");
            result.addAll(ImportedStructure.resolve(imported,new BlockPos(-imported.x()/2,14,-imported.z()/2)));
            SvExtract.LOGGER.info("Applied actual Vault author template to Blackglass preparation plan: {}x{}x{}, {} cells; entities/block-entity payloads omitted",imported.x(),imported.y(),imported.z(),imported.cells().size());
        }
        result.addAll(DenseMapDecorator.blackglass(BlackglassBlueprint.create()));return List.copyOf(result);
    }
}
