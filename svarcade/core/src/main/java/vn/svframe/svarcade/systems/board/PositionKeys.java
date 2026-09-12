package vn.svframe.svarcade.systems.board;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;

/** Shared compact key representation used by persisted repetition history and detached search. */
public final class PositionKeys {
    private PositionKeys() { }
    public static String digest(String canonical) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
