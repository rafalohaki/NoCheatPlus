package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;

import org.bukkit.entity.Boat;
import org.bukkit.entity.Camel;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Strider;
import org.bukkit.entity.AbstractHorse;
import org.junit.Test;

import fr.neatmonster.nocheatplus.checks.moving.magic.MagicVehicle;
import fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveInfo;
import fr.neatmonster.nocheatplus.checks.moving.vehicle.VehicleEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.vehicle.VehicleEnvelope.CheckDetails;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IHandle;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.players.DataManager;

public class TestVehicleBehavior {

    static class DummyHandle<T> implements IGenericInstanceHandle<T> {
        private final T handle;
        DummyHandle(T h){ this.handle = h; }
        @Override public T getHandle(){ return handle; }
        @Override public void disableHandle(){}
    }

    private static void setupAPI() throws Exception {
        MCAccess mc = (MCAccess) Proxy.newProxyInstance(MCAccess.class.getClassLoader(),
                new Class<?>[] { MCAccess.class }, (p, m, a) -> {
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
        IGenericInstanceHandle<MCAccess> mcHandle = new DummyHandle<>(mc);
        Object api = Proxy.newProxyInstance(NCPAPIProvider.class.getClassLoader(),
                new Class<?>[] { fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class },
                (pr, m, a) -> {
                    if ("getGenericInstanceHandle".equals(m.getName())) {
                        Class<?> c = (Class<?>) a[0];
                        if (c == MCAccess.class) return mcHandle;
                        return new DummyHandle<>(null);
                    }
                    return null;
                });
        Field f = NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        f.setAccessible(true);
        f.set(null, api);

        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        uf.setAccessible(true);
        sun.misc.Unsafe un = (sun.misc.Unsafe) uf.get(null);
        Object pdm = un.allocateInstance(fr.neatmonster.nocheatplus.players.PlayerDataManager.class);
        Field eh = fr.neatmonster.nocheatplus.players.PlayerDataManager.class.getDeclaredField("executionHistories");
        eh.setAccessible(true);
        eh.set(pdm, new java.util.HashMap<>());
        Field dm = DataManager.class.getDeclaredField("instance");
        dm.setAccessible(true);
        dm.set(null, pdm);
    }

    static class DummyLocation extends fr.neatmonster.nocheatplus.utilities.location.RichEntityLocation {
        DummyLocation() {
            super(new DummyHandle<MCAccess>(null), null);
        }
        @Override
        public boolean isOnRails() {
            return false;
        }
    }

    static class DummyMoveInfo extends fr.neatmonster.nocheatplus.checks.moving.model.VehicleMoveInfo {
        DummyMoveInfo() throws Exception {
            super(new DummyHandle<MCAccess>(null));
            Field f = fr.neatmonster.nocheatplus.checks.moving.model.MoveInfo.class.getDeclaredField("from");
            f.setAccessible(true);
            f.set(this, new DummyLocation());
            f = fr.neatmonster.nocheatplus.checks.moving.model.MoveInfo.class.getDeclaredField("to");
            f.setAccessible(true);
            f.set(this, new DummyLocation());
        }
    }

    private static Entity proxy(Class<?> clazz, EntityType type) {
        return (Entity) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[]{clazz},
                (p, m, a) -> {
                    if ("getType".equals(m.getName())) return type;
                    if ("getUniqueId".equals(m.getName())) return UUID.randomUUID();
                    Class<?> r = m.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
    }

    private CheckDetails prepare(Entity entity) throws Exception {
        setupAPI();
        VehicleEnvelope env = new VehicleEnvelope();
        VehicleMoveData data = new VehicleMoveData();
        data.setExtraVehicleProperties(entity);
        VehicleMoveInfo info = new DummyMoveInfo();
        Method m = VehicleEnvelope.class.getDeclaredMethod("prepareCheckDetails", Entity.class, VehicleMoveInfo.class, VehicleMoveData.class);
        m.setAccessible(true);
        m.invoke(env, entity, info, data);
        Field f = VehicleEnvelope.class.getDeclaredField("checkDetails");
        f.setAccessible(true);
        return (CheckDetails) f.get(env);
    }

    @Test
    public void testBoatSettings() throws Exception {
        CheckDetails cd = prepare(proxy(Boat.class, EntityType.BOAT));
        assertEquals(EntityType.BOAT, cd.simplifiedType);
        assertEquals(MagicVehicle.maxAscend, cd.maxAscend, 0.0);
    }

    @Test
    public void testMinecartSettings() throws Exception {
        CheckDetails cd = prepare(proxy(Minecart.class, EntityType.MINECART));
        assertEquals(EntityType.MINECART, cd.simplifiedType);
        assertTrue(cd.canRails);
        assertEquals(0.79, cd.gravityTargetSpeed, 0.0);
    }

    @Test
    public void testHorseSettings() throws Exception {
        CheckDetails cd = prepare(proxy(AbstractHorse.class, EntityType.HORSE));
        assertEquals(EntityType.HORSE, cd.simplifiedType);
        assertTrue(cd.canJump);
        assertTrue(cd.canStepUpBlock);
    }

    @Test
    public void testStriderSettings() throws Exception {
        CheckDetails cd = prepare(proxy(Strider.class, EntityType.STRIDER));
        assertFalse(cd.canJump);
        assertTrue(cd.canStepUpBlock);
        assertTrue(cd.canClimb);
        assertEquals(1.1, cd.maxAscend, 0.0);
    }

    @Test
    public void testCamelSettings() throws Exception {
        CheckDetails cd = prepare(proxy(Camel.class, EntityType.CAMEL));
        assertEquals(EntityType.CAMEL, cd.simplifiedType);
        assertFalse(cd.canJump);
        assertTrue(cd.canStepUpBlock);
        assertFalse(cd.canClimb);
    }

    @Test
    public void testPigSettings() throws Exception {
        CheckDetails cd = prepare(proxy(Pig.class, EntityType.PIG));
        assertEquals(EntityType.PIG, cd.simplifiedType);
        assertFalse(cd.canJump);
        assertTrue(cd.canStepUpBlock);
        assertTrue(cd.canClimb);
    }

    @Test
    public void testGenericSettings() throws Exception {
        CheckDetails cd = prepare(proxy(Entity.class, EntityType.COW));
        assertEquals(EntityType.COW, cd.simplifiedType);
    }
}
