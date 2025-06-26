package fr.neatmonster.nocheatplus.utilities.entity;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.Server;
import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.components.registry.event.IHandle;
import fr.neatmonster.nocheatplus.components.entity.IEntityAccessVehicle;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;

public class PassengerUtilTest {

    private static class SimpleHandle<T> implements IHandle<T> {
        private final T handle;
        SimpleHandle(T h){ this.handle = h; }
        @Override public T getHandle(){ return handle; }
        public void disableHandle(){}
    }

    private static class DummyVehicleAccess implements IEntityAccessVehicle {
        boolean called;
        Map<Entity, Integer> attempts = new HashMap<>();
        @Override public java.util.List<Entity> getEntityPassengers(Entity e) { return Collections.emptyList(); }
        @Override
        public boolean addPassenger(Entity entity, Entity vehicle) {
            called = true;
            int c = attempts.getOrDefault(entity, 0);
            attempts.put(entity, c + 1);
            return c > 0;
        }
    }

    private sun.misc.Unsafe unsafe;
    private Server previousServer;

    @Before
    public void setup() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (sun.misc.Unsafe) f.get(null);
        previousServer = org.bukkit.Bukkit.getServer();
    }

    private PassengerUtil newUtil(DummyVehicleAccess access) throws Exception {
        PassengerUtil util = (PassengerUtil) unsafe.allocateInstance(PassengerUtil.class);
        Field f = PassengerUtil.class.getDeclaredField("handleVehicle");
        f.setAccessible(true);
        f.set(util, new SimpleHandle<>(access));
        return util;
    }

    private MovingData newData() throws Exception {
        return (MovingData) unsafe.allocateInstance(MovingData.class);
    }

    private MovingConfig newConfig() throws Exception {
        MovingConfig cfg = (MovingConfig) unsafe.allocateInstance(MovingConfig.class);
        Field f = MovingConfig.class.getDeclaredField("schedulevehicleSetPassenger");
        f.setAccessible(true);
        f.setBoolean(cfg, false);
        return cfg;
    }

    @org.junit.After
    public void teardown() {
        if (previousServer != null && org.bukkit.Bukkit.getServer() != previousServer) {
            org.bukkit.Bukkit.setServer(previousServer);
        }
    }

    @Test
    public void testTeleportPlayerPassengerNullArgs() throws Exception {
        PassengerUtil util = newUtil(new DummyVehicleAccess());
        Method m = PassengerUtil.class.getDeclaredMethod("teleportPlayerPassenger", Player.class, Entity.class,
                org.bukkit.Location.class, boolean.class, MovingData.class, boolean.class);
        m.setAccessible(true);
        MovingData data = newData();
        boolean res = (boolean) m.invoke(util, null, null, null, false, data, false);
        assertFalse(res);
    }

    @Test
    public void testScheduleSetPassengerDirectAdd() throws Exception {
        DummyVehicleAccess access = new DummyVehicleAccess();
        PassengerUtil util = newUtil(access);
        Method sched = PassengerUtil.class.getDeclaredMethod("handlePassengerScheduling", Player.class,
                Entity.class, MovingConfig.class, MovingData.class, boolean.class);
        sched.setAccessible(true);
        Player player = mock(Player.class);
        Entity vehicle = mock(Entity.class);
        when(vehicle.getType()).thenReturn(EntityType.MINECART);
        MovingConfig cfg = newConfig();
        MovingData data = newData();
        sched.invoke(util, player, vehicle, cfg, data, false);
        assertTrue(access.called);
    }

    @Test
    public void testAddPassengerWithRetryMultiple() throws Exception {
        DummyVehicleAccess access = new DummyVehicleAccess();
        PassengerUtil util = newUtil(access);
        Method m = PassengerUtil.class.getDeclaredMethod("addPassengerWithRetry", Entity.class, Entity.class, int.class);
        m.setAccessible(true);

        org.junit.Assume.assumeTrue(org.bukkit.Bukkit.getScheduler() != null);

        List<Runnable> tasks = new ArrayList<>();
        Server server = createServer(tasks);
        if (previousServer == null) {
            org.bukkit.Bukkit.setServer(server);
        } else {
            server = previousServer;
        }

        Plugin plugin = mock(Plugin.class);
        Field pf = PassengerUtil.class.getDeclaredField("plugin");
        pf.setAccessible(true);
        pf.set(util, plugin);

        Entity boat = mock(Entity.class);
        when(boat.getType()).thenReturn(EntityType.BOAT);
        Entity p1 = mock(Entity.class);
        Entity p2 = mock(Entity.class);

        m.invoke(util, p1, boat, 1);
        m.invoke(util, p2, boat, 1);

        assertEquals(2, access.attempts.get(p1).intValue());
        assertEquals(2, access.attempts.get(p2).intValue());
    }

    private static Server createServer(List<Runnable> tasks) {
        PluginManager pm = (PluginManager) Proxy.newProxyInstance(PassengerUtilTest.class.getClassLoader(),
                new Class[]{PluginManager.class}, (proxy, method, args) -> defaultValue(method.getReturnType()));
        BukkitScheduler scheduler = (BukkitScheduler) Proxy.newProxyInstance(PassengerUtilTest.class.getClassLoader(),
                new Class[]{BukkitScheduler.class}, (proxy, method, args) -> {
                    if ("scheduleSyncDelayedTask".equals(method.getName())) {
                        Runnable r = (Runnable) args[1];
                        tasks.add(r);
                        r.run();
                        return 1;
                    }
                    return defaultValue(method.getReturnType());
                });
        InvocationHandler serverHandler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getPluginManager": return pm;
                case "getScheduler": return scheduler;
                case "getLogger": return Logger.getLogger("TestServer");
                default: return defaultValue(method.getReturnType());
            }
        };
        return (Server) Proxy.newProxyInstance(PassengerUtilTest.class.getClassLoader(), new Class[]{Server.class}, serverHandler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class || type == short.class || type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }
}
