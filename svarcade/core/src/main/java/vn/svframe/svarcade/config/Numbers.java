package vn.svframe.svarcade.config;

/** Finite numeric fields, accepting YAML integral and floating point scalars. */
public final class Numbers {
    private Numbers() { }
    public static double decimal(Node node, String key, double min, double max) {
        Object value = node.require(key);
        if (!(value instanceof Number number)) throw node.error(key, "Expected number");
        double result = number.doubleValue();
        if (!Double.isFinite(result) || result < min || result > max) throw node.error(key, "Number outside " + min + ".." + max);
        return result;
    }
}
