package fr.neatmonster.nocheatplus.utilities.entity;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.components.registry.event.IHandle;
import fr.neatmonster.nocheatplus.components.entity.IEntityAccessVehicle;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.logging.LogManager;
import fr.neatmonster.nocheatplus.logging.LoggerID;
import fr.neatmonster.nocheatplus.logging.StreamID;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.utilities.CheckUtils;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;

public class PassengerUtilTest {

    private static class SimpleHandle<T> implements IHandle<T> {
        private final T handle;
        SimpleHandle(T h){ this.handle = h; }
        @Override public T getHandle(){ return handle; }
        public void disableHandle(){}
    }

    private static class DummyVehicleAccess implements IEntityAccessVehicle {
        boolean called;
        @Override public java.util.List<Entity> getEntityPassengers(Entity e) { return Collections.emptyList(); }
        @Override
        public boolean addPassenger(Entity entity, Entity vehicle) {
            called = true;
            return true;
        }
    }

    private static class CollectingLogManager implements LogManager {
        final java.util.List<String> debugMessages = new java.util.ArrayList<>();
        @Override public void debug(StreamID streamID, String message) { debugMessages.add(message); }
        @Override public void debug(StreamID streamID, Throwable t) {}
        @Override public void info(StreamID streamID, String message) {}
        @Override public void info(StreamID streamID, Throwable t) {}
        @Override public void warning(StreamID streamID, String message) {}
        @Override public void warning(StreamID streamID, Throwable t) {}
        @Override public void severe(StreamID streamID, String message) {}
        @Override public void severe(StreamID streamID, Throwable t) {}
        @Override public void log(StreamID streamID, java.util.logging.Level level, String message) {}
        @Override public void log(StreamID streamID, java.util.logging.Level level, Throwable t) {}
        @Override public StreamID getVoidStreamID() { return Streams.STATUS; }
        @Override public StreamID getInitStreamID() { return Streams.INIT; }
        @Override public String getDefaultPrefix() { return Streams.defaultPrefix; }
        @Override public boolean hasLogger(String name) { return false; }
        @Override public boolean hasLogger(LoggerID loggerID) { return false; }
        @Override public LoggerID getLoggerID(String name) { return null; }
        @Override public boolean hasStream(String name) { return false; }
        @Override public boolean hasStream(StreamID streamID) { return false; }
        @Override public StreamID getStreamID(String name) { return null; }
    }

    private static class LoggingAPI implements java.lang.reflect.InvocationHandler {
        final CollectingLogManager logManager = new CollectingLogManager();
        NoCheatPlusAPI create() {
            return (NoCheatPlusAPI) java.lang.reflect.Proxy.newProxyInstance(
                    LoggingAPI.class.getClassLoader(),
                    new Class[]{fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class}, this);
        }
        @Override public Object invoke(Object proxy, Method method, Object[] args) {
            if ("getLogManager".equals(method.getName())) return logManager;
            Class<?> ret = method.getReturnType();
            if (ret.isPrimitive()) {
                if (ret == boolean.class) return false;
                if (ret == int.class) return 0;
                if (ret == long.class) return 0L;
                if (ret == double.class) return 0D;
                if (ret == float.class) return 0F;
                if (ret == short.class) return (short) 0;
                if (ret == byte.class) return (byte) 0;
                if (ret == char.class) return (char) 0;
            }
            return null;
        }
    }

    private sun.misc.Unsafe unsafe;

    @Before
    public void setup() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        unsafe = (sun.misc.Unsafe) f.get(null);
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
    public void testScheduleSetPassengerDebugLogging() throws Exception {
        DummyVehicleAccess access = new DummyVehicleAccess() {
            @Override
            public boolean addPassenger(Entity entity, Entity vehicle) {
                called = true;
                return false;
            }
        };
        LoggingAPI apiHandler = new LoggingAPI();
        fr.neatmonster.nocheatplus.components.NoCheatPlusAPI api = apiHandler.create();
        java.lang.reflect.Method setApi = NCPAPIProvider.class.getDeclaredMethod("setNoCheatPlusAPI", fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class);
        setApi.setAccessible(true);
        setApi.invoke(null, api);

        PassengerUtil util = newUtil(access);
        Method sched = PassengerUtil.class.getDeclaredMethod("handlePassengerScheduling", Player.class,
                Entity.class, MovingConfig.class, MovingData.class, boolean.class);
        sched.setAccessible(true);
        Player player = mock(Player.class);
        Entity vehicle = mock(Entity.class);
        when(vehicle.getType()).thenReturn(EntityType.MINECART);
        MovingConfig cfg = newConfig();
        MovingData data = newData();
        sched.invoke(util, player, vehicle, cfg, data, true);

        assertTrue(access.called);
        assertFalse(apiHandler.logManager.debugMessages.isEmpty());

        setApi.invoke(null, (Object) null);
    }
}
