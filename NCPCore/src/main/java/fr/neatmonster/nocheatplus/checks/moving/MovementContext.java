package fr.neatmonster.nocheatplus.checks.moving;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import fr.neatmonster.nocheatplus.players.IPlayerData;

/**
 * Aggregates commonly used parameters during movement processing.
 */
public class MovementContext {

    /** The player being processed. */
    public Player player;

    /** Initial location. */
    public Location from;

    /** Target location. */
    public Location to;

    /** The original move event. */
    public PlayerMoveEvent event;

    /** Player specific movement data. */
    public MovingData data;

    /** Configuration for movement checks. */
    public MovingConfig config;

    /** Player specific data container. */
    public IPlayerData pData;

    /** If debug output should be generated. */
    public boolean debug;

    /** Index for split moves. */
    public int multiMoveCount;
}
