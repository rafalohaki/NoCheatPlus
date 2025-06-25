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
package fr.neatmonster.nocheatplus.logging;

import fr.neatmonster.nocheatplus.logging.details.ILogString;
import fr.neatmonster.nocheatplus.logging.details.ILogThrowable;

/**
 * Central access point log manager with a bias towards String messages.
 * 
 * @author dev1mc
 *
 */
public interface LogManager extends ILogString, ILogThrowable {

    /**
     * A stream that skips all messages. It's not registered officially.
     * 
     * @return
     */
    StreamID getVoidStreamID();

    /**
     * This should be a fail-safe direct String-logger, that has the highest
     * probability of working within the default context and rather skips
     * messages instead of failing or scheduling tasks, typically the main
     * application primary thread.
     * 
     * @return
     */
    StreamID getInitStreamID();

    /**
     * Prefix for the names of the default streams. Don't use this prefix for
     * custom registrations with StreamID and LoggerID.
     * 
     * @return
     */
    String getDefaultPrefix();

    /**
     * Case-insensitive lookup.
     * 
     * @param name
     * @return
     */
    boolean hasLogger(String name);

    /**
     * A newly created id can be used here (case-insensitive comparison by
     * name). For logging use existing ids always.
     * 
     * @param loggerID
     * @return
     */
    boolean hasLogger(final LoggerID loggerID);

    /**
     * Case-insensitive lookup.
     * 
     * @param name
     * @return Returns the registered StreamID or null, if not registered.
     */
    LoggerID getLoggerID(String name);

    /**
     * Case-insensitive lookup.
     * 
     * @param name
     * @return
     */
    boolean hasStream(String name);

    /**
     * A newly created id can be used here (case-insensitive comparison by
     * name). For logging use existing ids always.
     * 
     * @param streamID
     * @return
     */
    boolean hasStream(StreamID streamID);

    /**
     * Case-insensitive lookup.
     * 
     * @param name
     * @return Returns the registered StreamID or null, if not registered.
     */
    StreamID getStreamID(String name);

}
