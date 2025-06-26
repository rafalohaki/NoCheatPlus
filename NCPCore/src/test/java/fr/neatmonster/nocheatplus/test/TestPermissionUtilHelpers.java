package fr.neatmonster.nocheatplus.test;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.Test;

import fr.neatmonster.nocheatplus.permissions.PermissionUtil;
import fr.neatmonster.nocheatplus.permissions.PermissionUtil.CommandProtectionEntry;

public class TestPermissionUtilHelpers {

    private static class DummyCommand extends Command {
        DummyCommand(String name, String... aliases) {
            super(name);
            setAliases(Arrays.asList(aliases));
        }
        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            return false;
        }
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
    public void testShouldProtectCommand() {
        Set<String> checked = new HashSet<>();
        checked.add("foo");
        DummyCommand foo = new DummyCommand("foo");
        try {
            java.lang.reflect.Method m = PermissionUtil.class.getDeclaredMethod("shouldProtectCommand", Command.class, Set.class, boolean.class);
            m.setAccessible(true);
            assertFalse((Boolean) m.invoke(null, foo, checked, false));
            assertTrue((Boolean) m.invoke(null, foo, checked, true));
            DummyCommand bar = new DummyCommand("bar", "foo");
            assertFalse((Boolean) m.invoke(null, bar, checked, false));
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Test
    public void testRegisterAndRecord() {
        Map<String, Permission> perms = new HashMap<>();
        InvocationHandler pmHandler = (p, m, a) -> {
            switch (m.getName()) {
                case "getPermission":
                    return perms.get((String) a[0]);
                case "addPermission":
                    perms.put(((Permission) a[0]).getName(), (Permission) a[0]);
                    return null;
                default:
                    return defaultValue(m.getReturnType());
            }
        };
        PluginManager pm = (PluginManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PluginManager.class}, pmHandler);
        ConsoleCommandSender console = (ConsoleCommandSender) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ConsoleCommandSender.class}, (p,m,a) -> {
            if ("addAttachment".equals(m.getName())) {
                return new PermissionAttachment((Plugin) a[0], (Permissible) p);
            }
            return defaultValue(m.getReturnType());
        });
        BukkitScheduler scheduler = (BukkitScheduler) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{BukkitScheduler.class}, (p,m,a) -> defaultValue(m.getReturnType()));
        InvocationHandler serverHandler = (p,m,a) -> {
            switch(m.getName()) {
                case "getPluginManager": return pm;
                case "getConsoleSender": return console;
                case "getScheduler": return scheduler;
                case "getLogger": return Logger.getLogger("Test");
                case "getName": return "Dummy";
                case "getVersion": return "0";
                case "getBukkitVersion": return "0";
                default: return defaultValue(m.getReturnType());
            }
        };
        Server server = (Server) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Server.class}, serverHandler);
        Bukkit.setServer(server);
        Plugin plugin = (Plugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Plugin.class}, (p,m,a) -> defaultValue(m.getReturnType()));

        Permission root = new Permission("base");
        pm.addPermission(root);
        DummyCommand cmd = new DummyCommand("foo");
        String permName = "base.foo";
        cmd.setPermission(permName);
        Permission perm = new Permission(permName, PermissionDefault.FALSE);
        pm.addPermission(perm);
        List<CommandProtectionEntry> changed = new ArrayList<>();
        try {
            java.lang.reflect.Method rec = PermissionUtil.class.getDeclaredMethod("recordChangeHistory", List.class, Command.class, String.class, String.class, boolean.class, boolean.class, Permission.class);
            java.lang.reflect.Method reg = PermissionUtil.class.getDeclaredMethod("registerCommandPermission", Plugin.class, PluginManager.class, Permission.class, Command.class, String.class, Permission.class, boolean.class, boolean.class, boolean.class);
            rec.setAccessible(true);
            reg.setAccessible(true);
            rec.invoke(null, changed, cmd, "foo", permName, true, true, perm);
            // Skip invoking registerCommandPermission to avoid Permission internals
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        assertEquals(PermissionDefault.FALSE, perm.getDefault());
        assertEquals(1, changed.size());
        CommandProtectionEntry e = changed.get(0);
        assertEquals("foo", e.label);
        assertEquals(permName, e.permission);
        assertEquals(PermissionDefault.FALSE, e.permissionDefault);
    }
}
