package fr.neatmonster.nocheatplus.checks.blockbreak;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.players.IPlayerData;

/**
 * Container for parameters used by the fast break check.
 *
 * @param player
 *            the player breaking the block
 * @param block
 *            the block being broken
 * @param config
 *            configuration reference for block breaking
 * @param breakData
 *            cached block breaking state
 * @param playerData
 *            cached player data instance
 * @param isInstaBreak
 *            predicted instant-break state for this block
 */
public record FastBreakContext(
        Player player,
        Block block,
        BlockBreakConfig config,
        BlockBreakData breakData,
        IPlayerData playerData,
        AlmostBoolean isInstaBreak) {
}
