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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A log message to be executed within a task from a queue, hiding the generics.
 * @author dev1mc
 *
 */
public class LogRecord<C> implements Runnable {

    /** Logger for reporting log invocation issues. */
    private static final Logger LOGGER =
            Logger.getLogger(LogRecord.class.getName());
    
    private final LogNode<C> node;
    private final Level level;
    private final C content;

    public LogRecord(LogNode<C> node, Level level, C content) {
        this.node = node;
        this.level = level;
        this.content = content;
    }
    
    @Override
    public void run() {
        if (node == null || node.logger == null) {
            LOGGER.log(Level.WARNING,
                    "Log node or logger missing, skipping record.");
            return;
        }
        try {
            node.logger.log(level, content);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to log content", t);
        }
    }
    
}
