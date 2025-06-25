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

import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.util.AuxMoving;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.checks.moving.velocity.AccountEntry;
import fr.neatmonster.nocheatplus.checks.workaround.WRPT;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.utilities.location.LocUtil;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;

/**
 * Horizontal acceleration magic
 */
public class MagicBunny {
    
    /** Relative speed decrease factor after bunnyhop */
    public static final double BUNNY_SLOPE = 0.66;
    /** (Air) Friction factor for bunnyhopping */
    public static final double BUNNY_FRICTION = 0.99;
    /** Maximum delay from hop to hop. */
    public static final int BUNNYHOP_MAX_DELAY = 10; // ((https://www.mcpk.wiki/wiki/Jumping)
    /** Divisor vs. last hDist for minimum slow down. */
    private static final double bunnyDivFriction = 160.0; // Rather in-air, blocks would differ by friction.
    private static final boolean ServerIsAtLeast1_13 = ServerVersion.compareMinecraftVersion("1.13") >= 0;

    /** Helper result for special hop delay handling. */
    private static class HopCheckResult {
        final boolean allowHop;
        final boolean doubleBunny;

        HopCheckResult(final boolean allowHop, final boolean doubleBunny) {
            this.allowHop = allowHop;
            this.doubleBunny = doubleBunny;
        }
    }


    /**
     * Test for bunny hop / bunny fly. Does modify data only if 0.0 is returned.
     * @param from
     * @param to
     * @param player
     * @param hDistance
     * @param hAllowedDistance
     * @param hDistanceAboveLimit
     * @param yDistance
     * @param sprinting
     * @param data
     * @param cc
     * @param tags
     * @return hDistanceAboveLimit
     */
    public static double bunnyHop(final PlayerLocation from, final PlayerLocation to, final Player player,
                                  final double hAllowedDistance, double hDistanceAboveLimit, final boolean sprinting,
                                  final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                  final MovingData data, final MovingConfig cc, final Collection<String> tags,
                                  final double speedAmplifier) {

        if (from == null || to == null || player == null) {
            return hDistanceAboveLimit;
        }

        // Shortcuts
        boolean allowHop = true; 
        boolean double_bunny = false;
        final double finalSpeed = thisMove.hAllowedDistance;
        final double hDistance = thisMove.hDistance;
        final double yDistance = thisMove.yDistance;
        final double baseSpeed = thisMove.hAllowedDistanceBase;
        final boolean headObstructed = thisMove.headObstructed || lastMove.headObstructed && lastMove.toIsValid;
        /** Catch-all multiplier for all those cases where bunnyhop activation can happen at lower accelerations.*/
        final boolean needLowerMultiplier = Magic.wasOnIceRecently(data) || Magic.wasOnBouncyBlockRecently(data) || headObstructed || !Double.isInfinite(speedAmplifier);
        final PlayerMoveData pastMove2 = data.playerMoves.getSecondPastMove();
        final PlayerMoveData pastMove3 = data.playerMoves.getThirdPastMove();
        final PlayerMoveData pastMove4 = data.playerMoves.getPastMove(3);
        final double minJumpGain = data.liftOffEnvelope.getMinJumpGain(data.jumpAmplifier);
        final int fromData = from.getData(from.getBlockX(), from.getBlockY(), from.getBlockZ());


        ///////////////////////////////////////////////////////////////////
        // After hop checks ("bunnyfly" phase, special cases)            //
        ///////////////////////////////////////////////////////////////////
        // Note: Rework and simplify bunnyfly, merge bunnyslope and friction behaviour. Possibly stick to how Minecraft calculates speed.
        // (bunnyhop model is decently accurate, despite a bit convoluted)
        if (lastMove.toIsValid && data.bunnyhopDelay > 0 && hDistance > baseSpeed) {
            allowHop = false;
            final int hopTime = BUNNYHOP_MAX_DELAY - data.bunnyhopDelay;

            // Bunnyfly phase (decreasing speed due to friction)
            if (lastMove.hDistance > hDistance) { 

                final double hDistDiff = lastMove.hDistance - hDistance;
                // Slope (directly after hop but before friction): the initial/bunnyhop acceleration needs to drop sharply at first.
                // Ensure relative speed decrease vs. hop is met somehow. 
                if (data.bunnyhopDelay == 9 && hDistDiff >= bunnySpeedLossMod(data, headObstructed) * (lastMove.hDistance - baseSpeed)) {
                    tags.add("bunnyslope");
                    hDistanceAboveLimit = 0.0;
                }
                // Bunny friction: very few air friction than ordinary.
                else if (bunnyFrictionEnvelope(hDistDiff, lastMove.hDistance, hDistanceAboveLimit, hDistance, baseSpeed, data)) {
                    
                    // Now, speed needs to decrease by some minimal amount per event. 
                    final double maxSpeed = baseSpeed * modBunny(headObstructed, data); 
                    final double allowedSpeed = maxSpeed * Math.pow(BUNNY_FRICTION, hopTime); 
                    // Speed is decreasing properly, allow the move.
                    // Consider ActionAccumulator based bunnyfly; sort of an "accounting-bunny" check.
                    // Bunnyhopping on soulsand increases air friction, requiring players to immediately lose the acceleration gain after hop.
                    if (hDistance <= allowedSpeed) {
                        tags.add("bunnyfriction");
                        hDistanceAboveLimit = 0.0;
                    }

                    // ... one move between toonground and liftoff remains for hbuf ... 
                    // Do prolong bunnyfly if the player is yet to touch the ground
                    if (data.bunnyhopDelay == 1 && !thisMove.to.onGround && !to.isResetCond()) {
                        data.bunnyhopDelay++;
                        tags.add("bunnyfly(keep)");
                    }
                    else tags.add("bunnyfly(" + data.bunnyhopDelay + ")");
                }
            } 

            // Check for special cases where the delay is shorter than ordinary (10).
            if (!allowHop) {
                final HopCheckResult result = evaluateSpecialDelayCases(thisMove, lastMove, pastMove2, to,
                        hDistance, baseSpeed, finalSpeed, yDistance, headObstructed, hopTime, data, tags);
                allowHop = result.allowHop;
                if (result.doubleBunny) {
                    double_bunny = true;
                }
            }
        }


        /////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Hitting ground while in a legitimate bunnyfly phase (see resetbunny, mostly happens with slopes)            // 
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // bunnyhop-> bunnyslope-> bunnyfriction-> ground-> microjump(still bunnyfriction)-> bunnyfriction
        //or bunnyhop-> ground-> slidedown-> bunnyfriction
        // Hit ground but slipped away by somehow and still remain bunny friction
        // Consider fixing resetbunny instead of adding this section.
        // This workaround might be obsolete now.
        hDistanceAboveLimit = handleGroundHitDuringBunnyFly(thisMove, lastMove, data, hDistance, baseSpeed,
                hDistanceAboveLimit, tags);


        //////////////////////////////////////////////////////////////////////////////////////////////
        // Bunnyhop model (singular peak up to roughly two times the allowed distance)              //
        //////////////////////////////////////////////////////////////////////////////////////////////
        // Further testing bunny spike over all sorts of speeds and attributes is necessary.
        final double MinAccelMod = needLowerMultiplier ? 1.0274 : (!lastMove.toIsValid || lastMove.hDistance == 0.0 && lastMove.yDistance == 0.0) ? 1.11 : 1.314;
        final double MaxAccelMod = data.momentumTick > 0 ? (data.momentumTick > 2 ? 1.76 : 1.96) : 2.15;
        final double MaxAccelMod1 = data.momentumTick > 0 ? (data.momentumTick > 2 ? 1.9 : 2.1) : 2.3;

        if (allowHop && shouldStartBunnyHop(from, to, cc, thisMove, lastMove, pastMove2, pastMove3, pastMove4,
                data, hDistance, baseSpeed, yDistance, minJumpGain, headObstructed,
                MinAccelMod, MaxAccelMod, MaxAccelMod1, double_bunny)) {
            data.bunnyhopDelay = BUNNYHOP_MAX_DELAY;
            hDistanceAboveLimit = 0.0;
            thisMove.bunnyHop = true;
            tags.add("bunnyhop");
        } else if (allowHop && hDistance >= baseSpeed) {
            tags.add("bunnyenv");
        }
        return hDistanceAboveLimit;
    }

    /**
     * Evaluate special cases for shortened bunnyhop delay.
     */
    private static HopCheckResult evaluateSpecialDelayCases(final PlayerMoveData thisMove,
                                                            final PlayerMoveData lastMove,
                                                            final PlayerMoveData pastMove2,
                                                            final PlayerLocation to,
                                                            final double hDistance, final double baseSpeed,
                                                            final double finalSpeed, final double yDistance,
                                                            final boolean headObstructed, final int hopTime,
                                                            final MovingData data, final Collection<String> tags) {
        boolean allowHop = false;
        boolean doubleBunny = false;

        if ((
                (hDistance / baseSpeed <= 1.92 || hDistance / lastMove.hDistance <= 1.92
                    || !Magic.touchedIce(thisMove) && hDistance / lastMove.hDistance > 1.947
                        && lastMove.hDistance / pastMove2.hDistance < 1.1 && hDistance / lastMove.hDistance < 3.0)
                && (Magic.inAir(pastMove2) && !lastMove.from.onGround && lastMove.to.onGround && thisMove.from.onGround
                    || Magic.touchedIce(pastMove2) && pastMove2.to.onGround && lastMove.from.onGround
                        && lastMove.to.onGround && thisMove.from.onGround && !thisMove.to.onGround)
                && !lastMove.bunnyHop && headObstructed)
                || (!pastMove2.bunnyHop && lastMove.bunnyHop && headObstructed && hopTime == 1 && thisMove.from.onGround
                        && lastMove.hDistance > pastMove2.hDistance && hDistance > lastMove.hDistance
                        && hDistance - lastMove.hDistance >= finalSpeed * 0.24 && hDistance - lastMove.hDistance < finalSpeed * 0.8)) {
            tags.add("headbangbunny");
            allowHop = true;
            data.clearHAccounting();
        } else if (hDistance - lastMove.hDistance >= baseSpeed * 0.5 && hopTime == 1
                && lastMove.yDistance >= -Magic.GRAVITY_MAX / 2.0 && lastMove.yDistance <= 0.0
                && yDistance >= LiftOffEnvelope.NORMAL.getMaxJumpGain(0.0) - 0.02
                && lastMove.touchedGround) {
            tags.add("doublebunny");
            allowHop = doubleBunny = true;
        } else if (data.bunnyhopDelay <= 6 && !thisMove.headObstructed
                && (thisMove.from.onGround || thisMove.touchedGroundWorkaround)) {
            tags.add("ediblebunny");
            allowHop = true;
        } else if (Magic.jumpedUpSlope(data, to, 30) && lastMove.bunnyHop
                && thisMove.from.onGround && lastMove.to.onGround && !lastMove.from.onGround
                && hDistance > lastMove.hDistance && hDistance / lastMove.hDistance <= 1.09
                && (to.getBlockFlags() & BlockFlags.F_BOUNCE25) != 0) {
            tags.add("bouncebunny");
            allowHop = true;
        }

        return new HopCheckResult(allowHop, doubleBunny);
    }

    /**
     * Handle landing during an active bunnyfly phase.
     */
    private static double handleGroundHitDuringBunnyFly(final PlayerMoveData thisMove,
                                                        final PlayerMoveData lastMove,
                                                        final MovingData data, final double hDistance,
                                                        final double baseSpeed, double hDistanceAboveLimit,
                                                        final Collection<String> tags) {
        final double inc = ServerIsAtLeast1_13 ? 0.03 : 0;
        final double hopMargin = Magic.wasOnIceRecently(data) ? 1.4 :
                (data.momentumTick > 0 ? (data.momentumTick > 2 ? 1.0 + inc : 1.11 + inc) : 1.22 + inc);

        if (lastMove.toIsValid && data.bunnyhopDelay <= 0 && data.lastbunnyhopDelay > 0
                && lastMove.hDistance > hDistance && baseSpeed > 0.0 && hDistance / baseSpeed < hopMargin) {
            final double hDistDiff = lastMove.hDistance - hDistance;
            if (bunnyFrictionEnvelope(hDistDiff, lastMove.hDistance, hDistanceAboveLimit, hDistance, baseSpeed, data)) {
                if (hDistDiff < 0.01 || Magic.wasOnIceRecently(data) && hDistDiff <= 0.027) {
                    hDistanceAboveLimit = 0.0;
                    tags.add("lostbunnyfly(" + data.lastbunnyhopDelay + ")");

                    if (data.sfLowJump) {
                        data.sfLowJump = false;
                        tags.add("bunnyflyresume");
                        data.bunnyhopDelay = data.lastbunnyhopDelay - 1;
                        data.lastbunnyhopDelay = 0;
                    }
                } else {
                    data.lastbunnyhopDelay = 0;
                }
            } else {
                data.lastbunnyhopDelay = 0;
            }
        }

        return hDistanceAboveLimit;
    }

    /**
     * Determine if bunnyhop should start for this move.
     */
    private static boolean shouldStartBunnyHop(final PlayerLocation from, final PlayerLocation to,
                                               final MovingConfig cc, final PlayerMoveData thisMove,
                                               final PlayerMoveData lastMove, final PlayerMoveData pastMove2,
                                               final PlayerMoveData pastMove3, final PlayerMoveData pastMove4,
                                               final MovingData data, final double hDistance, final double baseSpeed,
                                               final double yDistance, final double minJumpGain,
                                               final boolean headObstructed, final double minAccelMod,
                                               final double maxAccelMod, final double maxAccelMod1,
                                               final boolean doubleBunny) {

        if (!(hDistance >= baseSpeed
                && ((hDistance > minAccelMod * baseSpeed && hDistance < maxAccelMod * baseSpeed)
                    || ((yDistance > from.getyOnGround() || hDistance < maxAccelMod1 * baseSpeed) && lastMove.toIsValid
                        && hDistance > minAccelMod * lastMove.hDistance && hDistance < 2.15 * lastMove.hDistance
                        && (!pastMove2.bunnyHop || !pastMove3.bunnyHop && !pastMove4.bunnyHop || !thisMove.headObstructed))))) {
            return false;
        }

        if (!(data.liftOffEnvelope == LiftOffEnvelope.NORMAL
                && (!data.sfLowJump || data.sfNoLowJump)
                && (yDistance > 0.0 && yDistance > minJumpGain - Magic.GRAVITY_SPAN
                        || headObstructed
                        || (cc.sfGroundHop || yDistance == 0.0 && !lastMove.touchedGroundWorkaround && !lastMove.from.onGround)
                            && baseSpeed > 0.0 && hDistance / baseSpeed < 1.5
                            && (hDistance / lastMove.hDistance < 1.35 || hDistance / baseSpeed < 1.35)
                        || yDistance < 0.0 && (from.getBlockFlags() & BlockFlags.F_BOUNCE25) != 0
                        || Magic.jumpedUpSlope(data, from, 9) && !lastMove.bunnyHop
                            && yDistance > Magic.GRAVITY_MIN && yDistance <= minJumpGain - Magic.GRAVITY_SPAN
                        || Magic.wasOnIceRecently(data)
                            && (hDistance / baseSpeed < 1.32 || hDistance / lastMove.hDistance < 1.27)
                            && !headObstructed && Magic.jumpedUpSlope(data, from, 14)
                            && (yDistance == 0.0
                                && (lastMove.yDistance - yDistance == 0.0
                                    || Math.abs(lastMove.yDistance - yDistance) > 0.0
                                    && Math.abs(lastMove.yDistance - yDistance) < Magic.GRAVITY_ODD / 2.0)
                                || yDistance < 0.0 && yDistance > -Magic.GRAVITY_MAX
                                    && lastMove.yDistance > yDistance
                                    && lastMove.yDistance < -Magic.GRAVITY_ODD / 4.0)))) {
            return false;
        }

        if (!(data.sfJumpPhase == 0 && thisMove.from.onGround
                || data.sfJumpPhase <= 1 && (thisMove.touchedGroundWorkaround || lastMove.touchedGround && !lastMove.bunnyHop)
                || doubleBunny)) {
            return false;
        }

        return !from.isResetCond() && !to.isResetCond();
    }


    /**
     * After bunnyhop friction envelope (very few air friction).
     * Call if the player is in a "bunnyfly" phase and the distance is higher than allowed.
     * Requires last move's data.
     *
     * @param hDistDiff 
     *            Difference from last to current hDistance
     * @param lastHDistance
     *            hDistane before current
     * @param hDistanceAboveLimit
     * @param currentHDistance
     * @param currentAllowedBaseSpeed 
     *            Applicable base speed (not final;  not taking into account other mechanics, like friction)
     * @return
     */
    public static boolean bunnyFrictionEnvelope(final double hDistDiff, final double lastHDistance, final double hDistanceAboveLimit, 
                                                final double currentHDistance, final double currentAllowedBaseSpeed, final MovingData data) {

        // Conditions may still be too loose; they could be stricter.
        // Clearly not in a friction phase :p
        if (currentHDistance > lastHDistance) {
            return false;
        }
        // Ensure low-jumps don't allow bunnyhops to get through.
        if (data.sfLowJump) {
            return false;
        }
        return  hDistDiff >= lastHDistance / bunnyDivFriction 
                || hDistDiff >= hDistanceAboveLimit / 33.3 
                || hDistDiff >= (currentHDistance - currentAllowedBaseSpeed) * (1.0 - Magic.FRICTION_MEDIUM_AIR);
    }
    

    /**
     * Retrieve the appropriate modifier to calculate the relative speed decrease with
     * @param data
     * @param headObstructed
     * @return the modifier
     */
    public static double bunnySpeedLossMod(final MovingData data, final boolean headObstructed) {
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        return 
               // Slope-sprint-jumping on ice allows little to no speed decrease
               // How exactly was the 0.66 magic value for ordinary jumping derived?
               // Consider removing bunnyslope instead
               // Blue ice 
               Magic.touchedBlueIce(lastMove) ? BUNNY_SLOPE / (headObstructed ? 6.346 : 4.647) :
               // Ice/packed Ice
               Magic.touchedIce(lastMove) ? BUNNY_SLOPE / (headObstructed ? 5.739 : 4.150) :
               // Slimes and beds
               Magic.touchedBouncyBlock(lastMove) ? BUNNY_SLOPE / (headObstructed ? 1.74 : 1.11) :
               // Ordinary
               BUNNY_SLOPE / (headObstructed ? 1.4 : 1.0)
            ;
    }
    

    /**
     * Retrieve the appropriate modifier to increase allowed base speed with in bunnyfly
     * @param headObstructed
     * @param data
     * @return the modifier
     */
    public static double modBunny(final boolean headObstructed, final MovingData data) {
        return  
                // Ice (friction takes care with head obstr, so no need to increase the factor.)
                Magic.wasOnIceRecently(data) ? 1.5415 :
                // Slimes / beds
                Magic.wasOnBouncyBlockRecently(data) ? (headObstructed ? 1.9 : 1.4467) :
                // Ordinary
                headObstructed ? 1.474 : (data.momentumTick > 0 ? 1.09 : 1.255)
            ;
    }
}
