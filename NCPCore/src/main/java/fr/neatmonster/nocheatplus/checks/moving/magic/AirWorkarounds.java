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
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.block.Block;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.velocity.SimpleEntry;
import fr.neatmonster.nocheatplus.checks.moving.velocity.VelocityFlags;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;


/**
 * Aggregate every non-ordinary vertical movement here.
 */
public class AirWorkarounds {

    // ONGOING: Tighten/Review all workarounds: some of them date back to the pre past-move-tracking framework period. They're likely too generic by now.
    // OBSERVED: Review all "landing-on-ground-allows-a-shoter-move" workarounds. They can be exploited for 1-block step cheats.
    /**
     * REMOVED AND TESTED: 
     *  
     *  // REASON: Allows for fastfalling cheats. Falling from great heights doesn't yield any violation without this from testing.
     *  // 0: Disregard not falling faster at some point (our constants don't match 100%).
     *  yDistance < -3.0 && lastMove.yDistance < -3.0 
     *  && Math.abs(yDistDiffEx) < 5.0 * Magic.GRAVITY_MAX 
     *  && data.ws.use(WRPT.W_M_SF_FASTFALL_1)
     *  
     *  // REASON: With the LiquidEnvelope precondition, this will never be applied. Also the lack of any documentation makes it harder to debug. 
     *  // 1: Not documented (!)
     *  || !data.liftOffEnvelope.name().startsWith("LIMIT") && lastMove.yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN 
     *  && lastMove.yDistance > Magic.GRAVITY_ODD
     *  && yDistance > 0.4 * Magic.GRAVITY_ODD && yDistance - lastMove.yDistance < -Magic.GRAVITY_ODD / 2.0
     *  && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_NOT_NORMAL_ENVELOPE_2)
     * 
     * 
     *  // REASON: Not documented. Ignore it.
     *  // 1: (Could be reset condition?)
     *  || lastMove.yDistance > 0.4 * Magic.GRAVITY_ODD && lastMove.yDistance < Magic.GRAVITY_MIN && yDistance == 0.0
     *  && data.ws.use(WRPT.W_M_SF_ODDLIQUID_10)
     * 
     */

    /**
     * Workarounds for exiting cobwebs and jumping on slime.
     * 
     * @param from
     * @param to
     * @param yDistance
     * @param data
     * @return If to skip those sub-checks.
     */
    public static boolean venvHacks(final PlayerLocation from, final PlayerLocation to, final double yDistance, 
                                    final double yDistChange, final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                    final MovingData data, final boolean resetFrom, final boolean resetTo) {
        
        // Only for air phases.
        if (!resetFrom && !resetTo) {
            return false;
        }

        return 
                // 0: Intended for cobweb.
                data.liftOffEnvelope == LiftOffEnvelope.NO_JUMP && data.sfJumpPhase < 60
                && (
                    lastMove.toIsValid && lastMove.yDistance < 0.0 
                    && (
                        // 2: Switch to 0 y-Dist on early jump phase.
                        yDistance == 0.0 && lastMove.yDistance < -Magic.GRAVITY_ODD / 3.0 && lastMove.yDistance > -Magic.GRAVITY_MIN
                        && data.ws.use(WRPT.W_M_SF_WEB_0V1)
                        // 2: Decrease too few.
                        || yDistChange < -Magic.GRAVITY_MIN / 3.0 && yDistChange > -Magic.GRAVITY_MAX
                        && data.ws.use(WRPT.W_M_SF_WEB_MICROGRAVITY1)
                        // 2: Keep negative y-distance (very likely a player height issue).
                        || yDistChange == 0.0 && lastMove.yDistance > -Magic.GRAVITY_MAX && lastMove.yDistance < -Magic.GRAVITY_ODD / 3.0
                        && data.ws.use(WRPT.W_M_SF_WEB_MICROGRAVITY2)
                    )
                    // 1: Keep yDist == 0.0 on first falling.
                    || yDistance == 0.0 && data.sfZeroVdistRepeat > 0 && data.sfZeroVdistRepeat < 10 
                    && thisMove.hDistance < 0.125 && lastMove.hDistance < 0.125
                    && to.getY() - data.getSetBackY() < 0.0 && to.getY() - data.getSetBackY() > -2.0 // Quite coarse.
                    && data.ws.use(WRPT.W_M_SF_WEB_0V2)
                )
                // 0: Jumping on slimes, change viewing direction at the max. height.
                // NOTE: Implicitly removed condition: hdist < 0.125
                || yDistance == 0.0 && data.sfZeroVdistRepeat == 1 
                && (data.isVelocityJumpPhase() || data.hasSetBack() && to.getY() - data.getSetBackY() < 1.35 && to.getY() - data.getSetBackY() > 0.0)
                && Magic.wasOnBouncyBlockRecently(data)
                && data.ws.use(WRPT.W_M_SF_SLIME_JP_2X0)
                ;
    }


    /**
     * Search for velocity entries that have a bounce origin. On match, set the friction jump phase for thisMove/lastMove.
     * 
     * @param to
     * @param yDistance
     * @param lastMove
     * @param data
     * @return if to apply the friction jump phase
     */
    public static boolean oddBounce(final PlayerLocation to, final double yDistance, final PlayerMoveData lastMove, final MovingData data) {

        final SimpleEntry entry = data.peekVerticalVelocity(yDistance, 0, 4);
        if (entry != null && entry.hasFlag(VelocityFlags.ORIGIN_BLOCK_BOUNCE)) {
            data.setFrictionJumpPhase();
            return true;
        }
        else {
            // Try to use past yDis
            final SimpleEntry entry2 = data.peekVerticalVelocity(lastMove.yDistance, 0, 4);
            if (entry2 != null && entry2.hasFlag(VelocityFlags.ORIGIN_BLOCK_BOUNCE)) {
                data.setFrictionJumpPhase();
                return true;
            }
        }
        return false;
    }


    /**
     * Odd speed decrease after lift-off.
     * @param to
     * @param yDistance
     * @param maxJumpGain
     * @param yDistDiffEx
     * @param data
     * @return
     */
    private static boolean oddSlope(final PlayerLocation to, final double yDistance, final double maxJumpGain, 
                                    final double yDistDiffEx, final PlayerMoveData lastMove, 
                                    final MovingData data) {

        return data.sfJumpPhase == 1 
                && Math.abs(yDistDiffEx) < 2.0 * Magic.GRAVITY_SPAN 
                && lastMove.yDistance > 0.0 && yDistance < lastMove.yDistance
                && to.getY() - data.getSetBackY() <= data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                && (
                    // 1: Decrease more after lost-ground cases with more y-distance than normal lift-off.
                    lastMove.yDistance > maxJumpGain && lastMove.yDistance < 1.1 * maxJumpGain 
                    && data.ws.use(WRPT.W_M_SF_SLOPE1)
                    //&& fallingEnvelope(yDistance, lastMove.yDistance, 2.0 * GRAVITY_SPAN)
                    // 1: Decrease more after going through liquid (but normal ground envelope).
                    || lastMove.yDistance > 0.5 * maxJumpGain && lastMove.yDistance < 0.84 * maxJumpGain
                    && lastMove.yDistance - yDistance <= Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN
                    && data.ws.use(WRPT.W_M_SF_SLOPE2)
                );
    }


    /**
     * Jump after leaving the liquid near ground or jumping through liquid
     * (rather friction envelope, problematic). Needs last move data.
     * Jump phase checks are handled in the invoked helper methods.
     *
     * @return If the exemption condition applies.
     */
private static boolean oddLiquid(final double yDistance, final double yDistDiffEx, final double maxJumpGain,
                                 final boolean resetTo, final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                 final MovingData data, final boolean resetFrom, final PlayerLocation from) {

        final boolean liquidEnvelope = data.liftOffEnvelope == LiftOffEnvelope.LIMIT_LIQUID
                || data.liftOffEnvelope == LiftOffEnvelope.LIMIT_NEAR_GROUND
                || data.liftOffEnvelope == LiftOffEnvelope.LIMIT_SURFACE;
        final int blockData = from.getData(from.getBlockX(), from.getBlockY(), from.getBlockZ());
        return isFallingTooFast(yDistance, yDistDiffEx, thisMove, lastMove, data, liquidEnvelope)
                || isLimitLiquidVdistInversion(yDistance, yDistDiffEx, resetTo, lastMove, data, resetFrom)
                || isLiquidEnvelopeBehavior(yDistance, yDistDiffEx, maxJumpGain, liquidEnvelope,
                        blockData, lastMove, data);
    }
    private static boolean isFallingTooFast(final double yDistance, final double yDistDiffEx,
                                            final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                            final MovingData data, final boolean liquidEnvelope) {
        if (yDistDiffEx >= 0.0) {
            return false;
        }
        if (!liquidEnvelope && !data.isVelocityJumpPhase()) {
            return false;
        }
        final boolean frictionIssues = Magic.fallingEnvelope(yDistance, lastMove.yDistance, data.lastFrictionVertical, Magic.GRAVITY_ODD / 2.0)
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_1);
        final boolean leavingLava = lastMove.from.extraPropertiesValid && lastMove.from.inLava
                && Magic.enoughFrictionEnvelope(thisMove, lastMove, Magic.FRICTION_MEDIUM_LAVA, 0.0, 2.0 * Magic.GRAVITY_MAX, 4.0)
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_2);
        return frictionIssues || leavingLava;
    }

    private static boolean isLimitLiquidVdistInversion(final double yDistance, final double yDistDiffEx,
                                                       final boolean resetTo, final PlayerMoveData lastMove,
                                                       final MovingData data, final boolean resetFrom) {
        if (data.liftOffEnvelope != LiftOffEnvelope.LIMIT_LIQUID) {
            return false;
        }
        if (data.sfJumpPhase != 1 || !lastMove.toIsValid || yDistDiffEx > 0.0) {
            return false;
        }
        if (!lastMove.from.inLiquid || (lastMove.to.extraPropertiesValid && lastMove.to.inLiquid)) {
            return false;
        }
        if (resetFrom || !resetTo) {
            return false;
        }
        if (lastMove.yDistance <= 0.0 || lastMove.yDistance >= 0.5 * Magic.GRAVITY_ODD) {
            return false;
        }
        if (yDistance >= 0.0 || Math.abs(Math.abs(yDistance) - lastMove.yDistance) >= Magic.GRAVITY_SPAN / 2.0) {
            return false;
        }
        return data.ws.use(WRPT.W_M_SF_ODDLIQUID_3);
    }

    private static boolean isLiquidEnvelopeBehavior(final double yDistance, final double yDistDiffEx,
                                                    final double maxJumpGain, final boolean liquidEnvelope,
                                                    final int blockData, final PlayerMoveData lastMove,
                                                    final MovingData data) {
        if (!liquidEnvelope) {
            return false;
        }
        return jumpAfterSmallGain(yDistance, yDistDiffEx, maxJumpGain, lastMove, data)
                || tooFewDecreaseUpwards(yDistance, yDistDiffEx, lastMove, data)
                || oddDecreaseDownwards(yDistance, lastMove, data)
                || fallingTooSlowGravitySpeed(yDistance, lastMove, data)
                || wildCardHalfGravity(yDistance, liquidEnvelope, blockData, lastMove, data)
                || issue219Case(yDistance, lastMove, data)
                || tooSmallDecreaseAfterLiftOff(yDistance, lastMove, data)
                || leavingLiquidKeepingDistance(yDistance, lastMove, data)
                || leavingClimbable(yDistDiffEx, lastMove, data)
                || fallingSlightlyTooSlow(yDistance, yDistDiffEx, lastMove, data);
    }

    private static boolean jumpAfterSmallGain(final double yDistance, final double yDistDiffEx,
                                              final double maxJumpGain, final PlayerMoveData lastMove,
                                              final MovingData data) {
        return yDistDiffEx > 0.0 && yDistance > lastMove.yDistance && yDistance < 0.84 * maxJumpGain
                && lastMove.yDistance >= -Magic.GRAVITY_MAX - Magic.GRAVITY_MIN
                && lastMove.yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_4);
    }

    private static boolean tooFewDecreaseUpwards(final double yDistance, final double yDistDiffEx,
                                                 final PlayerMoveData lastMove, final MovingData data) {
        return lastMove.yDistance > 0.0 && yDistance < lastMove.yDistance - Magic.GRAVITY_MAX && yDistDiffEx > 0.0
                && yDistDiffEx < Magic.GRAVITY_MAX + Magic.GRAVITY_ODD
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_5);
    }

    private static boolean oddDecreaseDownwards(final double yDistance, final PlayerMoveData lastMove,
                                                final MovingData data) {
        return lastMove.yDistance < -2.0 * Magic.GRAVITY_MAX && data.sfJumpPhase == 1
                && yDistance < -Magic.GRAVITY_MAX && yDistance > lastMove.yDistance
                && Math.abs(yDistance - lastMove.yDistance * data.lastFrictionVertical) < Magic.GRAVITY_MAX
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_6);
    }

    private static boolean fallingTooSlowGravitySpeed(final double yDistance, final PlayerMoveData lastMove,
                                                      final MovingData data) {
        return data.sfJumpPhase == 1
                && lastMove.yDistance < -Magic.GRAVITY_ODD && lastMove.yDistance > -Magic.GRAVITY_MAX - Magic.GRAVITY_MIN
                && Math.abs(lastMove.yDistance - yDistance) < Magic.GRAVITY_SPAN
                && (yDistance < lastMove.yDistance || yDistance < Magic.GRAVITY_MIN)
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_7);
    }

    private static boolean wildCardHalfGravity(final double yDistance, final boolean liquidEnvelope, final int blockData,
                                               final PlayerMoveData lastMove, final MovingData data) {
        return !(liquidEnvelope && (Math.abs(yDistance) > data.liftOffEnvelope.getMaxJumpGain(0.0) || blockData > 3))
                && lastMove.yDistance > -10.0 * Magic.GRAVITY_ODD / 2.0 && lastMove.yDistance < 10.0 * Magic.GRAVITY_ODD
                && yDistance < lastMove.yDistance - Magic.GRAVITY_MIN / 2.0 && yDistance > lastMove.yDistance - Magic.GRAVITY_MAX
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_8);
    }

    private static boolean issue219Case(final double yDistance, final PlayerMoveData lastMove, final MovingData data) {
        return lastMove.yDistance < 0.2 && lastMove.yDistance >= 0.0
                && yDistance > -0.2 && yDistance < 2.0 * Magic.GRAVITY_MIN
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_9);
    }

    private static boolean tooSmallDecreaseAfterLiftOff(final double yDistance, final PlayerMoveData lastMove,
                                                        final MovingData data) {
        return data.sfJumpPhase == 1 && lastMove.yDistance > -Magic.GRAVITY_ODD
                && lastMove.yDistance <= Magic.GRAVITY_MAX + Magic.GRAVITY_SPAN
                && Math.abs(yDistance - lastMove.yDistance) < 0.0114
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_11);
    }

    private static boolean leavingLiquidKeepingDistance(final double yDistance, final PlayerMoveData lastMove,
                                                        final MovingData data) {
        return data.sfJumpPhase == 1 && Math.abs(yDistance) <= 0.3001
                && yDistance == lastMove.yDistance
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_12);
    }

    private static boolean leavingClimbable(final double yDistDiffEx, final PlayerMoveData lastMove, final MovingData data) {
        return lastMove.from.inLiquid && lastMove.from.onClimbable && yDistDiffEx > 0.0
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_13);
    }

    private static boolean fallingSlightlyTooSlow(final double yDistance, final double yDistDiffEx,
                                                  final PlayerMoveData lastMove, final MovingData data) {
        return yDistDiffEx > 0.0 && (
                fallingTooSlowAroundZero(yDistance, lastMove, data)
                        || movingOutOfLiquidWithVelocity(yDistance, yDistDiffEx, lastMove, data)
                        || oddDecreaseHavingBeenInWater(yDistance, yDistDiffEx, lastMove, data));
    }

    private static boolean fallingTooSlowAroundZero(final double yDistance, final PlayerMoveData lastMove,
                                                    final MovingData data) {
        return lastMove.yDistance > -2.0 * Magic.GRAVITY_MAX - Magic.GRAVITY_ODD
                && yDistance < lastMove.yDistance && lastMove.yDistance - yDistance < Magic.GRAVITY_MAX
                && lastMove.yDistance - yDistance > Magic.GRAVITY_MIN / 4.0
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_14);
    }

    private static boolean movingOutOfLiquidWithVelocity(final double yDistance, final double yDistDiffEx,
                                                         final PlayerMoveData lastMove, final MovingData data) {
        return yDistance > 0.0 && data.sfJumpPhase == 1 && yDistDiffEx < 4.0 * Magic.GRAVITY_MAX
                && yDistance < lastMove.yDistance - Magic.GRAVITY_MAX && data.isVelocityJumpPhase()
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_15);
    }

    private static boolean oddDecreaseHavingBeenInWater(final double yDistance, final double yDistDiffEx,
                                                        final PlayerMoveData lastMove, final MovingData data) {
        return yDistDiffEx > 0.0 && yDistDiffEx < Magic.GRAVITY_MIN && data.sfJumpPhase == 1
                && lastMove.from.extraPropertiesValid && lastMove.from.inWater
                && lastMove.yDistance < -Magic.GRAVITY_ODD / 2.0 && lastMove.yDistance > -Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN
                && yDistance < lastMove.yDistance - 0.001
                && data.ws.use(WRPT.W_M_SF_ODDLIQUID_16);
    }
    private static boolean oddGravity(final PlayerLocation from, final PlayerLocation to,
                                      final double yDistance, final double yDistChange,
                                      final double yDistDiffEx, final PlayerMoveData thisMove,
                                      final PlayerMoveData lastMove, final MovingData data) {

        if (from == null || to == null || thisMove == null || lastMove == null || data == null) {
            return false;
        }

        return isOddGravityAnyEnvelope(from, to, yDistance, yDistChange, thisMove, lastMove, data)
                || isOddGravityWithVelocity(yDistance, yDistChange, yDistDiffEx, lastMove, data)
                || isOddGravitySetBack(from, yDistChange, lastMove, data)
                || isOddGravityJumpEffect(yDistance, yDistChange, lastMove, data)
                || isOddGravityNearZero(yDistance, lastMove, data);
    }

    private static boolean isOddGravityAnyEnvelope(final PlayerLocation from, final PlayerLocation to,
                                                   final double yDistance, final double yDistChange,
                                                   final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                                   final MovingData data) {

        return yDistance > -2.0 * Magic.GRAVITY_MAX - Magic.GRAVITY_MIN && yDistance < 2.0 * Magic.GRAVITY_MAX + Magic.GRAVITY_MIN
                && (
                        lastMove.yDistance < 3.0 * Magic.GRAVITY_MAX + Magic.GRAVITY_MIN && yDistChange < -Magic.GRAVITY_MIN
                        && yDistChange > -2.5 * Magic.GRAVITY_MAX - Magic.GRAVITY_MIN
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_1)
                        || lastMove.yDistance > Magic.GRAVITY_ODD / 2.0 && lastMove.yDistance < Magic.GRAVITY_MIN && yDistance == 0.0
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_2)
                        || lastMove.yDistance <= Magic.GRAVITY_MAX + Magic.GRAVITY_MIN && lastMove.yDistance > Magic.GRAVITY_ODD
                        && yDistance < Magic.GRAVITY_ODD && yDistance > -2.0 * Magic.GRAVITY_MAX - Magic.GRAVITY_ODD / 2.0
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_3)
                        || lastMove.yDistance >= 0.0 && yDistance < Magic.GRAVITY_ODD
                        && (thisMove.headObstructed || lastMove.headObstructed)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_4)
                        || lastMove.yDistance < 0.0 && lastMove.to.extraPropertiesValid && lastMove.to.onGround
                        && yDistance >= -Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN && yDistance <= Magic.GRAVITY_MIN
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_5)
                        || lastMove.yDistance < -Magic.GRAVITY_MAX && yDistChange < -Magic.GRAVITY_ODD / 2.0 && yDistChange > -Magic.GRAVITY_MIN
                        && Magic.wasOnBouncyBlockRecently(data)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_6)
                        || lastMove.yDistance == 0.0 && yDistance < -Magic.GRAVITY_ODD / 2.5
                        && yDistance > -Magic.GRAVITY_MIN && to.isOnGround(Magic.GRAVITY_MIN)
                        && Magic.wasOnBouncyBlockRecently(data)
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_7)
                        || (lastMove.touchedGround || lastMove.to.resetCond) && lastMove.yDistance <= Magic.GRAVITY_MIN
                        && lastMove.yDistance >= -Magic.GRAVITY_MAX
                        && yDistance < lastMove.yDistance - Magic.GRAVITY_SPAN && yDistance < Magic.GRAVITY_ODD
                        && yDistance > lastMove.yDistance - Magic.GRAVITY_MAX
                        && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_8)
                );
    }

    private static boolean isOddGravityWithVelocity(final double yDistance, final double yDistChange,
                                                    final double yDistDiffEx, final PlayerMoveData lastMove,
                                                    final MovingData data) {
        if (!data.isVelocityJumpPhase()) {
            return false;
        }
        return lastMove.yDistance > Magic.GRAVITY_ODD && lastMove.yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_MIN
                && yDistance <= -lastMove.yDistance && yDistance > -lastMove.yDistance - Magic.GRAVITY_MAX - Magic.GRAVITY_ODD
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_1)
                || lastMove.yDistance < -0.204 && yDistance > -0.26
                && yDistChange > -Magic.GRAVITY_MIN && yDistChange < -Magic.GRAVITY_ODD / 4.0
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_2)
                || lastMove.yDistance < -Magic.GRAVITY_ODD && lastMove.yDistance > -Magic.GRAVITY_MIN
                && yDistance > -2.0 * Magic.GRAVITY_MAX - 2.0 * Magic.GRAVITY_MIN && yDistance < -Magic.GRAVITY_MAX
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_3)
                || yDistChange > -Magic.GRAVITY_MIN && yDistChange < -Magic.GRAVITY_ODD
                && lastMove.yDistance < 0.5 && lastMove.yDistance > 0.4
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_4)
                || lastMove.yDistance == 0.0 && yDistance > -Magic.GRAVITY_MIN && yDistance < -Magic.GRAVITY_ODD
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_5)
                || yDistDiffEx > 0.0 && yDistDiffEx < 0.01
                && yDistance > Magic.GRAVITY_MAX && yDistance < lastMove.yDistance - Magic.GRAVITY_MAX
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_VEL_6);
    }

    private static boolean isOddGravitySetBack(final PlayerLocation from, final double yDistChange,
                                               final PlayerMoveData lastMove, final MovingData data) {
        if (!data.hasSetBack() || Math.abs(data.getSetBackY() - from.getY()) >= 1.0 || data.sfLowJump) {
            return false;
        }
        return lastMove.yDistance > Magic.GRAVITY_MAX && lastMove.yDistance < 3.0 * Magic.GRAVITY_MAX
                && yDistChange > -Magic.GRAVITY_MIN && yDistChange < -Magic.GRAVITY_ODD
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_SETBACK);
    }

    private static boolean isOddGravityJumpEffect(final double yDistance, final double yDistChange,
                                                  final PlayerMoveData lastMove, final MovingData data) {
        return data.jumpAmplifier > 0 && lastMove.yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_MIN / 2.0
                && lastMove.yDistance > -2.0 * Magic.GRAVITY_MAX - 0.5 * Magic.GRAVITY_MIN
                && yDistance > -2.0 * Magic.GRAVITY_MAX - 2.0 * Magic.GRAVITY_MIN && yDistance < Magic.GRAVITY_MIN
                && yDistChange < -Magic.GRAVITY_SPAN && data.liftOffEnvelope == LiftOffEnvelope.NORMAL
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_JUMPEFFECT);
    }

    private static boolean isOddGravityNearZero(final double yDistance, final PlayerMoveData lastMove,
                                                final MovingData data) {
        return lastMove.yDistance > -Magic.GRAVITY_MAX && lastMove.yDistance < Magic.GRAVITY_MIN
                && !(lastMove.touchedGround || lastMove.to.extraPropertiesValid && lastMove.to.onGroundOrResetCond)
                && yDistance < lastMove.yDistance - Magic.GRAVITY_MIN / 2.0
                && yDistance > lastMove.yDistance - Magic.GRAVITY_MAX - 0.5 * Magic.GRAVITY_MIN
                && data.ws.use(WRPT.W_M_SF_ODDGRAVITY_NEAR_0);
    }


    /**
     * Odd behavior with moving up or (slightly) down, not like the ordinary
     * friction mechanics, accounting for more than one past move. Needs
     * lastMove to be valid.
     * 
     * @param yDistance
     * @param lastMove
     * @param data
     * @return
     */
    private static boolean oddFriction(final double yDistance, final double yDistDiffEx,
                                       final PlayerMoveData lastMove, final MovingData data,
                                       final PlayerLocation from) {

        if (data == null || lastMove == null) {
            return false;
        }

        final PlayerMoveData pastMove1 = data.playerMoves.getSecondPastMove();
        if (!isPastMoveDataValid(lastMove, pastMove1)) {
            return false;
        }

        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final boolean liquidEnvelope = isLiquidEnvelope(data);

        if (liquidEnvelope && data.sfJumpPhase == 1 && Magic.inAir(thisMove)
                && firstMoveOutOfLiquid(yDistance, yDistDiffEx, lastMove, pastMove1, thisMove, data)) {
            return true;
        }

        if (data.liftOffEnvelope == LiftOffEnvelope.NORMAL && data.sfJumpPhase == 1 && Magic.inAir(thisMove)
                && normalEnvelopeOddFriction(yDistance, lastMove, pastMove1, data)) {
            return true;
        }

        return berryBushExitCondition(yDistance, lastMove, thisMove, data);
    }

    private static boolean isPastMoveDataValid(final PlayerMoveData lastMove, final PlayerMoveData pastMove1) {
        return lastMove.to.extraPropertiesValid && pastMove1 != null && pastMove1.toIsValid && pastMove1.to.extraPropertiesValid;
    }

    private static boolean isLiquidEnvelope(final MovingData data) {
        return data.liftOffEnvelope == LiftOffEnvelope.LIMIT_LIQUID
                || data.liftOffEnvelope == LiftOffEnvelope.LIMIT_NEAR_GROUND
                || data.liftOffEnvelope == LiftOffEnvelope.LIMIT_SURFACE;
    }

    private static boolean firstMoveOutOfLiquid(final double yDistance, final double yDistDiffEx,
                                                final PlayerMoveData lastMove, final PlayerMoveData pastMove1,
                                                final PlayerMoveData thisMove, final MovingData data) {
        return pastMove1.yDistance > lastMove.yDistance - Magic.GRAVITY_MAX
                && lastMove.yDistance > yDistance + Magic.GRAVITY_MAX && lastMove.yDistance > 0.0
                && (
                        yDistDiffEx < 0.0 && Magic.splashMove(lastMove, pastMove1)
                                && (
                                        yDistance > lastMove.yDistance / 5.0 && data.ws.use(WRPT.W_M_SF_ODDFRICTION_1)
                                                || yDistance > -Magic.GRAVITY_MAX
                                                && Math.abs(pastMove1.yDistance - lastMove.yDistance - (lastMove.yDistance - thisMove.yDistance)) < Magic.GRAVITY_MAX
                                                && data.ws.use(WRPT.W_M_SF_ODDFRICTION_2)
                                )
                        || Magic.inLiquid(pastMove1) && pastMove1.from.inLava
                                && Magic.leavingLiquid(lastMove) && lastMove.yDistance > 4.0 * Magic.GRAVITY_MAX
                                && yDistance < lastMove.yDistance - Magic.GRAVITY_MAX
                                && yDistance > lastMove.yDistance - 2.0 * Magic.GRAVITY_MAX
                                && Math.abs(lastMove.yDistance - pastMove1.yDistance) > 4.0 * Magic.GRAVITY_MAX
                                && data.ws.use(WRPT.W_M_SF_ODDFRICTION_3)
                )
                || pastMove1.yDistance < 0.0
                && lastMove.yDistance - Magic.GRAVITY_MAX < yDistance && yDistance < 0.7 * lastMove.yDistance
                && Math.abs(pastMove1.yDistance + lastMove.yDistance) > 2.5
                && (
                        Magic.splashMove(lastMove, pastMove1) && pastMove1.yDistance > lastMove.yDistance
                                || Magic.inLiquid(pastMove1) && Magic.leavingLiquid(lastMove) && pastMove1.yDistance * .7 > lastMove.yDistance
                )
                && data.ws.use(WRPT.W_M_SF_ODDFRICTION_4)
                || yDistance < -0.5
                && pastMove1.yDistance < yDistance && lastMove.yDistance < yDistance
                && Math.abs(pastMove1.yDistance - lastMove.yDistance) < Magic.GRAVITY_ODD
                && yDistance < lastMove.yDistance * 0.67 && yDistance > lastMove.yDistance * data.lastFrictionVertical - Magic.GRAVITY_MIN
                && (Magic.splashMoveNonStrict(lastMove, pastMove1) || Magic.inLiquid(pastMove1) && Magic.leavingLiquid(lastMove))
                && data.ws.use(WRPT.W_M_SF_ODDFRICTION_5);
    }

    private static boolean normalEnvelopeOddFriction(final double yDistance, final PlayerMoveData lastMove,
                                                     final PlayerMoveData pastMove1, final MovingData data) {
        return (Magic.splashMoveNonStrict(lastMove, pastMove1) || Magic.inLiquid(pastMove1) && Magic.leavingLiquid(lastMove))
                && yDistance < lastMove.yDistance - Magic.GRAVITY_MAX
                && yDistance > lastMove.yDistance - 2.0 * Magic.GRAVITY_MAX
                && (
                        Math.abs(lastMove.yDistance - pastMove1.yDistance) > 4.0 * Magic.GRAVITY_MAX
                                || pastMove1.yDistance > 3.0 && lastMove.yDistance > 3.0
                                && Math.abs(lastMove.yDistance - pastMove1.yDistance) < 2.0 * Magic.GRAVITY_MAX
                )
                && data.ws.use(WRPT.W_M_SF_ODDFRICTION_6);
    }

    private static boolean berryBushExitCondition(final double yDistance, final PlayerMoveData lastMove,
                                                  final PlayerMoveData thisMove, final MovingData data) {
        return lastMove.from.inBerryBush && !thisMove.from.inBerryBush
                && data.liftOffEnvelope == LiftOffEnvelope.BERRY_JUMP
                && yDistance < -Magic.GRAVITY_MIN && yDistance > Magic.bushSpeedDescend
                && lastMove.from.extraPropertiesValid
                && data.ws.use(WRPT.W_M_SF_ODDFRICTION_7);
    }


    /**
     * Odd vertical movements with negative yDistance. Rather too fast falling cases.
     * Called after having checked for too big and too short moves, with negative yDist and yDistDiffEx <= 0.0.
     * Doesn't require lastMove's data.
     * 
     * @param yDistance
     * @param yDistDiffEx Difference from actual yDistance to vAllowedDistance
     * @param lastMove
     * @param data
     * @param from
     * @param to
     * @param now
     * @param strictVdistRel
     * @param yDistChange Change seen from last yDistance. Double.MAX_VALUE if lastMove is not valid.
     * @param resetTo
     * @param fromOnGround
     * @param toOnGround
     * @param maxJumpGain
     * @param player
     * @param thisMove
     * @param resetFrom
     * @return  
     */
    public static boolean fastFallExemptions(final double yDistance, final double yDistDiffEx,
                                             final PlayerMoveData lastMove, final MovingData data,
                                             final PlayerLocation from, final PlayerLocation to,
                                             final long now, final boolean strictVdistRel, final double yDistChange,
                                             final boolean resetTo, final boolean fromOnGround, final boolean toOnGround,
                                             final double maxJumpGain, final Player player,
                                             final PlayerMoveData thisMove, final boolean resetFrom) {

        if (isFireworkBoostTransition(data, lastMove, yDistance)) {
            data.keepfrictiontick = 0;
            return true;
        }

        return moveOntoGroundAllowsShorter(yDistDiffEx, yDistChange, resetTo, fromOnGround, thisMove, data)
                || mirroredFastFallCase(yDistance, lastMove, resetTo, thisMove, data)
                || stairsOffGround(yDistance, resetFrom, resetTo, to, lastMove, data)
                || headBlockedFastFall(yDistance, thisMove, lastMove, data)
                || breakingBlockBelow(yDistance, lastMove, data);
    }

    private static boolean isFireworkBoostTransition(final MovingData data, final PlayerMoveData lastMove,
                                                     final double yDistance) {
        return data.hasFireworkBoost && data.keepfrictiontick < 0
                && lastMove.toIsValid && yDistance - lastMove.yDistance > -0.7;
    }

    private static boolean moveOntoGroundAllowsShorter(final double yDistDiffEx, final double yDistChange,
                                                        final boolean resetTo, final boolean fromOnGround,
                                                        final PlayerMoveData thisMove, final MovingData data) {
        return resetTo && (yDistDiffEx > -Magic.GRAVITY_SPAN || !fromOnGround && !thisMove.touchedGround && yDistChange >= 0.0)
                && data.ws.use(WRPT.W_M_SF_FASTFALL_2);
    }

    private static boolean mirroredFastFallCase(final double yDistance, final PlayerMoveData lastMove,
                                                final boolean resetTo, final PlayerMoveData thisMove,
                                                final MovingData data) {
        return yDistance > lastMove.yDistance - Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN
                && (resetTo || thisMove.touchedGround)
                && data.ws.use(WRPT.W_M_SF_FASTFALL_3);
    }

    private static boolean stairsOffGround(final double yDistance, final boolean resetFrom,
                                           final boolean resetTo, final PlayerLocation to,
                                           final PlayerMoveData lastMove, final MovingData data) {
        return resetFrom && yDistance >= -0.5
                && (yDistance > -0.31 || (resetTo || to.isAboveStairs()) && (lastMove.yDistance < 0.0))
                && data.ws.use(WRPT.W_M_SF_FASTFALL_4);
    }

    private static boolean headBlockedFastFall(final double yDistance, final PlayerMoveData thisMove,
                                               final PlayerMoveData lastMove, final MovingData data) {
        return yDistance <= 0.0 && yDistance > -Magic.GRAVITY_MAX - Magic.GRAVITY_SPAN
                && (thisMove.headObstructed || lastMove.toIsValid && lastMove.headObstructed && lastMove.yDistance >= 0.0)
                && data.ws.use(WRPT.W_M_SF_FASTFALL_5);
    }

    private static boolean breakingBlockBelow(final double yDistance, final PlayerMoveData lastMove,
                                              final MovingData data) {
        return Bridge1_13.hasIsSwimming()
                && ((data.sfJumpPhase == 3 && lastMove.yDistance < -0.139 && yDistance > -0.1 && yDistance < 0.005)
                        || (yDistance < -0.288 && yDistance > -0.32 && lastMove.yDistance > -0.1 && lastMove.yDistance < 0.005))
                && data.ws.use(WRPT.W_M_SF_FASTFALL_6);
    }


    /**
     * Odd vertical movements yDistance >= 0.0.
     * Called after having checked for too big moves (yDistDiffEx > 0.0).
     * Doesn't require lastMove's data.
     * 
     * @param yDistance
     * @param yDistDiffEx Difference from actual yDistance to vAllowedDistance
     * @param lastMove
     * @param data
     * @param from
     * @param to
     * @param now
     * @param strictVdistRel 
     * @param maxJumpGain
     * @param vAllowedDistance
     * @param player
     * @param thisMove
     * @return 
     */
    public static boolean shortMoveExemptions(final double yDistance, final double yDistDiffEx, 
                                              final PlayerMoveData lastMove, final MovingData data,
                                              final PlayerLocation from, final PlayerLocation to,
                                              final long now, final boolean strictVdistRel,
                                              final double maxJumpGain, double vAllowedDistance, 
                                              final Player player, final PlayerMoveData thisMove) {

        if (data.hasFireworkBoost
            && data.keepfrictiontick < 0 && lastMove.toIsValid) {
            data.keepfrictiontick = 0;
            return true;
            // Early return: transition from CreativeFly to SurvivalFly having been in a gliding phase.
        }

        return 
                // 0: Allow jumping less high, unless within "strict envelope". 4
                (!strictVdistRel || Math.abs(yDistDiffEx) <= Magic.GRAVITY_SPAN || vAllowedDistance <= 0.2)
                && data.ws.use(WRPT.W_M_SF_SHORTMOVE_1)
                // 0: Too strong decrease with velocity.
                || yDistance > 0.0 && lastMove.toIsValid && lastMove.yDistance > yDistance
                && lastMove.yDistance - yDistance <= lastMove.yDistance / (lastMove.from.inLiquid ? 1.76 : 4.0)
                && data.isVelocityJumpPhase()
                && data.ws.use(WRPT.W_M_SF_SHORTMOVE_2)
                // 0: Head is blocked, thus a shorter move.
                || (thisMove.headObstructed || lastMove.toIsValid && lastMove.headObstructed && lastMove.yDistance >= 0.0)
                && data.ws.use(WRPT.W_M_SF_SHORTMOVE_3)
                // 0: Allow too strong decrease
                || thisMove.yDistance < 1.0 && thisMove.yDistance > 0.9 
                && lastMove.yDistance >= 1.5 && data.sfJumpPhase <= 2
                && lastMove.verVelUsed != null 
                && (lastMove.verVelUsed.flags & (VelocityFlags.ORIGIN_BLOCK_MOVE | VelocityFlags.ORIGIN_BLOCK_BOUNCE)) != 0
                && data.ws.use(WRPT.W_M_SF_SHORTMOVE_4)
        ;
        
    }


    /**
     * Odd vertical movements with yDistDiffEx having returned a positive value (yDistance is bigger than expected.)
     * Checked first.
     * Needs lastMove's data.
     * 
     * @param yDistance
     * @param yDistDiffEx Difference from actual yDistance to vAllowedDistance
     * @param lastMove
     * @param data
     * @param from
     * @param to
     * @param now
     * @param yDistChange Change seen from the last yDistance. Double.MAX_VALUE if lastMove is not valid.
     * @param maxJumpGain
     * @param player
     * @param thisMove
     * @param resetTo
     * @return 
     */
    public static boolean outOfEnvelopeExemptions(final double yDistance, final double yDistDiffEx,
                                                  final PlayerMoveData lastMove, final MovingData data,
                                                  final PlayerLocation from, final PlayerLocation to,
                                                  final long now, final double yDistChange,
                                                  final double maxJumpGain, final Player player,
                                                  final PlayerMoveData thisMove, final boolean resetTo) {

        if (!lastMove.toIsValid) {
            // Skip everything if last move is invalid
            return false;
        }

        if (isSlimeBounce(yDistance, lastMove, data, to)) {
            return true;
        }

        if (handleFrictionTransition(yDistance, lastMove, data)) {
            // Transition from CreativeFly to SurvivalFly having been in a gliding phase
            return true;
        }

        return groundCollisionWorkaround(from, to, yDistance, yDistChange, lastMove, data)
                || specialJump(yDistDiffEx, to, maxJumpGain, yDistance, lastMove, data)
                || noobTower(yDistDiffEx, yDistance, maxJumpGain, thisMove, lastMove, data)
                || breakingBlockSwimming(yDistance, lastMove, data);
    }

    private static boolean isSlimeBounce(final double yDistance, final PlayerMoveData lastMove,
                                         final MovingData data, final PlayerLocation to) {
        if (yDistance > 0.0 && lastMove.yDistance < 0.0
                && AirWorkarounds.oddBounce(to, yDistance, lastMove, data)
                && data.ws.use(WRPT.W_M_SF_SLIME_JP_2X0)) {
            data.setFrictionJumpPhase();
            // Odd slime bounce: set friction and return.
            return true;
        }
        return false;
    }

    private static boolean handleFrictionTransition(final double yDistance, final PlayerMoveData lastMove,
                                                    final MovingData data) {
        if (data.keepfrictiontick < 0) {
            if (lastMove.toIsValid && yDistance < 0.4 && lastMove.yDistance == yDistance) {
                data.keepfrictiontick = 0;
                data.setFrictionJumpPhase();
            } else if (!lastMove.toIsValid) {
                data.keepfrictiontick = 0;
            }
            return true;
        }
        return false;
    }

    private static boolean groundCollisionWorkaround(final PlayerLocation from, final PlayerLocation to,
                                                     final double yDistance, final double yDistChange,
                                                     final PlayerMoveData lastMove, final MovingData data) {
        return yDistance < 0.0 && lastMove.yDistance < 0.0 && yDistChange > -Magic.GRAVITY_MAX
                && (from.isOnGround(Math.abs(yDistance) + 0.001)
                || BlockProperties.isLiquid(to.getTypeId(to.getBlockX(), Location.locToBlock(to.getY() - 0.5),
                        to.getBlockZ())))
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_1);
    }

    private static boolean specialJump(final double yDistDiffEx, final PlayerLocation to, final double maxJumpGain,
                                       final double yDistance, final PlayerMoveData lastMove, final MovingData data) {
        return yDistDiffEx < Magic.GRAVITY_MIN / 2.0 && data.sfJumpPhase == 1
                && to.getY() - data.getSetBackY() <= data.liftOffEnvelope.getMaxJumpHeight(data.jumpAmplifier)
                && lastMove.yDistance <= maxJumpGain && yDistance > -Magic.GRAVITY_MAX
                && yDistance < lastMove.yDistance
                && lastMove.yDistance - yDistance > Magic.GRAVITY_ODD / 3.0
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_2);
    }

    private static boolean noobTower(final double yDistDiffEx, final double yDistance, final double maxJumpGain,
                                     final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                     final MovingData data) {
        return yDistDiffEx < Magic.Y_ON_GROUND_DEFAULT
                && Magic.noobJumpsOffTower(yDistance, maxJumpGain, thisMove, lastMove, data)
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_3);
    }

    private static boolean breakingBlockSwimming(final double yDistance, final PlayerMoveData lastMove,
                                                 final MovingData data) {
        return Bridge1_13.hasIsSwimming()
                && ((data.sfJumpPhase == 7 && yDistance < -0.02 && yDistance > -0.2)
                || (data.sfJumpPhase == 3 && lastMove.yDistance < -0.139 && yDistance > -0.1
                && yDistance < 0.005)
                || (yDistance < -0.288 && yDistance > -0.32 && lastMove.yDistance > -0.1
                && lastMove.yDistance < 0.005))
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_4);
    }
    

   /**
    * Conditions for exemption from the VDistSB check (Vertical distance to set back)
    *
    * @param fromOnGround
    * @param thisMove
    * @param lastMove
    * @param data
    * @param cc
    * @param tags
    * @param now
    * @param player
    * @param totalVDistViolation
    * @return true, if to skip this subcheck
    */
    public static boolean vDistSBExemptions(final boolean toOnGround, final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                            final MovingData data, final MovingConfig cc, final long now, final Player player, 
                                            double totalVDistViolation, final double yDistance, final boolean fromOnGround,
                                            final Collection<String> tags, final PlayerLocation to, final PlayerLocation from) {
        
        final PlayerMoveData pastMove2 = data.playerMoves.getSecondPastMove();
        final PlayerMoveData pastMove3 = data.playerMoves.getThirdPastMove();
        final PlayerMoveData pastMove6 = data.playerMoves.getPastMove(5);
        final double SetBackYDistance = to.getY() - data.getSetBackY();

        return 
                // 0: Ignore: Legitimate step.
                (fromOnGround || thisMove.touchedGroundWorkaround || lastMove.touchedGround
                && toOnGround && yDistance <= cc.sfStepHeight)
                // 0: Teleport to in-air (PaperSpigot 1.7.10).
                || Magic.skipPaper(thisMove, lastMove, data)
                // 0: Bunnyhop into a 1-block wide waterfall to reduce vertical water friction -> ascend in water -> leave waterfall 
                // -> have two, in-air ascending air phases -> double VdistSB violation due to a too high jump, since speed wasn't reduced by enough when in water.
                || data.sfJumpPhase <= 3 && data.liftOffEnvelope == LiftOffEnvelope.LIMIT_LIQUID
                && data.insideMediumCount < 6 && Bridge1_13.hasIsSwimming() && Magic.recentlyInWaterfall(data, 20)
                && (Magic.inAir(thisMove) || Magic.leavingWater(thisMove)) && SetBackYDistance < cc.sfStepHeight 
                && yDistance < LiftOffEnvelope.NORMAL.getMaxJumpGain(0.0) 
                // 0: Lost ground cases
                || thisMove.touchedGroundWorkaround 
                && ( 
                    // 1: Skip if the player could step up by lostground_couldstep.
                    yDistance <= cc.sfStepHeight && tags.contains("lostground_couldstep")
                    // 1: Server-sided-trapdoor-touch-miss: player lands directly onto the fence as if it were 1.0 block high
                    || yDistance < data.liftOffEnvelope.getMaxJumpGain(0.0) && tags.contains("lostground_trapfence")
                )
        ;
    }
    

    /**
     * Odd vertical movements with yDistDiffEx having returned a positive value (yDistance is bigger than expected.)
     * Checked first with outOfEnvelopeExemptions.
     * Does not require lastMove's data.
     * 
     * @param yDistance
     * @param from
     * @param to
     * @param thisMove
     * @param resetTo
     * @return 
     */
    public static boolean outOfEnvelopeNoData(final double yDistance, final PlayerLocation from, final PlayerLocation to, 
                                              final PlayerMoveData thisMove, final boolean resetTo, final MovingData data) {

        return  
                // 0: Allow falling shorter than expected, if onto ground.
                // Note resetFrom should usually mean that allowed dist is > 0 ?
                yDistance <= 0.0 && (resetTo || thisMove.touchedGround) 
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_NODATA1)
                // 0: Pre 1.17 bug.
                || to.isHeadObstructed() && yDistance > 0.0 && yDistance < 1.2 
                && from.getTypeId().toString().endsWith("SHULKER_BOX")
                && data.ws.use(WRPT.W_M_SF_OUT_OF_ENVELOPE_NODATA2)
        ;

   }


    /**
     * Several types of odd in-air moves, mostly with gravity near maximum,
     * friction, medium change. Needs lastMove.toIsValid.
     * 
     * @param from
     * @param to
     * @param yDistance
     * @param yDistChange
     * @param yDistDiffEx
     * @param maxJumpGain
     * @param resetTo
     * @param lastMove
     * @param data
     * @param cc
     * @return true if a workaround applies.
     */
    public static boolean oddJunction(final PlayerLocation from, final PlayerLocation to,
                                      final double yDistance, final double yDistChange, final double yDistDiffEx, 
                                      final double maxJumpGain, final boolean resetTo,
                                      final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                      final MovingData data, final MovingConfig cc, final boolean resetFrom) {
        if (!lastMove.toIsValid) {
            return false;
            // Skip everything if last move is invalid
        }
        if (AirWorkarounds.oddLiquid(yDistance, yDistDiffEx, maxJumpGain, resetTo, thisMove, lastMove, data, resetFrom, from)) {
            // Jump after leaving the liquid near ground.
            return true;
        }
        if (AirWorkarounds.oddGravity(from, to, yDistance, yDistChange, yDistDiffEx, thisMove, lastMove, data)) {
            // Starting to fall / gravity effects.
            return true;
        }
        if ((yDistDiffEx > 0.0 || yDistance >= 0.0) && AirWorkarounds.oddSlope(to, yDistance, maxJumpGain, yDistDiffEx, lastMove, data)) {
            // Odd decrease after lift-off.
            return true;
        }
        if (AirWorkarounds.oddFriction(yDistance, yDistDiffEx, lastMove, data, from)) {
            // Odd behavior with moving up or (slightly) down, accounting for more than one past move.
            return true;
        }
        return false;
    }
}
