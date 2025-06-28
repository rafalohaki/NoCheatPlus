package fr.neatmonster.nocheatplus.compat.bukkit;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mockStatic;

import java.lang.reflect.Method;
import java.util.List;

import org.bukkit.entity.Entity;
import org.junit.Test;
import org.mockito.MockedStatic;

import fr.neatmonster.nocheatplus.support.FeatureSupportRegistry;
import fr.neatmonster.nocheatplus.utilities.ReflectionUtil;

public class TestEntityAccessVehicleMultiPassenger {

    private static List<Entity> dummyPassengers() { return null; }
    private static boolean dummyAddPassenger() { return true; }
    private static void dummyAddPassengerVoid() { }

    @Test
    public void testMissingGetPassengers() throws Exception {
        FeatureSupportRegistry.clearCache();
        Method dummyAdd = TestEntityAccessVehicleMultiPassenger.class.getDeclaredMethod("dummyAddPassenger");
        try (MockedStatic<ReflectionUtil> util = mockStatic(ReflectionUtil.class)) {
            util.when(() -> ReflectionUtil.getMethodNoArgs(Entity.class, "getPassengers", List.class))
                    .thenReturn(null);
            util.when(() -> ReflectionUtil.getMethod(Entity.class, "addPassenger", Entity.class))
                    .thenReturn(dummyAdd);
            EntityAccessVehicleMultiPassenger access = EntityAccessVehicleMultiPassenger.createIfSupported();
            assertNull(access);
        }
    }

    @Test
    public void testMissingAddPassenger() throws Exception {
        FeatureSupportRegistry.clearCache();
        Method dummyGet = TestEntityAccessVehicleMultiPassenger.class.getDeclaredMethod("dummyPassengers");
        try (MockedStatic<ReflectionUtil> util = mockStatic(ReflectionUtil.class)) {
            util.when(() -> ReflectionUtil.getMethodNoArgs(Entity.class, "getPassengers", List.class))
                    .thenReturn(dummyGet);
            util.when(() -> ReflectionUtil.getMethod(Entity.class, "addPassenger", Entity.class))
                    .thenReturn(null);
            EntityAccessVehicleMultiPassenger access = EntityAccessVehicleMultiPassenger.createIfSupported();
            assertNull(access);
        }
    }

    @Test
    public void testCreateBooleanReturnType() throws Exception {
        Method dummyGet = TestEntityAccessVehicleMultiPassenger.class.getDeclaredMethod("dummyPassengers");
        Method dummyAdd = TestEntityAccessVehicleMultiPassenger.class.getDeclaredMethod("dummyAddPassenger");
        try (MockedStatic<ReflectionUtil> util = mockStatic(ReflectionUtil.class)) {
            util.when(() -> ReflectionUtil.getMethodNoArgs(Entity.class, "getPassengers", List.class))
                    .thenReturn(dummyGet);
            util.when(() -> ReflectionUtil.getMethod(Entity.class, "addPassenger", Entity.class))
                    .thenReturn(dummyAdd);
            EntityAccessVehicleMultiPassenger access = EntityAccessVehicleMultiPassenger.createIfSupported();
            assertNotNull(access);
        }
    }

    @Test
    public void testCreateVoidReturnType() throws Exception {
        Method dummyGet = TestEntityAccessVehicleMultiPassenger.class.getDeclaredMethod("dummyPassengers");
        Method dummyAdd = TestEntityAccessVehicleMultiPassenger.class.getDeclaredMethod("dummyAddPassengerVoid");
        try (MockedStatic<ReflectionUtil> util = mockStatic(ReflectionUtil.class)) {
            util.when(() -> ReflectionUtil.getMethodNoArgs(Entity.class, "getPassengers", List.class))
                    .thenReturn(dummyGet);
            util.when(() -> ReflectionUtil.getMethod(Entity.class, "addPassenger", Entity.class))
                    .thenReturn(dummyAdd);
            EntityAccessVehicleMultiPassenger access = EntityAccessVehicleMultiPassenger.createIfSupported();
            assertNotNull(access);
        }
    }

}
