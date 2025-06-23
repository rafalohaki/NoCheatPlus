package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

import org.bukkit.entity.Player;
import org.bukkit.Server;
import org.bukkit.Bukkit;
import java.lang.reflect.Proxy;
import org.junit.Test;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.player.SurvivalFly;
import fr.neatmonster.nocheatplus.worlds.WorldDataManager;
import fr.neatmonster.nocheatplus.permissions.PermissionRegistry;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;
import fr.neatmonster.nocheatplus.players.PlayerData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.worlds.IWorldData;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.registry.ComponentRegistry;
import fr.neatmonster.nocheatplus.components.registry.GenericInstanceRegistry;
import fr.neatmonster.nocheatplus.components.registry.DefaultGenericInstanceRegistry;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.components.registry.ComponentRegistryProvider;
import fr.neatmonster.nocheatplus.components.registry.setup.RegistrationContext;
import fr.neatmonster.nocheatplus.logging.StaticLog;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ActionFactory;
import fr.neatmonster.nocheatplus.actions.ActionFactoryFactory;
import fr.neatmonster.nocheatplus.worlds.WorldDataManager;
import fr.neatmonster.nocheatplus.worlds.IWorldDataManager;
import fr.neatmonster.nocheatplus.event.mini.EventRegistryBukkit;
import fr.neatmonster.nocheatplus.permissions.PermissionRegistry;
import fr.neatmonster.nocheatplus.components.registry.event.IHandle;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.checks.moving.location.tracking.LocationTrace.TraceEntryPool;
import fr.neatmonster.nocheatplus.players.IPlayerDataManager;
import fr.neatmonster.nocheatplus.players.PlayerDataManager;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.logging.LogManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;

public class TestSurvivalFlyWeb {

    static class DummyPlayerLocation extends PlayerLocation {
        boolean bubble = false;
        boolean dragged = false;
        DummyPlayerLocation() { super(null, null); }
        @Override public boolean isInBubbleStream() { return bubble; }
        @Override public boolean isDraggedByBubbleStream() { return dragged; }
    }

    /** Minimal API implementation for MovingData. */
    static class UnitTestNoCheatPlusAPI implements NoCheatPlusAPI {
        private final WorldDataManager worldDataManager = new WorldDataManager();
        private final DefaultGenericInstanceRegistry genericInstanceRegistry = new DefaultGenericInstanceRegistry();

        UnitTestNoCheatPlusAPI() {
            StaticLog.setUseLogManager(false);
            genericInstanceRegistry.registerGenericInstance(TraceEntryPool.class, new TraceEntryPool(16));
            genericInstanceRegistry.registerGenericInstance(WRPT.class, new WRPT());
        }

        @Override public boolean addComponent(Object component) { throw new UnsupportedOperationException(); }
        @Override public void removeComponent(Object component) { throw new UnsupportedOperationException(); }
        @Override public <T> Collection<ComponentRegistry<T>> getComponentRegistries(Class<ComponentRegistry<T>> clazz) { throw new UnsupportedOperationException(); }
        @Override public <T> T registerGenericInstance(T instance) { throw new UnsupportedOperationException(); }
        @Override public <T, TI extends T> T registerGenericInstance(Class<T> registerFor, TI instance) { return genericInstanceRegistry.registerGenericInstance(registerFor, instance); }
        @Override public <T> T getGenericInstance(Class<T> registeredFor) { return genericInstanceRegistry.getGenericInstance(registeredFor); }
        @Override public <T> T unregisterGenericInstance(Class<T> registeredFor) { return genericInstanceRegistry.unregisterGenericInstance(registeredFor); }
        @Override public <T> IGenericInstanceHandle<T> getGenericInstanceHandle(Class<T> registeredFor) { return genericInstanceRegistry.getGenericInstanceHandle(registeredFor); }
        @Override public boolean addComponent(Object obj, boolean allowComponentFactory) { throw new UnsupportedOperationException(); }
        @Override public void addFeatureTags(String key, Collection<String> featureTags) { }
        @Override public void setFeatureTags(String key, Collection<String> featureTags) { }
        @Override public boolean hasFeatureTag(String key, String feature) { return false; }
        @Override public Map<String, Set<String>> getAllFeatureTags() { throw new UnsupportedOperationException(); }
        @Override public int sendAdminNotifyMessage(String message) { return 1; }
        @Override public void sendMessageOnTick(String playerName, String message) { }
        @Override public boolean allowLogin(String playerName) { throw new UnsupportedOperationException(); }
        @Override public int allowLoginAll() { throw new UnsupportedOperationException(); }
        @Override public void denyLogin(String playerName, long duration) { throw new UnsupportedOperationException(); }
        @Override public boolean isLoginDenied(String playerName) { throw new UnsupportedOperationException(); }
        @Override public String[] getLoginDeniedPlayers() { throw new UnsupportedOperationException(); }
        @Override public boolean isLoginDenied(String playerName, long time) { throw new UnsupportedOperationException(); }
        @Override public BukkitAudiences adventure() { throw new UnsupportedOperationException(); }
        @Override public LogManager getLogManager() { throw new UnsupportedOperationException(); }
        private final BlockChangeTracker tracker = new BlockChangeTracker();
        @Override public BlockChangeTracker getBlockChangeTracker() { return tracker; }
        @Override public EventRegistryBukkit getEventRegistry() { throw new UnsupportedOperationException(); }
        @Override public PermissionRegistry getPermissionRegistry() { return new PermissionRegistry(10000); }
        @Override public WorldDataManager getWorldDataManager() { return worldDataManager; }
        @Override public IPlayerDataManager getPlayerDataManager() { throw new UnsupportedOperationException(); }
        @Override public RegistrationContext newRegistrationContext() { throw new UnsupportedOperationException(); }
        @Override public void register(RegistrationContext context) { throw new UnsupportedOperationException(); }
        @Override public ActionFactoryFactory getActionFactoryFactory() { ActionFactoryFactory factory = getGenericInstance(ActionFactoryFactory.class); if (factory == null) { factory = ActionFactory::new; genericInstanceRegistry.registerGenericInstance(ActionFactoryFactory.class, factory); } return factory; }
        @Override public ActionFactoryFactory setActionFactoryFactory(ActionFactoryFactory actionFactoryFactory) { return genericInstanceRegistry.registerGenericInstance(ActionFactoryFactory.class, actionFactoryFactory); }
    }

    private double[] invokeVDistWeb(SurvivalFly sf, PlayerMoveData move, MovingData data, MovingConfig cc, PlayerLocation from) throws Exception {
        Method m = SurvivalFly.class.getDeclaredMethod("vDistWeb", Player.class, PlayerMoveData.class, boolean.class, double.class, long.class, MovingData.class, MovingConfig.class, PlayerLocation.class);
        m.setAccessible(true);
        return (double[]) m.invoke(sf, null, move, false, 0.0, 0L, data, cc, from);
    }

    private ArrayList<String> getTags(SurvivalFly sf) throws Exception {
        Field f = SurvivalFly.class.getDeclaredField("tags");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        ArrayList<String> tags = (ArrayList<String>) f.get(sf);
        return tags;
    }

    @Test
    public void testAscendingWeb() throws Exception {
        Field apiField = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        apiField.setAccessible(true);
        apiField.set(null, new UnitTestNoCheatPlusAPI());
        // Dummy server for Bukkit.getVersion().
        Server dummyServer = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getVersion".equals(method.getName())) return "git-Spigot";
                    if ("getBukkitVersion".equals(method.getName())) return "1.18.2-R0.1-SNAPSHOT";
                    if (method.getReturnType().isPrimitive()) {
                        if (method.getReturnType() == boolean.class) return false;
                        return 0;
                    }
                    return null;
                });
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, dummyServer);
        WorldDataManager worldMan = new WorldDataManager();
        IWorldData world = worldMan.getDefaultWorldData();
        MovingConfig cc = new MovingConfig(world);

        PermissionRegistry perm = new PermissionRegistry(10000);
        for (RegisteredPermission rp : Permissions.getPermissions()) {
            perm.addRegisteredPermission(rp);
        }
        new PlayerDataManager(worldMan, perm); // initialize DataManager.instance
        SurvivalFly sf = new SurvivalFly();
        PlayerData pData = new PlayerData(java.util.UUID.randomUUID(), "Test", perm);
        MovingData data = new MovingData(cc, pData);
        PlayerMoveData move = new PlayerMoveData();
        move.yDistance = 0.2;
        move.from.onGround = true;
        move.from.inWeb = true;

        double[] res = invokeVDistWeb(sf, move, data, cc, new DummyPlayerLocation());
        assertEquals(0.1, res[0], 1e-8);
        assertEquals(0.1, res[1], 1e-8);
        assertTrue(getTags(sf).contains("vweb"));
    }

    @Test
    public void testDescendingWeb() throws Exception {
        Field apiField = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        apiField.setAccessible(true);
        apiField.set(null, new UnitTestNoCheatPlusAPI());
        Server dummyServer = (Server) Proxy.newProxyInstance(Server.class.getClassLoader(), new Class<?>[]{Server.class},
                (proxy, method, args) -> {
                    if ("getVersion".equals(method.getName())) return "git-Spigot";
                    if ("getBukkitVersion".equals(method.getName())) return "1.18.2-R0.1-SNAPSHOT";
                    if (method.getReturnType().isPrimitive()) {
                        if (method.getReturnType() == boolean.class) return false;
                        return 0;
                    }
                    return null;
                });
        Field serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        serverField.set(null, dummyServer);
        WorldDataManager worldMan = new WorldDataManager();
        IWorldData world = worldMan.getDefaultWorldData();
        MovingConfig cc = new MovingConfig(world);

        PermissionRegistry perm = new PermissionRegistry(10000);
        for (RegisteredPermission rp : Permissions.getPermissions()) {
            perm.addRegisteredPermission(rp);
        }
        new PlayerDataManager(worldMan, perm);
        SurvivalFly sf = new SurvivalFly();
        PlayerData pData = new PlayerData(java.util.UUID.randomUUID(), "Test", perm);
        MovingData data = new MovingData(cc, pData);
        data.insideMediumCount = 5;
        PlayerMoveData move = new PlayerMoveData();
        move.yDistance = -0.1;
        move.from.inWeb = true;

        double[] res = invokeVDistWeb(sf, move, data, cc, new DummyPlayerLocation());
        double expectedAllowed = -0.0624 * 0.98;
        assertEquals(expectedAllowed, res[0], 1e-8);
        assertEquals(Math.abs(-0.1 - expectedAllowed), res[1], 1e-8);
        assertTrue(getTags(sf).contains("vwebdesc"));
    }
}
