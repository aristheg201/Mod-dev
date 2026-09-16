package vn.svframe.svrelationships.fabric.localization;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MessageServiceTest {
    @Test
    void legacyUiNamespaceResolvesToCanonicalGuiNamespace() {
        Map<String, String> messages = Map.of("gui.main.title", "SVRelationships");
        assertEquals("SVRelationships", MessageService.resolveTemplate(messages, "ui.main.title"));
    }

    @Test
    void canonicalGuiNamespaceCanReadLegacyCustomPack() {
        Map<String, String> messages = Map.of("ui.main.title", "Legacy title");
        assertEquals("Legacy title", MessageService.resolveTemplate(messages, "gui.main.title"));
    }
}
