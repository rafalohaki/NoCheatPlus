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
package fr.neatmonster.nocheatplus.utilities.location;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.Location;

/**
 * Pool of reusable {@link Location} objects.
 *
 * <p>This pool is thread-safe for the acquire and release operations.
 * Instances are meant for temporary use on the calling thread and should not be
 * shared across asynchronous tasks. Returned locations are reset with
 * {@code setWorld(null)} before being offered back to the pool.</p>
 */
public class LocationPool {

    private final ConcurrentLinkedQueue<Location> pool = new ConcurrentLinkedQueue<>();

    /**
     * Acquire a temporary {@link Location} instance.
     *
     * @return a location either taken from the pool or a new instance
     */
    public Location acquire() {
        Location loc = pool.poll();
        if (loc == null) {
            loc = new Location(null, 0, 0, 0);
        }
        return loc;
    }

    /**
     * Release a location previously acquired via {@link #acquire()}.
     *
     * <p>The location will have its world cleared via {@code setWorld(null)}
     * before being returned to the pool.</p>
     *
     * @param loc the location to release; ignored if {@code null}
     */
    public void release(final Location loc) {
        if (loc == null) {
            return;
        }
        loc.setWorld(null);
        pool.offer(loc);
    }
}
