package vn.svframe.lively;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.svframe.lively.skin.SkinConfig;
import vn.svframe.lively.skin.SkinSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

final class SkinSecurityTest {
    @TempDir Path temp;

    @Test
    void defaultHostPolicyOnlyAllowsConfiguredSkinServices() {
        SkinConfig config = SkinConfig.load(temp.resolve("config"));
        assertTrue(config.hostAllowed("textures.minecraft.net"));
        assertTrue(config.hostAllowed("www.namemc.com"));
        assertTrue(config.hostAllowed("namemc.com"));
        assertTrue(config.hostAllowed("skinsmc.org"));
        assertFalse(config.hostAllowed("example.com"));
        assertFalse(config.hostAllowed("localhost"));
        assertFalse(config.hostAllowed("127.0.0.1"));
        assertFalse(config.allowHttp());
        assertFalse(config.allowUnlistedHosts());
    }

    @Test
    void numericLimitsAreClampedAndMalformedNumbersUseSafeDefaults() throws Exception {
        Path dir = temp.resolve("limits");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("skins.properties"), String.join("\n",
                "cache_ttl_hours=-5",
                "connect_timeout_ms=999999999",
                "request_timeout_ms=not-a-number",
                "max_page_bytes=1",
                "max_png_bytes=999999999",
                "allow_http=false",
                "allow_unlisted_hosts=false",
                "allowed_hosts=textures.minecraft.net"), StandardCharsets.UTF_8);
        SkinConfig config = SkinConfig.load(dir);
        assertEquals(1L, config.cacheTtl().toHours());
        assertEquals(60_000L, config.connectTimeout().toMillis());
        assertEquals(15_000L, config.requestTimeout().toMillis());
        assertEquals(65_536, config.maxPageBytes());
        assertEquals(4_194_304, config.maxPngBytes());
    }

    @Test
    void sourceParserNeverTreatsNonHttpSchemesAsRemoteUrls() {
        assertEquals(SkinSource.Kind.MOJANG, SkinSource.parse("file:///etc/passwd").kind());
        assertEquals(SkinSource.Kind.MOJANG, SkinSource.parse("jar:file:///tmp/x.jar!/skin.png").kind());
        assertEquals(SkinSource.Kind.MOJANG, SkinSource.parse("ftp://example.com/skin.png").kind());
        assertEquals(SkinSource.Kind.URL, SkinSource.parse("https://namemc.com/skin/abc").kind());
        assertEquals(SkinSource.Kind.URL, SkinSource.parse("http://example.com/skin.png").kind(), "HTTP is parsed as a URL but resolver policy still denies it by default");
    }

    @Test
    void textureSourceSplitsSignatureWithoutTouchingPayload() {
        SkinSource signed = SkinSource.parse("texture:value-data|signature-data");
        assertEquals(SkinSource.Kind.TEXTURE, signed.kind());
        assertEquals("value-data", signed.value());
        assertEquals("signature-data", signed.signature());
    }
}
