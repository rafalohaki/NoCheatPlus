package fr.neatmonster.nocheatplus;

import org.mockbukkit.mockbukkit.MockBukkit;
import fr.neatmonster.nocheatplus.components.registry.feature.IDisableListener;
import fr.neatmonster.nocheatplus.logging.BukkitLogManager;
import org.bukkit.NamespacedKey;
import org.bukkit.event.Listener;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TestPluginLifecycle {

    private NoCheatPlus plugin;

    @BeforeEach
    public void setup() throws Exception {
        PluginTests.setUnitTestNoCheatPlusAPI(true);
        installTestPatches();
        MockBukkit.mock();
        plugin = MockBukkit.load(NoCheatPlus.class);
        DummyLogManager log = new DummyLogManager(plugin);
        java.lang.reflect.Field fLog = NoCheatPlus.class.getDeclaredField("logManager");
        fLog.setAccessible(true);
        fLog.set(plugin, log);
    }

    @AfterEach
    public void teardown() {
        MockBukkit.unmock();
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
        ((Map<Object, Set<?>>) attachmentsField.get(registry))
                .put(new Object(), new HashSet<>());
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
        assertTrue(((Map<?, ?>) attachmentsField.get(registry)).isEmpty());
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

    private static void installTestPatches() {
        ByteBuddyAgent.install();
        new net.bytebuddy.agent.builder.AgentBuilder.Default()
                .type(net.bytebuddy.matcher.ElementMatchers.named("org.bukkit.NamespacedKey"))
                .transform((builder, td, cl, module, pd) -> {
                    if (td.getDeclaredMethods()
                            .filter(net.bytebuddy.matcher.ElementMatchers.named("value")).isEmpty()) {
                        return builder.defineMethod("value", String.class, Modifier.PUBLIC)
                                .intercept(MethodCall.invoke(td.getDeclaredMethods()
                                        .filter(net.bytebuddy.matcher.ElementMatchers.named("toString")).getOnly()));
                    }
                    return builder;
                })
                .installOnByteBuddyAgent();

        new net.bytebuddy.agent.builder.AgentBuilder.Default()
                .type(net.bytebuddy.matcher.ElementMatchers.named("org.mockbukkit.mockbukkit.tags.TagsMock"))
                .transform((builder, td, cl, module, pd) ->
                        builder.method(net.bytebuddy.matcher.ElementMatchers.named("loadDefaultTags"))
                                .intercept(net.bytebuddy.implementation.StubMethod.INSTANCE))
                .installOnByteBuddyAgent();

        new net.bytebuddy.agent.builder.AgentBuilder.Default()
                .type(net.bytebuddy.matcher.ElementMatchers.named("org.mockbukkit.mockbukkit.tags.internal.InternalTag"))
                .transform((builder, td, cl, module, pd) ->
                        builder.method(net.bytebuddy.matcher.ElementMatchers.named("loadInternalTags"))
                                .intercept(net.bytebuddy.implementation.StubMethod.INSTANCE))
                .installOnByteBuddyAgent();

        new net.bytebuddy.agent.builder.AgentBuilder.Default()
                .type(net.bytebuddy.matcher.ElementMatchers.named("org.bukkit.plugin.java.JavaPlugin"))
                .transform((builder, td, cl, module, pd) -> builder.visit(
                        net.bytebuddy.asm.Advice.to(JavaPluginCtorAdvice.class).on(net.bytebuddy.matcher.ElementMatchers.isConstructor())))
                .installOnByteBuddyAgent();
    }

    private static class JavaPluginCtorAdvice {
        @net.bytebuddy.asm.Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
        static void exit(@net.bytebuddy.asm.Advice.Thrown(readOnly = false) Throwable t) {
            if (t instanceof IllegalStateException && t.getMessage() != null && t.getMessage().contains("JavaPlugin requires")) {
                t = null; // ignore plugin class loader check
            }
        }
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
