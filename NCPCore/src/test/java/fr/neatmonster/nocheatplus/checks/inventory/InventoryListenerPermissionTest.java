package fr.neatmonster.nocheatplus.checks.inventory;

import static org.mockito.Mockito.*;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.inventory.InventoryData;
import sun.misc.Unsafe;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.permissions.Permissions;

public class InventoryListenerPermissionTest {

    @Before
    public void setupApi() throws Exception {
        InvocationHandler logHandler = (p,m,a) -> defaultValue(m.getReturnType());
        Object logManager = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{fr.neatmonster.nocheatplus.logging.LogManager.class},
                logHandler);
        Object counters = new fr.neatmonster.nocheatplus.stats.Counters();
        InvocationHandler apiHandler = (p,m,a) -> {
            if ("getLogManager".equals(m.getName())) return logManager;
            if ("getGenericInstance".equals(m.getName()) && a.length == 1 && a[0] == fr.neatmonster.nocheatplus.stats.Counters.class) {
                return counters;
            }
            return defaultValue(m.getReturnType());
        };
        Object api = Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{fr.neatmonster.nocheatplus.components.NoCheatPlusAPI.class},
                apiHandler);
        Field f = fr.neatmonster.nocheatplus.NCPAPIProvider.class.getDeclaredField("noCheatPlusAPI");
        f.setAccessible(true);
        f.set(null, api);
    }

    private static Unsafe getUnsafe() throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (Unsafe) f.get(null);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class || type == short.class || type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return null;
    }

    @Test
    public void unauthorizedClickIsCancelled() {
        InventoryListener listener;
        try {
            listener = (InventoryListener) getUnsafe().allocateInstance(InventoryListener.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Player player = mock(Player.class);
        when(event.getWhoClicked()).thenReturn(player);

        IPlayerData pData = mock(IPlayerData.class);
        when(pData.isCheckActive(CheckType.INVENTORY, player)).thenReturn(true);
        when(pData.hasPermission(Permissions.INVENTORY_ACCESS, player)).thenReturn(false);

        try (MockedStatic<DataManager> dm = mockStatic(DataManager.class)) {
            dm.when(() -> DataManager.getPlayerData(player)).thenReturn(pData);
            listener.onInventoryClick(event);
        }

        verify(event).setCancelled(true);
        verify(event, never()).getSlot();
    }

    @Test
    public void authorizedClickProceeds() {
        InventoryListener listener;
        try {
            listener = (InventoryListener) getUnsafe().allocateInstance(InventoryListener.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        Player player = mock(Player.class);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getSlot()).thenReturn(0);

        IPlayerData pData = mock(IPlayerData.class);
        when(pData.isCheckActive(CheckType.INVENTORY, player)).thenReturn(false);
        when(pData.hasPermission(Permissions.INVENTORY_ACCESS, player)).thenReturn(true);
        when(pData.getGenericInstance(InventoryData.class)).thenReturn(new InventoryData());

        try (MockedStatic<DataManager> dm = mockStatic(DataManager.class)) {
            dm.when(() -> DataManager.getPlayerData(player)).thenReturn(pData);
            listener.onInventoryClick(event);
        }

        verify(event, never()).setCancelled(true);
        verify(event, never()).getSlot();
    }
}
