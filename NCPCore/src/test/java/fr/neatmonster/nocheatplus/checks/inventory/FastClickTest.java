package fr.neatmonster.nocheatplus.checks.inventory;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.actions.ActionList;
import fr.neatmonster.nocheatplus.checks.ViolationData;
//import fr.neatmonster.nocheatplus.checks.inventory.FastClick;
//import fr.neatmonster.nocheatplus.checks.inventory.InventoryConfig;
//import fr.neatmonster.nocheatplus.checks.inventory.InventoryData;

public class FastClickTest {

    private static class TestableFastClick extends FastClick {
        ViolationData last;
        @Override
        public ViolationData executeActions(ViolationData violationData) {
            last = violationData;
            return violationData;
        }
    }

    private TestableFastClick check;
    private InventoryConfig config;
    private InventoryData data;

    @Before
    public void setup() throws Exception {
        Object api = java.lang.reflect.Proxy.newProxyInstance(
                fr.neatmonster.nocheatplus.NCPAPIProvider.class.getClassLoader(),
                new Class<?>[]{fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class},
                (p, m, a) -> null);
        Field nf = fr.neatmonster.nocheatplus.NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        nf.setAccessible(true);
        nf.set(null, api);
        sun.misc.Unsafe u = getUnsafe();
        Object pdm = u.allocateInstance(fr.neatmonster.nocheatplus.players.PlayerDataManager.class);
        Field eh = fr.neatmonster.nocheatplus.players.PlayerDataManager.class.getDeclaredField("executionHistories");
        eh.setAccessible(true);
        eh.set(pdm, new java.util.HashMap<>());
        Field dm = fr.neatmonster.nocheatplus.players.DataManager.class.getDeclaredField("instance");
        dm.setAccessible(true);
        dm.set(null, pdm);
        check = new TestableFastClick();
        data = new InventoryData();
        sun.misc.Unsafe unsafe = getUnsafe();
        config = (InventoryConfig) unsafe.allocateInstance(InventoryConfig.class);
        Field f = InventoryConfig.class.getDeclaredField("fastClickActions");
        f.setAccessible(true);
        f.set(config, new ActionList(null));
    }

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    @Test
    public void testViolationIncreasesVl() throws Exception {
        Method m = FastClick.class.getDeclaredMethod("processViolation", Player.class, InventoryData.class,
                InventoryConfig.class, float.class, float.class);
        m.setAccessible(true);
        m.invoke(check, null, data, config, 1.0f, 0.0f);
        assertTrue(data.fastClickVL > 0.9);
        assertNotNull(check.last);
    }

    @Test
    public void testNoViolationDecaysVl() throws Exception {
        data.fastClickVL = 2.0;
        Method m = FastClick.class.getDeclaredMethod("processViolation", Player.class, InventoryData.class,
                InventoryConfig.class, float.class, float.class);
        m.setAccessible(true);
        m.invoke(check, null, data, config, 0.0f, 0.0f);
        assertTrue(data.fastClickVL < 2.0);
        assertNull(check.last);
    }
}
