package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.*;

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
import org.junit.jupiter.api.Test;

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

}
