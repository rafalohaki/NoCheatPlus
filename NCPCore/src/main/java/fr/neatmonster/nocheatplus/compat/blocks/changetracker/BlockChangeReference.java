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
package fr.neatmonster.nocheatplus.compat.blocks.changetracker;

import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.BlockChangeEntry;
import fr.neatmonster.nocheatplus.utilities.location.RichBoundsLocation;

/**
 * Simple class for helping with query functionality. Reference a
 * BlockChangeEntry and contain more information, such as validity for further
 * use/effects. This is meant for storing the state of last-consumed block
 * change entries for a context within some data.
 * 
 * @author asofold
 *
 */
public class BlockChangeReference {
    // NOTE: consider implementing an IBlockChangeReference interface

    /*
     * Possible alternative: public BlockChangeEntry firstUsedEntry = null;
     * Would the span suffice? Consider using span + timing or just the
     * span during one check covering multiple blocks.
     */

    /** First used (oldest) entry during checking. */
    public BlockChangeEntry firstSpanEntry = null;
    /** Last used (newest) entry during checking. */
    public BlockChangeEntry lastSpanEntry = null;

    /**
     * Last used block change entry, set after a complete iteration of
     * checking, update with updateFinal.
     */
    public BlockChangeEntry lastUsedEntry = null;
    // Consider storing the tick of when lastUsedEntry had been used, to allow invalidation.

    /**
     * Indicate if the timing of the last entry is still regarded as valid.
     */
    public boolean valid = false;

    /**
     * Check if this reference can be updated with the given entry, considering
     * set validity information. By default, the given tick either must be
     * greater than the stored one, or the tick are the same and valid is set to
     * true. The internal state is not changed by calling this.
     * 
     * @param entry
     * @return
     */
    public boolean canUpdateWith(final BlockChangeEntry entry) {
        // Formerly: return this.lastUsedEntry == null || entry.id > this.lastUsedEntry.id || entry.id == this.lastUsedEntry.id && valid;
        // Consider allowing the same tick if the id is higher (order of ids doesn't help much though).
        // Consider a tick-tolerance value (would need storing the tick of setting/altering).
        // Consider keeping a map/set of used entries and allow reuse depending on context.
        /*
         * Possible alternative context definition: hard invalidation via lastUsedEntry
         * and soft invalidation via TBA span. E.g. hard for on ground and
         * passable, soft for push/pull.
         */
        // There might be a span of validity.
        /*
         * Using ticks seems more appropriate, as ids are not necessarily
         * ordered in a relevant way, if they reference the same tick. Even
         * one tick may be too narrow.
         */

        // Once the rest works, reinstate invalidation and boil it down to something useful.

        //return this.lastUsedEntry == null || entry.tick > this.lastUsedEntry.tick || entry.tick == this.lastUsedEntry.tick && valid;
        return true; // temporary testing behavior
    }

    /**
     * Update the span during checking. Ensure to test canUpdateWith(entry)
     * before calling this.
     * 
     * @param entry
     */
    public void updateSpan(final BlockChangeEntry entry) {
        if (firstSpanEntry == null || entry.id < firstSpanEntry.id) {
            firstSpanEntry = entry;
        }
        if (lastSpanEntry == null || entry.id > lastSpanEntry.id) {
            lastSpanEntry = entry;
        }
    }

    /**
     * Update lastUsedEntry by the set span, assuming <i>to</i> to be the
     * move end-point to continue from next time. This is meant to finalize
     * prepared changes/span for use with the next move.
     * 
     * @param to
     *            If not null, allows keeping the latest entry valid, if
     *            intersecting with the bounding box of <i>to</i>.
     */
    public void updateFinal(final RichBoundsLocation to) {
        if (firstSpanEntry == null) {
            return;
        }
        // Consider a span margin, for which we set last used to first span.
        /*
         * Question: What with latest entries that stay valid until half round
         * trip time? Should perhaps keep validity also if entries are the
         * latest ones, needs updating in span already - can/should do
         * without bounds?
         */
        if (lastSpanEntry != null && (lastUsedEntry == null || lastSpanEntry.id > lastUsedEntry.id)) {
            lastUsedEntry = lastSpanEntry;
            if (to != null && to.isBlockIntersecting(
                    lastSpanEntry.x, lastSpanEntry.y, lastSpanEntry.z, lastSpanEntry.direction.blockFace)) {
                valid = true;
            }
            else {
                valid = false;
            }
        }
        firstSpanEntry = lastSpanEntry = null;
    }

    /**
     * Retrieve a shallow copy of this object.
     * 
     * @return
     */
    public BlockChangeReference copy() {
        final BlockChangeReference copy = new BlockChangeReference();
        copy.firstSpanEntry = this.firstSpanEntry;
        copy.lastSpanEntry = this.lastSpanEntry;
        copy.lastUsedEntry = this.lastUsedEntry;
        copy.valid = this.valid;
        return copy;
    }

    public void clear() {
        firstSpanEntry = lastSpanEntry = lastUsedEntry = null;
        valid = false;
    }

    @Override
    public int hashCode() {
        return 42;
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || !(obj instanceof BlockChangeReference)) {
            return false;
        }
        final BlockChangeReference other = (BlockChangeReference) obj;
        return valid == other.valid && 
                (lastUsedEntry != null && lastUsedEntry.equals(other.lastUsedEntry) 
                || lastUsedEntry == null && other.lastUsedEntry == null)
                && (firstSpanEntry != null && firstSpanEntry.equals(other.firstSpanEntry) 
                || firstSpanEntry == null && other.firstSpanEntry == null)
                && (lastSpanEntry != null && lastSpanEntry.equals(other.lastSpanEntry) 
                || lastSpanEntry == null && other.lastSpanEntry == null)
                ;
    }

}
