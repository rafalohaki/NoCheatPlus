import fr.neatmonster.nocheatplus.PluginTests;
import fr.neatmonster.nocheatplus.NoCheatPlus;
import fr.neatmonster.nocheatplus.components.registry.feature.IDisableListener;
import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPluginLoader;
import fr.neatmonster.nocheatplus.logging.BukkitLogManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class TestPluginLifecycle {

    private NoCheatPlus plugin;
    private Server server;

    @Before
    public void setup() throws Exception {
        PluginTests.setUnitTestNoCheatPlusAPI(true);
        server = createServer();
        JavaPluginLoader loader = new JavaPluginLoader(server);
        if (org.bukkit.Bukkit.getServer() == null) org.bukkit.Bukkit.setServer(server);
        PluginDescriptionFile desc = new PluginDescriptionFile("Test", "1", "test.Main");
        java.lang.reflect.Constructor<NoCheatPlus> ctor = NoCheatPlus.class.getDeclaredConstructor(JavaPluginLoader.class, PluginDescriptionFile.class, File.class, File.class);
        ctor.setAccessible(true);
        plugin = ctor.newInstance(loader, desc, new File("dummy"), new File("dummy"));
        DummyLogManager log = new DummyLogManager(plugin);
        java.lang.reflect.Field fLog = NoCheatPlus.class.getDeclaredField("logManager");
        fLog.setAccessible(true);
        fLog.set(plugin, log);
    }

    @After
    public void teardown() {
    }
@Test

    public void testAddComponent() throws Exception {
        DummyComponent comp = new DummyComponent("A", new ArrayList<>());
        assertTrue(plugin.addComponent(comp));
        assertFalse(plugin.addComponent(comp));
        assertTrue(getListeners().contains(comp));
        assertTrue(getDisableListeners().contains(comp));
        assertTrue(getAllComponents().contains(comp));
    }

    @Test
    public void testOnDisableOrderAndCleanup() throws Exception {
        List<String> calls = new ArrayList<>();
        DummyComponent a = new DummyComponent("A", calls);
        DummyComponent b = new DummyComponent("B", calls);
        plugin.addComponent(a);
        plugin.addComponent(b);
        // populate event registry and task fields to verify cleanup
        Field regField = NoCheatPlus.class.getDeclaredField("eventRegistry");
        regField.setAccessible(true);
        Object registry = regField.get(plugin);
        Field attachmentsField = registry.getClass().getSuperclass().getSuperclass()
                .getDeclaredField("attachments");
        attachmentsField.setAccessible(true);
        ((java.util.Map<Object, java.util.Set<?>>) attachmentsField.get(registry))
                .put(new Object(), new java.util.HashSet<>());
        Field dataTaskField = NoCheatPlus.class.getDeclaredField("dataManTaskId");
        dataTaskField.setAccessible(true);
        dataTaskField.set(plugin, Integer.valueOf(1));
        Field ccTaskField = NoCheatPlus.class.getDeclaredField("consistencyCheckerTaskId");
        ccTaskField.setAccessible(true);
        ccTaskField.set(plugin, Integer.valueOf(2));

        plugin.onDisable();
        assertEquals("B", calls.get(0));
        assertEquals("A", calls.get(1));
        assertTrue(getListeners().isEmpty());
        assertTrue(getDisableListeners().isEmpty());
        assertTrue(getAllComponents().isEmpty());
        assertNull(dataTaskField.get(plugin));
        assertNull(ccTaskField.get(plugin));
        assertTrue(((java.util.Map<?, ?>) attachmentsField.get(registry)).isEmpty());
    }

    private static class DummyComponent implements Listener, IDisableListener {
        private final String name;
        private final List<String> log;
        DummyComponent(String name, List<String> log) {
            this.name = name;
            this.log = log;
        }
        @Override
        public void onDisable() { log.add(name); }
    }
    private static class DummyLogManager extends BukkitLogManager {
        DummyLogManager(NoCheatPlus plugin) { super(plugin); }
        @Override public void shutdown() {}
    }


    private static Server createServer() {
        PluginManager pluginManager = (PluginManager) Proxy.newProxyInstance(
                TestPluginLifecycle.class.getClassLoader(), new Class[]{PluginManager.class}, defaultHandler());
        BukkitScheduler scheduler = (BukkitScheduler) Proxy.newProxyInstance(
                TestPluginLifecycle.class.getClassLoader(), new Class[]{BukkitScheduler.class}, defaultHandler());
        InvocationHandler serverHandler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "getPluginManager": return pluginManager;
                case "getScheduler": return scheduler;
                case "getLogger": return Logger.getLogger("TestServer");
                case "getName": return "Dummy";
                case "getVersion": return "0";
                case "getBukkitVersion": return "0";
                default: return defaultValue(method.getReturnType());
            }
        };
        return (Server) Proxy.newProxyInstance(TestPluginLifecycle.class.getClassLoader(), new Class[]{Server.class}, serverHandler);
    }

    private static InvocationHandler defaultHandler() {
        return (proxy, method, args) -> defaultValue(method.getReturnType());
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

    @SuppressWarnings("unchecked")
    private List<Object> getListeners() throws Exception {
        Field f = NoCheatPlus.class.getDeclaredField("listeners");
        f.setAccessible(true);
        return (List<Object>) f.get(plugin);
    }

    @SuppressWarnings("unchecked")
    private List<IDisableListener> getDisableListeners() throws Exception {
        Field f = NoCheatPlus.class.getDeclaredField("disableListeners");
        f.setAccessible(true);
        return (List<IDisableListener>) f.get(plugin);
    }

    @SuppressWarnings("unchecked")
    private Set<Object> getAllComponents() throws Exception {
        Field f = NoCheatPlus.class.getDeclaredField("allComponents");
        f.setAccessible(true);
        return (Set<Object>) f.get(plugin);
    }
}
