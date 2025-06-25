package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Modifier;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.player.CreativeFly;
import fr.neatmonster.nocheatplus.checks.moving.velocity.SimpleEntry;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;

import org.junit.Before;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class TestCreativeFlyHelpers {

    static class SimpleHandle<T> implements IGenericInstanceHandle<T> {
        private final T handle;
        SimpleHandle(T h){ this.handle = h; }
        @Override public T getHandle(){ return handle; }
        @Override public void disableHandle(){}
    }

    static class DummyPlayerLocation extends PlayerLocation {
        final Player player;
        final World world;
        DummyPlayerLocation(Player p) {
            super(new SimpleHandle<MCAccess>(null), null);
            this.player = p;
            this.world = createWorld();
        }
        @Override public Player getPlayer(){ return player; }
        @Override public org.bukkit.Location getLocation() { return new org.bukkit.Location(world,0,0,0); }
    }

    private static World createWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class[]{World.class}, (p,m,a) -> {
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive()) return 0;
            return null;
        });
    }

    private static Player createPlayer() {
        PlayerInventory inv = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
                new Class<?>[]{PlayerInventory.class},
                (proxy, method, args) -> {
                    if ("getChestplate".equals(method.getName())) {
                        return new ItemStack(Material.AIR);
                    }
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if ("getInventory".equals(method.getName())) return inv;
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
    }

    private static void setupAPI() throws Exception {
        IAttributeAccess attr = (IAttributeAccess)Proxy.newProxyInstance(IAttributeAccess.class.getClassLoader(),
                new Class<?>[]{IAttributeAccess.class}, (p,m,a) -> 1.0);
        MCAccess mc = (MCAccess)Proxy.newProxyInstance(MCAccess.class.getClassLoader(),
                new Class<?>[]{MCAccess.class}, (p,m,a) -> {
                    if ("getFasterMovementAmplifier".equals(m.getName())) return Double.POSITIVE_INFINITY;
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
        IGenericInstanceHandle<IAttributeAccess> attrHandle = new SimpleHandle<>(attr);
        IGenericInstanceHandle<MCAccess> mcHandle = new SimpleHandle<>(mc);
        Object api = Proxy.newProxyInstance(NCPAPIProvider.class.getClassLoader(),
                new Class<?>[]{fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class},
                (pr,m,a) -> {
                    if ("getGenericInstanceHandle".equals(m.getName())) {
                        Class<?> c = (Class<?>)a[0];
                        if (c == MCAccess.class) return mcHandle;
                        if (c == IAttributeAccess.class) return attrHandle;
                        return new SimpleHandle<>(null);
                    }
                    if ("getBlockChangeTracker".equals(m.getName())) return new BlockChangeTracker();
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
        Field f = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        f.setAccessible(true);
        f.set(null, api);

        // Minimal DataManager setup using mocks
        Object pdm = mock(fr.neatmonster.nocheatplus.players.PlayerDataManager.class);
        Field dm = fr.neatmonster.nocheatplus.players.DataManager.class.getDeclaredField("instance");
        dm.setAccessible(true);
        dm.set(null, pdm);
    }

    private static MovingData newData() {
        return mock(MovingData.class);
    }

    @Before
    public void init() throws Exception {
        setupAPI();
    }

    @Test
    public void testApplyVerticalVelocityAdjustmentWithVelocity() throws Exception {
        CreativeFly cf = new CreativeFly();
        MovingData data = newData();
        when(data.getOrUseVerticalVelocity(anyDouble())).thenReturn(new SimpleEntry(0.3,1));
        double[] arr = {0.3, 0.0};
        Method m = CreativeFly.class.getDeclaredMethod("applyVerticalVelocityAdjustment", MovingData.class, double.class, double.class, double[].class);
        m.setAccessible(true);
        m.invoke(cf, data, 0.3, 0.0, arr);
        assertEquals(0.0, arr[0], 1e-6);
        assertEquals(0.0, arr[1], 1e-6);
    }

    @Test
    public void testApplyVerticalVelocityAdjustmentNoVelocity() throws Exception {
        CreativeFly cf = new CreativeFly();
        MovingData data = newData();
        when(data.getOrUseVerticalVelocity(anyDouble())).thenReturn(null);
        double[] arr = {0.3, 0.1};
        Method m = CreativeFly.class.getDeclaredMethod("applyVerticalVelocityAdjustment", MovingData.class, double.class, double.class, double[].class);
        m.setAccessible(true);
        m.invoke(cf, data, 0.3, 0.1, arr);
        assertEquals(0.3, arr[0], 1e-6);
        assertEquals(0.1, arr[1], 1e-6);
    }

}
