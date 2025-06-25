package fr.neatmonster.nocheatplus.event.mini;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.junit.Test;

import fr.neatmonster.nocheatplus.components.registry.order.RegistrationOrder;
import fr.neatmonster.nocheatplus.components.registry.order.RegistrationOrder.RegisterMethodWithOrder;

public class TestMultiListenerRegistryAnnotation {

    private static class DummyRegistry extends MultiListenerRegistry<Object, Integer> {
        @Override
        protected boolean shouldBeEventHandler(Method method) {
            return true;
        }

        @Override
        protected boolean getIgnoreCancelled(Method method, boolean defaultIgnoreCancelled) {
            return defaultIgnoreCancelled;
        }

        @Override
        protected Integer getPriority(Method method, Integer defaultPriority) {
            return defaultPriority;
        }

        @Override
        protected <E extends Object> void registerNode(Class<E> eventClass, MiniListenerNode<E, Integer> node,
                Integer basePriority) {
            // No-op for tests.
        }
    }

    private static class DummyListener {
        @RegisterMethodWithOrder(tag = "test")
        public void handle(Object event) {
        }
    }

    @Test
    public void testAnnotationDetected() throws Exception {
        DummyRegistry registry = new DummyRegistry();
        Method method = DummyListener.class.getMethod("handle", Object.class);
        RegistrationOrder order = new RegistrationOrder("default");
        MiniListener<?> mini = registry.register(new DummyListener(), method, 0, order, false);
        assertNotNull(mini);
        assertTrue(mini instanceof MiniListenerWithOrder);
        RegistrationOrder result = ((MiniListenerWithOrder<?>) mini).getRegistrationOrder();
        assertEquals("test", result.getTag());
    }
}
