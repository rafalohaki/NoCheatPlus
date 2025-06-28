package org.mockbukkit.mockbukkit.tags;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.bukkit.NamespacedKey;

/** Replacement for MockBukkit TagFactory that avoids using NamespacedKey.value(). */
public final class TagFactory {
    private TagFactory() {
    }

    public static org.bukkit.Tag<?> createTag(TagRegistry registry, NamespacedKey key) {
        Preconditions.checkNotNull(registry, "registry cannot be null");
        Preconditions.checkNotNull(key, "key cannot be null");
        String path = String.format("/tags/%s/%s.json", registry.getRegistry(), key.getKey());
        JsonObject json = org.mockbukkit.mockbukkit.util.ResourceLoader.loadResource(path).getAsJsonObject();
        JsonElement values = json.get("values");
        Preconditions.checkArgument(values.isJsonArray(), "Invalid tag values");
        return createTag(registry, key, values.getAsJsonArray());
    }

    static org.bukkit.Tag<?> createTag(TagRegistry registry, NamespacedKey key, JsonArray array) {
        switch (registry) {
            case BLOCKS:
            case ITEMS:
                return MaterialTagMock.from(key, array);
            default:
                return new org.bukkit.Tag<org.bukkit.Keyed>() {
                    @Override
                    public boolean isTagged(org.bukkit.Keyed tagged) {
                        return false;
                    }

                    @Override
                    public java.util.Set<org.bukkit.Keyed> getValues() {
                        return java.util.Collections.emptySet();
                    }

                    @Override
                    public NamespacedKey getKey() {
                        return key;
                    }
                };
        }
    }
}
