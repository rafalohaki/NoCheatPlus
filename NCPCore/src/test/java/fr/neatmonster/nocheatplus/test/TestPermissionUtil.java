package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import fr.neatmonster.nocheatplus.permissions.PermissionUtil;
import fr.neatmonster.nocheatplus.permissions.PermissionUtil.CommandProtectionEntry;

public class TestPermissionUtil {

    static class DummyCommand extends Command {
        private String permission;
        private List<String> aliases;
        DummyCommand(String name) { super(name); }
        @Override public boolean execute(CommandSender sender, String label, String[] args) { return false; }
        @Override public List<String> tabComplete(CommandSender sender, String alias, String[] args) { return null; }
        @Override public List<String> getAliases() { return aliases; }
        @Override
        public Command setAliases(List<String> a) {
            this.aliases = (a != null) ? new ArrayList<>(a) : null;
            return this;
        }
        @Override public String getPermission() { return permission; }
        @Override public void setPermission(String perm) { this.permission = perm; }
    }

    private Server server;
    private PluginManager pluginManager;
    private ConsoleCommandSender console;
    private Server previousServer;

    @BeforeEach
    public void setup() throws Exception {
        pluginManager = mock(PluginManager.class);
        console = mock(ConsoleCommandSender.class);
        server = (Server) Proxy.newProxyInstance(TestPermissionUtil.class.getClassLoader(), new Class[]{Server.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getPluginManager": return pluginManager;
                        case "getConsoleSender": return console;
                        case "getLogger": return java.util.logging.Logger.getLogger("TestServer");
                        default: return null;
                    }
                });
        previousServer = Bukkit.getServer();
        if (previousServer == null) {
            Bukkit.setServer(server);
        } else {
            server = previousServer;
        }
    }

    @AfterEach
    public void teardown() {
        if (previousServer != null && Bukkit.getServer() != previousServer) {
            Bukkit.setServer(previousServer);
        }
    }

    @Test
    public void testShouldProtectCommand() {
        DummyCommand cmd = new DummyCommand("test");
        cmd.setAliases(Arrays.asList("alias"));
        assertFalse(callShouldProtectCommand(cmd, new HashSet<>(Arrays.asList("test")), false));
        assertTrue(callShouldProtectCommand(cmd, new HashSet<>(Arrays.asList("alias")), true));
    }

    @Test
    public void testRegisterAndRecord() {
        Plugin plugin = mock(Plugin.class);
        Permission root = new Permission("base");
        DummyCommand cmd = spy(new DummyCommand("cmd"));
        when(pluginManager.getPermission("base.cmd")).thenReturn(null);
        // capture added permission
        doAnswer(invocation -> null).when(pluginManager).addPermission(any(Permission.class));
        Object info = invokeRegister(plugin, root, cmd, "cmd", true);
        Permission perm;
        try {
            java.lang.reflect.Field f = info.getClass().getDeclaredField("permission");
            f.setAccessible(true);
            perm = (Permission) f.get(info);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        PermissionDefault def = perm.getDefault();
        assertEquals(PermissionDefault.OP, def);
        verify(console, never()).addAttachment(any(), anyString(), anyBoolean());
        List<CommandProtectionEntry> list = new java.util.LinkedList<>();
        invokeRecord(list, cmd, "cmd", info);
        assertEquals(1, list.size());
        CommandProtectionEntry entry = list.get(0);
        assertEquals("cmd", entry.label);
        assertNull(entry.permission);
    }

    @SuppressWarnings("deprecation")
    private Object invokeRegister(Plugin plugin, Permission root, DummyCommand cmd, String lcLabel, boolean ops) {
        try {
            Method m = PermissionUtil.class.getDeclaredMethod("registerCommandPermission", Plugin.class,
                    PluginManager.class, Permission.class, Command.class, String.class, String.class, boolean.class);
            m.setAccessible(true);
            return m.invoke(null, plugin, pluginManager, root, cmd, lcLabel, "base", ops);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean callShouldProtectCommand(Command cmd, java.util.Set<String> checked, boolean invert) {
        try {
            Method m = PermissionUtil.class.getDeclaredMethod("shouldProtectCommand", Command.class,
                    java.util.Set.class, boolean.class);
            m.setAccessible(true);
            return (Boolean) m.invoke(null, cmd, checked, invert);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void invokeRecord(List<CommandProtectionEntry> list, Command cmd, String label, Object info) {
        try {
            Class<?> infoClass = Class.forName(PermissionUtil.class.getName() + "$CommandPermissionInfo");
            Method m = PermissionUtil.class.getDeclaredMethod("recordChangeHistory", List.class, Command.class,
                    String.class, infoClass);
            m.setAccessible(true);
            m.invoke(null, list, cmd, label, info);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
