package vn.svframe.svarcade.editor;

import java.util.*;
import vn.svframe.svarcade.config.*;
import vn.svframe.svarcade.runtime.ThreadGuard;

/** Definition-authored world-click wizard over generic editor primitives. All selections remain in RAM until EditorSession.save. */
public final class EditorWizardSession {
    public enum Tool { POINT, REGION, MULTI_REGION, PATH, DIRECTION, OBJECT }
    public record Point(double x, double y, double z) {
        public Point { if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) throw new IllegalArgumentException("Editor point"); }
        Map<String,Object> map() { return Map.of("x", x, "y", y, "z", z); }
    }
    public record Input(Point point, String direction, String object) {
        public Input { Objects.requireNonNull(point); direction = direction == null ? "" : direction; object = object == null ? "" : object; }
    }
    public record Step(String key, Tool tool, boolean required) { }
    public record Progress(int index, int total, Step step, boolean complete) { }

    private final ThreadGuard thread;
    private final EditorSession editor;
    private final List<Step> steps;
    private final int maxPoints;
    private final List<Point> pending = new ArrayList<>();
    private int index;

    public EditorWizardSession(Node config, EditorSession editor, int maxPoints, ThreadGuard thread) {
        this.thread = Objects.requireNonNull(thread); this.editor = Objects.requireNonNull(editor);
        if (maxPoints < 2 || maxPoints > 100000) throw new IllegalArgumentException("Editor point limit"); this.maxPoints = maxPoints;
        config.only("schema", "wizard", "preview", "save"); if (config.has("schema")) config.integer("schema", 1, 1);
        List<Step> parsed = new ArrayList<>();
        for (Node row : config.nodes("wizard")) {
            row.only("id", "key", "tool", "required"); String key = row.has("key") ? row.string("key") : row.string("id");
            if (key.isBlank() || key.length() > 256) throw row.error("key", "Invalid editor key"); Tool tool;
            try { tool = Tool.valueOf(row.string("tool").replace('-', '_').toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw row.error("tool", "Unknown editor tool"); }
            parsed.add(new Step(key, tool, row.bool("required", true)));
        }
        if (parsed.isEmpty() || parsed.size() > 256) throw new ConfigException("Editor wizard step limits"); steps = List.copyOf(parsed);
    }

    public Progress progress() { thread.check(); return new Progress(index, steps.size(), index < steps.size() ? steps.get(index) : null, index >= steps.size()); }
    public void click(Input input) {
        thread.check(); Step step = current(); Objects.requireNonNull(input);
        switch (step.tool()) {
            case POINT -> { editor.select(step.key(), input.point().map()); advance(); }
            case REGION -> {
                pending.add(input.point()); if (pending.size() == 2) { editor.select(step.key(), region(pending.get(0), pending.get(1))); pending.clear(); advance(); }
            }
            case MULTI_REGION, PATH -> {
                if (pending.size() >= maxPoints) throw new IllegalStateException("Editor point capacity"); pending.add(input.point());
            }
            case DIRECTION -> {
                String direction = input.direction().trim().toLowerCase(Locale.ROOT); if (!direction.matches("[a-z0-9_:-]{1,80}")) throw new IllegalArgumentException("Editor direction");
                editor.select(step.key(), direction); advance();
            }
            case OBJECT -> {
                String object = input.object().trim(); if (object.isEmpty() || object.length() > 256) throw new IllegalArgumentException("Editor object");
                editor.select(step.key(), Map.of("id", object, "position", input.point().map())); advance();
            }
        }
    }
    /** Finalizes variable-length path/multi-region steps; fixed-size tools advance automatically. */
    public void finishStep() {
        thread.check(); Step step = current();
        if (step.tool() == Tool.PATH) {
            if (pending.size() < 2) throw new IllegalStateException("Path needs at least two points"); editor.select(step.key(), pending.stream().map(Point::map).toList());
        } else if (step.tool() == Tool.MULTI_REGION) {
            if (pending.size() < 2 || pending.size() % 2 != 0) throw new IllegalStateException("Multi-region needs point pairs"); List<Object> regions = new ArrayList<>();
            for (int i = 0; i < pending.size(); i += 2) regions.add(region(pending.get(i), pending.get(i + 1))); editor.select(step.key(), regions);
        } else throw new IllegalStateException("Current tool does not need explicit finish");
        pending.clear(); advance();
    }
    public boolean skip() { thread.check(); Step step = current(); if (step.required()) return false; pending.clear(); advance(); return true; }
    public void cancelSelection() { thread.check(); pending.clear(); }
    public boolean undo() {
        thread.check(); pending.clear(); boolean changed = editor.undo(); if (changed && index > 0) index--; return changed;
    }
    public Map<String,Object> preview() { thread.check(); return editor.preview(); }
    private Step current() { if (index >= steps.size()) throw new IllegalStateException("Editor wizard complete"); return steps.get(index); }
    private void advance() { index++; }
    private static Map<String,Object> region(Point a, Point b) {
        return Map.of("min", new Point(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()), Math.min(a.z(), b.z())).map(),
                "max", new Point(Math.max(a.x(), b.x()), Math.max(a.y(), b.y()), Math.max(a.z(), b.z())).map());
    }
}
