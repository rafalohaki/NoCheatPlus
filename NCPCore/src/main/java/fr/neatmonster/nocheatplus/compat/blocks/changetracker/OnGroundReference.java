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

import java.util.LinkedList;
import java.util.ListIterator;

import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.BlockChangeEntry;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache.IBlockCacheNode;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;

/**
 * Maintain consistent past states for a block and the block above.
 * 
 * @author asofold
 *
 */
public class OnGroundReference {

    // Consider a more simplified opportunistic variant.
    // Edge cases may be included or excluded.

    /*
     * Consider adding a super interface or abstract class to make the
     * implementation interchangeable, aiming at a very simple and
     * configurable setup.
     */
    // Possibly detach this so it can be used from within BlockProperties.

    private BlockCache blockCache = null;
    private BlockChangeReference ref = null;
    private long ignoreFlags = 0L;

    private LinkedList<BlockChangeEntry> entries = null;
    private ListIterator<BlockChangeEntry> itEntries = null;
    private BlockChangeEntry entry = null;
    private IBlockCacheNode node = null;

    private LinkedList<BlockChangeEntry> entriesAbove = null;
    private ListIterator<BlockChangeEntry> itEntriesAbove = null;
    private BlockChangeEntry entryAbove = null;
    private IBlockCacheNode nodeAbove = null;

    private int entriesAboveLockIndex = 0;

    public void init(BlockCache blockCache, BlockChangeReference ref, long ignoreFlags) {
        this.blockCache = blockCache;
        this.ref = ref;
        this.ignoreFlags = ignoreFlags;
    }

    public void setEntries(LinkedList<BlockChangeEntry> entries) {
        this.entries = entries;
        this.itEntries = null;
        this.entry = null;
        this.node = null;
    }

    public void setEntriesAbove(LinkedList<BlockChangeEntry> entriesAbove) {
        this.entriesAbove = entriesAbove;
        this.itEntriesAbove = null;
        this.entryAbove = null;
        this.nodeAbove = null;
    }

    public void moveDown() {
        entriesAbove = entries;
        itEntriesAbove = null; //  Gets overridden if not null.
        entryAbove = null;
        nodeAbove = node; // Gets overridden, if entriesAbove are not null.
        // Possibly set other fields to null as well.
    }

    public void updateSpan() {
        if (entry != null) {
            ref.updateSpan(entry);
        }
        if (entryAbove != null) {
            ref.updateSpan(entryAbove);
        }
    }

    /**
     * Detach all.
     */
    public void clear() {
        ignoreFlags = 0L;
        ref = null;
        entries = entriesAbove = null;
        itEntries = itEntriesAbove = null;
        entry = entryAbove = null;
        node = nodeAbove = null;
    }

    public boolean hasAnyEntries() {
        return entries != null || entriesAbove != null;
    }

    public IBlockCacheNode getNode() {
        return node;
    }

    public IBlockCacheNode getNodeAbove() {
        return nodeAbove;
    }

    /**
     * Initialize basics, nodes, etc. Only call after having ensured, that there
     * are any entries (hasAnyEntries).
     * 
     * @return Returns true, if a usable entry or pair of entries has been set.
     *         If no suitable entries exist from start, false is returned.
     */
    public boolean initEntries(final int x, final int y, final int z) {

        itEntries = entries == null ? null : entries.listIterator();
        itEntriesAbove = entriesAbove == null ? null : entriesAbove.listIterator();
        entriesAboveLockIndex = 0;

        entry = fetchNext(itEntries, true);
        if (entry == null) {
            node = blockCache.getOrCreateBlockCacheNode(x, y, z, false);
            if (!BlockProperties.isGround(node.getType(), ignoreFlags)) {
                entriesAbove = entries;
                return false;
            }
        } else {
            node = null;
        }

        entryAbove = fetchNext(itEntriesAbove, false);
        if (entry == null && entryAbove == null) {
            entriesAbove = entries;
            return false;
        }

        if (!alignEntries(x, y, z)) {
            return false;
        }

        initNodes(x, y, z);
        return true;
    }

    /**
     * Advance pair-iteration state.
     * 
     * @return Returns true, if a usable entry or pair of entries has been set.
     *         If no suitable entry could be found, false is returned.
     */
    public boolean advance() {

        // Evaluate which part is most often called.

        if (entries == null) { // Which to test for: entries or entry?
            if (itEntriesAbove.hasNext()) {
                entryAbove = itEntriesAbove.next();
                nodeAbove = entryAbove.previousState;
                return true;
            }
            else {
                // No more entries to check.
                return false;
            }
        }
        else if (entriesAbove == null) {
            entry = null;
            while (itEntries.hasNext()) {
                entry = itEntries.next();
                node = entry.previousState;
                if (BlockProperties.isGround(node.getType(), ignoreFlags)) {
                    // If nodeAbove is ground too, cases could be excluded here.
                    return true;
                }
                else {
                    entry = null;
                    node = null;
                }
            }
            // No more entries to check?
            return entry != null;
        }
        else {
            // Both not null (that case has been excluded above).
            return advanceDualEntries();
        }
    }

    private boolean advanceDualEntries() {
        while (true) {
            if (advanceEntriesAbove()) {
                return true;
            }
            if (!moveToNextEntry()) {
                return false;
            }
            alignEntryAbove();
            if (entryAbove != null) {
                return true;
            }
            if (entry == null && entryAbove == null) {
                return false;
            }
        }
    }

    private boolean advanceEntriesAbove() {
        entryAbove = null;
        nodeAbove = null;
        while (itEntriesAbove.hasNext()) {
            entryAbove = itEntriesAbove.next();
            nodeAbove = entryAbove.previousState;
            if (entry == null) {
                return true;
            }
            if (entry.nextEntryTick >= 0 && entryAbove.tick > entry.nextEntryTick) {
                entryAbove = null;
                nodeAbove = null;
                break;
            }
            if (entry.overlapsIntervalOfValidity(entryAbove)) {
                return true;
            }
            entryAbove = null;
            nodeAbove = null;
        }
        return false;
    }

    private boolean moveToNextEntry() {
        entry = null;
        node = null;
        if (!itEntries.hasNext()) {
            entry = entryAbove = null;
            node = nodeAbove = null;
            return false;
        }
        entry = itEntries.next();
        node = entry.previousState;
        rewindEntriesAbove();
        return true;
    }

    private void rewindEntriesAbove() {
        while (itEntriesAbove.nextIndex() > entriesAboveLockIndex) {
            entryAbove = itEntriesAbove.previous();
            nodeAbove = entryAbove.previousState;
        }
    }

    private void alignEntryAbove() {
        if (entryAbove == null) {
            return;
        }
        while (!entry.overlapsIntervalOfValidity(entryAbove)) {
            if (entry.nextEntryTick >= 0 && entryAbove.tick > entry.nextEntryTick) {
                entryAbove = null;
                nodeAbove = null;
                break;
            }
            if (itEntriesAbove.hasNext()) {
                entryAbove = itEntriesAbove.next();
                nodeAbove = entryAbove.previousState;
                entriesAboveLockIndex = itEntriesAbove.nextIndex();
            }
            else {
                entryAbove = null;
                nodeAbove = null;
                break;
            }
        }
    }

    private BlockChangeEntry fetchNext(final ListIterator<BlockChangeEntry> it, final boolean requireGround) {
        if (it == null) {
            return null;
        }
        while (it.hasNext()) {
            final BlockChangeEntry candidate = it.next();
            if (ref != null && !ref.canUpdateWith(candidate)) {
                continue;
            }
            if (requireGround && !BlockProperties.isGround(candidate.previousState.getType(), ignoreFlags)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private BlockChangeEntry advanceAboveUntil(final long tick) {
        if (itEntriesAbove == null) {
            return null;
        }
        BlockChangeEntry result = null;
        while (itEntriesAbove.hasNext()) {
            result = itEntriesAbove.next();
            if (result.nextEntryTick >= 0 && tick > result.nextEntryTick) {
                result = null;
            } else {
                break;
            }
        }
        return result;
    }

    private BlockChangeEntry advanceBelowUntil(final long tick) {
        if (itEntries == null) {
            return null;
        }
        BlockChangeEntry result = null;
        while (itEntries.hasNext()) {
            result = itEntries.next();
            if ((result.nextEntryTick >= 0 && tick > result.nextEntryTick)
                    || !BlockProperties.isGround(result.previousState.getType(), ignoreFlags)) {
                result = null;
            } else {
                break;
            }
        }
        return result;
    }

    private boolean alignEntries(final int x, final int y, final int z) {
        if (entry != null && entryAbove != null && !entry.overlapsIntervalOfValidity(entryAbove)) {
            if (entryAbove.nextEntryTick >= 0 && entry.tick > entryAbove.nextEntryTick) {
                entryAbove = advanceAboveUntil(entry.tick);
            } else if (entry.nextEntryTick >= 0 && entryAbove.tick > entry.nextEntryTick) {
                entry = advanceBelowUntil(entryAbove.tick);
            } else {
                throw new IllegalStateException("Unintended pun.");
            }
            if (entry == null && entryAbove == null) {
                return false;
            }
            if (entry == null) {
                node = blockCache.getOrCreateBlockCacheNode(x, y, z, false);
                if (!BlockProperties.isGround(node.getType(), ignoreFlags)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void initNodes(final int x, final int y, final int z) {
        if (nodeAbove == null) {
            nodeAbove = entryAbove == null ? blockCache.getOrCreateBlockCacheNode(x, y + 1, z, false)
                    : entryAbove.previousState;
        }
        if (node == null) {
            node = entry == null ? blockCache.getOrCreateBlockCacheNode(x, y, z, false) : entry.previousState;
        }
    }

}
