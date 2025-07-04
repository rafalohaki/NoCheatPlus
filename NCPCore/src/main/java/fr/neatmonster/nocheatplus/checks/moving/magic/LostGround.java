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
package fr.neatmonster.nocheatplus.checks.moving.magic;

import java.util.Collection;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.player.Passable;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.compat.Bridge1_17;
import fr.neatmonster.nocheatplus.compat.MCAccess;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.IBlockChangeTracker;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockCache;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;


/**
 * Lost ground workarounds.
 * 
 * @author asofold
 *
 */
public class LostGround {


    /**
     * Check if touching the ground was lost (client did not send, or server did not put it through).
     * @param player
     * @param from
     * @param to
     * @param hDistance
     * @param yDistance
     * @param sprinting
     * @param data
     * @param cc
     * @param useBlockChangeTracker 
     * @return If touching the ground was lost.
     */
    public static boolean lostGround(final Player player, final PlayerLocation from, final PlayerLocation to, 
                                     final double hDistance, final double yDistance, final boolean sprinting, 
                                     final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc,
                                     final IBlockChangeTracker blockChangeTracker, final Collection<String> tags) {
        // Consider regrouping conditions with toOnGround first.
        // Some workarounds allow step height (0.6 on MC 1.8).
        // The current yDistance limit might not be appropriate.
        // Temporary let it here
        data.snowFix = (from.getBlockFlags() & BlockFlags.F_HEIGHT_8_INC) != 0;
        if (yDistance >= -0.7 && yDistance <= Math.max(cc.sfStepHeight, LiftOffEnvelope.NORMAL.getMaxJumpGain(data.jumpAmplifier) + 0.174)) {

            // "Mild" Ascending / descending.
            // Ascending
            if (yDistance >= 0.0) {
                if (lastMove.toIsValid && lostGroundAscend(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, tags)) {
                    return true;
                }
            }

            // Descending.
            if (yDistance <= 0.0) {
                if (lostGroundDescend(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, tags)) {
                    return true;	
                }
            }
        }
        else if (yDistance < -0.7) {

            // Clearly descending.
            // Candidate for removal in future revisions.
            if (lastMove.toIsValid && hDistance <= 0.5) {
                if (lostGroundFastDescend(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, tags)) {
                    return true;
                }
            }
        }

        // Block change tracker (kept extra for now).
        if (blockChangeTracker != null && lostGroundPastState(player, from, to, data, cc, blockChangeTracker, tags)) {
            return true;
        }
        // Nothing found.
        return false;
    }


    private static boolean lostGroundPastState(final Player player, final PlayerLocation from, final PlayerLocation to,
                                               final MovingData data, final MovingConfig cc, final IBlockChangeTracker blockChangeTracker,
                                               final Collection<String> tags) {
        // Requires additional heuristics.
        // Consider performing a full y-move at from-xz.
        final int tick = TickTask.getTick();
        if (from.isOnGroundOpportune(cc.yOnGround, 0L, blockChangeTracker, data.blockChangeRef, tick)) {
            // Unsure if setBackSafe is appropriate here; it could move players far on parkour.
            return applyLostGround(player, from, false, data.playerMoves.getCurrentMove(), data, "past", tags);
        }
        return false;
    }


    /**
     * Check if a ground-touch has been lost due to event-sending-frequency or other reasons.<br>
     * This is for ascending only (yDistance >= 0). Needs last move data.
     * @param player
     * @param from
     * @param loc 
     * @param to
     * @param hDistance
     * @param yDistance
     * @param sprinting
     * @param data
     * @param cc
     * @return
     */
    private static boolean lostGroundAscend(final Player player, final PlayerLocation from, final PlayerLocation to, final double hDistance, final double yDistance,
                                            final boolean sprinting, final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc, final Collection<String> tags) {

        if (!validateAscendArgs(player, from, to, lastMove, data, cc)) {
            return false;
        }

        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();

        if (checkMicroLostGround(player, from, hDistance, thisMove, data, tags)) {
            return true;
        }

        final double setBackYDistance = from.getY() - data.getSetBackY();

        if (yDistance <= cc.sfStepHeight && hDistance <= 1.5 && !from.isResetCond()) { // hDistance is arbitrary, just to confine.
            if (handleStepHeightAscend(player, from, to, hDistance, yDistance, lastMove, data, cc, tags, thisMove, setBackYDistance)) {
                return true;
            }
        }

        return false;
    }

    private static boolean validateAscendArgs(final Player player, final PlayerLocation from, final PlayerLocation to,
                                              final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc) {
        return player != null && from != null && to != null && lastMove != null && data != null && cc != null;
    }

    private static boolean checkMicroLostGround(final Player player, final PlayerLocation from, final double hDistance,
                                                final PlayerMoveData thisMove, final MovingData data, final Collection<String> tags) {
        if (hDistance <= 0.03 && from.isOnGround(0.03)) {
            return applyLostGround(player, from, true, thisMove , data, "micro", tags);
        }
        return false;
    }

    private static boolean handleStepHeightAscend(final Player player, final PlayerLocation from, final PlayerLocation to,
                                                  final double hDistance, final double yDistance, final PlayerMoveData lastMove,
                                                  final MovingData data, final MovingConfig cc, final Collection<String> tags,
                                                  final PlayerMoveData thisMove, final double setBackYDistance) {

        final double setBackYMargin = data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier) - setBackYDistance;
        if (setBackYMargin >= 0.0) {
            if (checkHalfStep(player, from, to, hDistance, yDistance, lastMove, data, cc, tags, thisMove, setBackYMargin)) {
                return true;
            }
            if (checkTrapFenceJump(player, from, to, yDistance, lastMove, data, tags, setBackYDistance, setBackYMargin, thisMove)) {
                return true;
            }
            if (checkNoobTower(player, from, to, yDistance, lastMove, data, cc, tags, thisMove)) {
                return true;
            }
        }

        if (lastMove.yDistance < 0.0) {
            if (checkCouldStep(player, from, to, data, cc, tags, thisMove)) {
                return true;
            }
            if (checkEdgeCases(player, from, to, hDistance, yDistance, lastMove, data, tags)) {
                return true;
            }
        }

        return false;
    }

    private static boolean checkHalfStep(final Player player, final PlayerLocation from, final PlayerLocation to,
                                         final double hDistance, final double yDistance, final PlayerMoveData lastMove,
                                         final MovingData data, final MovingConfig cc, final Collection<String> tags,
                                         final PlayerMoveData thisMove, final double setBackYMargin) {
        if (to.isOnGround() && setBackYMargin >= yDistance && hDistance <= thisMove.hAllowedDistanceBase * 2.2) {
            if (lastMove.yDistance < 0.0 || yDistance <= cc.sfStepHeight && from.isOnGround(cc.sfStepHeight - yDistance)) {
                return applyLostGround(player, from, true, thisMove, data, "step", tags);
            }
        }
        return false;
    }

    private static boolean checkTrapFenceJump(final Player player, final PlayerLocation from, final PlayerLocation to,
                                              final double yDistance, final PlayerMoveData lastMove, final MovingData data,
                                              final Collection<String> tags, final double setBackYDistance,
                                              final double setBackYMargin, final PlayerMoveData thisMove) {

        if (setBackYDistance > 1.0 && setBackYDistance <= 1.5
            && setBackYMargin < 0.6 && data.getBunnyhopDelay() > 0
            && yDistance > from.getyOnGround() && lastMove.yDistance <= Magic.GRAVITY_MAX
            && yDistance < Magic.GRAVITY_MIN) {

            to.collectBlockFlags();
            if ((to.getBlockFlags() & BlockFlags.F_ATTACHED_LOW2_SNEW) != 0
                && (to.getBlockFlags() & BlockFlags.F_HEIGHT150) != 0) {

                if (to.isOnGround(0.003, 0.0, 0.0)) {
                    return applyLostGround(player, from, false, thisMove, data, "trapfence", tags);
                }
            }
        }
        return false;
    }

    private static boolean checkNoobTower(final Player player, final PlayerLocation from, final PlayerLocation to,
                                          final double yDistance, final PlayerMoveData lastMove, final MovingData data,
                                          final MovingConfig cc, final Collection<String> tags,
                                          final PlayerMoveData thisMove) {

        final double maxJumpGain = data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier);
        if (maxJumpGain > yDistance
                && (yDistance > 0.0
                    && lastMove.yDistance < 0.0
                    && Math.abs(lastMove.yDistance) + Magic.GRAVITY_MAX + yDistance > cc.yOnGround + maxJumpGain
                    && from.isOnGround(0.025)
                    || lastMove.yDistance == 0.0 && noobTowerStillCommon(to, yDistance))) {

            return applyLostGround(player, from, false, thisMove, data, "nbtwr", tags);
        }
        return false;
    }

    private static boolean checkCouldStep(final Player player, final PlayerLocation from, final PlayerLocation to,
                                          final MovingData data, final MovingConfig cc, final Collection<String> tags,
                                          final PlayerMoveData thisMove) {

        if (from.isOnGround(1.0)
            && BlockProperties.isOnGroundShuffled(to.getBlockCache(), from.getX(), from.getY() + cc.sfStepHeight, from.getZ(),
                                                 to.getX(), to.getY(), to.getZ(), 0.1 + from.getBoxMarginHorizontal(),
                                                 to.getyOnGround(), 0.0)) {
            return applyLostGround(player, from, false, thisMove, data, "couldstep", tags);
        }
        return false;
    }

    private static boolean checkEdgeCases(final Player player, final PlayerLocation from, final PlayerLocation to,
                                          final double hDistance, final double yDistance, final PlayerMoveData lastMove,
                                          final MovingData data, final Collection<String> tags) {

        if (!to.isOnGround()) {
            if (lostGroundEdgeAsc(player, from.getBlockCache(), from.getWorld(), from.getX(), from.getY(), from.getZ(),
                                  from.getBoxMarginHorizontal(), from.getyOnGround(), lastMove, data, "asc1", tags, from.getMCAccess())) {
                return true;
            }

            if (yDistance == 0.0 && lastMove.yDistance <= -0.1515 && (hDistance <= lastMove.hDistance * 1.1)) {
                final double xzMargin = lastMove.yDistance <= -0.23 ? 0.3 : 0.15;
                if (lostGroundEdgeAsc(player, from.getBlockCache(), to.getWorld(), to.getX(), to.getY(),
                                      to.getZ(), from.getX(), from.getY(), from.getZ(),
                                      hDistance, to.getBoxMarginHorizontal(), xzMargin,
                                      data, "asc5", tags, from.getMCAccess())) {
                    return true;
                }
            } else if (from.isOnGround(from.getyOnGround(), 0.0625, 0.0)) {
                return applyLostGround(player, from, false, data.playerMoves.getCurrentMove(), data, "edgeasc2", tags);
            }
        }

        return false;
    }


    /**
     * Common conditions for noob tower without y distance taken (likely also no
     * hdist).
     * 
     * @param to
     * @param yDistance
     * @return
     */
    private static boolean noobTowerStillCommon(final PlayerLocation to, final double yDistance) {
        // Evaluate if a block was recently placed underneath (xz box with 0.025 down, Direction.NONE).
        return yDistance < 0.025 && to.getY() - to.getBlockY() < 0.025
               && to.isOnGround(0.025, Bridge1_17.hasLeatherBootsOn(to.getPlayer()) ? 0 : BlockFlags.F_POWDERSNOW);
    }


    /**
     * Preconditions move dist is 0, not on ground, last h dist > 0, last y dist
     * < 0. Needs last move data.
     * 
     * @param player
     * @param from
     * @param loc
     * @param to
     * @param hDistance
     * @param yDistance
     * @param sprinting
     * @param data
     * @param cc
     * @return
     */
    public static boolean lostGroundStill(final Player player, final PlayerLocation from, final PlayerLocation to, 
                                          final double hDistance, final double yDistance, final boolean sprinting, 
                                          final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc, 
                                          final Collection<String> tags) {

        if (lastMove.yDistance <= -0.23 && lastMove.hDistance > 0.0 && lastMove.yDistance < -0.3) {
            // Duplicated logic with edgeasc5 above.
            if (lostGroundEdgeAsc(player, from.getBlockCache(), to.getWorld(), to.getX(), to.getY(), to.getZ(), from.getX(), from.getY(), from.getZ(), hDistance, to.getBoxMarginHorizontal(), 0.3, data, "asc7", tags, from.getMCAccess())) {
                return true;
            }
        }
        else if ((lastMove.yDistance == 0.0 && lastMove.touchedGround || lastMove.yDistance < 0.0)
                && data.liftOffEnvelope.getMaxJumpGain(data.jumpAmplifier) > yDistance
                && noobTowerStillCommon(to, yDistance)) {
            // Ensure set back is slightly lower, if still on ground.
            final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
            // setBackSafe: false to prevent a lowjump due to the setback reset.
            return applyLostGround(player, from, false, thisMove, data, "nbtwr", tags);
        }
        return false;
    }


    /**
     * Vertical collision with ground on client side, shifting over an edge with
     * the horizontal move. Needs last move data.
     * 
     * @param player
     * @param blockCache
     * @param world
     * @param x1
     *            Target position.
     * @param y1
     * @param z1
     * @param boxMarginHorizontal
     *            Center to edge, at some resolution.
     * @param yOnGround
     * @param data
     * @param tag
     * @return
     */
    private static boolean lostGroundEdgeAsc(final Player player, final BlockCache blockCache, final World world, final double x1, final double y1, 
                                             final double z1, final double boxMarginHorizontal, final double yOnGround, 
                                             final PlayerMoveData lastMove, final MovingData data, final String tag, final Collection<String> tags, 
                                             final MCAccess mcAccess) {

        return lostGroundEdgeAsc(player, blockCache, world, x1, y1, z1, lastMove.from.getX(), lastMove.from.getY(), lastMove.from.getZ(), lastMove.hDistance, boxMarginHorizontal, yOnGround, data, tag, tags, mcAccess);
    }


    /**
     * Vertical collision with ground on client side, shifting over an edge with
     * the horizontal move. Needs last move data.
     * 
     * @param player
     * @param blockCache
     * @param world
     * @param x1
     * @param y1
     * @param z1
     * @param x2
     * @param y2
     * @param z2
     * @param hDistance2
     * @param boxMarginHorizontal
     *            Center to edge, at some resolution.
     * @param yOnGround
     * @param data
     * @param tag
     * @param tags
     * @param mcAccess
     * @return
     */
    private static boolean lostGroundEdgeAsc(final Player player, final BlockCache blockCache, final World world, 
                                             final double x1, final double y1, final double z1, double x2, final double y2, double z2, 
                                             final double hDistance2, final double boxMarginHorizontal, final double yOnGround, 
                                             final MovingData data, final String tag, final Collection<String> tags, final MCAccess mcAccess) {

        // First: calculate vector towards last from.
        x2 -= x1;
        z2 -= z1;

        // double y2 = data.fromY - y1; // Just for consistency checks (lastYDist).
        // Second: cap the size of the extra box (at least horizontal).
        double fMin = 1.0; // Factor for capping.
        if (Math.abs(x2) > hDistance2) {
            fMin = Math.min(fMin, hDistance2 / Math.abs(x2));
        }
        if (Math.abs(z2) > hDistance2) {
            fMin = Math.min(fMin, hDistance2 / Math.abs(z2));
        }

        // Could be extended to be more precise.
        // Third: calculate end points.
        x2 = fMin * x2 + x1;
        z2 = fMin * z2 + z1;

        // Finally test for ground.
        // (We don't add another xz-margin here, as the move should cover ground.)
        if (BlockProperties.isOnGroundShuffled(blockCache, x1, y1, z1, x2, y1 + (data.snowFix ? 0.125 : 0.0), z2, boxMarginHorizontal + (data.snowFix ? 0.1 : 0.0), yOnGround, 0.0)) {
            // data.fromY for set back is not correct, but currently it is more safe; ideally maintain a "distance to ground".
            return applyLostGround(player, new Location(world, x2, y2, z2), true, data.playerMoves.getCurrentMove(), data, "edge" + tag, tags, mcAccess);
        } 
        else {
            return false;
        }
    }


    /**
     * Check if a ground-touch has been lost due to event-sending-frequency or
     * other reasons.<br>
     * This is for descending "mildly" only (-0.5 <= yDistance <= 0). Needs last
     * move data.
     * 
     * @param player
     * @param from
     * @param to
     * @param hDistance
     * @param yDistance
     * @param sprinting
     * @param data
     * @param cc
     * @return
     */
    private static boolean lostGroundDescend(final Player player, final PlayerLocation from, final PlayerLocation to, final double hDistance, final double yDistance, final boolean sprinting, final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc, final Collection<String> tags) {
        // Reorganize for faster exclusions based on hDistance and yDistance.
        // Conditions could be made more strict.
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final double setBackYDistance = to.getY() - data.getSetBackY();
        final double setBackYMargin = setBackYDistance - LiftOffEnvelope.NORMAL.getMaxJumpHeight(data.jumpAmplifier);

        // Collides vertically.
        // Note: checking loc should make sense, rather if loc is higher than from?
        if (yDistance < 0.0 && !to.isOnGround() && from.isOnGround(from.getY() - to.getY() + 0.001)) {
            // Test for passability of the entire box, roughly from feet downwards.
            // Efficiency with Location instances could be improved.
            // Full bounds check might be necessary.
            final Location ref = from.getLocation();
            ref.setY(to.getY());
            // Passable test is obsolete with PassableAxisTracing.
            if (Passable.isPassable(from.getLocation(), ref)) {
                // Needs new model (store detailed on-ground properties).
                return applyLostGround(player, from, false, thisMove, data, "vcollide", tags);
            }
        }

        if (!lastMove.toIsValid) {
            return false;
        }
        
        if (data.sfJumpPhase <= 7) {
                   
            // Check for sprinting down blocks etc.
            if (lastMove.yDistance <= yDistance && setBackYDistance < 0 && !to.isOnGround()) {
                // Consider using setbackYDist <= -1.0 or similar.
                // NOTE: Doesn't seem to be relevant with speed potions.
                if (from.isOnGround(0.6, 0.4, 0.0, 0L)) {
                    // Temporary "fix". (Not so temporary. It's been 6 years... :))
                    // NOTE: Seems to virtually always be preceded by a "vcollide" move.
                    return applyLostGround(player, from, true, thisMove, data, "pyramid", tags);
                }
            }

            // Check for jumping up strange blocks like flower pots on top of other blocks.
            if (yDistance == 0.0 && lastMove.yDistance > 0.0 && lastMove.yDistance < 0.25 
                && data.sfJumpPhase <= data.liftOffEnvelope.getMaxJumpPhase(data.jumpAmplifier)
                && setBackYDistance > 1.0 && setBackYDistance < Math.max(0.0, 1.5 + 0.2 * data.jumpAmplifier) 
                && !to.isOnGround()) {
                
                // Possibly confine by block types.
                if (from.isOnGround(0.25, 0.4, 0, 0L)) {
                    return applyLostGround(player, from, false, thisMove, data, "ministep", tags); // Maybe set to false to prevent setback resetting at the step point, which will cause a lowjump.
                }
            }
        }

        // Lost ground while falling onto/over edges of blocks.
        if (yDistance < 0 && hDistance <= 1.5 && lastMove.yDistance < 0.0 && yDistance > lastMove.yDistance && !to.isOnGround()) {
            // yDistance <= 0 might be better.
            if (from.isOnGround(0.5, 0.2, 0) || to.isOnGround(0.5, Math.min(0.2, 0.01 + hDistance), Math.min(0.1, 0.01 + -yDistance))) {
                return applyLostGround(player, from, true, thisMove, data, "edgedesc", tags);
            }
        }

        // Nothing found.
        return false;
    }

    /**
     * Check if a ground-touch has been lost due to event-sending-frequency or
     * other reasons.<br>
     * This is for fast descending only (yDistance < -0.5). Needs last move
     * data.
     * 
     * @param player
     * @param from
     * @param to
     * @param hDistance
     * @param yDistance
     * @param sprinting
     * @param data
     * @param cc
     * @return
     */
    private static boolean lostGroundFastDescend(final Player player, final PlayerLocation from, final PlayerLocation to, final double hDistance, final double yDistance, final boolean sprinting, final PlayerMoveData lastMove, final MovingData data, final MovingConfig cc, final Collection<String> tags) {
        // Reorganize for faster exclusions based on hDistance and yDistance.
        // More strict conditions might be needed.
        // Lost ground while falling onto/over edges of blocks.
        if (yDistance > lastMove.yDistance && !to.isOnGround()) {
            // yDistance <= 0 might be better.
            // Evaluate handling of stairs.
            // Determine if checking with a raised margin is safe; ideally check from higher yMin downward.
            // Consider using an interpolation method between from and to.
            if (from.isOnGround(0.5, 0.2, 0) || to.isOnGround(0.5, Math.min(0.3, 0.01 + hDistance), Math.min(0.1, 0.01 + -yDistance))) {
                // (Usually yDistance should be -0.078)
                return applyLostGround(player, from, true, data.playerMoves.getCurrentMove(), data, "fastedge", tags);
            }
        }
        return false;
    }


    /**
     * Apply lost-ground workaround.
     * @param player
     * @param refLoc
     * @param setBackSafe If to use the given location as set back.
     * @param data
     * @param tag Added to "lostground_" as tag.
     * @return Always true.
     */
    private static boolean applyLostGround(final Player player, final Location refLoc, final boolean setBackSafe, final PlayerMoveData thisMove, final MovingData data, final String tag, final Collection<String> tags, final MCAccess mcAccess) {
        if (setBackSafe) {
            data.setSetBack(refLoc);
        }
        else {
            // Keep Set back.
        }
        return applyLostGround(player, thisMove, data, tag, tags, mcAccess);
    }


    /**
     * Apply lost-ground workaround.
     * @param player
     * @param refLoc
     * @param setBackSafe If to use the given location as set back.
     * @param data
     * @param tag Added to "lostground_" as tag.
     * @return Always true.
     */
    private static boolean applyLostGround(final Player player, final PlayerLocation refLoc, final boolean setBackSafe, final PlayerMoveData thisMove, final MovingData data, final String tag, final Collection<String> tags) {
        // Set the new setBack and reset the jumpPhase.
        if (setBackSafe) {
            data.setSetBack(refLoc);
        }
        else {
            // Keep Set back.
        }
        return applyLostGround(player, thisMove, data, tag, tags, refLoc.getMCAccess());
    }


    /**
     * Apply lost-ground workaround (data adjustments and tag).
     * @param player
     * @param refLoc
     * @param setBackSafe If to use the given location as set back.
     * @param data
     * @param tag Added to "lostground_" as tag.
     * @return Always true.
     */
    private static boolean applyLostGround(final Player player, final PlayerMoveData thisMove, final MovingData data, final String tag, final Collection<String> tags, final MCAccess mcAccess) {
        // Reset the jumpPhase.
        // ? set jumpphase to 1 / other, depending on stuff ?
        data.sfJumpPhase = 0;
        data.jumpAmplifier = MovingUtil.getJumpAmplifier(player, mcAccess);
        data.clearAccounting();
        // Tell NoFall that we assume the player to have been on ground somehow.
        thisMove.touchedGround = true;
        thisMove.touchedGroundWorkaround = true;
        tags.add("lostground_" + tag);
        return true;
    }

}
