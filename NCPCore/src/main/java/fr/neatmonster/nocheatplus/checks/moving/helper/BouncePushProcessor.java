package fr.neatmonster.nocheatplus.checks.moving.helper;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.util.bounce.BounceType;
import fr.neatmonster.nocheatplus.checks.moving.util.bounce.BounceUtil;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.velocity.AccountEntry;
import fr.neatmonster.nocheatplus.checks.moving.velocity.SimpleEntry;
import fr.neatmonster.nocheatplus.checks.moving.velocity.VelocityFlags;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.BlockChangeEntry;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker.Direction;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.components.debug.IDebugPlayer;
import fr.neatmonster.nocheatplus.checks.moving.player.SurvivalFly;

/**
 * Handle bounce and push related pre-check logic.
 */
public final class BouncePushProcessor {

    private BouncePushProcessor() {}

    /** Result container for bounce processing. */
    public static class Result {
        public BounceType verticalBounce = BounceType.NO_BOUNCE;
        public boolean checkNoFall = true;
    }

    public static Result handleBounce(Player player, PlayerLocation pFrom, PlayerLocation pTo,
            Location fromLoc, Location toLoc,
            PlayerMoveData thisMove, PlayerMoveData lastMove, int tick, boolean debug, MovingData data,
            MovingConfig cc, IPlayerData pData, boolean useBlockChangeTracker, BounceType verticalBounce,
            boolean checkNf, BlockChangeTracker blockChangeTracker, IDebugPlayer debugPlayer,
            SurvivalFly survivalFly) {
        Result result = new Result();
        result.verticalBounce = verticalBounce;
        result.checkNoFall = checkNf;

        if (thisMove.yDistance < 0.0) {
            if (!survivalFly.isReallySneaking(player)
                    && BounceUtil.checkBounceEnvelope(player, pFrom, pTo, data, cc, pData)) {
                if ((pTo.getBlockFlags() & fr.neatmonster.nocheatplus.utilities.map.BlockFlags.F_BOUNCE25) != 0L) {
                    result.verticalBounce = BounceType.STATIC;
                    result.checkNoFall = false;
                }
                if (result.verticalBounce == BounceType.NO_BOUNCE && useBlockChangeTracker) {
                    if (BounceUtil.checkPastStateBounceDescend(player, pFrom, pTo, thisMove, lastMove, tick,
                            data, cc, blockChangeTracker) != BounceType.NO_BOUNCE) {
                        result.checkNoFall = false;
                    }
                }
            }
        } else {
            if ((data.verticalBounce != null && BounceUtil.onPreparedBounceSupport(player, fromLoc,
                    toLoc, thisMove, lastMove, tick, data))
                    || (useBlockChangeTracker && thisMove.yDistance <= 1.515 && thisMove.yDistance >= 0.0)) {
                result.verticalBounce = BounceUtil.checkPastStateBounceAscend(player, pFrom, pTo, thisMove,
                        lastMove, tick, pData, debugPlayer, data, cc, blockChangeTracker);
                if (result.verticalBounce != BounceType.NO_BOUNCE) {
                    result.checkNoFall = false;
                }
            }
        }

        if (useBlockChangeTracker && result.checkNoFall
                && !checkPastStateVerticalPush(player, pFrom, pTo, thisMove, lastMove, tick, debug, data, cc,
                        blockChangeTracker, debugPlayer)) {
            checkPastStateHorizontalPush(player, pFrom, pTo, thisMove, lastMove, tick, debug, data, cc,
                    blockChangeTracker, debugPlayer);
        }
        return result;
    }

    public static boolean checkPastStateVerticalPush(Player player, PlayerLocation from, PlayerLocation to,
            PlayerMoveData thisMove, PlayerMoveData lastMove, int tick, boolean debug, MovingData data,
            MovingConfig cc, BlockChangeTracker blockChangeTracker, IDebugPlayer debugPlayer) {
        UUID worldId = from.getWorld().getUID();
        double amount = -1.0;
        boolean addvel = false;
        BlockChangeEntry entryBelowY_POS = blockChangeSearch(from, tick, Direction.Y_POS, debug, data, cc, worldId,
                true, blockChangeTracker);
        if (entryBelowY_POS != null) {
            if (debug) {
                StringBuilder builder = new StringBuilder(150);
                builder.append("Direct block push at (");
                builder.append("x:" + entryBelowY_POS.x);
                builder.append(" y:" + entryBelowY_POS.y);
                builder.append(" z:" + entryBelowY_POS.z);
                builder.append(" direction:" + entryBelowY_POS.direction.name());
                builder.append(")");
                if (debugPlayer != null) {
                    debugPlayer.debug(player, builder.toString());
                }
            }
            if (lastMove.valid && thisMove.yDistance >= 0.0) {
                if ((from.isOnGroundOrResetCond() || thisMove.touchedGroundWorkaround) && from.isOnGround(1.0)) {
                    amount = Math.min(thisMove.yDistance, 0.5625);
                } else if (lastMove.yDistance < -fr.neatmonster.nocheatplus.checks.moving.magic.Magic.GRAVITY_MAX) {
                    amount = Math.min(thisMove.yDistance, 0.34);
                }
                if (thisMove.yDistance == 0.0)
                    amount = 0.0;
            }
            if (lastMove.toIsValid && amount < 0.0 && thisMove.yDistance < 0.0 && thisMove.yDistance > -1.515
                    && lastMove.yDistance >= 0.0) {
                amount = thisMove.yDistance;
                addvel = true;
            }
            data.blockChangeRef.updateSpan(entryBelowY_POS);
        }
        if (amount >= 0.0 || addvel) {
            data.removeLeadingQueuedVerticalVelocityByFlag(VelocityFlags.ORIGIN_BLOCK_MOVE);
            SimpleEntry vel = new SimpleEntry(tick, amount, VelocityFlags.ORIGIN_BLOCK_MOVE, 1);
            data.verticalBounce = vel;
            data.useVerticalBounce(player);
            data.useVerticalVelocity(thisMove.yDistance);
            if (debug && debugPlayer != null) {
                debugPlayer.debug(player, "checkPastStateVerticalPush: set velocity: " + vel);
            }
            return true;
        }
        return false;
    }

    public static boolean checkPastStateHorizontalPush(Player player, PlayerLocation from, PlayerLocation to,
            PlayerMoveData thisMove, PlayerMoveData lastMove, int tick, boolean debug, MovingData data,
            MovingConfig cc, BlockChangeTracker blockChangeTracker, IDebugPlayer debugPlayer) {
        UUID worldId = from.getWorld().getUID();
        double xDistance = to.getX() - from.getX();
        double zDistance = to.getZ() - from.getZ();
        Direction dir;
        if (Math.abs(xDistance) > Math.abs(zDistance))
            dir = xDistance > 0.0 ? Direction.X_POS : Direction.X_NEG;
        else
            dir = zDistance > 0.0 ? Direction.Z_POS : Direction.Z_NEG;
        BlockChangeEntry entry = blockChangeSearch(from, tick, dir, debug, data, cc, worldId, false, blockChangeTracker);
        if (entry != null) {
            int count = MovingData.getHorVelValCount(0.6);
            data.clearActiveHorVel();
            data.addHorizontalVelocity(new AccountEntry(tick, 0.6, count, count));
            data.addVerticalVelocity(new SimpleEntry(-0.35, 6));
            data.blockChangeRef.updateSpan(entry);
            if (debug && debugPlayer != null) {
                StringBuilder builder = new StringBuilder(150);
                builder.append("Direct block push at (");
                builder.append("x:" + entry.x);
                builder.append(" y:" + entry.y);
                builder.append(" z:" + entry.z);
                builder.append(" direction:" + entry.direction.name());
                builder.append(")");
                debugPlayer.debug(player, builder.toString());
                debugPlayer.debug(player, "checkPastStateHorizontalPush: set velocity: " + 0.6);
            }
            return true;
        }
        return false;
    }

    private static BlockChangeEntry blockChangeSearch(PlayerLocation from, int tick, Direction direction,
            boolean debug, MovingData data, MovingConfig cc, UUID worldId, boolean searchBelow,
            BlockChangeTracker blockChangeTracker) {
        int iMinX = Location.locToBlock(from.getMinX());
        int iMaxX = Location.locToBlock(from.getMaxX());
        int iMinZ = Location.locToBlock(from.getMinZ());
        int iMaxZ = Location.locToBlock(from.getMaxZ());
        int belowY = from.getBlockY() - (searchBelow ? 1 : 0);
        for (int x = iMinX; x <= iMaxX; x++) {
            for (int z = iMinZ; z <= iMaxZ; z++) {
                for (int y = belowY; y <= belowY + 1; y++) {
                    BlockChangeEntry entryBelowY_POS = blockChangeTracker.getBlockChangeEntry(data.blockChangeRef, tick,
                            worldId, x, y, z, direction);
                    if (entryBelowY_POS != null)
                        return entryBelowY_POS;
                }
            }
        }
        return null;
    }
}
