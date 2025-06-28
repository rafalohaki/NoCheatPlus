package fr.neatmonster.nocheatplus.checks.blockbreak;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.blockbreak.BlockBreakConfig;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;

/**
 * Container for FastBreak check parameters.
 */
public record FastBreakContext(
        Player player,
        Block block,
        BlockBreakConfig config,
        BlockBreakData breakData,
        IPlayerData playerData,
        AlmostBoolean isInstaBreak) {
}
