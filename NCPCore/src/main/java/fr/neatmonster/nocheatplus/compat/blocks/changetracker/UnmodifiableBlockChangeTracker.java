package fr.neatmonster.nocheatplus.compat.blocks.changetracker;

import java.util.UUID;

import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.BlockChangeEntry;
import fr.neatmonster.nocheatplus.components.location.IGetPosition;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;

public class UnmodifiableBlockChangeTracker implements IBlockChangeTracker {

    private final BlockChangeTracker delegate;

    public UnmodifiableBlockChangeTracker(BlockChangeTracker delegate) {
        this.delegate = delegate;
    }

    @Override
    public BlockChangeEntry getBlockChangeEntry(BlockChangeReference ref, int tick, UUID worldId,
                                                int x, int y, int z, BlockChangeTracker.Direction direction) {
        return delegate.getBlockChangeEntry(ref, tick, worldId, x, y, z, direction);
    }

    @Override
    public BlockChangeEntry getBlockChangeEntryMatchFlags(BlockChangeReference ref, int tick, UUID worldId,
                                                          int x, int y, int z, BlockChangeTracker.Direction direction,
                                                          long matchFlags) {
        return delegate.getBlockChangeEntryMatchFlags(ref, tick, worldId, x, y, z, direction, matchFlags);
    }

    @Override
    public boolean hasActivityShuffled(UUID worldId, IGetPosition pos1, IGetPosition pos2, double margin) {
        return delegate.hasActivityShuffled(worldId, pos1, pos2, margin);
    }

    @Override
    public boolean hasActivity(UUID worldId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return delegate.hasActivity(worldId, minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    public boolean hasActivityShuffled(UUID worldId, double x1, double y1, double z1, double x2, double y2,
                                       double z2, double margin) {
        return delegate.hasActivityShuffled(worldId, x1, y1, z1, x2, y2, z2, margin);
    }

    @Override
    public boolean isOnGround(BlockCache blockCache, BlockChangeReference ref, int tick, UUID worldId,
                              double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                              long ignoreFlags) {
        return delegate.isOnGround(blockCache, ref, tick, worldId, minX, minY, minZ, maxX, maxY, maxZ, ignoreFlags);
    }
}
