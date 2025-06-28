package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.fail;
import java.util.logging.Level;
import org.junit.jupiter.api.Test;
import fr.neatmonster.nocheatplus.logging.LoggerID;
import fr.neatmonster.nocheatplus.logging.details.AbstractLogNodeDispatcher;
import fr.neatmonster.nocheatplus.logging.details.LogNode;
import fr.neatmonster.nocheatplus.logging.details.LogOptions;

public class TestLogNodeDispatcher {

    private static class DummyDispatcher extends AbstractLogNodeDispatcher {
        @Override
        protected boolean isPrimaryThread() {
            return true;
        }
        @Override
        protected void scheduleAsynchronous() {
        }
        @Override
        protected void cancelTask(Object taskInfo) {
        }
        @Override
        protected boolean isTaskScheduled(Object taskInfo) {
            return false;
        }
    }

    @Test
    public void testDispatchNullLogger() {
        DummyDispatcher dispatcher = new DummyDispatcher();
        LogNode<String> node = new LogNode<>(new LoggerID("test"), null,
                new LogOptions("test", LogOptions.CallContext.ANY_THREAD_DIRECT));
        try {
            dispatcher.dispatch(node, Level.INFO, "content");
        } catch (Exception e) {
            fail("Dispatch threw exception for null logger: " + e.getMessage());
        }
    }

    @Test
    public void testDispatchNullNode() {
        DummyDispatcher dispatcher = new DummyDispatcher();
        try {
            dispatcher.dispatch(null, Level.INFO, "content");
        } catch (Exception e) {
            fail("Dispatch threw exception for null node: " + e.getMessage());
        }
    }
}
