package org.mockbukkit.mockbukkit.tags;

import com.google.common.base.Preconditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

/** Test replacement that ignores unknown Material names to maintain compatibility. */
public class MaterialTagMock extends BaseTagMock<Material> {
    public MaterialTagMock(NamespacedKey key, Collection<Material> materials) {
        super(key, materials);
    }

    public MaterialTagMock(NamespacedKey key, Material... materials) {
        super(key, materials);
    }

    public static MaterialTagMock from(NamespacedKey key, JsonArray array) {
        Collection<Material> materials = new ArrayList<>();
        for (JsonElement element : array) {
            Preconditions.checkState(element.isJsonPrimitive(), "The value is not a primitive value");
            JsonPrimitive prim = element.getAsJsonPrimitive();
            Preconditions.checkState(prim.isString(), "The value is not a string value");
            NamespacedKey materialKey = NamespacedKey.fromString(prim.getAsString());
            Preconditions.checkArgument(materialKey != null, "The value is not a valid namespaced key");
            String matName = materialKey.value().toUpperCase(Locale.ROOT);
            try {
                materials.add(Material.valueOf(matName));
            } catch (IllegalArgumentException ignored) {
                // Material not present in this MockBukkit version; ignore.
            }
        }
        return new MaterialTagMock(key, materials);
    }
}
