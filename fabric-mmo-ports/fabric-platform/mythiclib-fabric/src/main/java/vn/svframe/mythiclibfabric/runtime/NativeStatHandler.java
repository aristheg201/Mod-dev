package vn.svframe.mythiclibfabric.runtime;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Native counterpart of MythicLib 1.7.1 StatHandler. */
public class NativeStatHandler {
    @FunctionalInterface
    public interface ModifierEditor {
        NativeStatEngine.Modifier apply(NativeStatEngine.StatInstance instance, NativeStatEngine.Modifier modifier);
    }

    @FunctionalInterface
    public interface UpdateListener {
        void onUpdate(NativeStatEngine.StatInstance instance);
    }

    private final String stat;
    private final boolean hasMinValue;
    private final boolean hasMaxValue;
    private final double baseValue;
    private final double minValue;
    private final double maxValue;
    private final DecimalFormat decimalFormat;
    private final CopyOnWriteArrayList<UpdateListener> updates = new CopyOnWriteArrayList<>();
    private volatile ModifierEditor modifierEditor;
    private volatile boolean updateOnLogin;

    public NativeStatHandler(String stat) {
        this(stat, 0.0d, null, null, new DecimalFormat("0.#"));
    }

    public NativeStatHandler(String stat, double baseValue, Double minValue, Double maxValue, DecimalFormat decimalFormat) {
        this.stat = normalize(stat);
        if (this.stat.isEmpty()) throw new IllegalArgumentException("stat must not be blank");
        if (!Double.isFinite(baseValue)) throw new IllegalArgumentException("baseValue must be finite");
        if (minValue != null && !Double.isFinite(minValue)) throw new IllegalArgumentException("minValue must be finite");
        if (maxValue != null && !Double.isFinite(maxValue)) throw new IllegalArgumentException("maxValue must be finite");
        if (minValue != null && maxValue != null && minValue > maxValue) {
            throw new IllegalArgumentException("minValue must be <= maxValue");
        }
        this.baseValue = baseValue;
        this.hasMinValue = minValue != null;
        this.hasMaxValue = maxValue != null;
        this.minValue = minValue == null ? 0.0d : minValue;
        this.maxValue = maxValue == null ? 0.0d : maxValue;
        this.decimalFormat = (DecimalFormat) Objects.requireNonNull(decimalFormat, "decimalFormat").clone();
    }

    public String stat() {
        return stat;
    }

    public double configuredBaseValue() {
        return baseValue;
    }

    public boolean hasMinValue() {
        return hasMinValue;
    }

    public boolean hasMaxValue() {
        return hasMaxValue;
    }

    public double minValue() {
        return minValue;
    }

    public double maxValue() {
        return maxValue;
    }

    public DecimalFormat decimalFormat() {
        return (DecimalFormat) decimalFormat.clone();
    }

    public void addUpdateListener(UpdateListener listener) {
        updates.add(Objects.requireNonNull(listener, "listener"));
    }

    public List<UpdateListener> updateListeners() {
        return List.copyOf(updates);
    }

    public void setModifierEditor(ModifierEditor modifierEditor) {
        this.modifierEditor = modifierEditor;
    }

    public ModifierEditor modifierEditor() {
        return modifierEditor;
    }

    public void setUpdateOnLogin(boolean updateOnLogin) {
        this.updateOnLogin = updateOnLogin;
    }

    public boolean updateOnLogin() {
        return updateOnLogin;
    }

    public boolean forcesUpdates() {
        return updateOnLogin;
    }

    public double getBaseValue(NativeStatEngine.StatInstance instance) {
        return baseValue;
    }

    public double getPlayerDefaultBase() {
        return 0.0d;
    }

    public double getFinalValue(NativeStatEngine.StatInstance instance, NativeStatEngine.EquipmentSlot actionHand) {
        return instance.total(actionHand);
    }

    public double clampValue(double value) {
        if (hasMaxValue && value > maxValue) value = maxValue;
        if (hasMinValue && value < minValue) value = minValue;
        return value;
    }

    public String format(double value) {
        synchronized (decimalFormat) {
            return decimalFormat.format(value);
        }
    }

    public void runUpdates(NativeStatEngine.StatInstance instance) {
        for (UpdateListener listener : updates) listener.onUpdate(instance);
    }

    private static String normalize(String stat) {
        return stat == null ? "" : stat.trim().toUpperCase(Locale.ROOT);
    }
}
