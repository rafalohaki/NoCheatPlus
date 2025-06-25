package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.util.UUID;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import org.junit.Before;
import org.junit.Test;

import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.PlayerData;
import fr.neatmonster.nocheatplus.players.PlayerDataManager;

public class TestPlayerDataManagerLogin {

    static class TestManager extends PlayerDataManager {
        PlayerData byId;
        PlayerData byPlayer;
        int getByIdCalls;
        int getByPlayerCalls;
        TestManager() {
            super(null, null);
        }
        @Override
        public PlayerData getPlayerData(UUID id) {
            getByIdCalls++;
            return byId;
        }
        @Override
        public PlayerData getPlayerData(Player player) {
            getByPlayerCalls++;
            return byPlayer;
        }
    }

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

    private static Player createPlayer(UUID id, String name, World world) {
        InvocationHandler h = (proxy, method, args) -> {
            switch(method.getName()) {
                case "getUniqueId": return id;
                case "getName": return name;
                case "getWorld": return world;
                default:
                    Class<?> r = method.getReturnType();
                    if (r == boolean.class) return false;
                    if (r.isPrimitive()) return 0;
                    return null;
            }
        };
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class[]{Player.class}, h);
    }

    private TestManager manager;

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static TestManager createManager() throws Exception {
        sun.misc.Unsafe u = getUnsafe();
        return (TestManager) u.allocateInstance(TestManager.class);
    }

    @Before
    public void setup() throws Exception {
        manager = createManager();
        Field f = PlayerDataManager.class.getDeclaredField("worldDataManager");
        f.setAccessible(true);
        f.set(manager, mock(fr.neatmonster.nocheatplus.worlds.WorldDataManager.class));
        f = DataManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, manager);
    }

    @Test
    public void testExistingDataPath() throws Exception {
        PlayerData data = mock(PlayerData.class);
        when(data.getPlayerName()).thenReturn("Dummy");
        manager.byId = data;
        manager.byPlayer = mock(PlayerData.class);
        Player player = createPlayer(UUID.randomUUID(), "Dummy", createWorld());
        PlayerLoginEvent event = mock(PlayerLoginEvent.class);
        when(event.getPlayer()).thenReturn(player);
        Method m = PlayerDataManager.class.getDeclaredMethod("onPlayerLogin", PlayerLoginEvent.class);
        m.setAccessible(true);
        m.invoke(manager, event);
        assertEquals(1, manager.getByIdCalls);
        assertEquals(0, manager.getByPlayerCalls);
        verify(data).removeTag(PlayerData.TAG_OPTIMISTIC_CREATE);
    }

    @Test
    public void testCreatedDataPath() throws Exception {
        PlayerData data = mock(PlayerData.class);
        manager.byId = null;
        manager.byPlayer = data;
        Player player = createPlayer(UUID.randomUUID(), "Dummy", createWorld());
        PlayerLoginEvent event = mock(PlayerLoginEvent.class);
        when(event.getPlayer()).thenReturn(player);
        Method m = PlayerDataManager.class.getDeclaredMethod("onPlayerLogin", PlayerLoginEvent.class);
        m.setAccessible(true);
        m.invoke(manager, event);
        assertEquals(1, manager.getByIdCalls);
        assertEquals(1, manager.getByPlayerCalls);
        verify(data).removeTag(PlayerData.TAG_OPTIMISTIC_CREATE);
    }
}
