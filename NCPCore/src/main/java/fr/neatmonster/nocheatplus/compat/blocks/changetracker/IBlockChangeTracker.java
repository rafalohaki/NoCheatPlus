package fr.neatmonster.nocheatplus.compat.blocks.changetracker;

import java.util.UUID;

import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.BlockChangeEntry;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.components.location.IGetPosition;

public interface IBlockChangeTracker {
    BlockChangeEntry getBlockChangeEntry(BlockChangeReference ref, int tick, UUID worldId,
                                         int x, int y, int z, BlockChangeTracker.Direction direction);

    BlockChangeEntry getBlockChangeEntryMatchFlags(BlockChangeReference ref, int tick, UUID worldId,
                                                   int x, int y, int z, BlockChangeTracker.Direction direction,
                                                   long matchFlags);

    boolean hasActivityShuffled(UUID worldId, IGetPosition pos1, IGetPosition pos2, double margin);

    boolean hasActivity(UUID worldId, int minX, int minY, int minZ,
                        int maxX, int maxY, int maxZ);

    boolean hasActivityShuffled(UUID worldId, double x1, double y1, double z1,
                                double x2, double y2, double z2, double margin);

    boolean isOnGround(BlockCache blockCache, BlockChangeReference ref, int tick, UUID worldId,
                       double minX, double minY, double minZ,
                       double maxX, double maxY, double maxZ,
                       long ignoreFlags);
}
