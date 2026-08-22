package vn.svframe.lively.integration;

import vn.svframe.lively.api.HologramBridge;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

/** Optional packet-hologram bridge verified against HoloDisplays 0.4.8 public API and bytecode. */
final class HoloDisplaysBridge implements HologramBridge {
    private final Object api;

    private HoloDisplaysBridge(Object api) { this.api = api; }

    static HologramBridge create() {
        try {
            Class<?> type = Class.forName("dev.furq.holodisplays.api.HoloDisplaysAPI");
            return new HoloDisplaysBridge(type.getMethod("get", String.class).invoke(null, "livelynpcs"));
        } catch (ReflectiveOperationException error) {
            return HologramBridge.unavailable();
        }
    }

    @Override public boolean available() { return api != null; }

    @Override
    public boolean remove(String id) {
        if (id == null || id.isBlank()) return false;
        String displayId = id + "_text";
        String temporaryId = displayId + "_next";
        boolean changed = false;
        try {
            changed |= Boolean.TRUE.equals(api.getClass().getMethod("unregisterHologram", String.class).invoke(api, id));
            changed |= Boolean.TRUE.equals(api.getClass().getMethod("unregisterDisplay", String.class).invoke(api, displayId));
            changed |= Boolean.TRUE.equals(api.getClass().getMethod("unregisterDisplay", String.class).invoke(api, temporaryId));
            return changed;
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    @Override
    public boolean upsertText(String id, String world, double x, double y, double z, List<String> lines) {
        if (id == null || id.isBlank() || world == null || world.isBlank() || lines == null || lines.isEmpty()) return false;
        try {
            String displayId = id + "_text";
            String temporaryId = displayId + "_next";
            Method createText = api.getClass().getMethod("createTextDisplay", String.class, Consumer.class);
            Method isDisplayRegistered = api.getClass().getMethod("isDisplayRegistered", String.class);
            Method unregisterDisplay = api.getClass().getMethod("unregisterDisplay", String.class);
            Method updateDisplay = api.getClass().getMethod("updateDisplay", String.class,
                    Class.forName("dev.furq.holodisplays.data.DisplayData"));

            Consumer<Object> configure = builder -> {
                try {
                    builder.getClass().getMethod("text", String[].class).invoke(builder, (Object) lines.toArray(String[]::new));
                    builder.getClass().getMethod("billboardMode", String.class).invoke(builder, "CENTER");
                } catch (ReflectiveOperationException error) {
                    throw new IllegalStateException(error);
                }
            };

            boolean displayExists = Boolean.TRUE.equals(isDisplayRegistered.invoke(api, displayId));
            if (displayExists) {
                // HoloDisplays' createTextDisplay always registers. Build the replacement under a temporary id,
                // copy its DisplayData into the stable id, then remove the temporary registry entry.
                unregisterDisplay.invoke(api, temporaryId);
                Object replacement = createText.invoke(api, temporaryId, configure);
                boolean updated = Boolean.TRUE.equals(updateDisplay.invoke(api, displayId, replacement));
                unregisterDisplay.invoke(api, temporaryId);
                if (!updated) return false;
            } else {
                createText.invoke(api, displayId, configure);
            }

            Object builder = api.getClass().getMethod("createHologramBuilder").invoke(api);
            builder = call(builder, "position", new Class[]{float.class, float.class, float.class}, (float) x, (float) y, (float) z);
            builder = call(builder, "world", new Class[]{String.class}, world);
            builder = call(builder, "billboardMode", new Class[]{String.class}, "CENTER");
            builder = call(builder, "addDisplay", new Class[]{String.class}, displayId);
            Object data = builder.getClass().getMethod("build").invoke(builder);

            boolean exists = Boolean.TRUE.equals(api.getClass().getMethod("isHologramRegistered", String.class).invoke(api, id));
            Class<?> hologramType = Class.forName("dev.furq.holodisplays.data.HologramData");
            Method operation = api.getClass().getMethod(exists ? "updateHologram" : "registerHologram", String.class, hologramType);
            return Boolean.TRUE.equals(operation.invoke(api, id, data));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return false;
        }
    }

    private static Object call(Object target, String method, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        return target.getClass().getMethod(method, types).invoke(target, args);
    }
}
