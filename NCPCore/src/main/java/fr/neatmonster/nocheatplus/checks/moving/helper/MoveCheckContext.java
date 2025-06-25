package fr.neatmonster.nocheatplus.checks.moving.helper;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;

/**
 * Immutable context for movement related helpers.
 *
 * @param player the player
 * @param thisMove data for the current move
 * @param lastMove data for the previous move
 * @param data player specific moving data
 */
public record MoveCheckContext(Player player, PlayerMoveData thisMove,
        PlayerMoveData lastMove, MovingData data) {}
