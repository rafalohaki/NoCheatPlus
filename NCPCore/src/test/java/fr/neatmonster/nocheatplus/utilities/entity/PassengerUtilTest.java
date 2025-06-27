package fr.neatmonster.nocheatplus.utilities.entity;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
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
        @Override public java.util.List<Entity> getEntityPassengers(Entity e) { return Collections.emptyList(); }
        @Override
        public boolean addPassenger(Entity entity, Entity vehicle) {
            called = true;
            return true;
        }
    }

    private static class CountingVehicleAccess extends DummyVehicleAccess {
        int count;
        @Override
        public boolean addPassenger(Entity entity, Entity vehicle) {
            count++;
            return super.addPassenger(entity, vehicle);
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
    public void testAddPassengerWithRetryImmediate() throws Exception {
        CountingVehicleAccess access = new CountingVehicleAccess();
        PassengerUtil util = newUtil(access);
        Method m = PassengerUtil.class.getDeclaredMethod("addPassengerWithRetry", Entity.class, Entity.class, int.class);
        m.setAccessible(true);
        Entity passenger = mock(Entity.class);
        Entity vehicle = mock(Entity.class);
        CompletableFuture<Boolean> res = (CompletableFuture<Boolean>) m.invoke(util, passenger, vehicle, 2);
        assertTrue(res.get());
        assertEquals(1, access.count);
    }

    @Test
    public void testHandlePassengerSchedulingBoatMultiplePassengers() throws Exception {
        CountingVehicleAccess access = new CountingVehicleAccess();
        PassengerUtil util = newUtil(access);
        Method sched = PassengerUtil.class.getDeclaredMethod("handlePassengerScheduling", Player.class,
                Entity.class, MovingConfig.class, MovingData.class, boolean.class);
        sched.setAccessible(true);
        Player player1 = mock(Player.class);
        Player player2 = mock(Player.class);
        Entity vehicle = mock(Entity.class);
        when(vehicle.getType()).thenReturn(EntityType.BOAT);
        MovingConfig cfg = newConfig();
        MovingData d1 = newData();
        MovingData d2 = newData();
        sched.invoke(util, player1, vehicle, cfg, d1, false);
        sched.invoke(util, player2, vehicle, cfg, d2, false);
        assertEquals(2, access.count);
    }
}
