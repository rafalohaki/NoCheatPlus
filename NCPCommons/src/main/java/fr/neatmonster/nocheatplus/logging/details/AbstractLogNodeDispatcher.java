/*
 * This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package fr.neatmonster.nocheatplus.logging.details;

import java.util.List;
import java.util.logging.Level;

import fr.neatmonster.nocheatplus.utilities.ds.corw.IQueueRORA;
import fr.neatmonster.nocheatplus.utilities.ds.corw.QueueRORA;

/**
 * Basic functionality, with int task ids, assuming a primary thread exists.
 * @author dev1mc
 *
 */
public abstract class AbstractLogNodeDispatcher implements LogNodeDispatcher { // NOTE: Name

    // NOTE: Queues might need a drop policy with thresholds.
    // NOTE: Allow multiple tasks for logging, e.g. per file, also thinking of SQL logging. Could pool + round-robin.

    /**
     * This queue has to be processed in a task within the primary thread with calling runLogsPrimary.
     */
    protected final IQueueRORA<LogRecord<?>> queuePrimary = new QueueRORA<LogRecord<?>>();
    protected final IQueueRORA<LogRecord<?>> queueAsynchronous = new QueueRORA<LogRecord<?>>();

    /** Once a queue reaches this size, it will be reduced (loss of content). */
    protected int maxQueueSize = 5000;

    /**
     * Task id, null means the asynchronous task is not running. Synchronize over
     * queueAsynchronous. Must be maintained.
     */
    protected Object taskAsynchronousID = null;
    /**
     * Optional implementation for an asynchronous task, using the
     * taskAsynchronousID, synchronized over queueAsynchronous.
     */
    protected final Runnable taskAsynchronous = () -> {
        // NOTE: A more sophisticated System to allow "wake up on burst"?
        int i = 0;
        while (i < 3) {
            if (runLogsAsynchronous()) {
                i = 0;
                if (!isTaskScheduled(taskAsynchronousID)) {
                    // Shutdown, hard return;
                    return;
                }
                Thread.yield();
            } else {
                i ++;
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    synchronized (queueAsynchronous) {
                        // Ensure re-scheduling can happen.
                        taskAsynchronousID = null;
                    }
                    // NOTE: throw?
                    return;
                }
            }
            synchronized (queueAsynchronous) {
                if (queueAsynchronous.isEmpty()) {
                    if (i >= 3) {
                        // Ensure re-scheduling can happen.
                        taskAsynchronousID = null;
                    }
                } else {
                    i = 0;
                }
            }
        }

    };

    /**
     * Optional init logger to log errors. Should log to the init stream, no queuing.
     */
    protected ContentLogger<String> initLogger = null;

    public <C> void dispatch(LogNode<C> node, Level level, C content) {
        if (node == null || node.logger == null) {
            logINIT(Level.WARNING,
                    "Log node or logger missing, skipping record.");
            logINIT(Level.FINE, "Skipped content: "
                    + (content != null ? content.toString() : "null"));
            logFallback(level, content);
            return;
        }
        if (isWithinContext(node)) {
            try {
                node.logger.log(level, content);
            } catch (RuntimeException e) {
                logINIT(Level.WARNING, "Failed to log content: " + e.getMessage());
            }
        } else {
            scheduleLog(node, level, content);
        }
    }

    private <C> void logFallback(Level level, C content) {
        if (initLogger != null) {
            initLogger.log(level, content != null ? content.toString() : "null");
        }
    }

    protected boolean runLogsPrimary() {
        return runLogs(queuePrimary);
    }

    protected boolean runLogsAsynchronous() {
        return runLogs(queueAsynchronous);
    }

    private boolean runLogs(final IQueueRORA<LogRecord<?>> queue) {
        // NOTE: Consider allowYield + msYield, calling yield after 5 ms if async.
        final List<LogRecord<?>> records = queue.removeAll();
        if (records.isEmpty()) {
            return false;
        }
        for (final LogRecord<?> record : records) {
            record.run();
        }
        return true;
    }

    @Override
    public void flush(long ms) {
        if (!isPrimaryThread()) {
            // NOTE: Could also switch policy to emptying the primary-thread queue if not called from within the primary thread.
            throw new IllegalStateException("Must only be called from within the primary thread.");
        }
        // NOTE: Note that all streams should be emptied here, except the fallback logger.

        // Cancel task.
        synchronized (queueAsynchronous) {
            if (isTaskScheduled(taskAsynchronousID)) {
                // NOTE: Allow queues to set to "no more input" ?
                cancelTask(taskAsynchronousID);
                taskAsynchronousID = null;
            } else {
                // No need to wait.
                ms = 0L;
            }
        }
        if (ms > 0) {
            try {
                // NOTE: Replace by a better concept.
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                // Ignore.
            }
        }

        // Log the rest (from here logging should be done via the appropriate direct-only stream).
        runLogsPrimary();
        runLogsAsynchronous();
    }

    protected <C> boolean isWithinContext(LogNode<C> node) {
        switch (node.options.callContext) {
            case PRIMARY_THREAD_DIRECT:
            case PRIMARY_THREAD_ONLY:
                return isPrimaryThread();
            case ANY_THREAD_DIRECT:
                return true;
            case ASYNCHRONOUS_ONLY:
            case ASYNCHRONOUS_DIRECT:
                return !isPrimaryThread();
            case PRIMARY_THREAD_TASK:
            case ASYNCHRONOUS_TASK:
                // NOTE: Each: Consider detecting if within that task (should rather be in case of re-scheduling?).
                return false; // Always schedule (!).
            default:
                return false; // Force scheduling.
        }
    }

    protected <C> void scheduleLog(LogNode<C> node, Level level, C content) {
        final LogRecord<C> record = new LogRecord<C>(node, level, content); // NOTE: parameters.
        switch (node.options.callContext) {
            case ASYNCHRONOUS_TASK:
            case ASYNCHRONOUS_DIRECT:
                if (queueAsynchronous.add(record) > maxQueueSize) {
                    reduceQueue(queueAsynchronous);
                }
                if (!isTaskScheduled(taskAsynchronousID)) { // Works, due to add being synchronized (not sure it's really better than full sync).
                    scheduleAsynchronous();
                }
                break;
            case PRIMARY_THREAD_TASK:
            case PRIMARY_THREAD_DIRECT:
                queuePrimary.add(record);
                // NOTE: Consider re-scheduling policy ?
                break;
            case ASYNCHRONOUS_ONLY:
            case PRIMARY_THREAD_ONLY:
                // SKIP LOGGING.
                break;
            default:
                // (ANY_THREAD_DIRECT never gets scheduled.)
                throw new IllegalArgumentException("Bad CallContext: " + node.options.callContext);
        }
    }

    /**
     * Hard reduce the queue (heavy locking!).
     * @param queue
     */
    private void reduceQueue(final IQueueRORA<LogRecord<?>> queue) {
        // NOTE: Different dropping strategies (drop first, last, alternate).
        final int dropped;
        synchronized (queue) {
            final int size = queue.size();
            if (size < maxQueueSize) {
                // Never mind :).
                return; 
            }
            logINIT(Level.WARNING, "Dropping log entries from the " + (queue == queueAsynchronous ? "asynchronous" : "primary thread") + " queue to reduce memory consumption...");
            dropped = queue.reduce(maxQueueSize / 2);
        }
        logINIT(Level.WARNING, "Dropped " + dropped + " log entries from the " + (queue == queueAsynchronous ? "asynchronous" : "primary thread") + " queue.");
    }

    @Override
    public void setMaxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
    }

    @Override
    public void setInitLogger(ContentLogger<String> logger) {
        this.initLogger = logger;
    }

    protected void logINIT(final Level level, final String message) {
        if (initLogger != null) {
            initLogger.log(level, message);
        }
    }

    protected abstract boolean isPrimaryThread();

    protected abstract void scheduleAsynchronous();

    protected abstract void cancelTask(Object taskInfo);

    protected abstract boolean isTaskScheduled(Object taskInfo);

}
