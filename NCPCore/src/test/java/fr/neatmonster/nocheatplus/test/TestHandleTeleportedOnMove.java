package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.MovingListener;
import fr.neatmonster.nocheatplus.checks.moving.util.AuxMoving;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.PlayerData;
import fr.neatmonster.nocheatplus.players.PlayerDataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.permissions.PermissionRegistry;
import fr.neatmonster.nocheatplus.worlds.WorldDataManager;

public class TestHandleTeleportedOnMove {

    static class SimpleHandle<T> implements IGenericInstanceHandle<T> {
        private final T handle;
        SimpleHandle(T h){ this.handle = h; }
        @Override public T getHandle(){ return handle; }
        @Override public void disableHandle(){}
    }

    static class DummyPlayerData extends PlayerData {
        boolean scheduled;
        DummyPlayerData(boolean scheduled, PermissionRegistry reg) {
            super(UUID.randomUUID(), "dummy", reg);
            this.scheduled = scheduled;
        }
        @Override
        public boolean isPlayerSetBackScheduled() {
            return scheduled;
        }
        @Override
        public boolean isDebugActive(fr.neatmonster.nocheatplus.checks.CheckType checkType) {
            return false;
        }
    }

    static class DummyPlayerDataManager extends PlayerDataManager {
        DummyPlayerDataManager() { super(null, null); }
        DummyPlayerData pd;
        @Override
        public PlayerData getPlayerData(Player player, boolean create) { return pd; }
        @Override
        public PlayerData getPlayerData(Player player) { return pd; }
    }

    private DummyPlayerDataManager pdManager;
    private DummyPlayerData pData;
    private MovingListener listener;
    private MovingConfig config;

    private static Player createPlayer() {
        InvocationHandler handler = (pr, m, a) -> {
            switch (m.getName()) {
                case "getName": return "dummy";
                case "getUniqueId": return UUID.randomUUID();
                default:
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false;
                    if (r == double.class) return 0.0;
                    if (r == float.class) return 0.0f;
                    if (r == long.class) return 0L;
                    if (r == int.class) return 0;
                    if (r == short.class) return (short)0;
                    if (r == byte.class) return (byte)0;
                    if (r == char.class) return (char)0;
                    if (r.isPrimitive()) return 0;
                    return null;
            }
        };
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, handler);
    }

    private static World createWorld() {
        InvocationHandler handler = (pr, m, a) -> {
            if ("getName".equals(m.getName())) return "world";
            if ("getUID".equals(m.getName())) return UUID.randomUUID();
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r == double.class) return 0.0;
            if (r == float.class) return 0.0f;
            if (r == long.class) return 0L;
            if (r == int.class) return 0;
            if (r == short.class) return (short)0;
            if (r == byte.class) return (byte)0;
            if (r == char.class) return (char)0;
            if (r.isPrimitive()) return 0;
            return null;
        };
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class}, handler);
    }

    private static void setupAPI() throws Exception {
        Object mc = Proxy.newProxyInstance(MovingListener.class.getClassLoader(), new Class<?>[]{fr.neatmonster.nocheatplus.compat.MCAccess.class}, (p,m,a) -> {
            if ("getBlockCache".equals(m.getName())) {
                return new fr.neatmonster.nocheatplus.utilities.map.FakeBlockCache();
            }
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r == double.class) return 0.0;
            if (r == float.class) return 0.0f;
            if (r == long.class) return 0L;
            if (r == int.class) return 0;
            if (r == short.class) return (short)0;
            if (r == byte.class) return (byte)0;
            if (r == char.class) return (char)0;
            if (r.isPrimitive()) return 0;
            return null;
        });
        IGenericInstanceHandle<?> mcHandle = new SimpleHandle<>(mc);
        fr.neatmonster.nocheatplus.actions.ActionFactoryFactory actionFactoryFactory =
                library -> new fr.neatmonster.nocheatplus.actions.ActionFactory((java.util.Map<String,Object>) library);
        PermissionRegistry permRegistry = new PermissionRegistry(0);
        Object api = Proxy.newProxyInstance(NCPAPIProvider.class.getClassLoader(), new Class<?>[]{fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class}, (pr,m,a) -> {
            if ("getGenericInstanceHandle".equals(m.getName())) {
                Class<?> c = (Class<?>) a[0];
                if (c == fr.neatmonster.nocheatplus.compat.MCAccess.class) return mcHandle;
                return new SimpleHandle<>(null);
            }
            if ("getActionFactoryFactory".equals(m.getName())) return actionFactoryFactory;
            if ("setActionFactoryFactory".equals(m.getName())) return actionFactoryFactory;
            if ("getPermissionRegistry".equals(m.getName())) return permRegistry;
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive()) return 0;
            return null;
        });
        Field f = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        f.setAccessible(true);
        f.set(null, api);
    }

    @Before
    public void init() throws Exception {
        setupAPI();
        PermissionRegistry reg = new PermissionRegistry(0);
        pData = new DummyPlayerData(false, reg);
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        sun.misc.Unsafe u = (sun.misc.Unsafe) uf.get(null);
        pdManager = (DummyPlayerDataManager) u.allocateInstance(DummyPlayerDataManager.class);
        pdManager.pd = pData;
        Field dm = DataManager.class.getDeclaredField("instance");
        dm.setAccessible(true);
        dm.set(null, pdManager);
        config = (MovingConfig) u.allocateInstance(MovingConfig.class);
        java.lang.reflect.Field cf;
        cf = MovingConfig.class.getDeclaredField("loadChunksOnTeleport");
        cf.setAccessible(true);
        cf.setBoolean(config, false);
        cf = MovingConfig.class.getDeclaredField("yOnGround");
        cf.setAccessible(true);
        cf.setDouble(config, 0.0);
        listener = (MovingListener) u.allocateInstance(MovingListener.class);
        Field auxF = MovingListener.class.getDeclaredField("aux");
        auxF.setAccessible(true);
        auxF.set(listener, new AuxMoving());
        this.u = u;
    }

    private sun.misc.Unsafe u;

    private MovingData newData() throws Exception {
        MovingData data = (MovingData) u.allocateInstance(MovingData.class);
        java.lang.reflect.Field f;
        f = MovingData.class.getDeclaredField("pData");
        f.setAccessible(true);
        f.set(data, pData);
        f = MovingData.class.getDeclaredField("vDistAcc");
        f.setAccessible(true);
        f.set(data, new fr.neatmonster.nocheatplus.utilities.ds.count.ActionAccumulator(3,3));
        f = MovingData.class.getDeclaredField("hDistAcc");
        f.setAccessible(true);
        f.set(data, new fr.neatmonster.nocheatplus.utilities.ds.count.ActionAccumulator(1,100));
        return data;
    }

    private boolean invokeHandle(Player player, PlayerMoveEvent event, MovingData data) throws Exception {
        Method m = MovingListener.class.getDeclaredMethod("handleTeleportedOnMove", Player.class, PlayerMoveEvent.class, MovingData.class, MovingConfig.class, IPlayerData.class);
        m.setAccessible(true);
        return (boolean) m.invoke(listener, player, event, data, config, pData);
    }

    @Test
    public void testScheduledSetBack() throws Exception {
        pData.scheduled = true;
        Player player = createPlayer();
        World world = createWorld();
        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 1, 64, 0);
        MovingData data = newData();
        data.setTeleported(new Location(world, 5, 64, 0));
        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        boolean early = invokeHandle(player, event, data);
        assertTrue(early);
        assertTrue(event.isCancelled());
        assertTrue(data.hasTeleported());
    }

    @Test
    public void testLeftOverTeleported() throws Exception {
        Player player = createPlayer();
        World world = createWorld();
        Location from = new Location(world, 0, 64, 0);
        Location to = new Location(world, 1, 64, 0);
        MovingData data = newData();
        data.setTeleported(new Location(world, 5, 64, 0));
        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        boolean early = invokeHandle(player, event, data);
        assertFalse(early);
        assertFalse(event.isCancelled());
        assertFalse(data.hasTeleported());
    }
}
