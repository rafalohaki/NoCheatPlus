package fr.neatmonster.nocheatplus.utilities.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.components.entity.IEntityAccessVehicle;
import fr.neatmonster.nocheatplus.components.registry.event.IHandle;

public class PassengerUtilTest {

    private static class SimpleHandle<T> implements IHandle<T> {
        private final T handle;
        SimpleHandle(T h){ this.handle = h; }
        @Override public T getHandle(){ return handle; }
        public void disableHandle(){}
    }

    private static class DummyVehicleAccess implements IEntityAccessVehicle {
        boolean called;
        @Override public java.util.List<Entity> getEntityPassengers(Entity vehicle) { return Collections.emptyList(); }
        @Override
        public boolean addPassenger(Entity passenger, Entity vehicle) {
            called = true;
            return true;
        }
    }

    private static class FailingVehicleAccess extends DummyVehicleAccess {
        private final int fails;
        private int calls;
        FailingVehicleAccess(int fails) { this.fails = fails; }
        @Override
        public boolean addPassenger(Entity passenger, Entity vehicle) {
            calls++;
            if (calls <= fails) {
                return false;
            }
            return super.addPassenger(passenger, vehicle);
        }
    }

    private sun.misc.Unsafe unsafe;

    @BeforeEach
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
    public void testAddPassengerWithRetrySuccessNoPlugin() throws Exception {
        DummyVehicleAccess access = new DummyVehicleAccess();
        PassengerUtil util = newUtil(access);
        Method m = PassengerUtil.class.getDeclaredMethod("addPassengerWithRetry", Entity.class, Entity.class, int.class);
        m.setAccessible(true);
        Player player = mock(Player.class);
        Entity vehicle = mock(Entity.class);
        @SuppressWarnings("unchecked")
        java.util.concurrent.CompletableFuture<Boolean> res = (java.util.concurrent.CompletableFuture<Boolean>) m.invoke(util, player, vehicle, 1);
        assertTrue(res.get());
    }

    @Test
    public void testAddPassengerWithRetryFailNoPlugin() throws Exception {
        FailingVehicleAccess access = new FailingVehicleAccess(1);
        PassengerUtil util = newUtil(access);
        Method m = PassengerUtil.class.getDeclaredMethod("addPassengerWithRetry", Entity.class, Entity.class, int.class);
        m.setAccessible(true);
        Player player = mock(Player.class);
        Entity vehicle = mock(Entity.class);
        @SuppressWarnings("unchecked")
        java.util.concurrent.CompletableFuture<Boolean> res = (java.util.concurrent.CompletableFuture<Boolean>) m.invoke(util, player, vehicle, 1);
        assertFalse(res.get());
    }
}
