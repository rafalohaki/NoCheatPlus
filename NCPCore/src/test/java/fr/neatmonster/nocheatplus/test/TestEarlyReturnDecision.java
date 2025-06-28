package fr.neatmonster.nocheatplus.test;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.MovingListener;
import fr.neatmonster.nocheatplus.checks.moving.vehicle.VehicleChecks;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class TestEarlyReturnDecision {

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static World createWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class[]{World.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return "dummy";
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
    }

    private static Player createPlayer(boolean insideVehicle) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class},
                (proxy, method, args) -> {
                    if ("isInsideVehicle".equals(method.getName())) return insideVehicle;
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r == double.class || r == float.class) return 0.0;
                    if (r.isPrimitive()) return 0;
                    return null;
                });
    }

    private static class DummyVehicleChecks extends VehicleChecks {
        Location result;
        @Override
        public Location onPlayerMoveVehicle(Player player, Location from, Location to, MovingData data, IPlayerData pData) {
            return result;
        }
    }

    private MovingListener listener;
    private MovingConfig config;
    private World world;
    private sun.misc.Unsafe unsafe;
    private Method determine;
    private DummyVehicleChecks vehicle;

    @BeforeEach
    public void setup() throws Exception {
        unsafe = getUnsafe();
        listener = (MovingListener) unsafe.allocateInstance(MovingListener.class);

        Field vf = MovingListener.class.getDeclaredField("vehicleChecks");
        vf.setAccessible(true);
        vehicle = (DummyVehicleChecks) unsafe.allocateInstance(DummyVehicleChecks.class);
        vf.set(listener, vehicle);

        determine = MovingListener.class.getDeclaredMethod("determineEarlyReturn",
                Player.class, Location.class, Location.class, PlayerMoveEvent.class,
                MovingData.class, MovingConfig.class, IPlayerData.class);
        determine.setAccessible(true);

        config = (MovingConfig) unsafe.allocateInstance(MovingConfig.class);
        world = createWorld();
    }

    private Object invoke(Player player, Location from, Location to, MovingData data, IPlayerData pData) throws Exception {
        PlayerMoveEvent event = new PlayerMoveEvent(player, from, to);
        return determine.invoke(listener, player, from, to, event, data, config, pData);
    }

    @Test
    public void testVehicleTrigger() throws Exception {
        Player player = createPlayer(true);
        MovingData data = (MovingData) unsafe.allocateInstance(MovingData.class);
        IPlayerData pData = mock(IPlayerData.class);

        vehicle.result = new Location(world, 1, 1, 1);
        Object decision = invoke(player, new Location(world, 0, 0, 0), new Location(world, 0, 0, 1), data, pData);

        Field token = decision.getClass().getDeclaredField("token");
        token.setAccessible(true);
        assertEquals("vehicle", token.get(decision));

        Field early = decision.getClass().getDeclaredField("earlyReturn");
        early.setAccessible(true);
        assertTrue((Boolean) early.get(decision));

        Field newTo = decision.getClass().getDeclaredField("newTo");
        newTo.setAccessible(true);
        assertEquals(vehicle.result, newTo.get(decision));
    }

    @Test
    public void testDuplicateMoveTrigger() throws Exception {
        Player player = createPlayer(false);
        MovingData data = (MovingData) unsafe.allocateInstance(MovingData.class);
        IPlayerData pData = mock(IPlayerData.class);

        Location loc = new Location(world, 0, 0, 0);
        Object decision = invoke(player, loc, loc, data, pData);

        Field token = decision.getClass().getDeclaredField("token");
        token.setAccessible(true);
        assertEquals("duplicate", token.get(decision));

        Field early = decision.getClass().getDeclaredField("earlyReturn");
        early.setAccessible(true);
        assertTrue((Boolean) early.get(decision));

        Field newTo = decision.getClass().getDeclaredField("newTo");
        newTo.setAccessible(true);
        assertNull(newTo.get(decision));
    }
}
