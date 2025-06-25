package fr.neatmonster.nocheatplus.test;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.MovingListener;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveInfo;
import fr.neatmonster.nocheatplus.checks.moving.util.AuxMoving;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.players.IPlayerDataManager;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.PlayerData;
import fr.neatmonster.nocheatplus.players.PlayerDataManager;
import fr.neatmonster.nocheatplus.permissions.PermissionRegistry;
import fr.neatmonster.nocheatplus.checks.combined.CombinedData;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.utilities.map.FakeBlockCache;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class TestTeleportHandling {

    static class SimpleHandle<T> implements IGenericInstanceHandle<T> {
        private final T handle;
        SimpleHandle(T h){ this.handle = h; }
        @Override public T getHandle(){ return handle; }
        @Override public void disableHandle(){}
    }

    private static void setupAPI(sun.misc.Unsafe u) throws Exception {
        IAttributeAccess attr = (IAttributeAccess) Proxy.newProxyInstance(IAttributeAccess.class.getClassLoader(),
                new Class<?>[]{IAttributeAccess.class}, (p,m,a) -> 1.0);
        MCAccess mc = (MCAccess) Proxy.newProxyInstance(MCAccess.class.getClassLoader(),
                new Class<?>[]{MCAccess.class}, (p,m,a) -> {
                    if ("getBlockCache".equals(m.getName())) return new FakeBlockCache();
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
                        Class<?> c = (Class<?>) a[0];
                        if (c == MCAccess.class) return mcHandle;
                        if (c == IAttributeAccess.class) return attrHandle;
                        return null;
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

        Object pdm = u.allocateInstance(PlayerDataManager.class);
        Field eh = PlayerDataManager.class.getDeclaredField("executionHistories");
        eh.setAccessible(true);
        eh.set(pdm, new java.util.HashMap<>());
        Field dm = DataManager.class.getDeclaredField("instance");
        dm.setAccessible(true);
        dm.set(null, pdm);
    }

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static World createWorld() {
        InvocationHandler h = (proxy, method, args) -> {
            if ("getName".equals(method.getName())) return "dummy";
            Class<?> r = method.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive()) return 0;
            return null;
        };
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class[]{World.class}, h);
    }

    private static Player createPlayer() {
        InvocationHandler h = (proxy, method, args) -> {
            Class<?> r = method.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive()) return 0;
            return null;
        };
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, h);
    }

    private static class Flag { boolean value; }

    public static class DummyPlayerData extends PlayerData {
        Flag flag;
        CombinedData combined = new CombinedData();
        private DummyPlayerData() { super(null, "", null); }
        @Override public boolean isPlayerSetBackScheduled() { return flag.value; }
        @Override public boolean isDebugActive(CheckType t) { return false; }
        @Override public <T> T getGenericInstance(Class<T> c) { if (c == CombinedData.class) return c.cast(combined); return null; }
    }

    private static DummyPlayerData createPlayerData(Flag flag, sun.misc.Unsafe u) throws Exception {
        DummyPlayerData pd = (DummyPlayerData) u.allocateInstance(DummyPlayerData.class);
        pd.flag = flag;
        return pd;
    }

    public static class DummyPlayerDataManager extends fr.neatmonster.nocheatplus.players.PlayerDataManager {
        PlayerData pData;
        private DummyPlayerDataManager() {
            super((fr.neatmonster.nocheatplus.worlds.WorldDataManager) null,
                  (PermissionRegistry) null);
        }
        @Override public PlayerData getPlayerData(Player player, boolean create) { return pData; }
        @Override public PlayerData getPlayerData(Player player) { return pData; }
        @Override public PlayerData getPlayerData(String playerName) { return pData; }
        @Override public PlayerData getPlayerData(java.util.UUID id) { return pData; }
    }

    private static DummyPlayerDataManager createDataMan(PlayerData data, sun.misc.Unsafe u) throws Exception {
        DummyPlayerDataManager man = (DummyPlayerDataManager) u.allocateInstance(DummyPlayerDataManager.class);
        man.pData = data;
        return man;
    }

    public static class DummyAuxMoving extends AuxMoving {
        @Override
        public synchronized PlayerMoveInfo usePlayerMoveInfo() {
            MCAccess mc = (MCAccess) Proxy.newProxyInstance(MCAccess.class.getClassLoader(), new Class[]{MCAccess.class},
                    (p,m,a) -> new FakeBlockCache());
            return new PlayerMoveInfo(new SimpleHandle<>(mc));
        }
        @Override
        public synchronized void returnPlayerMoveInfo(PlayerMoveInfo moveInfo) {
        }
    }

    private MovingListener listener;
    private MovingConfig config;
    private World world;
    private Player player;
    private sun.misc.Unsafe unsafe;

    @Before
    public void setup() throws Exception {
        unsafe = getUnsafe();
        setupAPI(unsafe);
        listener = (MovingListener) unsafe.allocateInstance(MovingListener.class);
        Field ct = listener.getClass().getSuperclass().getDeclaredField("checkType");
        ct.setAccessible(true);
        ct.set(listener, CheckType.MOVING);
        DummyAuxMoving aux = (DummyAuxMoving) unsafe.allocateInstance(DummyAuxMoving.class);
        Field fAux = MovingListener.class.getDeclaredField("aux");
        fAux.setAccessible(true);
        fAux.set(listener, aux);
        config = (MovingConfig) unsafe.allocateInstance(MovingConfig.class);
        Field y = MovingConfig.class.getDeclaredField("yOnGround");
        y.setAccessible(true);
        y.set(config, 0.0);
        Field lct = MovingConfig.class.getDeclaredField("loadChunksOnTeleport");
        lct.setAccessible(true);
        lct.set(config, false);
        world = createWorld();
        player = createPlayer();
    }

    private boolean invokeHandle(Location from, Location to, MovingData data, Flag flag) throws Exception {
        PlayerData pData = createPlayerData(flag, unsafe);
        Field f = DataManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, createDataMan(pData, unsafe));
        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        Method m = MovingListener.class.getDeclaredMethod("handleTeleportedOnMove", Player.class, PlayerMoveEvent.class, MovingData.class, MovingConfig.class, IPlayerData.class);
        m.setAccessible(true);
        return (boolean) m.invoke(listener, player, event, data, config, pData);
    }


    @Test
    public void testScheduledEarlyReturn() throws Exception {
        Location from = new Location(world, 1, 2, 3);
        Location to = new Location(world, 2, 2, 3);
        MovingData data = (MovingData) unsafe.allocateInstance(MovingData.class);
        data.setTeleported(new Location(world, 0, 0, 0));
        Flag flag = new Flag();
        flag.value = true;
        boolean early = invokeHandle(from, to, data, flag);
        assertTrue(early);
        assertTrue(data.hasTeleported());
    }

    @Test
    public void testLeftoverTeleported() throws Exception {
        Location from = new Location(world, 1, 2, 3);
        Location to = new Location(world, 2, 2, 3);
        MovingData data = (MovingData) unsafe.allocateInstance(MovingData.class);
        data.setTeleported(new Location(world, 0, 0, 0));
        Flag flag = new Flag();
        boolean early = invokeHandle(from, to, data, flag);
        assertFalse(early);
        assertFalse(data.hasTeleported());
    }
}
