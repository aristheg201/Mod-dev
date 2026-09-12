package vn.svframe.svarcade.config;

public final class ConfigException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;
    public ConfigException(String message) { super(message); }
    public ConfigException(String message, Throwable cause) { super(message, cause); }
}
