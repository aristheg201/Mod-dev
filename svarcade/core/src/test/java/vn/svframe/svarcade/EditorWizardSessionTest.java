package vn.svframe.svarcade;

import java.util.*;
import org.junit.jupiter.api.Test;
import vn.svframe.svarcade.config.Node;
import vn.svframe.svarcade.editor.*;
import vn.svframe.svarcade.runtime.ThreadGuard;
import static org.junit.jupiter.api.Assertions.*;

class EditorWizardSessionTest {
    @Test void wizardConsumesGenericPointRegionPathAndDirectionTools() {
        ThreadGuard thread = new ThreadGuard(); EditorSession editor = new EditorSession(Map.of(), 32, thread);
        Node config = new Node(Map.of("schema", 1, "wizard", List.of(
                Map.of("id", "board", "tool", "region", "required", true),
                Map.of("key", "route", "tool", "path", "required", true),
                Map.of("key", "facing", "tool", "direction", "required", true))), "editor");
        EditorWizardSession wizard = new EditorWizardSession(config, editor, 32, thread);
        wizard.click(new EditorWizardSession.Input(new EditorWizardSession.Point(5, 2, 5), "", ""));
        wizard.click(new EditorWizardSession.Input(new EditorWizardSession.Point(1, 0, 1), "", "")); assertEquals(1, wizard.progress().index());
        wizard.click(new EditorWizardSession.Input(new EditorWizardSession.Point(0, 0, 0), "", ""));
        wizard.click(new EditorWizardSession.Input(new EditorWizardSession.Point(10, 0, 0), "", "")); wizard.finishStep();
        wizard.click(new EditorWizardSession.Input(new EditorWizardSession.Point(0, 0, 0), "north", "")); assertTrue(wizard.progress().complete());
        assertEquals("north", wizard.preview().get("facing")); assertTrue(wizard.preview().containsKey("board")); assertTrue(wizard.preview().containsKey("route"));
    }
}
