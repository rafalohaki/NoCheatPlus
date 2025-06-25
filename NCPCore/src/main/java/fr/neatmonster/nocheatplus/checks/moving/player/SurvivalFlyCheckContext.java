package fr.neatmonster.nocheatplus.checks.moving.player;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;

/**
 * Aggregates parameters used for {@link SurvivalFly#check(SurvivalFlyCheckContext)}.
 */
public record SurvivalFlyCheckContext(
        Player player,
        PlayerLocation from,
        PlayerLocation to,
        int multiMoveCount,
        MovingData data,
        MovingConfig config,
        IPlayerData playerData,
        int tick,
        long now,
        boolean useBlockChangeTracker) {}
