package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import fr.neatmonster.nocheatplus.command.testing.stopwatch.LocationBasedStopWatchData;

public class TestLocationBasedStopWatchData {

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

    private static Player createPlayer(World world) {
        InvocationHandler h = (proxy, method, args) -> {
            switch(method.getName()) {
                case "getWorld":
                    return world;
                case "getLocation":
                    Location l = (Location) args[0];
                    l.setWorld(null);
                    l.setX(0);
                    l.setY(0);
                    l.setZ(0);
                    return l;
                case "getName":
                    return "player";
                default:
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
            }
        };
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, h);
    }

    @Test
    public void testConstructorNullLocationWorld() {
        World world = createWorld();
        Player player = createPlayer(world);
        LocationBasedStopWatchData data = new LocationBasedStopWatchData(player) {
            @Override
            public boolean checkStop() { return false; }
            @Override
            public boolean needsTick() { return false; }
        };
        assertEquals(world.getName(), data.worldName);
    }
}
