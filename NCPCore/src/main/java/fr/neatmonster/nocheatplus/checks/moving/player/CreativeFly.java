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
package fr.neatmonster.nocheatplus.checks.moving.player;

import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Arrays;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.magic.LostGround;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.model.ModelFlying;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.checks.moving.velocity.AccountEntry;
import fr.neatmonster.nocheatplus.checks.moving.velocity.SimpleEntry;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeEnchant;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.compat.blocks.changetracker.BlockChangeTracker;
import fr.neatmonster.nocheatplus.components.modifier.IAttributeAccess;
import fr.neatmonster.nocheatplus.components.registry.event.IGenericInstanceHandle;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;
import fr.neatmonster.nocheatplus.checks.moving.helper.MoveCheckContext;
import fr.neatmonster.nocheatplus.checks.moving.helper.VelocityAdjustment;


/**
 * A check designed for people that are exposed to particular effects, flying
 * or gliding. The complement to the "SurvivalFly" check, which is for ordinary gameplay.
 */
public class CreativeFly extends Check {

    /**
     * Debug tags appended during processing. Examples include:
     * <ul>
     *   <li>{@code e_pre} - indicates a pre-glide phase</li>
     *   <li>{@code fw_speed} - applied while fireworks boost is active</li>
     *   <li>{@code e_hspeed} - horizontal speed threshold exceeded</li>
     * </ul>
     */
    private final List<String> tags = new LinkedList<String>();
    private final BlockChangeTracker blockChangeTracker;
    private IGenericInstanceHandle<IAttributeAccess> attributeAccess = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstanceHandle(IAttributeAccess.class);
    /** Default result for invalid elytra handling parameters. */
    private static final double[] INVALID_ELYTRA_RESULT = new double[] {Double.NaN, Double.NaN};

    /**
     * Container holding intermediate glide related values. This keeps
     * parameters grouped and helps to reduce the overall complexity of the
     * gliding computation logic.
     */
    private static class AirGlideState {
        double x;
        double z;
        double allowedY;
        double allowedH;
        double resultV;
        double resultH;
    }


   /**
    * Instantiates a new creative fly check.
    */
    public CreativeFly() {
        super(CheckType.MOVING_CREATIVEFLY);
        blockChangeTracker = NCPAPIProvider.getNoCheatPlusAPI().getBlockChangeTracker();
    }


   /**
    * Checks a player
    *
    * @param player
    * @param from
    * @param to
    * @param data
    * @param cc
    * @param time Milliseconds.
    * @return
    */
    public Location check(final Player player, final PlayerLocation from, final PlayerLocation to,
                          final MovingData data, final MovingConfig cc, final IPlayerData pData,
                          final long time, final int tick,
                          final boolean useBlockChangeTracker) {

        if (player == null || from == null || to == null) {
            return null;
        }

        // Reset tags, just in case.
        tags.clear();
        final boolean debug = pData.isDebugActive(type);
        final GameMode gameMode = player.getGameMode();
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        final ModelFlying model = thisMove.modelFlying;
        final double yDistance = thisMove.yDistance;
        final double hDistance = thisMove.hDistance;
        final boolean flying = gameMode == BridgeMisc.GAME_MODE_SPECTATOR || player.isFlying();
        final boolean sprinting = time <= data.timeSprinting + cc.sprintingGrace;
        final long now = System.currentTimeMillis();
        boolean lostGround = false;

        // Allow elytra fly (not packet mode). Since Winds Anarchy handles
        // Elytra flight in a different plugin, skip further checks here.
        if (allowElytraFlight(player, to, thisMove, data, pData)) {
            return null;
        }

        // Lost ground, if set so.
        if (model.getGround()) {
            lostGround = detectLostGround(player, from, to, thisMove, lastMove, hDistance, yDistance,
                    sprinting, data, cc, useBlockChangeTracker);
        }

        // Do not check for nofall if the player has slowfalling active or is gliding
        handleNoFall(player, model, thisMove, data);
        
        // HACK: when switching model, we need to add some velocity to harmonize the transition and not trggering fps.
        workaroundSwitchingModel(player, thisMove, lastMove, model, data, cc, debug);






        //////////////////////////
        // Horizontal move.
        //////////////////////////

        double[] hMove = processHorizontalMove(player, from, to, hDistance, yDistance, sprinting,
                flying, thisMove, lastMove, time, model, data, cc, lostGround, debug);
        double limitH = hMove[0];
        double resultH = hMove[1];






        //////////////////////////
        // Vertical move.
        //////////////////////////

        double[] vMove = processVerticalMove(player, from, to, yDistance, flying, thisMove, lastMove,
                model, data, cc, debug, hMove[2], pData, now);
        double limitV = vMove[0];
        double resultV = vMove[1];
        final double maximumHeight = vMove[2];

        final double result = Math.max(0.0, resultH) + Math.max(0.0, resultV);



        //////////////////////////
        // Output debug
        //////////////////////////
        if (debug) {
            outpuDebugMove(player, hDistance, limitH, yDistance, limitV, model, tags, data);
        }



        ///////////////////////
        // Violation handling
        ///////////////////////

        Location setBack = handleViolation(player, from, to, model, data, cc, result,
                maximumHeight, debug, tags);

        return finalizeMove(setBack, to, data, thisMove, maximumHeight, player, debug);
    }

    /**
     * Bypass further checks if elytra flight should be ignored.
     */
    private boolean allowElytraFlight(final Player player, final PlayerLocation to,
            final PlayerMoveData thisMove, final MovingData data, final IPlayerData pData) {
        return pData.hasPermission(Permissions.MOVING_ELYTRAFLY, player)
                && player.getInventory() != null
                && player.getInventory().getChestplate() != null
                && player.getInventory().getChestplate().getType() == Material.ELYTRA
                && player.isGliding()
                && updateJumpPhaseAndSetBack(to, data, thisMove);
    }

    private boolean updateJumpPhaseAndSetBack(final PlayerLocation to, final MovingData data,
            final PlayerMoveData move) {
        data.setSetBack(to);
        if (!move.from.onGroundOrResetCond && !move.to.onGroundOrResetCond) {
            data.sfJumpPhase++;
        } else if (move.touchedGround && !move.to.onGroundOrResetCond) {
            data.sfJumpPhase = 1;
        } else {
            data.sfJumpPhase = 0;
        }
        return true;
    }

    private boolean detectLostGround(final Player player, final PlayerLocation from,
            final PlayerLocation to, final PlayerMoveData thisMove, final PlayerMoveData lastMove,
            final double hDistance, final double yDistance, final boolean sprinting,
            final MovingData data, final MovingConfig cc, final boolean useBlockChangeTracker) {

        MovingUtil.prepareFullCheck(from, to, thisMove, Math.max(cc.yOnGround, cc.noFallyOnGround));
        if (!thisMove.from.onGroundOrResetCond) {
            if (from.isSamePos(to)) {
                if (lastMove.toIsValid && lastMove.hDistance > 0.0 && lastMove.yDistance < -0.3
                        && LostGround.lostGroundStill(player, from, to, hDistance, yDistance,
                                sprinting, lastMove, data, cc, tags)) {
                    return true;
                }
            } else if (LostGround.lostGround(player, from, to, hDistance, yDistance, sprinting,
                    lastMove, data, cc,
                    useBlockChangeTracker ? blockChangeTracker : null, tags)) {
                return true;
            }
        }
        return false;
    }

    private void handleNoFall(final Player player, final ModelFlying model,
            final PlayerMoveData thisMove, final MovingData data) {
        if ((Bridge1_13.hasSlowfalling() && model.getScaleSlowfallingEffect())
                || (Bridge1_9.isGlidingWithElytra(player) && thisMove.yDistance > -0.5)) {
            data.clearNoFallData();
        }
    }

    private double[] processHorizontalMove(final Player player, final PlayerLocation from,
            final PlayerLocation to, final double hDistance, final double yDistance,
            final boolean sprinting, final boolean flying, final PlayerMoveData thisMove,
            final PlayerMoveData lastMove, final long time, final ModelFlying model,
            final MovingData data, final MovingConfig cc, final boolean lostGround,
            final boolean debug) {

        double[] resH = hDist(player, from, to, hDistance, yDistance, sprinting, flying,
                thisMove, lastMove, time, model, data, cc);
        double limitH = resH[0];
        double resultH = resH[1];
        double[] rese = hackElytraH(player, from, to, hDistance, yDistance, thisMove, lastMove,
                lostGround, data, cc, debug);
        resultH = Math.max(resultH, rese[1]);
        double baseRes = rese[0];

        if (resultH > 0) {
            double hFreedom = data.getHorizontalFreedom();
            if (hFreedom < resultH) {
                hFreedom += data.useHorizontalVelocity(resultH - hFreedom);
            }
            if (hFreedom > 0.0) {
                resultH = Math.max(0.0, resultH - hFreedom);
                if (resultH <= 0.0) {
                    limitH = hDistance;
                }
                tags.add("hvel");
            }
        } else {
            data.clearActiveHorVel();
        }

        resultH *= 100.0;
        if (resultH > 0.0) {
            tags.add("hdist");
        }
        return new double[] {limitH, resultH, baseRes};
    }

    private double[] processVerticalMove(final Player player, final PlayerLocation from,
            final PlayerLocation to, final double yDistance, final boolean flying,
            final PlayerMoveData thisMove, final PlayerMoveData lastMove, final ModelFlying model,
            final MovingData data, final MovingConfig cc, final boolean debug, final double baseRes,
            final IPlayerData pData, final long now) {

        double limitV = 0.0;
        double resultV = baseRes;

        if (yDistance > 0.0) {
            double[] res = vDistAscend(from, to, yDistance, flying, thisMove, lastMove, model, data,
                    cc, debug);
            resultV = Math.max(resultV, res[1]);
            limitV = res[0];
        } else if (yDistance < 0.0) {
            double[] res = vDistDescend(from, to, yDistance, flying, thisMove, lastMove, model,
                    data, cc);
            resultV = Math.max(resultV, res[1]);
            limitV = res[0];
        } else {
            double[] res = vDistZero(from, to, yDistance, flying, thisMove, lastMove, model, data,
                    cc);
            resultV = Math.max(resultV, res[1]);
            limitV = res[0];
        }

        if (resultV > 0.0
                && (thisMove.verVelUsed != null || data.getOrUseVerticalVelocity(yDistance) != null)) {
            resultV = 0.0;
            tags.add("vvel");
        }

        if (lastMove.toIsValid && !player.isFlying() && model.getScaleLevitationEffect()
                && thisMove.modelFlying == lastMove.modelFlying) {
            final double level = Bridge1_9.getLevitationAmplifier(player) + 1;
            final double allowY = (lastMove.yDistance + (0.05D * level - lastMove.yDistance) * 0.2D)
                    * Magic.FRICTION_MEDIUM_AIR;
            if (allowY * 1.001 >= yDistance) {
                resultV = 0.0;
            }

            if (!from.isHeadObstructed() && !to.isHeadObstructed()
                    && !(now > pData.getLastJoinTime() && pData.getLastJoinTime() + 20000 > now)
                    && !(thisMove.yDistance < 0.0 && lastMove.yDistance - thisMove.yDistance < 0.0001)) {

                if (lastMove.yDistance < 0.0 && thisMove.yDistance < allowY
                        || from.getY() >= to.getY() && !(thisMove.yDistance == 0.0 && allowY < 0.0)) {
                    resultV = Math.max(resultV, 0.1);
                    tags.add("antilevitate");

                    if (data.getOrUseVerticalVelocity(
                            getBaseV(0.0, yDistance, 0f, 0.0, level, 0.0, false)) != null) {
                        data.addVerticalVelocity(new SimpleEntry(yDistance, 2));
                        resultV = 0.0;
                    }
                }
            }
        }

        final double maximumHeight = model.getMaxHeight() + player.getWorld().getMaxHeight();
        if (to.getY() > maximumHeight) {
            tags.add("maxheight");
        }

        resultV *= 100.0;
        if (resultV > 0.0) {
            tags.add("vdist");
        }

        return new double[] {limitV, resultV, maximumHeight};
    }

    private Location handleViolation(final Player player, final PlayerLocation from,
            final PlayerLocation to, final ModelFlying model, final MovingData data,
            final MovingConfig cc, final double result, final double maximumHeight,
            final boolean debug, final List<String> localTags) {

        Location setBack = null;

        if (result > 0.0) {
            data.creativeFlyVL += result;
            final ViolationData vd = new ViolationData(this, player, data.creativeFlyVL, result,
                    cc.creativeFlyActions);
            if (vd.needsParameters()) {
                vd.setParameter(ParameterName.LOCATION_FROM, String.format(Locale.US, "%.2f, %.2f, %.2f",
                        from.getX(), from.getY(), from.getZ()));
                vd.setParameter(ParameterName.LOCATION_TO, String.format(Locale.US, "%.2f, %.2f, %.2f",
                        to.getX(), to.getY(), to.getZ()));
                vd.setParameter(ParameterName.DISTANCE,
                        String.format(Locale.US, "%.2f", TrigUtil.distance(from, to)));
                if (model != null) {
                    vd.setParameter(ParameterName.MODEL, model.getId().toString());
                }
                if (!localTags.isEmpty()) {
                    vd.setParameter(ParameterName.TAGS, StringUtil.join(localTags, "+"));
                }
            }
            if (executeActions(vd).willCancel()) {
                setBack = data.getSetBack(to);
            }
        } else {
            if (to.getY() > maximumHeight) {
                setBack = data.getSetBack(to);
                if (debug) {
                    debug(player, "Maximum height exceeded, silent set-back.");
                }
            }
            if (setBack == null) {
                data.creativeFlyVL *= 0.97;
            }
        }
        return setBack;
    }

    private Location finalizeMove(final Location setBack, final PlayerLocation to, final MovingData data,
            final PlayerMoveData thisMove, final double maximumHeight, final Player player,
            final boolean debug) {

        if (setBack != null) {
            if (setBack.getY() > maximumHeight) {
                setBack.setY(getCorrectedHeight(maximumHeight, setBack.getWorld()));
                if (debug) {
                    debug(player, "Maximum height exceeded by set back, correct to: " + setBack.getY());
                }
            }
            data.sfJumpPhase = 0;
            return setBack;
        }

        data.setSetBack(to);
        if (!thisMove.from.onGroundOrResetCond && !thisMove.to.onGroundOrResetCond) {
            data.sfJumpPhase++;
        } else if (thisMove.touchedGround && !thisMove.to.onGroundOrResetCond) {
            data.sfJumpPhase = 1;
        } else {
            data.sfJumpPhase = 0;
        }
        return null;
    }


    /**
     * Horizontal distance checking.
     * @param player
     * @param from
     * @param to
     * @param hDistance
     * @param yDistance
     * @param flying
     * @param lastMove
     * @param time
     * @param model
     * @param data
     * @param cc
     * @return limitH, resultH (not normalized).
     */
    private double[] hDist(final Player player, final PlayerLocation from, final PlayerLocation to, final double hDistance, 
                           final double yDistance, final boolean sprinting, final boolean flying, final PlayerMoveData thisMove, 
                           final PlayerMoveData lastMove, final long time, final ModelFlying model, final MovingData data, final MovingConfig cc) {

        // Base speed and horizontal limit.
        double fSpeed = computeBaseSpeed(player, flying, sprinting, data, model);
        double limitH = model.getHorizontalModSpeed() / 100.0 * ModelFlying.HORIZONTAL_SPEED * fSpeed;

        // Environment influences.
        limitH = applyEnvironmentModifiers(from, to, limitH, model);

        // Friction and related special cases.
        limitH = applySpecialFriction(player, thisMove, lastMove, limitH, model);

        final double hDistanceActual = thisMove.hDistance;

        // Finally, determine how far the player went beyond the set limits.
        double resultH = Math.max(0.0, hDistanceActual - limitH);

        if (model.getApplyModifiers()) {
            resultH = handleBunnyHop(from, to, yDistance, flying, thisMove, lastMove, resultH, data);
        }
        return new double[] {limitH, resultH};
    }


   /**
     * Ascending (yDistance > 0.0) check.
     * @param from
     * @param to
     * @param yDistance
     * @param flying
     * @param lastMove
     * @param model
     * @param data
     * @param cc
     * @return limitV, resultV (not normalized).
     */
    private double[] vDistAscend(final PlayerLocation from, final PlayerLocation to, final double yDistance,
                                 final boolean flying, final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                 final ModelFlying model, final MovingData data, final MovingConfig cc, final boolean debug) {

        final Player player = from != null ? from.getPlayer() : null;
        final boolean ripglide = player != null && Bridge1_13.isRiptiding(player) && Bridge1_9.isGlidingWithElytra(player);
        final long now = System.currentTimeMillis();
        // Set the vertical limit.
        double limitV = model.getVerticalAscendModSpeed() / 100.0 * ModelFlying.VERTICAL_ASCEND_SPEED;
        double resultV = 0.0;

        limitV = applyFlyLevitationModifiers(from, flying, yDistance, thisMove, data, model, limitV);
        limitV = applyElytraAscend(from, yDistance, thisMove, lastMove, model, data, limitV);
        limitV = handleRipGlide(ripglide, yDistance, lastMove, limitV);
        applyRiptideBounce(from, yDistance, thisMove, data, debug, limitV);
        limitV = adjustForWaterGlide(from, data, limitV);
        limitV = applyFrictionWithGravity(model, yDistance, lastMove, flying, limitV);
        limitV = applyJumpLiftOffGain(model, yDistance, thisMove, lastMove, data, limitV);
        limitV = applyStepUp(cc, yDistance, lastMove, from, thisMove, to, limitV);

        resultV = Math.max(0.0, yDistance - limitV);
        return new double[] {limitV, resultV};
    }


    /**
     * Elytra gliding model
     * @param from
     * @param to
     * @param hDistance
     * @param yDistance
     * @param thisMove
     * @param lastMove
     * @param lostGround
     * @param data
     * @param player
     * @return resultH, resultV.
     *
     * @author xaw3ep
     */
    private boolean invalidElytraArgs(final Player player, final PlayerLocation from, final PlayerLocation to, final MovingConfig cc) {
        return player == null || from == null || to == null || !shouldProcessElytra(player, cc);
    }

    private double[] hackElytraH(final Player player, final PlayerLocation from, final PlayerLocation to, final double hDistance,
                                 final double yDistance, final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                 final boolean lostGround, final MovingData data, final MovingConfig cc, final boolean debug) {
        if (invalidElytraArgs(player, from, to, cc)) {
            return Arrays.copyOf(INVALID_ELYTRA_RESULT, 2);
        }

        /* Known false positives:
         * Still have setback with taking off ?
         * Fly out water with low envelope
         * Head obstructed ?
         */
        final long now = System.currentTimeMillis();
        double resultV = 0.0;
        double resultH = 0.0;
        double allowedElytraHDistance = 0.0;
        double allowedElytraYDistance = 0.0;
        double baseV = 0.0;

        if ((lastMove.flyCheck != thisMove.flyCheck || lastMove.modelFlying != thisMove.modelFlying) && !lastMove.elytrafly) {
            tags.add("e_pre");
        }
        else if (!from.isResetCond() && !isCollideWithHB(from)) {
            final double[] res = processAirGlide(player, from, to, hDistance, yDistance, thisMove, lastMove, data, debug);
            resultV = res[0];
            resultH = res[1];
            baseV = res[2];
            allowedElytraYDistance = res[3];
            allowedElytraHDistance = res[4];
        }
        else if (from.isInLiquid()) {
            final double[] res = processWaterGlide(player, from, to, hDistance, yDistance, thisMove, lastMove, data, cc);
            resultV = res[0];
            resultH = res[1];
            baseV = res[2];
            allowedElytraYDistance = res[3];
            allowedElytraHDistance = res[4];
        }

        final double[] adjust = new double[] {resultV, allowedElytraYDistance};
        applyVerticalVelocityAdjustment(data, baseV, yDistance, adjust);
        resultV = adjust[0];
        allowedElytraYDistance = adjust[1];

        thisMove.hAllowedDistance = allowedElytraHDistance;
        thisMove.yAllowedDistance = isNear(allowedElytraYDistance, yDistance, 0.001) ? yDistance : allowedElytraYDistance;
        return new double[] {resultV, resultH};
    }


    /**
     * Get vertical velocity stand behind this move 
     * @param lasthDistance
     * @param yDistance
     * @param radPitch pitch in Radians (elytra)
     * @param squaredCos squared of cos(radPitch) (elytra)
     * @param levitation (levitation level)
     * @param speed (elytra)
     * @param up (elytra)
     * @return baseV.
     */
    private double getBaseV(double lasthDistance, double yDistance, float radPitch, double squaredCos, double levitation, double speed, boolean up) { 

        double baseV = yDistance;

        if (levitation >= 0.0) {
            baseV /= Magic.FRICTION_MEDIUM_AIR;
            return (baseV - 0.01 * levitation) / 0.8 + 0.221;
        } 
        if (radPitch < 0.0 && !up) baseV -= lasthDistance * -Math.sin(radPitch) * 0.128;
        if (baseV < 0.0)  baseV /= (1.0 - (0.1 * squaredCos));
        baseV -= speed * (-1.0 + squaredCos * 0.75);
        return baseV;
    }
    
    private static boolean isNear(double a, double b, double c) {
        if (c < 0.0) return false;
        return Math.abs(a-b) <= c;
    }
    
    /**
     * 
     * @param yDistance
     * @param limitV
     * @param thisMove
     * @param lastMove
     * @param from
     * @param data
     * @return limitV
     */
    private double hackLytra(final double yDistance, final double limitV, final PlayerMoveData thisMove, 
                             final PlayerMoveData lastMove, final PlayerLocation from, 
                             final MovingData data) {

        // Elytra jump, let hackElytraH hande it
        if (yDistance > 0.0 && yDistance < 0.42 && thisMove.touchedGround) {
            tags.add("e_jump");
            return yDistance;
        } 
        // Ignore slowfalling here
        else if (Bridge1_13.getSlowfallingAmplifier(from.getPlayer()) >= 0.0) {
            tags.add("e_slowfall");
            return yDistance;
        }
        // Do ignore riptiding.
        else if (Bridge1_13.isRiptiding(from.getPlayer())) {
            tags.add("e_riptide");
            return yDistance;
        }

        if (yDistance > Magic.GLIDE_DESCEND_PHASE_MIN && yDistance < 34.0 * Magic.GRAVITY_MAX 
            && (
                // Normal envelope.
                lastMove.hDistance < 3.3 && yDistance - lastMove.yDistance < lastMove.hDistance / 11.0
                // Inversion (neg -> pos).
                || lastMove.yDistance < -Magic.GRAVITY_SPAN 
                && yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_ODD && yDistance > Magic.GRAVITY_SPAN
            )
            && thisMove.hDistance < lastMove.hDistance
            && (lastMove.yDistance > 0.0 || lastMove.hDistance > 0.55) // Demand some speed on the transition.
            // Demand total speed to decrease somehow, unless for the very transition.
            //&& (thisMove.distanceSquared / lastMove.distanceSquared < 0.99
            //        || lastMove.yDistance < 0.0) // Might confine the latter something to be tested.
            ) {

            // (Increasing y-distance.)
            if (lastMove.hDistance > 0.51) {
                tags.add("e_asc1");
                return yDistance;
            }
            // (Decreasing y-distance.)
            else if (thisMove.hDistance > Magic.GRAVITY_MIN && yDistance < lastMove.yDistance) {

                final PlayerMoveData pastMove1 = data.playerMoves.getSecondPastMove();
                if (pastMove1.toIsValid && pastMove1.to.extraPropertiesValid) {
                    // Demand this being the first one, or decreasing by a decent amount with past two moves.
                    if (
                        // First move rather decreasing.
                        pastMove1.yDistance < lastMove.yDistance 
                        // Decreasing by a reasonable (?) amount.
                        || yDistance - pastMove1.yDistance < -0.001) {
                        tags.add("e_asc2");
                        return yDistance;
                    }
                }
            }
        }

        // Elytra boost with fireworks rockets.
        if (yDistance > limitV && data.fireworksBoostDuration > 0 && lastMove.toIsValid
            && (
                yDistance >= lastMove.yDistance 
                || yDistance - lastMove.yDistance < Magic.GRAVITY_MAX
            )
            && (
                yDistance - lastMove.yDistance < 0.79
                || lastMove.yDistance < 0.0 && yDistance < 1.54
            )
            && yDistance < 1.67) {
            /* Additional checks for fireworks boost could be implemented here. */
            tags.add("fw_boost_asc");
            return yDistance;
        }

        return limitV;
    }

    /**
     * Determine if the elytra movement should be processed.
     *
     * @param player the player
     * @param cc the moving configuration
     * @return true if processing should continue
     */
    private boolean shouldProcessElytra(final Player player, final MovingConfig cc) {
        return cc.elytraStrict && Bridge1_9.isGlidingWithElytra(player) && !player.isFlying();
    }

    /**
     * Adjust horizontal and vertical movement based on the player's look
     * direction and pitch.
     */
    private void updateDirection(final Player player, final PlayerLocation to,
            final double yDistance, final double hDistance, final Vector lookvec,
            final float radPitch, final double xzlength, final double lastHdist,
            final double squaredCos, final boolean debug, final AirGlideState state) {

        if (state.allowedY < 0.0D && xzlength > 0.0) {
            final double d = state.allowedY * -0.1 * squaredCos;
            state.x += lookvec.getX() * d / xzlength;
            state.z += lookvec.getZ() * d / xzlength;
            state.allowedY += d;
        }

        if (radPitch < 0.0F) {
            if (to.getPitch() == -90f && isNear(yDistance, state.allowedY * Magic.FRICTION_MEDIUM_ELYTRA_AIR, 0.01)) {
                state.allowedH += 0.01;
                if (debug) {
                    debug(player, "Add the distance to allowed on look up (hDist/Allowed): "
                            + hDistance + "/" + state.allowedH);
                }
            } else if (xzlength > 0.0) {
                final double d = lastHdist * -Math.sin(radPitch) * 0.04;
                state.x -= lookvec.getX() * d / xzlength;
                state.z -= lookvec.getZ() * d / xzlength;
                state.allowedY += d * 3.2;
            }
        }

        if (xzlength > 0.0) {
            state.x += (lookvec.getX() / xzlength * lastHdist - state.x) * 0.1D;
            state.z += (lookvec.getZ() / xzlength * lastHdist - state.z) * 0.1D;
        }

        state.allowedY *= Magic.FRICTION_MEDIUM_ELYTRA_AIR;
    }

    /**
     * Apply modifications related to fireworks boost.
     *
     * @return true if no further processing should be done
     */
    private boolean handleFireworkBoost(final Player player, final double hDistance,
            final double yDistance, final Vector lookvec, final double xzlength,
            final PlayerMoveData thisMove, final PlayerMoveData lastMove,
            final MovingData data, final boolean debug, final AirGlideState state) {

        thisMove.yAllowedDistance = state.allowedY = yDistance;
        if (Math.round(data.fireworksBoostTickNeedCheck / 4) > data.fireworksBoostDuration
                && hDistance < Math.sqrt(state.x * state.x + state.z * state.z)) {
            thisMove.hAllowedDistance = Math.sqrt(state.x * state.x + state.z * state.z);
            if (debug) {
                debug(player, "Set hAllowedDistance for this firework boost phase (hDist/Allowed): "
                        + thisMove.hDistance + "/" + thisMove.hAllowedDistance);
            }
            state.resultV = 0.0;
            state.resultH = 0.0;
            return true;
        }

        state.x *= 0.99;
        state.z *= 0.99;
        state.x += lookvec.getX() * 0.1D + (lookvec.getX() * 1.5D - state.x) * 0.5D;
        state.z += lookvec.getZ() * 0.1D + (lookvec.getZ() * 1.5D - state.z) * 0.5D;
        tags.add("fw_speed");
        if (hDistance < lastMove.hAllowedDistance * 0.994) {
            thisMove.hAllowedDistance = lastMove.hAllowedDistance * 0.994;
            if (debug) {
                debug(player,
                        "Firework boost phase has ended sooner than expected, but the player is still legitimately boosting (hDist/Allowed): "
                                + thisMove.hDistance + "/" + thisMove.hAllowedDistance);
            }
            state.resultV = 0.0;
            state.resultH = 0.0;
            return true;
        }

        state.allowedH += 0.2;
        return false;
    }

    /**
     * Handle gliding in the air.
     *
     * @return {resultV, resultH, baseV, allowedY, allowedH}
     */
    private double[] processAirGlide(final Player player, final PlayerLocation from, final PlayerLocation to,
            final double hDistance, final double yDistance, final PlayerMoveData thisMove,
            final PlayerMoveData lastMove, final MovingData data, final boolean debug) {

        thisMove.elytrafly = true;

        AirGlideState state = new AirGlideState();
        state.allowedY = lastMove.elytrafly ? lastMove.yAllowedDistance
                : lastMove.toIsValid ? lastMove.yDistance : 0.0;
        if (Math.abs(state.allowedY) < 0.003D) {
            state.allowedY = 0.0D;
        }

        final double speed = Bridge1_13.getSlowfallingAmplifier(player) >= 0.0 ? 0.01 : 0.08;
        final double lastHdist = lastMove.toIsValid ? lastMove.hDistance : 0.0;
        final Vector lookvec = to.getLocation().getDirection();
        final float radPitch = (float) Math.toRadians(to.getPitch());
        final double xzlength = Math.sqrt(lookvec.getX() * lookvec.getX() + lookvec.getZ() * lookvec.getZ());
        double squaredCos = Math.cos(radPitch);
        squaredCos *= squaredCos;

        double baseV = getBaseV(hDistance, yDistance, radPitch, squaredCos, -1.0, speed, to.getPitch() == -90f);

        state.allowedY += speed * (-1.0D + squaredCos * 0.75D);
        state.x = lastMove.to.getX() - lastMove.from.getX();
        state.z = lastMove.to.getZ() - lastMove.from.getZ();
        if (Math.abs(state.x) < 0.003D) {
            state.x = 0.0D;
        }
        if (Math.abs(state.z) < 0.003D) {
            state.z = 0.0D;
        }

        updateDirection(player, to, yDistance, hDistance, lookvec, radPitch, xzlength, lastHdist,
                squaredCos, debug, state);

        boolean boosted = false;
        if (data.fireworksBoostDuration > 0) {
            boosted = handleFireworkBoost(player, hDistance, yDistance, lookvec, xzlength, thisMove,
                    lastMove, data, debug, state);
        }

        if (!boosted) {
            state.allowedH += Math.sqrt(state.x * state.x + state.z * state.z) + 0.1;
            if (debug) {
                debug(player, "Cumulative elytra hDistance (hDist/Allowed): " + hDistance + "/" + state.allowedH
                        + " lasthDist:" + lastHdist);
                debug(player, "radiansPitch: " + radPitch + " yDist:" + yDistance + " lastyDist:" + lastMove.yDistance
                        + " allowy:" + state.allowedY);
            }

            final double yDistDiffEx = yDistance - state.allowedY;

            if (data.fireworksBoostDuration <= 0) {
                if (yDistance > 0.0 && yDistance < 0.42 && thisMove.touchedGround) {
                    state.allowedY = yDistance;
                    state.allowedH = Math.max(0.35, state.allowedH * 1.35);
                    if (debug) {
                        debug(player, "Elytra jump (hDist/Allowed): " + thisMove.hDistance + "/" + state.allowedH);
                    }
                } else if (from.isHeadObstructed() && lastMove.yDistance > 0.0 && yDistDiffEx < 0.0
                        && (state.allowedY > 0.0 || yDistance == 0.0)) {
                    state.allowedY = yDistance;
                } else if (yDistance < 0.0) {
                    if (lastMove.yDistance > 0.0 && yDistance < 0.0
                            && (lastMove.yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_MIN
                                    && yDistance > -Magic.GRAVITY_MIN
                                    || lastMove.yDistance < Magic.GRAVITY_MIN
                                            && yDistance > -Magic.GRAVITY_MIN - Magic.GRAVITY_MAX)) {
                        state.allowedY = yDistance;
                    }
                }

                if (yDistance > 0.0) {
                    if (state.allowedY < yDistance && !isNear(state.allowedY, yDistance, 0.001)) {
                        tags.add("e_vasc");
                        state.resultV = yDistance;
                    }
                } else if (yDistance < 0.0) {
                    if (state.allowedY > yDistance && !isNear(state.allowedY, yDistance, Magic.GRAVITY_MAX)) {
                        tags.add("e_vdesc");
                        state.resultV = Math.abs(yDistance);
                    }
                }

                if (yDistance <= 0.0 && (to.isOnGround() || to.isResetCond() || thisMove.touchedGround)
                        || yDistDiffEx > -Magic.GRAVITY_MAX && yDistDiffEx < 0.0
                        || speed < 0.05 && !TrigUtil.isSamePos(lastMove.from, lastMove.to)
                                && (hDistance == 0.0 && yDistance == 0.0 || yDistance < -Magic.GRAVITY_SPAN)) {
                    state.allowedY = yDistance;
                } else if (Math.abs(yDistDiffEx) > (speed < 0.05 ? 0.00001 : 0.03)) {
                    tags.add("e_vdiff");
                    state.resultV = Math.max(Math.abs(yDistance - state.allowedY), state.resultV);
                }
            }
        }

        if (state.allowedH < hDistance) {
            tags.add("e_hspeed");
            state.resultH = hDistance - state.allowedH;
        }

        return new double[] {state.resultV, state.resultH, baseV, state.allowedY, state.allowedH};
    }

    /**
     * Handle gliding in water.
     *
     * @return {resultV, resultH, baseV, allowedY, allowedH}
     */
    private double[] processWaterGlide(final Player player, final PlayerLocation from, final PlayerLocation to,
            final double hDistance, final double yDistance, final PlayerMoveData thisMove, final PlayerMoveData lastMove,
            final MovingData data, final MovingConfig cc) {

        double resultV = 0.0;
        double resultH = 0.0;
        double allowedH = thisMove.walkSpeed * cc.survivalFlyWalkingSpeed / 100D;
        double allowedY = 0.0;
        double baseV = 0.0;

        if (Bridge1_13.isRiptiding(player)) {
            return new double[] {0.0, 0.0, baseV, allowedY, allowedH};
        }

        final int level = BridgeEnchant.getDepthStriderLevel(player);

        if (!Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))) {
            allowedH *= Magic.modDolphinsGrace;
            if (level > 0) allowedH *= 1.0 + 0.1 * level;
        }

        if (level > 0) {
            allowedH *= Magic.modDepthStrider[level];
            final double attrMod = attributeAccess.getHandle().getSpeedAttributeMultiplier(player);
            if (attrMod == Double.MAX_VALUE) {
                final double speedAmplifier = mcAccess.getHandle().getFasterMovementAmplifier(player);
                if (!Double.isInfinite(speedAmplifier)) {
                    allowedH *= 1.0D + 0.2D * (speedAmplifier + 1);
                }
            }
            else allowedH *= attrMod;
        }

        if (lastMove.toIsValid && data.liqtick < 3 && hDistance < lastMove.hAllowedDistance + 0.07) {
            allowedH = lastMove.hAllowedDistance + 0.07;
        }

        if (data.fireworksBoostDuration > 0) allowedH = Math.max(allowedH, 1.8);

        if (hDistance < lastMove.hAllowedDistance * (data.liqtick < 5 ? 1.0 : Magic.FRICTION_MEDIUM_WATER)) {
            allowedH = lastMove.hAllowedDistance * (data.liqtick < 5 ? 1.0 : Magic.FRICTION_MEDIUM_WATER);
        }

        if (thisMove.hDistance > allowedH) {
            tags.add("e_hspeed(liq)");
            resultH = hDistance - allowedH;
        }

        return new double[] {resultV, resultH, baseV, allowedY, allowedH};
    }

    /**
     * Apply adjustments for vertical velocity usage.
     */
    private void applyVerticalVelocityAdjustment(final MovingData data, final double baseV,
            final double yDistance, final double[] adjust) {
        if (adjust[0] > 0.0 && data.getOrUseVerticalVelocity(baseV) != null) {
            adjust[1] = yDistance;
            adjust[0] = 0.0;
        }
    }

    /**
     * Descending phase vDist check
     * @param from
     * @param to
     * @param yDistance
     * @param flying
     * @param thisMove
     * @param lastMove
     * @param model
     * @param data
     * @param cc
     * @return limitV, resultV
     */
    private double[] vDistDescend(final PlayerLocation from, final PlayerLocation to, final double yDistance, final boolean flying, 
                                  final PlayerMoveData thisMove, final PlayerMoveData lastMove, final ModelFlying model, 
                                  final MovingData data, final MovingConfig cc) {
        double limitV = 0.0;
        double resultV = 0.0;
        
        if (model.getScaleSlowfallingEffect() && lastMove.modelFlying == thisMove.modelFlying 
            && data.liqtick <= 0 && !from.isOnClimbable() && !to.isOnClimbable()) {

            if (!thisMove.touchedGround && !to.isResetCond()) {

                final PlayerMoveData pastmove2 = data.playerMoves.getSecondPastMove();
                final double allowY = lastMove.toIsValid ? lastMove.yDistance : 0.0;
                if (isCollideWithHB(from)) limitV = -0.05; 
                else if (from.isInBerryBush()) limitV = -0.085;
                else limitV = allowY * Magic.FRICTION_MEDIUM_AIR - 0.0097;// -0.0098
                if (!pastmove2.toIsValid && allowY < -0.035) limitV = -0.035;
                if (limitV != 0.0 && yDistance > limitV) resultV = Math.abs(limitV);
            }
        }
        // Note that 'extreme moves' are covered by the extreme move check.
        return new double[] {limitV, resultV};
    }


    /**
     * Keep the altitude
     * @param from
     * @param to
     * @param yDistance
     * @param flying
     * @param thisMove
     * @param lastMove
     * @param model
     * @param data
     * @param cc
     * @return limitV, resultV
     */
    private double[] vDistZero(final PlayerLocation from, final PlayerLocation to, final double yDistance, final boolean flying, 
                               final PlayerMoveData thisMove, final PlayerMoveData lastMove, final ModelFlying model, 
                               final MovingData data, final MovingConfig cc) {

        double limitV = 0.0;
        double resultV = 0.0;

        if (model.getScaleSlowfallingEffect() && lastMove.modelFlying == thisMove.modelFlying && !to.isInWeb()) {
            if (thisMove.touchedGround || thisMove.from.onClimbable || data.liqtick > 0) {
                // Allow normal jumping.
                final double maxGain = LiftOffEnvelope.NORMAL.getMinJumpGain(data.jumpAmplifier) + 0.01;
                if (maxGain > limitV) limitV = maxGain;
            }
            if (limitV <= 0.0 && lastMove.yDistance == 0.0) resultV = 0.1;
        }

        return new double[] {limitV, resultV};
    }


   /**
    * 
    * @param maximumHeight
    * @param world
    * @return 
    */
    private double getCorrectedHeight(final double maximumHeight, final World world) {
        return Math.max(maximumHeight - 10.0, world.getMaxHeight());
    }


   /**
    * Adds velocity to a player upon switching movement model, in order to
    * workaround false positives
    * @param player
    * @param thisMove
    * @param lastMove
    * @param model
    * @param data
    * @param cc
    * @author xaw3ep
    */
    private void workaroundSwitchingModel(final Player player, final PlayerMoveData thisMove, final PlayerMoveData lastMove, 
                                          final ModelFlying model, final MovingData data, final MovingConfig cc, final boolean debug) {

        if (lastMove.toIsValid && lastMove.modelFlying != thisMove.modelFlying) {

            // Other modelflying -> levitation
            if (model.getScaleLevitationEffect()) {
                final double amount = lastMove.hAllowedDistance > 0.0 ? lastMove.hAllowedDistance : lastMove.hDistance;
                if (thisMove.touchedGround) data.addHorizontalVelocity(new AccountEntry(amount, 2, MovingData.getHorVelValCount(amount)));
                if (debug) debug(player, lastMove.modelFlying.getId().toString() + " -> potion.levitation: add velocity");
                return;
            }

            // Gliding -> Other modelflying
            if (lastMove.modelFlying != null && lastMove.modelFlying.getVerticalAscendGliding()) {
                final double amount = guessVelocityAmount(player, thisMove, lastMove, data);
                if (thisMove.touchedGround || model.getId().equals("gamemode.creative")) {
                    data.addHorizontalVelocity(new AccountEntry(amount, 3, MovingData.getHorVelValCount(amount)));
                    if (debug) debug(player, "Jetpack.elytra -> " + (thisMove.touchedGround ? "touchedGround" : "gamemode.creative") + ": add velocity");
                }

                if (model.getId().equals("gamemode.creative")) {
                    data.addVerticalVelocity(new SimpleEntry(0.0, 2));
                    if (debug) debug(player, "Jetpack.elytra -> gamemode.creative: add velocity");
                }
                return;
            }
            // A ripglide phase has ended, smoothen the transition.
            if (lastMove.modelFlying != null && lastMove.modelFlying.getScaleRiptidingEffect() && thisMove.modelFlying.getVerticalAscendGliding()) {

                final double amount = guessVelocityAmount(player, thisMove, lastMove, data);
                if (!thisMove.from.onGround && !thisMove.to.onGround) {
                    data.addVerticalVelocity(new SimpleEntry(thisMove.yDistance, cc.velocityActivationCounter));
                    data.addVerticalVelocity(new SimpleEntry(0.0, cc.velocityActivationCounter));
                    data.addHorizontalVelocity(new AccountEntry(amount, 4, MovingData.getHorVelValCount(amount)));
                    if (debug) debug(player, "Effect.riptiding -> Jetpack.elytra: add velocity");
                }
                return;
            }
        }

        final PlayerMoveData secondPastMove = data.playerMoves.getSecondPastMove();
        // Quick change between models, reset friction, invalid
        if (secondPastMove.modelFlying != null && lastMove.modelFlying != null
            && secondPastMove.modelFlying == model && model != lastMove.modelFlying) {
            if (debug) debug(player, "Invalidate this move on too fast model switch: " + (secondPastMove.modelFlying.getId().toString() + " -> " + lastMove.modelFlying.getId().toString() + " -> " + model.getId().toString()));
            thisMove.invalidate();
        }
    }


   /**
    * @param player
    * @param thisMove
    * @param lastMove
    * @param data
    * @return
    */
    private static double guessVelocityAmount(final Player player, final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data) {

        // Default margin: Allow slightly less than the previous speed.
        final double defaultAmount = lastMove.hDistance * (1.0 + Magic.FRICTION_MEDIUM_AIR) / 2.0;

        // Test for exceptions.
        if (thisMove.hDistance > defaultAmount && Bridge1_9.isWearingElytra(player) 
            && lastMove.modelFlying != null 
            && lastMove.modelFlying.getId().equals(MovingConfig.ID_JETPACK_ELYTRA)) {
            // Allowing the same speed won't always work on elytra (still increasing, differing modeling on client side with motXYZ).
            // (Doesn't seem to be overly effective.)

            final PlayerMoveData secondPastMove = data.playerMoves.getSecondPastMove();
            if (secondPastMove.modelFlying != null
                && Magic.glideEnvelopeWithHorizontalGain(thisMove, lastMove, secondPastMove)) {
                return lastMove.hDistance + 0.1468;
            }
        }
        return defaultAmount;
    }
    
    /**
     * Estimate allowed velocity adjustments for Elytra gliding.
     *
     * @param context movement context containing player and move data
     * @return horizontal and vertical velocity adjustments
     */
    public static VelocityAdjustment guessElytraVelocityAmount(final MoveCheckContext context) {
        final Player player = context.player();
        final PlayerMoveData thisMove = context.thisMove();
        final PlayerMoveData lastMove = context.lastMove();
        final MovingData data = context.data();
        final Location useLoc = new Location(null, 0, 0, 0);
        useLoc.setYaw(thisMove.to.getYaw());
        useLoc.setPitch(thisMove.to.getPitch());
        final double speed = Bridge1_13.getSlowfallingAmplifier(player) >= 0.0 ? 0.01 : 0.08;
        double allowedElytraYDistance = 0.0;
        double allowedElytraHDistance = 0.0;
        final double lastHdist = lastMove.toIsValid ? lastMove.hDistance : 0.0;
        final Vector lookvec = useLoc.getDirection();
        final float radPitch = (float) Math.toRadians(thisMove.to.getPitch());
        allowedElytraYDistance = lastMove.elytrafly ? lastMove.yAllowedDistance : lastMove.toIsValid ? lastMove.yDistance : 0.0;
        if (Math.abs(allowedElytraYDistance) < 0.003D) allowedElytraYDistance = 0.0D;
        final double xzlength = Math.sqrt(lookvec.getX() * lookvec.getX() + lookvec.getZ() * lookvec.getZ());
        double f4 = Math.cos(radPitch);
        f4 = f4 * f4;

        allowedElytraYDistance += speed * (-1.0D + f4 * 0.75D);
        double x = lastMove.to.getX() - lastMove.from.getX();
        double z = lastMove.to.getZ() - lastMove.from.getZ();
        
        if (allowedElytraYDistance < 0.0D && xzlength > 0.0) {
            final double d = allowedElytraYDistance * -0.1 * f4;
            x += lookvec.getX() * d / xzlength;
            z += lookvec.getZ() * d / xzlength;
            allowedElytraYDistance += d;
        }
        
        // Look up
        if (radPitch < 0.0F) {
            // For compatibility
            if (thisMove.to.getPitch() == -90f
                && isNear(thisMove.yDistance, allowedElytraYDistance * Magic.FRICTION_MEDIUM_ELYTRA_AIR, 0.01)) {
                allowedElytraHDistance += 0.01;
            }
            else if (xzlength > 0.0) {
                final double d = lastHdist * -Math.sin(radPitch) * 0.04;
                x -= lookvec.getX() * d / xzlength;
                z -= lookvec.getZ() * d / xzlength;
                allowedElytraYDistance += d * 3.2;
            } 
        }

        if (xzlength > 0.0) {
            x += (lookvec.getX() / xzlength * lastHdist - x) * 0.1D;
            z += (lookvec.getZ() / xzlength * lastHdist - z) * 0.1D;
        }
        
        if (data.fireworksBoostDuration > 0) {
            allowedElytraYDistance = Math.abs(thisMove.yDistance) < 2.0 ?
                    thisMove.yDistance : lastMove.toIsValid ? lastMove.yDistance : 0;
            if (Math.round(data.fireworksBoostTickNeedCheck / 4) > data.fireworksBoostDuration 
                && thisMove.hDistance < Math.sqrt(x*x + z*z)) {
                return new VelocityAdjustment(Math.sqrt(x*x + z*z), allowedElytraYDistance);
            }

            x *= 0.99;
            z *= 0.99;
            x += lookvec.getX() * 0.1D + (lookvec.getX() * 1.5D - x) * 0.5D;
            z += lookvec.getZ() * 0.1D + (lookvec.getZ() * 1.5D - z) * 0.5D;

            if (thisMove.hDistance < lastMove.hAllowedDistance * 0.994) {
                return new VelocityAdjustment(lastMove.hAllowedDistance * 0.994, allowedElytraYDistance);
            } 
            else allowedElytraHDistance += 0.2;
        }

        // Adjust false
        allowedElytraHDistance += Math.sqrt(x*x + z*z) + 0.1;
        return new VelocityAdjustment(allowedElytraHDistance, allowedElytraYDistance);
    }


  /**
    * @param from
    * @return
    */
    private boolean isCollideWithHB(PlayerLocation from) {
        return (from.getBlockFlags() & BlockFlags.F_STICKY) != 0;
    }

    // --- Helper methods split from hDist -------------------------------------------------

    /**
     * Compute the base speed multiplier considering current modifiers.
     *
     * @param player the player
     * @param flying whether the player is flying
     * @param sprinting whether the player is sprinting
     * @param data movement related data
     * @param model movement model
     * @return the base speed multiplier
     */
    private double computeBaseSpeed(Player player, boolean flying, boolean sprinting,
            MovingData data, ModelFlying model) {
        double fSpeed;
        if (model.getApplyModifiers()) {
            final double speedModifier = mcAccess.getHandle().getFasterMovementAmplifier(player);
            if (Double.isInfinite(speedModifier)) fSpeed = 1.0;
            else fSpeed = 1.0 + 0.2 * (speedModifier + 1.0);
            if (flying) {
                fSpeed *= data.flySpeed / Magic.DEFAULT_FLYSPEED;
                if (sprinting) {
                    fSpeed *= model.getHorizontalModSprint();
                    tags.add("sprint");
                }
                tags.add("flying");
            }
            else {
                final double attrMod = attributeAccess.getHandle().getSpeedAttributeMultiplier(player);
                if (attrMod != Double.MAX_VALUE) fSpeed *= attrMod;
                fSpeed *= data.walkSpeed / Magic.DEFAULT_WALKSPEED;
            }
        }
        else {
            fSpeed = 1.0;
        }
        return fSpeed;
    }

    /**
     * Apply environment-based speed modifiers such as water or stairs.
     *
     * @param from the starting location
     * @param to the destination location
     * @param baseLimit current horizontal speed limit
     * @param model the flying model in use
     * @return the adjusted horizontal speed limit
     */
    private double applyEnvironmentModifiers(PlayerLocation from, PlayerLocation to,
            double baseLimit, ModelFlying model) {
        double limitH = baseLimit;
        final Player player = from.getPlayer();
        if (from.isInWater() || to.isInWater()) {
            if (!Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))) {
                limitH *= Magic.modDolphinsGrace;
                tags.add("hdolphinsgrace");
            }
        }
        if (Bridge1_9.hasElytra() && from.isAboveStairs() && to.isAboveStairs()) {
            double fSpeed = baseLimit / (ModelFlying.HORIZONTAL_SPEED * model.getHorizontalModSpeed() / 100.0);
            limitH = Math.max(limitH, 0.7 * fSpeed);
        }
        return limitH;
    }

    /**
     * Apply friction related modifiers including riptide and levitation.
     *
     * @param player the player
     * @param thisMove current move data
     * @param lastMove last move data
     * @param limitH current horizontal limit
     * @param model model in use
     * @return the adjusted limit
     */
    private double applySpecialFriction(Player player, PlayerMoveData thisMove,
            PlayerMoveData lastMove, double limitH, ModelFlying model) {
        final boolean ripglide = Bridge1_13.isRiptiding(player) && Bridge1_9.isGlidingWithElytra(player);
        double hDistance = thisMove.hDistance;
        if (lastMove.toIsValid && ripglide && hDistance > limitH) {
            limitH += 9.3;
            tags.add("hripglide");
        }
        if (lastMove.toIsValid && model.getScaleLevitationEffect()
                && (lastMove.hDistance + 0.005) * Magic.FRICTION_MEDIUM_AIR < lastMove.hDistance) {
            limitH = Math.max((lastMove.hDistance + 0.005) * Magic.FRICTION_MEDIUM_AIR, limitH);
            tags.add("hfrict_lev");
        }
        if (lastMove.toIsValid && model.getScaleRiptidingEffect()
                && lastMove.hDistance * Magic.FRICTION_MEDIUM_AIR <= lastMove.hDistance
                && thisMove.hDistance > 3.0 && thisMove.hDistance < 3.9
                && Bridge1_13.isRiptiding(player) && hDistance > limitH) {
            limitH = Math.max((lastMove.hDistance + 2.9974) * Magic.FRICTION_MEDIUM_AIR, limitH);
            tags.add("hfrict_ript");
        }
        if (lastMove.toIsValid && !ripglide) {
            double frictionDist = lastMove.hDistance * Magic.FRICTION_MEDIUM_AIR;
            limitH = Math.max(frictionDist, limitH);
            tags.add("hfrict");
        }
        return limitH;
    }

    /**
     * Handle bunny-hop acceleration detection and tagging.
     *
     * @param from starting location
     * @param to destination location
     * @param yDistance vertical distance moved
     * @param flying whether flying is enabled
     * @param thisMove current move data
     * @param lastMove previous move data
     * @param resultH the current exceed amount
     * @param data movement data for the player
     * @return possibly adjusted exceed amount
     */
    private double handleBunnyHop(PlayerLocation from, PlayerLocation to, double yDistance,
            boolean flying, PlayerMoveData thisMove, PlayerMoveData lastMove, double resultH,
            MovingData data) {
        data.bunnyhopDelay--;
        if (!flying && resultH > 0 && resultH < 0.3) {
            if (yDistance >= 0.0 &&
                    (yDistance > 0.0
                            && yDistance > LiftOffEnvelope.NORMAL.getMinJumpGain(data.jumpAmplifier) - Magic.GRAVITY_SPAN
                            || thisMove.headObstructed || lastMove.toIsValid && lastMove.headObstructed && lastMove.yDistance <= 0.0)
                    && (data.sfJumpPhase <= 1 && (thisMove.touchedGroundWorkaround ||
                            lastMove.touchedGround && !lastMove.bunnyHop))
                    && (!from.isResetCond() && !to.isResetCond())) {
                tags.add("bunnyhop");
                data.bunnyhopDelay = 9;
                thisMove.bunnyHop = true;
                return 0.0;
            }
            else if (data.bunnyhopDelay <= 0) {
                tags.add("bunnyhop");
                return 0.0;
            }
        }
        return resultH;
    }

    /**
     * Apply fly speed and levitation related modifiers.
     */
    private double applyFlyLevitationModifiers(PlayerLocation from, boolean flying, double yDistance,
            PlayerMoveData thisMove, MovingData data, ModelFlying model, double limitV) {
        final Player player = from != null ? from.getPlayer() : null;
        if (model.getApplyModifiers() && flying && yDistance > 0.0) {
            limitV *= data.flySpeed / Magic.DEFAULT_FLYSPEED;
        } else if (model.getScaleLevitationEffect() && Bridge1_9.hasLevitation()) {
            final double levitation = player != null ? Bridge1_9.getLevitationAmplifier(player) : -1.0;
            if (levitation >= 0.0) {
                limitV += 0.046 * levitation;
                final double minJumpGain = LiftOffEnvelope.NORMAL.getMinJumpGain(data.jumpAmplifier) + 0.01;
                if (yDistance > 0.0 && yDistance < minJumpGain && thisMove.touchedGround) {
                    limitV = minJumpGain;
                    data.addVerticalVelocity(new SimpleEntry(yDistance, 2));
                }
                tags.add("levitation:" + levitation);
            }
        }
        return limitV;
    }

    /**
     * Apply elytra ascending adjustments.
     */
    private double applyElytraAscend(PlayerLocation from, double yDistance, PlayerMoveData thisMove,
            PlayerMoveData lastMove, ModelFlying model, MovingData data, double limitV) {
        if (model.getVerticalAscendGliding()) {
            double newLimit = hackLytra(yDistance, limitV, thisMove, lastMove, from, data);
            limitV = Math.max(limitV, newLimit);
        }
        return limitV;
    }

    /**
     * Apply additional speed allowed while riptiding and gliding.
     */
    private double handleRipGlide(boolean ripglide, double yDistance, PlayerMoveData lastMove, double limitV) {
        if (lastMove.toIsValid && ripglide && yDistance > limitV) {
            limitV += 5.9;
            tags.add("vripglide");
        }
        return limitV;
    }

    /**
     * Handle riptide bounce while on bouncy blocks.
     */
    private void applyRiptideBounce(PlayerLocation from, double yDistance, PlayerMoveData thisMove,
            MovingData data, boolean debug, double limitV) {
        final Player player = from != null ? from.getPlayer() : null;
        if (player != null && Bridge1_13.isRiptiding(player)
                && (from.getBlockFlags() & BlockFlags.F_BOUNCE25) != 0
                && yDistance > limitV && data.sfJumpPhase <= 2
                && yDistance > 0.0 && yDistance < 7.5
                && thisMove.from.onGround && !thisMove.to.onGround) {
            data.addVerticalVelocity(new SimpleEntry(yDistance, 4));
            if (debug) {
                debug(player, "Riptide bounce: add velocity");
            }
        }
    }

    /**
     * Adjust vertical limit for gliding in water.
     */
    private double adjustForWaterGlide(PlayerLocation from, MovingData data, double limitV) {
        final Player player = from != null ? from.getPlayer() : null;
        if (player != null && Bridge1_9.isGlidingWithElytra(player) && data.liqtick > 1) {
            limitV = Math.max(limitV, 0.35);
        }
        return limitV;
    }

    /**
     * Apply friction with gravity influence.
     */
    private double applyFrictionWithGravity(ModelFlying model, double yDistance, PlayerMoveData lastMove,
            boolean flying, double limitV) {
        if (model.getGravity() && yDistance > limitV && lastMove.toIsValid) {
            double frictionDist = lastMove.yDistance * Magic.FRICTION_MEDIUM_AIR;
            if (!flying) {
                frictionDist -= 0.019;
            }
            if (frictionDist > limitV) {
                limitV = frictionDist;
                tags.add("vfrict_g");
            }
        }
        return limitV;
    }

    /**
     * Apply jump lift off gain when leaving ground.
     */
    private double applyJumpLiftOffGain(ModelFlying model, double yDistance, PlayerMoveData thisMove,
            PlayerMoveData lastMove, MovingData data, double limitV) {
        if (model.getGround() && yDistance > limitV && !thisMove.to.onGroundOrResetCond
                && !thisMove.from.onGroundOrResetCond
                && (lastMove.toIsValid && lastMove.touchedGround
                        && (lastMove.yDistance <= 0.0 || lastMove.to.extraPropertiesValid && lastMove.to.onGround)
                        || thisMove.touchedGroundWorkaround)) {
            final double maxGain = LiftOffEnvelope.NORMAL.getMaxJumpGain(data.jumpAmplifier);
            if (maxGain > limitV) {
                limitV = maxGain;
                tags.add("jump_gain");
            }
        }
        return limitV;
    }

    /**
     * Apply step up adjustments when climbing up small blocks.
     */
    private double applyStepUp(MovingConfig cc, double yDistance, PlayerMoveData lastMove, PlayerLocation from,
            PlayerMoveData thisMove, PlayerLocation to, double limitV) {
        if (yDistance > limitV && yDistance <= cc.sfStepHeight
                && (lastMove.toIsValid && lastMove.yDistance < 0.0 || from.isOnGroundOrResetCond()
                        || thisMove.touchedGroundWorkaround)
                && to.isOnGround()) {
            limitV = cc.sfStepHeight;
            tags.add("step_up");
        }
        return limitV;
    }


  /**
    * Output debug
    * @param player
    * @param hDistance
    * @param limitH
    * @param yDistance
    * @param limitV
    * @param model
    * @param tags
    * @param data
    * @return
    */
    private void outpuDebugMove(final Player player, final double hDistance, final double limitH, 
                                final double yDistance, final double limitV, final ModelFlying model, final List<String> tags, 
                                final MovingData data) {

        final PlayerMoveData lastMove = data.playerMoves.getFirstPastMove();
        StringBuilder builder = new StringBuilder(350);
        final String dHDist = lastMove.toIsValid ? " (" + StringUtil.formatDiff(hDistance, lastMove.hDistance) + ")" : "";
        final String dYDist = lastMove.toIsValid ? " (" + StringUtil.formatDiff(yDistance, lastMove.yDistance)+ ")" : "";
        builder.append("hDist: " + hDistance + dHDist + " / " + limitH + " , vDist: " + yDistance + dYDist + " / " + limitV);
        final PlayerMoveData thisMove = data.playerMoves.getCurrentMove();
        if (lastMove.toIsValid) {
            builder.append(" , fdsq: " + StringUtil.fdec3.format(thisMove.distanceSquared / lastMove.distanceSquared));
        }
        if (thisMove.verVelUsed != null) {
            builder.append(" , vVelUsed: " + thisMove.verVelUsed);
        }
        if (data.fireworksBoostDuration > 0 && MovingConfig.ID_JETPACK_ELYTRA.equals(model.getId())) {
            builder.append(" , boost: " + data.fireworksBoostDuration);
        }
        if (thisMove.elytrafly) {
            builder.append(", elytraFly");
        }
        builder.append(" , model: " + model.getId());
        if (!tags.isEmpty()) {
            builder.append(" , tags: ");
            builder.append(StringUtil.join(tags, "+"));
        }
        builder.append(" , jumpphase: " + data.sfJumpPhase);
        thisMove.addExtraProperties(builder, " , ");
        debug(player, builder.toString());
    }
}
