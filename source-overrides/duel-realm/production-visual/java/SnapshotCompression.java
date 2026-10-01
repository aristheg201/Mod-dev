package vn.svarcade.tcg.fabric;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.zip.*;

/** Catalog compositions repeat across cards; keep snapshots below Minecraft's payload limit. */
public final class SnapshotCompression {
    private static final int MAX_JSON=8*1024*1024,MAX_WIRE=900*1024;
    public static byte[] encode(String json) {
        byte[] raw=json.getBytes(StandardCharsets.UTF_8);
        if(raw.length>MAX_JSON)throw new IllegalArgumentException("Card Worlds snapshot exceeds bounded catalog size");
        try(var buffer=new ByteArrayOutputStream()) {
            try(var stream=new GZIPOutputStream(buffer)){stream.write(raw);}
            byte[] result=buffer.toByteArray();if(result.length>MAX_WIRE)throw new IllegalArgumentException("Card Worlds compressed snapshot exceeds payload limit");return result;
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    public static String decode(byte[] compressed) {
        if(compressed.length>MAX_WIRE)throw new IllegalArgumentException("Oversized Card Worlds snapshot");
        try(var stream=new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            byte[] raw=stream.readNBytes(MAX_JSON+1);if(raw.length>MAX_JSON)throw new IllegalArgumentException("Oversized expanded Card Worlds snapshot");return new String(raw,StandardCharsets.UTF_8);
        }catch(IOException e){throw new IllegalArgumentException("Invalid Card Worlds snapshot",e);}
    }
    private SnapshotCompression() {}
}
