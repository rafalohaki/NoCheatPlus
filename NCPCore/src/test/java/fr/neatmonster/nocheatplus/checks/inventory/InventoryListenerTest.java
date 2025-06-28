package fr.neatmonster.nocheatplus.checks.inventory;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import fr.neatmonster.nocheatplus.players.PlayerDataManager;
import fr.neatmonster.nocheatplus.stats.Counters;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;

public class InventoryListenerTest {

    private InventoryListener listener;
    private InventoryData data;

    @BeforeEach
    public void setup() throws Exception {
        Counters counters = new Counters();
        Object api = Proxy.newProxyInstance(
                fr.neatmonster.nocheatplus.NCPAPIProvider.class.getClassLoader(),
                new Class<?>[]{fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class},
                (p, m, a) -> {
                    if ("getGenericInstance".equals(m.getName())) {
                        if (a[0] == Counters.class) return counters;
                    }
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
        Field nf = fr.neatmonster.nocheatplus.NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        nf.setAccessible(true);
        nf.set(null, api);
        sun.misc.Unsafe u = getUnsafe();
        PlayerDataManager pdm = (PlayerDataManager) u.allocateInstance(PlayerDataManager.class);
        Field eh = PlayerDataManager.class.getDeclaredField("executionHistories");
        eh.setAccessible(true);
        eh.set(pdm, new java.util.HashMap<>());
        new fr.neatmonster.nocheatplus.players.DataManager(pdm);
        listener = (InventoryListener) u.allocateInstance(InventoryListener.class);
        data = new InventoryData();
    }

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    @Test
    public void testFastFoodInteractionStoresFood() throws Exception {
        Method m = InventoryListener.class.getDeclaredMethod("rememberFoodInteract", InventoryData.class, Material.class);
        m.setAccessible(true);

        data.eatTracker.setLast(System.currentTimeMillis() - 100);
        m.invoke(listener, data, Material.BREAD);
        assertEquals(Material.BREAD, data.instantEatFood);
    }

    @Test
    public void testSlowFoodInteractionClearsFood() throws Exception {
        Method m = InventoryListener.class.getDeclaredMethod("rememberFoodInteract", InventoryData.class, Material.class);
        m.setAccessible(true);

        data.eatTracker.setLast(System.currentTimeMillis() - 1200);
        m.invoke(listener, data, Material.APPLE);
        assertNull(data.instantEatFood);
    }
}
