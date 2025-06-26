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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;
import org.bukkit.ChatColor;

/**
 * A check used to verify if the player isn't using a forcefield in order to attack multiple entities at the same time.
 * 
 * Thanks @asofold for the original idea!
 */
public class Angle extends Check {

    /** Container for calculated averages. */
    private static final class Averages {
        final double move;
        final double time;
        final double yaw;
        final double switchCount;

        Averages(final double move, final double time, final double yaw, final double switchCount) {
            this.move = move;
            this.time = time;
            this.yaw = yaw;
            this.switchCount = switchCount;
        }
    }


    public static class AttackLocation {
        public final double x, y, z;
        /** Yaw of the attacker. */
        public final float yaw;
        public long time;
        public final UUID damagedId;
        /** Squared distance to the last location (0 if none given). */
        public final double distSqLast;
        /** Difference in yaw to the last location (0 if none given). */
        public final double yawDiffLast;
        /** Time difference to the last location (0 if none given). */
        public final long timeDiff;
        /** If the id differs from the last damaged entity (true if no lastLoc is given). */
        public final boolean idDiffLast;
        public AttackLocation(final Location loc, final UUID damagedId, final long time, final AttackLocation lastLoc) {
            x = loc.getX();
            y = loc.getY();
            z = loc.getZ();
            yaw = loc.getYaw();
            this.time = time;
            this.damagedId = damagedId;

            if (lastLoc != null) {
                distSqLast = TrigUtil.distanceSquared(x, y, z, lastLoc.x, lastLoc.y, lastLoc.z);
                yawDiffLast = TrigUtil.yawDiff(yaw, lastLoc.yaw);
                timeDiff = Math.max(0L, time - lastLoc.time);
                idDiffLast = !damagedId.equals(lastLoc.damagedId);
            } else {
                distSqLast = 0.0;
                yawDiffLast = 0f;
                timeDiff = 0L;
                idDiffLast = true;
            }
        }
    }


    public static long maxTimeDiff = 1000L;


    /**
     * Instantiates a new angle check.
     */
    public Angle() {
        super(CheckType.FIGHT_ANGLE);
    }

    /** Update the player's attack history. */
    private void updateAttackHistory(final Player player, final Location loc,
                                     final Entity damagedEntity, final FightData data,
                                     final long currentTime) {
        AttackLocation lastLoc = data.angleHits.isEmpty() ? null : data.angleHits.getLast();
        if (lastLoc != null && currentTime - lastLoc.time > maxTimeDiff) {
            data.angleHits.clear();
            lastLoc = null;
        }
        data.angleHits.add(new AttackLocation(loc, damagedEntity.getUniqueId(), currentTime, lastLoc));
    }

    /**
     * Calculate averages based on stored attack history.
     *
     * @param data
     * @param currentTime
     * @param pData       Optional player data for debug output
     * @return Averages for movement, time, yaw, and target switch count
     */
    private Averages calculateAverages(final FightData data, final long currentTime,
                                       final IPlayerData pData) {
        double deltaMove = 0D;
        long deltaTime = 0L;
        float deltaYaw = 0f;
        int deltaSwitchTarget = 0;
        final Iterator<AttackLocation> it = data.angleHits.iterator();
        while (it.hasNext()) {
            final AttackLocation refLoc = it.next();
            if (currentTime - refLoc.time > maxTimeDiff) {
                it.remove();
                continue;
            }
            deltaMove += refLoc.distSqLast;
            final double yawDiff = Math.abs(refLoc.yawDiffLast);
            deltaYaw += yawDiff;
            deltaTime += refLoc.timeDiff;
            if (refLoc.idDiffLast && yawDiff > 30.0) {
                deltaSwitchTarget += 1;
            }
        }
        final double n = (double) (data.angleHits.size() - 1);
        if (n <= 0D) {
            if (pData != null && pData.isDebugActive(type)) {
                debug(null, "Insufficient data points to calculate averages");
            }
            return new Averages(0D, 0D, 0D, 0D);
        }
        return new Averages(deltaMove / n, ((double) deltaTime) / n,
                ((double) deltaYaw) / n, ((double) deltaSwitchTarget) / n);
    }

    /** Determine the violation value from the given averages. */
    private double evaluateViolations(final double avgMove, final double avgTime,
                                      final double avgYaw, final double avgSwitch,
                                      final FightConfig cc, final List<String> tags) {
        double violationMove = 0.0;
        double violationTime = 0.0;
        double violationYaw = 0.0;
        double violationSwitch = 0.0;

        if (avgMove >= 0.0 && avgMove < 0.2D) {
            violationMove += 20.0 * (0.2 - avgMove) / 0.2;
            tags.add("avgmove");
        }
        if (avgTime >= 0.0 && avgTime < 150.0) {
            violationTime += 30.0 * (150.0 - avgTime) / 150.0;
            tags.add("avgtime");
        }
        if (avgYaw > 50.0) {
            violationYaw += 30.0 * avgYaw / 180.0;
            tags.add("avgyaw");
        }
        if (avgSwitch > 0.0) {
            violationSwitch += 20.0 * avgSwitch;
            tags.add("switchspeed");
        }

        if (violationMove > cc.angleMove) {
            return violationMove;
        } else if (violationTime > cc.angleTime) {
            return violationTime;
        } else if (violationYaw > cc.angleYaw) {
            return violationYaw;
        } else if (violationSwitch > cc.angleSwitch) {
            return violationSwitch;
        }
        return 0.0;
    }

    /** Apply the violation and execute configured actions. */
    private boolean applyViolation(final Player player, final double violation,
                                   final FightData data, final FightConfig cc,
                                   final List<String> tags) {
        data.angleVL += violation;
        final ViolationData vd = new ViolationData(this, player, data.angleVL,
                violation, cc.angleActions);
        if (vd.needsParameters()) {
            vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
        }
        return executeActions(vd).willCancel();
    }


    /**
     * The Angle check.
     * @param player
     * @param loc Location of the player.
     * @param worldChanged
     * @param data
     * @param cc
     * @return
     */
    public boolean check(final Player player, final Location loc,
                         final Entity damagedEntity, final boolean worldChanged,

                         final FightData data, final FightConfig cc, final IPlayerData pData) {

        if (player == null || loc == null || damagedEntity == null) {
            return false;
        }

        if (worldChanged) {
            data.angleHits.clear();
        }

        boolean cancel = false;
        final List<String> tags = new LinkedList<String>();

        final long time = System.currentTimeMillis();
        updateAttackHistory(player, loc, damagedEntity, data, time);

        if (data.angleHits.size() >= 2) {
            final Averages avg = calculateAverages(data, time, pData);

            if (pData.isDebugActive(type) && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
                player.sendMessage(ChatColor.RED + "NC+ Debug: " + ChatColor.RESET
                        + "avgMove: " + avg.move + " avgTime: " + avg.time
                        + " avgYaw: " + avg.yaw + " avgSwitch: " + avg.switchCount);
            }

            final double violation = evaluateViolations(avg.move, avg.time, avg.yaw, avg.switchCount, cc, tags);

            if (violation > 0.0 && TickTask.getLag(maxTimeDiff, true) < 1.5f) {
                cancel = applyViolation(player, violation, data, cc, tags);
            } else if (violation <= 0.0) {
                data.angleVL *= 0.98D;
            }
        } else {
            cancel = false;
        }
        return cancel;
    }
}
