package fr.neatmonster.nocheatplus.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import java.util.logging.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import fr.neatmonster.nocheatplus.logging.LoggerID;
import fr.neatmonster.nocheatplus.logging.details.AbstractLogNodeDispatcher;
import fr.neatmonster.nocheatplus.logging.details.LogNode;
import fr.neatmonster.nocheatplus.logging.details.LogOptions;

/**
 * A test suite for the {@link AbstractLogNodeDispatcher} class, verifying
 * its robustness against invalid or null inputs.
 */
@DisplayName("AbstractLogNodeDispatcher Tests")
public class TestLogNodeDispatcher {

    /**
     * A minimal implementation (a "stub") of the AbstractLogNodeDispatcher class,
     * necessary for testing the logic of the dispatch() method.
     * The abstract methods perform no operations.
     */
    private static class DummyDispatcher extends AbstractLogNodeDispatcher {
        @Override
        protected boolean isPrimaryThread() {
            return true;
        }

        @Override
        protected void scheduleAsynchronous() {
            // Do nothing
        }

        @Override
        protected void cancelTask(Object taskInfo) {
            // Do nothing
        }

        @Override
        protected boolean isTaskScheduled(Object taskInfo) {
            return false;
        }
    }

    @Nested
    @DisplayName("dispatch() method")
    class DispatchMethodTests {

        private DummyDispatcher dispatcher;

        @BeforeEach
        void setUp() {
            dispatcher = new DummyDispatcher();
        }

        @Test
        @DisplayName("should not throw an exception when the log node is null")
        void shouldNotThrowExceptionForNullNode() {
            assertDoesNotThrow(
                () -> dispatcher.dispatch(null, Level.INFO, "content"),
                "Dispatch should handle a null node gracefully."
            );
        }

        @Test
        @DisplayName("should not throw an exception when the node's parent logger is null")
        void shouldNotThrowExceptionForNullParentLogger() {
            // Given a log node with a null parent
            LogNode<String> nodeWithNullParent = new LogNode<>(
                new LoggerID("test"),
                null, // The parent logger is null
                new LogOptions("test", LogOptions.CallContext.ANY_THREAD_DIRECT)
            );

            // Then dispatching should not throw an exception
            assertDoesNotThrow(
                () -> dispatcher.dispatch(nodeWithNullParent, Level.INFO, "content"),
                "Dispatch should handle a null parent logger gracefully."
            );
        }
    }
}