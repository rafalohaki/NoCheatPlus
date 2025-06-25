package fr.neatmonster.nocheatplus.checks.moving.helper;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.moving.MovingData;

/**
 * Context object shared among check helpers.
 *
 * @param player the player related to the check
 * @param data moving related data of the player
 */
public record CheckContext(Player player, MovingData data) {}
