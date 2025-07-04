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
package fr.neatmonster.nocheatplus.checks.fight;


import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Giant;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.location.tracking.LocationTrace.ITraceEntry;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.ViolationData;



/**
 * The Reach check will find out if a player interacts with something that's too far away.
 */
public class Reach extends Check {


    /** The maximum distance allowed to interact with an entity in creative mode. */
    public static final double CREATIVE_DISTANCE = 6D;

    /** Reusable vector for inset calculations to reduce allocations. */
    private final Vector insetVec1 = new Vector();

    /** Second reusable vector for inset calculations to reduce allocations. */
    private final Vector insetVec2 = new Vector();

    /** Lag threshold used to skip violations during heavy server lag. */
    private static final float LAG_THRESHOLD = 1.5f;

    /** Divisor used when feeding improbable data for silent cancels. */
    private static final float IMPROBABLE_FEED_DIVISOR = 4f;


    /** Additum for distance, based on entity. */
    private static double getDistMod(final Entity damaged) {
        // Handle the EnderDragon differently.
        if (damaged instanceof EnderDragon)
            return 6.5D;
        else if (damaged instanceof Giant){
            return 1.5D;
        }
        else return 0;
    }

    /**
     * Check if the server is currently lagging significantly.
     *
     * @return {@code true} if lag exceeds {@link #LAG_THRESHOLD}
     */
    private static boolean isLagging() {
        return TickTask.getLag(1000, true) >= LAG_THRESHOLD;
    }

    /**
     * Calculate the distance from the player's eye location to the reference
     * point, applying precision settings and Y adjustments.
     *
     * @param player
     *            attacker
     * @param pLoc
     *            attacker location
     * @param dRef
     *            target reference (modified in-place)
     * @param width
     *            target width
     * @param height
     *            target height
     * @param cc
     *            fight configuration
     * @return the distance between attacker and target reference
     */
    private double calculateReachDistance(final Player player, final Location pLoc,
                                          final Location dRef, final double width,
                                          final double height, final FightConfig cc) {

        final double pY = pLoc.getY() + player.getEyeHeight();
        final double dY = dRef.getY();

        if (pY >= dY + height) {
            dRef.setY(dY + height);
        }
        else if (pY > dY) {
            dRef.setY(pY);
        }

        double centerToEdge = 0.0;
        if (cc.reachPrecision) {
            centerToEdge = getinset(pLoc, dRef, width / 2, 0.0);
        }

        return TrigUtil.distance(dRef.getX(), dRef.getY(), dRef.getZ(),
                pLoc.getX(), pY, pLoc.getZ()) - centerToEdge;
    }

    private boolean handleViolation(final Player player, final double lenpRel,
                                    final double violation, final FightData data,
                                    final FightConfig cc, final IPlayerData pData) {
        boolean cancel = false;
        if (!isLagging()) {
            data.reachVL += violation;
            final ViolationData vd = new ViolationData(this, player, data.reachVL,
                    violation, cc.reachActions);
            vd.setParameter(ParameterName.REACH_DISTANCE,
                    StringUtil.fdec3.format(lenpRel));
            cancel = executeActions(vd).willCancel();
        }

        if (Improbable.check(player, (float) violation / 2f,
                System.currentTimeMillis(), "fight.reach", pData)) {
            cancel = true;
        }

        if (cancel && cc.reachPenalty > 0) {
            data.attackPenalty.applyPenalty(cc.reachPenalty);
        }

        return cancel;
    }

    private boolean handleSilentViolation(final Player player, final double lenpRel,
                                          final double distanceLimit, final double reachMod,
                                          final FightConfig cc, final FightData data) {
        if (cc.reachPenalty > 0) {
            data.attackPenalty.applyPenalty(cc.reachPenalty / 2);
        }
        Improbable.feed(player,
                (float) (lenpRel - distanceLimit * reachMod) / IMPROBABLE_FEED_DIVISOR,
                System.currentTimeMillis());
        return true;
    }


    /**
     * Instantiates a new reach check.
     */
    public Reach() {
        super(CheckType.FIGHT_REACH);
    }


    /**
     * "Classic" check.
     * 
     * @param player
     *            the player
     * @param damaged
     *            the damaged
     * @return true, if successful
     */
    public boolean check(final Player player, final Location pLoc,
                         final Entity damaged, final boolean damagedIsFake, final Location dRef,
                         final FightData data, final FightConfig cc, final IPlayerData pData) {

        if (player == null || pLoc == null || damaged == null || dRef == null) {
            return false;
        }

        boolean cancel = false;
        final double SURVIVAL_DISTANCE = cc.reachSurvivalDistance;
        final double DYNAMIC_RANGE = cc.reachReduceDistance;
        final double DYNAMIC_STEP = cc.reachReduceStep / SURVIVAL_DISTANCE;
        final double distanceLimit = player.getGameMode() == GameMode.CREATIVE
                ? CREATIVE_DISTANCE : SURVIVAL_DISTANCE + getDistMod(damaged);
        final double distanceMin = (distanceLimit - DYNAMIC_RANGE) / distanceLimit;
        final double height = damagedIsFake
                ? (damaged instanceof LivingEntity ? ((LivingEntity) damaged).getEyeHeight() : 1.75)
                : mcAccess.getHandle().getHeight(damaged);
        final double width = damagedIsFake ? 0.6 : mcAccess.getHandle().getWidth(damaged);

        final double lenpRel = calculateReachDistance(player, pLoc, dRef, width, height, cc);
        final double violation = lenpRel - distanceLimit;
        final double reachMod = data.reachMod;

        if (violation > 0) {
            cancel = handleViolation(player, lenpRel, violation, data, cc, pData);
        } else if (lenpRel - distanceLimit * reachMod > 0) {
            cancel = handleSilentViolation(player, lenpRel, distanceLimit, reachMod, cc, data);
        } else {
            data.reachVL *= 0.8D;
        }
            

        if (!cc.reachReduce){
            data.reachMod = 1d;
        }
        else if (lenpRel > distanceLimit - DYNAMIC_RANGE){
            data.reachMod = Math.max(distanceMin, data.reachMod - DYNAMIC_STEP);
        }
        else { 
            data.reachMod = Math.min(1.0, data.reachMod + DYNAMIC_STEP);
        }

        if (pData.isDebugActive(type) && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)){
            player.sendMessage("NC+: Attack/reach " + damaged.getType()+ " height="+ StringUtil.fdec3.format(height) + " dist=" + StringUtil.fdec3.format(lenpRel) +" @" + StringUtil.fdec3.format(reachMod));
        }

        return cancel;
    }

    /**
     * Data context for iterating over ITraceEntry instances.
     * @param player
     * @param pLoc
     * @param damaged
     * @param damagedLoc
     * @param data
     * @param cc
     * @return
     */
    public ReachContext getContext(final Player player, final Location pLoc, 
                                   final Entity damaged, final Location damagedLoc, 
                                   final FightData data, final FightConfig cc) {

        final ReachContext context = new ReachContext();
        context.distanceLimit = player.getGameMode() == GameMode.CREATIVE ? CREATIVE_DISTANCE : cc.reachSurvivalDistance + getDistMod(damaged);
        context.distanceMin = (context.distanceLimit - cc.reachReduceDistance) / context.distanceLimit;
        //context.eyeHeight = player.getEyeHeight();
        context.pY = pLoc.getY() + player.getEyeHeight();
        return context;
    }

    /**
     * Check if the player fails the reach check, no change of FightData.
     * @param player
     * @param pLoc
     * @param damaged
     * @param dRef
     * @param context
     * @param data
     * @param cc
     * @return
     */
    public boolean loopCheck(final Player player, final Location pLoc, final Entity damaged, 
                             final ITraceEntry dRef, final ReachContext context, 
                             final FightData data, final FightConfig cc) {

        boolean cancel = false;

        // Refine y position.
        final double dY = dRef.getY();
        double y = dRef.getY();

        if (context.pY <= dY) {
            // Keep the foot level y.
        }
        else if (context.pY >= dY + dRef.getBoxMarginVertical()) {
            y = dY + dRef.getBoxMarginVertical(); // Highest ref y.
        }
        else {
            y = context.pY; // Level with damaged.
        }

        double centertoedge = 0.0;
        if (cc.reachPrecision) centertoedge = getinset(pLoc, new Location(null, dRef.getX(), dRef.getY(), dRef.getZ()), dRef.getBoxMarginHorizontal(), y - context.pY);
        
        // Distance is calculated from eye location to center of targeted. If the player is further away from their target
        // than allowed, the difference will be assigned to "distance".
        // Checking on squared distances would be more efficient and could use
        // stored boundary-squared values.
        final double lenpRel = TrigUtil.distance(dRef.getX(), y, dRef.getZ(), pLoc.getX(), context.pY, pLoc.getZ()) - centertoedge;
        double violation = lenpRel - context.distanceLimit;

        if (violation > 0 || lenpRel - context.distanceLimit * data.reachMod > 0){
            // NOTE: The silent cancel parts might be seen as "no violation".
            // Set minimum violation in context
            context.minViolation = Math.min(context.minViolation, lenpRel);
            cancel = true;
        }
        context.minResult = Math.min(context.minResult, lenpRel);

        return cancel;

    }

    /**
     * Apply changes to FightData according to check results (context), trigger violations.
     * @param player
     * @param pLoc
     * @param damaged
     * @param context
     * @param forceViolation
     * @param data
     * @param cc
     * @return
     */public boolean loopFinish(final Player player, final Location pLoc, final Entity damaged, 
                          final ReachContext context, final ITraceEntry traceEntry, final boolean forceViolation, 
                          final FightData data, final FightConfig cc, final IPlayerData pData) {

    final double lenpRel = forceViolation && context.minViolation != Double.MAX_VALUE
            ? context.minViolation : context.minResult;

    if (lenpRel == Double.MAX_VALUE) {
        return false;
    }

    final double violation = lenpRel - context.distanceLimit;
    boolean cancel = false;

    if (violation > 0) {
        if (TickTask.getLag(1000, true) < LAG_THRESHOLD) {
            data.reachVL += violation;
            final ViolationData vd = new ViolationData(this, player, data.reachVL, violation, cc.reachActions);
            vd.setParameter(ParameterName.REACH_DISTANCE, StringUtil.fdec3.format(lenpRel));
            cancel = executeActions(vd).willCancel();
        }

        if (cc.reachImprobableWeight > 0.0f) {
            final float weight = (float) violation / cc.reachImprobableWeight;
            if (!cc.reachImprobableFeedOnly) {
                if (Improbable.check(player, weight, System.currentTimeMillis(), "fight.reach", pData)) {
                    cancel = true;
                }
            }
        }

        if (cancel && cc.reachPenalty > 0) {
            data.attackPenalty.applyPenalty(cc.reachPenalty);
        }

    } else if (lenpRel - context.distanceLimit * data.reachMod > 0) {
        // Silent cancel
        if (cc.reachPenalty > 0) {
            data.attackPenalty.applyPenalty(cc.reachPenalty / 2);
        }

        cancel = true;

        if (cc.reachImprobableWeight > 0.0f) {
            Improbable.feed(player,
                    (float) (lenpRel - context.distanceLimit * data.reachMod) / cc.reachImprobableWeight,
                    System.currentTimeMillis());
        }
    } else {
        data.reachVL *= 0.8D;
    }

    updateReachModifier(lenpRel, context, data, cc);
    sendDebugInfo(player, damaged, traceEntry, lenpRel, data, pData);

    return cancel;
}

    /**
     * Update the dynamic reach modifier based on the last result.
     */
    private void updateReachModifier(final double lenpRel, final ReachContext context,
                                     final FightData data, final FightConfig cc) {
        final double dynamicStep = cc.reachReduceStep / cc.reachSurvivalDistance;
        if (!cc.reachReduce) {
            data.reachMod = 1d;
        } else if (lenpRel > context.distanceLimit - cc.reachReduceDistance) {
            data.reachMod = Math.max(context.distanceMin, data.reachMod - dynamicStep);
        } else {
            data.reachMod = Math.min(1.0, data.reachMod + dynamicStep);
        }
    }

    /**
     * Send debug information about the reach calculation.
     */
    private void sendDebugInfo(final Player player, final Entity damaged, final ITraceEntry traceEntry,
                               final double lenpRel, final FightData data, final IPlayerData pData) {
        if (pData.isDebugActive(type) && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
            final String heightInfo = traceEntry == null ? "" : " height=" + traceEntry.getBoxMarginVertical();
            player.sendMessage("NC+: Attack/reach " + damaged.getType() + heightInfo
                    + " dist=" + StringUtil.fdec3.format(lenpRel)
                    + " @" + StringUtil.fdec3.format(data.reachMod));
        }
    }


    private boolean isSameXZ(final Location loc1, final Location loc2) {
        return loc1.getX() == loc2.getX() && loc1.getZ() == loc2.getZ();
    }


    /**
     *
     * @param pLoc
     *            the player location
     * @param dRef
     *            the target location
     * @param damagedBoxMarginHorizontal
     *            the target Width / 2
     * @param diffY the Y different
     * @return the double represent for the distance from target location to the edge of target hitbox
     */
    private double getinset(final Location pLoc, final Location dRef, final double damagedBoxMarginHorizontal, final double diffY) {

        if (!isSameXZ(pLoc, dRef)) {
            final Location dRefc = dRef.clone();
            insetVec1.setX(pLoc.getX() - dRef.getX());
            insetVec1.setY(diffY);
            insetVec1.setZ(pLoc.getZ() - dRef.getZ());
            if (insetVec1.length() < damagedBoxMarginHorizontal * Math.sqrt(2)) {
                insetVec1.zero();
                return 0.0;
            }
            if (insetVec1.getZ() > 0.0) {
                dRefc.setZ(dRefc.getZ() + damagedBoxMarginHorizontal);
            }
            else if (insetVec1.getZ() < 0.0) {
                dRefc.setZ(dRefc.getZ() - damagedBoxMarginHorizontal);
            }
            else if (insetVec1.getX() > 0.0) {
                dRefc.setX(dRefc.getX() + damagedBoxMarginHorizontal);
            }
            else dRefc.setX(dRefc.getX() - damagedBoxMarginHorizontal);

            insetVec2.setX(dRefc.getX() - dRef.getX());
            insetVec2.setY(0.0);
            insetVec2.setZ(dRefc.getZ() - dRef.getZ());
            double angle = TrigUtil.angle(insetVec1, insetVec2);
            // Require < 45deg, if not 90deg-angel
            if (angle > Math.PI / 4) angle = Math.PI / 2 - angle;
            // Evaluate if this condition is actually required
            if (angle >= 0.0 && angle <= Math.PI / 4) {
                insetVec1.zero();
                insetVec2.zero();
                return damagedBoxMarginHorizontal / Math.cos(angle);
            }
            insetVec1.zero();
            insetVec2.zero();
        }
        return 0.0;
    }
}
