package org.bukkit;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Simplified NamespacedKey for tests with value() support. */
public final class NamespacedKey {
    public static final String MINECRAFT = "minecraft";
    public static final String BUKKIT = "bukkit";

    private final String namespace;
    private final String key;

    public NamespacedKey(String namespace, String key) {
        this.namespace = Objects.requireNonNull(namespace, "Namespace").toLowerCase(Locale.ROOT);
        this.key = Objects.requireNonNull(key, "Key").toLowerCase(Locale.ROOT);
    }

    public NamespacedKey(org.bukkit.plugin.Plugin plugin, String key) {
        this(plugin.getName(), key);
    }

    public String getNamespace() {
        return namespace;
    }

    public String getKey() {
        return key;
    }

    public String value() {
        return key;
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace, key);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NamespacedKey other = (NamespacedKey) obj;
        return namespace.equals(other.namespace) && key.equals(other.key);
    }

    @Override
    public String toString() {
        return namespace + ":" + key;
    }

    public static NamespacedKey minecraft(String key) {
        return new NamespacedKey(MINECRAFT, key);
    }

    public static NamespacedKey fromString(String string) {
        if (string == null) return null;
        int idx = string.indexOf(':');
        if (idx == -1) {
            return new NamespacedKey(MINECRAFT, string);
        }
        return new NamespacedKey(string.substring(0, idx), string.substring(idx + 1));
    }

    public static NamespacedKey randomKey() {
        return new NamespacedKey(MINECRAFT, UUID.randomUUID().toString());
    }
}
