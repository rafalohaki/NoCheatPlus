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

        // Allow elytra fly (not packet mode)
        // Since in Winds Anarchy, we have another plugin to handle elyta fly better.
        if (pData.hasPermission(Permissions.MOVING_ELYTRAFLY, player)
                && player.getInventory().getChestplate() != null && player.getInventory().getChestplate().getType() == Material.ELYTRA && player.isGliding()) {
            // Adjust the set back and other last distances.
            data.setSetBack(to);
            // Adjust jump phase.
            if (!thisMove.from.onGroundOrResetCond && !thisMove.to.onGroundOrResetCond) {
                data.sfJumpPhase ++;
            }
            else if (thisMove.touchedGround && !thisMove.to.onGroundOrResetCond) {
                data.sfJumpPhase = 1;
            }
            else {
                data.sfJumpPhase = 0;
            }
            return null;
        }

        // Lost ground, if set so.
        if (model.getGround()) {
            MovingUtil.prepareFullCheck(from, to, thisMove, Math.max(cc.yOnGround, cc.noFallyOnGround));
            if (!thisMove.from.onGroundOrResetCond) {
                if (from.isSamePos(to)) {
                    if (lastMove.toIsValid && lastMove.hDistance > 0.0 && lastMove.yDistance < -0.3 // Copy and paste from sf.
                        && LostGround.lostGroundStill(player, from, to, hDistance, yDistance, sprinting, lastMove, data, cc, tags)) {
                        lostGround = true;
                    }
                }
                else if (LostGround.lostGround(player, from, to, hDistance, yDistance, sprinting, lastMove, 
                                               data, cc, useBlockChangeTracker ? blockChangeTracker : null, tags)) {
                    lostGround = true;
                }
            }
        }

        // Do not check for nofall if the player has slowfalling active or is gliding
        if (Bridge1_13.hasSlowfalling() && model.getScaleSlowfallingEffect() 
            || Bridge1_9.isGlidingWithElytra(player) && thisMove.yDistance > -0.5) {
            data.clearNoFallData();
        } 
        
        // HACK: when switching model, we need to add some velocity to harmonize the transition and not trggering fps.
        workaroundSwitchingModel(player, thisMove, lastMove, model, data, cc, debug);






        //////////////////////////
        // Horizontal move.
        //////////////////////////

        double[] resH = hDist(player, from, to, hDistance, yDistance, sprinting, flying, thisMove, lastMove, time, model, data, cc);
        double limitH = resH[0];
        double resultH = resH[1];
        double[] rese = hackElytraH(player, from, to, hDistance, yDistance, thisMove, lastMove, lostGround, data, cc, debug); // Related to the elytra
        resultH = Math.max(resultH, rese[1]);

        // Check velocity.
        if (resultH > 0) {
            double hFreedom = data.getHorizontalFreedom();
            if (hFreedom < resultH) {
                // Use queued velocity if possible.
                hFreedom += data.useHorizontalVelocity(resultH - hFreedom);
            }
            if (hFreedom > 0.0) {
                resultH = Math.max(0.0, resultH - hFreedom);
                if (resultH <= 0.0) {
                    limitH = hDistance;
                }
                tags.add("hvel");
            }
        }
        else {
            data.clearActiveHorVel();
        }

        resultH *= 100.0; // Normalize to % of a block.
        if (resultH > 0.0) {
            tags.add("hdist");
        }






        //////////////////////////
        // Vertical move.
        //////////////////////////

        double limitV = 0.0; // Limit. For debug only, violation handle on resultV
        double resultV = rese[0]; // Violation (normalized to 100 * 1 block, applies if > 0.0).

        // Distinguish checking method by y-direction of the move:
        // Ascend.
        if (yDistance > 0.0) {
            double[] res = vDistAscend(from, to, yDistance, flying, thisMove, lastMove, model, data, cc, debug);
            resultV = Math.max(resultV, res[1]);
            limitV = res[0];
        }
        // Descend.
        else if (yDistance < 0.0) {
            double[] res = vDistDescend(from, to, yDistance, flying, thisMove, lastMove, model, data, cc);
            resultV = Math.max(resultV, res[1]);
            limitV = res[0];
        }
        // Keep altitude.
        else {
            double[] res = vDistZero(from, to, yDistance, flying, thisMove, lastMove, model, data, cc);
            resultV = Math.max(resultV, res[1]);
            limitV = res[0];
        }

        // Velocity.
        if (resultV > 0.0 && (thisMove.verVelUsed != null || data.getOrUseVerticalVelocity(yDistance) != null)) {
            resultV = 0.0;
            tags.add("vvel");
        }
        
        // The antilevitation subcheck
        if (lastMove.toIsValid && !player.isFlying() && model.getScaleLevitationEffect()
            && thisMove.modelFlying == lastMove.modelFlying) { // InLiquid check is alread included in MovingConfig.getModelFlying()

            final double level = Bridge1_9.getLevitationAmplifier(player) + 1;
            final double allowY = (lastMove.yDistance + (0.05D * level - lastMove.yDistance) * 0.2D) * Magic.FRICTION_MEDIUM_AIR;
            if (allowY * 1.001 >= yDistance) resultV = 0.0;

            if (!from.isHeadObstructed() && !to.isHeadObstructed()
                // Exempt check for 20 seconds after joined
                && !(now > pData.getLastJoinTime() && pData.getLastJoinTime() + 20000 > now)
                && !(thisMove.yDistance < 0.0 && lastMove.yDistance - thisMove.yDistance < 0.0001)
                ) {
 
                if (lastMove.yDistance < 0.0 && thisMove.yDistance < allowY
                    || from.getY() >= to.getY() && !(thisMove.yDistance == 0.0 && allowY < 0.0)
                    ) {
                    resultV = Math.max(resultV, 0.1);
                    tags.add("antilevitate");

                    if (data.getOrUseVerticalVelocity(getBaseV(0.0, yDistance, 0f, 0.0, level, 0.0, false)) != null) {
                        data.addVerticalVelocity(new SimpleEntry(yDistance, 2));
                        resultV = 0.0;
                    }
                }
            }
        }

        // Add tag for maximum height check (silent set back).
        final double maximumHeight = model.getMaxHeight() + player.getWorld().getMaxHeight();
        if (to.getY() > maximumHeight) {
            tags.add("maxheight");
        }

        resultV *= 100.0; // Normalize to % of a block.
        if (resultV > 0.0) {
            tags.add("vdist");
        }

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

        Location setBack = null; // Might get altered below.

        if (result > 0.0) {
            data.creativeFlyVL += result;
            // Execute whatever actions are associated with this check and the violation level and find out if we
            // should cancel the event.
            final ViolationData vd = new ViolationData(this, player, data.creativeFlyVL, result, cc.creativeFlyActions);
            if (vd.needsParameters()) {
                vd.setParameter(ParameterName.LOCATION_FROM, String.format(Locale.US, "%.2f, %.2f, %.2f", from.getX(), from.getY(), from.getZ()));
                vd.setParameter(ParameterName.LOCATION_TO, String.format(Locale.US, "%.2f, %.2f, %.2f", to.getX(), to.getY(), to.getZ()));
                vd.setParameter(ParameterName.DISTANCE, String.format(Locale.US, "%.2f", TrigUtil.distance(from,  to)));
                if (model != null) {
                    vd.setParameter(ParameterName.MODEL, model.getId().toString());
                }
                if (!tags.isEmpty()) {
                    vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
                }
            }
            if (executeActions(vd).willCancel()) {
                // Compose a new location based on coordinates of "newTo" and viewing direction of "event.getTo()"
                // to allow the player to look somewhere else despite getting pulled back by NoCheatPlus.
                setBack = data.getSetBack(to); // (OK)
            }
        }
        else {
            // Maximum height check (silent set back).
            if (to.getY() > maximumHeight) {
                setBack = data.getSetBack(to); // (OK)
                if (debug) {
                    debug(player, "Maximum height exceeded, silent set-back.");
                }
            }
            if (setBack == null) {
                // Slowly reduce the violation level with each event.
                data.creativeFlyVL *= 0.97;
            }
        }

        // Return setBack, if set.
        if (setBack != null) {
            // Check for max height of the set back.
            if (setBack.getY() > maximumHeight) {
                // Correct the y position.
                setBack.setY(getCorrectedHeight(maximumHeight, setBack.getWorld()));
                if (debug) {
                    debug(player, "Maximum height exceeded by set back, correct to: " + setBack.getY());
                }
            }
            data.sfJumpPhase = 0;
            return setBack;
        }
        else {
            // Adjust the set back and other last distances.
            data.setSetBack(to);
            // Adjust jump phase.
            if (!thisMove.from.onGroundOrResetCond && !thisMove.to.onGroundOrResetCond) {
                data.sfJumpPhase ++;
            }
            else if (thisMove.touchedGround && !thisMove.to.onGroundOrResetCond) {
                data.sfJumpPhase = 1;
            }
            else {
                data.sfJumpPhase = 0;
            }
            return null;
        }
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

        final boolean ripglide = Bridge1_13.isRiptiding(from.getPlayer()) && Bridge1_9.isGlidingWithElytra(from.getPlayer());
        final long now = System.currentTimeMillis();
        // Set the vertical limit.
        double limitV = model.getVerticalAscendModSpeed() / 100.0 * ModelFlying.VERTICAL_ASCEND_SPEED; 
        double resultV = 0.0;
        
        // Let fly speed apply with moving upwards.
        if (model.getApplyModifiers() && flying && yDistance > 0.0) {
            limitV *= data.flySpeed / Magic.DEFAULT_FLYSPEED;
        }
        else if (model.getScaleLevitationEffect() && Bridge1_9.hasLevitation()) {
            // Exclude modifiers for now.
            final double levitation = Bridge1_9.getLevitationAmplifier(from.getPlayer());
            if (levitation >= 0.0) {
                // (Double checked.)
                limitV += 0.046 * levitation; // (It ends up like 0.5 added extra for some levels of levitation, roughly.)
                final double minJumpGain = LiftOffEnvelope.NORMAL.getMinJumpGain(data.jumpAmplifier) + 0.01;
                // Bug, duplicate motion
                if (yDistance > 0.0 && yDistance < minJumpGain && thisMove.touchedGround) {
                    limitV = minJumpGain;
                    data.addVerticalVelocity(new SimpleEntry(yDistance, 2));
                }
                tags.add("levitation:" + levitation);
            }
        }

        // Related to elytra.
        if (model.getVerticalAscendGliding()) {
            limitV = Math.max(limitV, limitV = hackLytra(yDistance, limitV, thisMove, lastMove, from, data));
        }

        // "Ripglide" (riptiding+gliding phase): allow some additional speed increase
        // Note that the ExtremeMove subcheck is skipped during such phases.
        if (lastMove.toIsValid && ripglide && yDistance > limitV) {
            limitV += 5.9;
            tags.add("vripglide");
        }
        
        // Riptiding right onto a bouncy block (2nd time, higher bounce distance)
        // Note that the ExtremeMove subcheck is skipped during such phases.
        if (Bridge1_13.isRiptiding(from.getPlayer()) && (from.getBlockFlags() & BlockFlags.F_BOUNCE25) != 0
            && yDistance > limitV && data.sfJumpPhase <= 2
            && yDistance > 0.0 && yDistance < 7.5  // Cap the distance: observed maximum speed -> 5.536355205897621 (+5.993) / 5.0
            && thisMove.from.onGround && !thisMove.to.onGround) {
            data.addVerticalVelocity(new SimpleEntry(yDistance, 4));
            if (debug) debug(from.getPlayer(), "Riptide bounce: add velocity");
        }
        
        // Gliding in water
        if (Bridge1_9.isGlidingWithElytra(from.getPlayer()) && data.liqtick > 1) {
            limitV = Math.max(limitV, 0.35);
        }
        
        // Friction with gravity.
        if (model.getGravity()) {
            if (yDistance > limitV && lastMove.toIsValid) { 
                // (Disregard gravity.)
                double frictionDist = lastMove.yDistance * Magic.FRICTION_MEDIUM_AIR;
                if (!flying) {
                    frictionDist -= 0.019;
                }
                if (frictionDist > limitV) {
                    limitV = frictionDist;
                    tags.add("vfrict_g");
                }
            }
        }

        if (model.getGround()) {
            // Jump lift off gain.
            // NOTE: This assumes SurvivalFly busies about moves with from.onGroundOrResetCond.
            if (yDistance > limitV && !thisMove.to.onGroundOrResetCond && !thisMove.from.onGroundOrResetCond && (
                // Last move touched ground.
                lastMove.toIsValid && lastMove.touchedGround && 
                (lastMove.yDistance <= 0.0 || lastMove.to.extraPropertiesValid && lastMove.to.onGround)
                // This move touched ground by a workaround.
                || thisMove.touchedGroundWorkaround
                )) {
                // Allow normal jumping.
                final double maxGain = LiftOffEnvelope.NORMAL.getMaxJumpGain(data.jumpAmplifier);
                if (maxGain > limitV) {
                    limitV = maxGain;
                    tags.add("jump_gain");
                }
            }
        }

        // Ordinary step up.
        if (yDistance > limitV && yDistance <= cc.sfStepHeight 
            && (lastMove.toIsValid && lastMove.yDistance < 0.0 || from.isOnGroundOrResetCond() || thisMove.touchedGroundWorkaround)
            && to.isOnGround()) {
            // (Jump effect not checked yet.)
            limitV = cc.sfStepHeight;
            tags.add("step_up");
        }

        // Determine violation amount.
        resultV = Math.max(0.0, yDistance - limitV);
        // Post-violation recovery.
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
    private double[] hackElytraH(final Player player, final PlayerLocation from, final PlayerLocation to, final double hDistance,
                                 final double yDistance, final PlayerMoveData thisMove, final PlayerMoveData lastMove,
                                 final boolean lostGround, final MovingData data, final MovingConfig cc, final boolean debug) {

        if (player == null || from == null || to == null) {
            return new double[] {0.0, 0.0};
        }

        if (!shouldProcessElytra(player, cc)) {
            return new double[] {0.0, 0.0};
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
     * Handle gliding in the air.
     *
     * @return {resultV, resultH, baseV, allowedY, allowedH}
     */
    private double[] processAirGlide(final Player player, final PlayerLocation from, final PlayerLocation to,
            final double hDistance, final double yDistance, final PlayerMoveData thisMove,
            final PlayerMoveData lastMove, final MovingData data, final boolean debug) {

        thisMove.elytrafly = true;

        double resultV = 0.0;
        double resultH = 0.0;
        double allowedH = 0.0;
        double allowedY = lastMove.elytrafly ? lastMove.yAllowedDistance : lastMove.toIsValid ? lastMove.yDistance : 0.0;
        if (Math.abs(allowedY) < 0.003D) allowedY = 0.0D;

        final double speed = Bridge1_13.getSlowfallingAmplifier(player) >= 0.0 ? 0.01 : 0.08;
        final double lastHdist = lastMove.toIsValid ? lastMove.hDistance : 0.0;
        final Vector lookvec = to.getLocation().getDirection();
        final float radPitch = (float) Math.toRadians(to.getPitch());
        final double xzlength = Math.sqrt(lookvec.getX() * lookvec.getX() + lookvec.getZ() * lookvec.getZ());
        double squaredCos = Math.cos(radPitch);
        squaredCos *= squaredCos;

        double baseV = getBaseV(hDistance, yDistance, radPitch, squaredCos, -1.0, speed, to.getPitch() == -90f);

        allowedY += speed * (-1.0D + squaredCos * 0.75D);
        double x = lastMove.to.getX() - lastMove.from.getX();
        double z = lastMove.to.getZ() - lastMove.from.getZ();
        if (Math.abs(x) < 0.003D) x = 0.0D;
        if (Math.abs(z) < 0.003D) z = 0.0D;

        if (allowedY < 0.0D && xzlength > 0.0) {
            final double d = allowedY * -0.1 * squaredCos;
            x += lookvec.getX() * d / xzlength;
            z += lookvec.getZ() * d / xzlength;
            allowedY += d;
        }

        if (radPitch < 0.0F) {
            if (to.getPitch() == -90f && isNear(yDistance, allowedY * Magic.FRICTION_MEDIUM_ELYTRA_AIR, 0.01)) {
                allowedH += 0.01;
                if (debug) debug(player, "Add the distance to allowed on look up (hDist/Allowed): " + hDistance +"/"+ allowedH);
            }
            else if (xzlength > 0.0) {
                final double d = lastHdist * -Math.sin(radPitch) * 0.04;
                x -= lookvec.getX() * d / xzlength;
                z -= lookvec.getZ() * d / xzlength;
                allowedY += d * 3.2;
            }
        }

        if (xzlength > 0.0) {
            x += (lookvec.getX() / xzlength * lastHdist - x) * 0.1D;
            z += (lookvec.getZ() / xzlength * lastHdist - z) * 0.1D;
        }

        allowedY *= Magic.FRICTION_MEDIUM_ELYTRA_AIR;

        if (data.fireworksBoostDuration > 0) {
            thisMove.yAllowedDistance = allowedY = yDistance;
            if (Math.round(data.fireworksBoostTickNeedCheck / 4) > data.fireworksBoostDuration
                    && hDistance < Math.sqrt(x*x + z*z)) {
                thisMove.hAllowedDistance = Math.sqrt(x*x + z*z);
                if (debug) debug(player, "Set hAllowedDistance for this firework boost phase (hDist/Allowed): " + thisMove.hDistance + "/" + thisMove.hAllowedDistance);
                return new double[] {0.0, 0.0, baseV, allowedY, allowedH};
            }
            x *= 0.99;
            z *= 0.99;
            x += lookvec.getX() * 0.1D + (lookvec.getX() * 1.5D - x) * 0.5D;
            z += lookvec.getZ() * 0.1D + (lookvec.getZ() * 1.5D - z) * 0.5D;
            tags.add("fw_speed");
            if (hDistance < lastMove.hAllowedDistance * 0.994) {
                thisMove.hAllowedDistance = lastMove.hAllowedDistance * 0.994;
                if (debug) debug(player, "Firework boost phase has ended sooner than expected, but the player is still legitimately boosting (hDist/Allowed): " + thisMove.hDistance + "/" + thisMove.hAllowedDistance);
                return new double[] {0.0, 0.0, baseV, allowedY, allowedH};
            }
            else allowedH += 0.2;
        }

        allowedH += Math.sqrt(x*x + z*z) + 0.1;
        if (debug) {
            debug(player, "Cumulative elytra hDistance (hDist/Allowed): " + hDistance + "/" + allowedH + " lasthDist:" + lastHdist);
            debug(player, "radiansPitch: " + radPitch + " yDist:" + yDistance + " lastyDist:" + lastMove.yDistance + " allowy:" + allowedY);
        }

        final double yDistDiffEx = yDistance - allowedY;

        if (data.fireworksBoostDuration <= 0) {
            if (yDistance > 0.0 && yDistance < 0.42 && thisMove.touchedGround) {
                allowedY = yDistance;
                allowedH = Math.max(0.35, allowedH * 1.35);
                if (debug) debug(player, "Elytra jump (hDist/Allowed): " + thisMove.hDistance +"/"+ allowedH);
            }
            else if (from.isHeadObstructed() && lastMove.yDistance > 0.0 && yDistDiffEx < 0.0 && (allowedY > 0.0 || yDistance == 0.0)) {
                allowedY = yDistance;
            }
            else if (yDistance < 0.0) {
                if (lastMove.yDistance > 0.0 && yDistance < 0.0
                        && (lastMove.yDistance < Magic.GRAVITY_MAX + Magic.GRAVITY_MIN && yDistance > - Magic.GRAVITY_MIN
                                || lastMove.yDistance < Magic.GRAVITY_MIN && yDistance > - Magic.GRAVITY_MIN - Magic.GRAVITY_MAX)) {
                    allowedY = yDistance;
                }
            }

            if (yDistance > 0.0) {
                if (allowedY < yDistance && !isNear(allowedY, yDistance, 0.001)) {
                    tags.add("e_vasc");
                    resultV = yDistance;
                }
            }
            else if (yDistance < 0.0) {
                if (allowedY > yDistance && !isNear(allowedY, yDistance, Magic.GRAVITY_MAX)) {
                    tags.add("e_vdesc");
                    resultV = Math.abs(yDistance);
                }
            }

            if (yDistance <= 0.0 && (to.isOnGround() || to.isResetCond() || thisMove.touchedGround)
                    || yDistDiffEx > -Magic.GRAVITY_MAX && yDistDiffEx < 0.0
                    || speed < 0.05 && !TrigUtil.isSamePos(lastMove.from, lastMove.to) && (hDistance == 0.0 && yDistance == 0.0 || yDistance < -Magic.GRAVITY_SPAN)) {
                allowedY = yDistance;
            }
            else if (Math.abs(yDistDiffEx) > (speed < 0.05 ? 0.00001 : 0.03)) {
                tags.add("e_vdiff");
                resultV = Math.max(Math.abs(yDistance - allowedY), resultV);
            }
        }

        if (allowedH < hDistance) {
            tags.add("e_hspeed");
            resultH = hDistance - allowedH;
        }

        return new double[] {resultV, resultH, baseV, allowedY, allowedH};
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
    
    public static double[] guessElytraVelocityAmount(final Player player, final PlayerMoveData thisMove, final PlayerMoveData lastMove, final MovingData data) {
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
                return new double[] {Math.sqrt(x*x + z*z), allowedElytraYDistance};
            }

            x *= 0.99;
            z *= 0.99;
            x += lookvec.getX() * 0.1D + (lookvec.getX() * 1.5D - x) * 0.5D;
            z += lookvec.getZ() * 0.1D + (lookvec.getZ() * 1.5D - z) * 0.5D;

            if (thisMove.hDistance < lastMove.hAllowedDistance * 0.994) {
                return new double[] {lastMove.hAllowedDistance * 0.994, allowedElytraYDistance};
            } 
            else allowedElytraHDistance += 0.2;
        }

        // Adjust false
        allowedElytraHDistance += Math.sqrt(x*x + z*z) + 0.1;
        return new double[] {allowedElytraHDistance, allowedElytraYDistance};
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
