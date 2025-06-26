package fr.neatmonster.nocheatplus.checks.moving.vehicle;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;

import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Strider;
import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;

import fr.neatmonster.nocheatplus.checks.moving.magic.MagicVehicle;
import fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveInfo;

public class VehicleBehaviorTest {

    private static class DummyVehicleMoveData extends VehicleMoveData {
        @Override
        public void setExtraMinecartProperties(final VehicleMoveInfo moveInfo) {
            // Do nothing for tests.
        }
    }

    static class SimpleHandle<T> implements IGenericInstanceHandle<T> {
        private final T handle;
        SimpleHandle(T h) { this.handle = h; }
        @Override public T getHandle() { return handle; }
        @Override public void disableHandle() { }
    }

    @Before
    public void setupAPI() throws Exception {
        Object api = java.lang.reflect.Proxy.newProxyInstance(
                NCPAPIProvider.class.getClassLoader(),
                new Class<?>[]{NoCheatPlusAPI.class},
                (proxy, method, args) -> {
                    if ("getGenericInstanceHandle".equals(method.getName())) {
                        return new SimpleHandle<>(null);
                    }
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
        Field f = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        f.setAccessible(true);
        f.set(null, api);
        Field dm = fr.neatmonster.nocheatplus.players.DataManager.class.getDeclaredField("instance");
        dm.setAccessible(true);
        dm.set(null, mock(fr.neatmonster.nocheatplus.players.PlayerDataManager.class));
    }

    private VehicleEnvelope.CheckDetails getDetails(VehicleEnvelope env) throws Exception {
        Field f = VehicleEnvelope.class.getDeclaredField("checkDetails");
        f.setAccessible(true);
        return (VehicleEnvelope.CheckDetails) f.get(env);
    }

    @Test
    public void testBoatBehavior() throws Exception {
        Entity boat = mock(Entity.class);
        when(boat.getType()).thenReturn(EntityType.BOAT);
        VehicleEnvelope env = new VehicleEnvelope();
        DummyVehicleMoveData data = new DummyVehicleMoveData();
        env.prepareCheckDetails(boat, null, data);
        VehicleEnvelope.CheckDetails details = getDetails(env);
        assertEquals(EntityType.BOAT, details.simplifiedType);
        assertEquals(MagicVehicle.maxAscend, details.maxAscend, 0.0);
    }

    @Test
    public void testMinecartBehavior() throws Exception {
        Minecart minecart = mock(Minecart.class);
        when(minecart.getType()).thenReturn(EntityType.MINECART);
        VehicleEnvelope env = new VehicleEnvelope();
        DummyVehicleMoveData data = new DummyVehicleMoveData();
        env.prepareCheckDetails(minecart, null, data);
        VehicleEnvelope.CheckDetails details = getDetails(env);
        assertEquals(EntityType.MINECART, details.simplifiedType);
        assertTrue(details.canRails);
    }

    @Test
    public void testHorseBehavior() throws Exception {
        AbstractHorse horse = mock(AbstractHorse.class);
        when(horse.getType()).thenReturn(EntityType.HORSE);
        VehicleEnvelope env = new VehicleEnvelope();
        DummyVehicleMoveData data = new DummyVehicleMoveData();
        env.prepareCheckDetails(horse, null, data);
        VehicleEnvelope.CheckDetails details = getDetails(env);
        assertEquals(EntityType.HORSE, details.simplifiedType);
        assertTrue(details.canJump);
    }

    @Test
    public void testStriderBehavior() throws Exception {
        Strider strider = mock(Strider.class);
        when(strider.getType()).thenReturn(EntityType.STRIDER);
        VehicleEnvelope env = new VehicleEnvelope();
        DummyVehicleMoveData data = new DummyVehicleMoveData();
        env.prepareCheckDetails(strider, null, data);
        VehicleEnvelope.CheckDetails details = getDetails(env);
        assertTrue(details.canClimb);
    }

    @Test
    public void testCamelBehavior() throws Exception {
        Camel camel = mock(Camel.class);
        when(camel.getType()).thenReturn(EntityType.CAMEL);
        VehicleEnvelope env = new VehicleEnvelope();
        DummyVehicleMoveData data = new DummyVehicleMoveData();
        env.prepareCheckDetails(camel, null, data);
        VehicleEnvelope.CheckDetails details = getDetails(env);
        assertEquals(EntityType.CAMEL, details.simplifiedType);
        assertTrue(details.canStepUpBlock);
    }

    @Test
    public void testPigBehavior() throws Exception {
        Pig pig = mock(Pig.class);
        when(pig.getType()).thenReturn(EntityType.PIG);
        VehicleEnvelope env = new VehicleEnvelope();
        DummyVehicleMoveData data = new DummyVehicleMoveData();
        env.prepareCheckDetails(pig, null, data);
        VehicleEnvelope.CheckDetails details = getDetails(env);
        assertEquals(EntityType.PIG, details.simplifiedType);
        assertTrue(details.canClimb);
    }

    @Test
    public void testGenericBehavior() throws Exception {
        Entity e = mock(Entity.class);
        when(e.getType()).thenReturn(EntityType.SHEEP);
        DummyVehicleMoveData data = new DummyVehicleMoveData();
        data.vehicleType = EntityType.SHEEP;
        VehicleEnvelope env = new VehicleEnvelope();
        env.prepareCheckDetails(e, null, data);
        VehicleEnvelope.CheckDetails details = getDetails(env);
        assertEquals(EntityType.SHEEP, details.simplifiedType);
    }
}
