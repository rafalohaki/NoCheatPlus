package fr.neatmonster.nocheatplus.test;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.MovingListener;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class TestConfirmSetBack {

    private static World createWorld() {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class[]{World.class}, (p,m,a) -> {
            if ("getName".equals(m.getName())) return "dummy";
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive()) return 0;
            return null;
        });
    }

    private static Player createPlayer() {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, (p,m,a) -> {
            Class<?> r = m.getReturnType();
            if (r == boolean.class) return false;
            if (r.isPrimitive()) return 0;
            return null;
        });
    }

    @Test
    public void yawForSetBackHandlesNullTeleported() throws Exception {
        sun.misc.Unsafe unsafe = getUnsafe();
        MovingListener listener = (MovingListener) unsafe.allocateInstance(MovingListener.class);
        Field ct = listener.getClass().getSuperclass().getDeclaredField("checkType");
        ct.setAccessible(true);
        ct.set(listener, CheckType.MOVING);
        Method m = MovingListener.class.getDeclaredMethod("getYawForSetBack", Location.class, Location.class);
        m.setAccessible(true);
        World w = createWorld();
        Location fallback = new Location(w, 1, 2, 3);
        Float yaw = (Float) m.invoke(listener, null, fallback);
        assertEquals(fallback.getYaw(), yaw.floatValue(), 0.0);
    }

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }
}
