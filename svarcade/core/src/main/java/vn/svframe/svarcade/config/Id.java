package vn.svframe.svarcade.config;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable, explicitly namespaced identifier. No implicit default namespace. */
public record Id(String value) implements Comparable<Id> {
    private static final Pattern VALID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    public Id {
        Objects.requireNonNull(value, "value");
        if (value.length() > 160 || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid namespaced identifier: " + value);
        }
    }
    public static Id of(String value) { return new Id(value); }
    @Override public int compareTo(Id other) { return value.compareTo(other.value); }
    @Override public String toString() { return value; }
}
