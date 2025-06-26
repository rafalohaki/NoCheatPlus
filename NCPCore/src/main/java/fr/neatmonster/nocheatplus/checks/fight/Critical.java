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

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveInfo;
import fr.neatmonster.nocheatplus.checks.moving.util.AuxMoving;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.penalties.IPenaltyList;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.BlockFlags;

/**
 * A check used to verify that critical hits done by players are legit.
 */
public class Critical extends Check {


    /** Utility helper providing movement-related methods. */
    private final AuxMoving auxMoving = NCPAPIProvider.getNoCheatPlusAPI().getGenericInstance(AuxMoving.class);


    /**
     * Instantiates a new critical check.
     */
    public Critical() {
        super(CheckType.FIGHT_CRITICAL);
    }


    /**
     * Checks if the player's attack should count as a critical hit violation.
     * The caller must ensure that {@link #isEnabled(Player, IPlayerData)} has
     * been verified before invoking this method.
     *
     * @param player the attacking player (may be {@code null})
     * @param loc a trusted location representing the player
     * @return {@code true} if the event should be cancelled
     */
    public boolean check(final Player player, final Location loc, final FightData data, final FightConfig cc,
                         final IPlayerData pData, final IPenaltyList penaltyList) {

        if (player == null || loc == null) {
            return false;
        }

        final MovingData mData = pData.getGenericInstance(MovingData.class);
        final MovingConfig mCC = pData.getGenericInstance(MovingConfig.class);
        final PlayerMoveData thisMove = mData.playerMoves.getCurrentMove();
        final double mcFallDistance = (double) player.getFallDistance();
        final double ncpFallDistance = mData.noFallFallDistance;

        if (mcFallDistance <= 0.0 || player.isInsideVehicle() || player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
            return false;
        }

        final double realisticFallDistance = MovingUtil.getRealisticFallDistance(player, thisMove.from.getY(),
                thisMove.to.getY(), mData, pData);

        if (pData.isDebugActive(type)) {
            debug(player,
                    "Fall distances: MC(" + StringUtil.fdec3.format(mcFallDistance) + ") | NCP(" + StringUtil.fdec3.format(ncpFallDistance)
                            + ") | R(" + StringUtil.fdec3.format(realisticFallDistance) + ")"
                            + "\nfD diff: " + StringUtil.fdec3.format(Math.abs(ncpFallDistance - mcFallDistance))
                            + "\nJumpPhase: " + mData.sfJumpPhase + " | LowJump: " + mData.sfLowJump
                            + " | NCP onGround: " + (thisMove.from.onGround ? "ground -> " : "--- -> ")
                            + (thisMove.to.onGround ? "ground" : "---") + " | MC onGround: " + player.isOnGround());
        }

        final List<String> tags = new ArrayList<String>();
        final boolean violation = shouldInvalidateCritical(player, loc, mcFallDistance, ncpFallDistance,
                mData, mCC, thisMove, cc, tags);

        boolean cancel = false;
        if (violation) {
            cancel = handleCriticalViolation(player, loc, data, cc, pData, penaltyList, tags, mData, mCC, thisMove);
        } else {
            rewardLegitCritical(data);
        }

        return cancel;
    }

    private boolean shouldInvalidateCritical(final Player player, final Location loc, final double mcFallDistance,
            final double ncpFallDistance, final MovingData mData, final MovingConfig mCC, final PlayerMoveData thisMove,
            final FightConfig cc, final List<String> tags) {
        if (Math.abs(ncpFallDistance - mcFallDistance) > cc.criticalFallDistLeniency
                && mcFallDistance <= cc.criticalFallDistance && mData.sfJumpPhase <= 1
                && !BlockProperties.isResetCond(player, loc, mCC.yOnGround)) {
            tags.add("fakejump");
            return true;
        } else if (mData.sfLowJump) {
            tags.add("lowjump");
            return true;
        } else if (Math.abs(ncpFallDistance - mcFallDistance) > 1e-5 && thisMove.from.onGround && thisMove.to.onGround
                && !BlockProperties.isResetCond(player, loc, mCC.yOnGround)) {
            tags.add("falldist_mismatch");
            return true;
        } else if ((thisMove.from.inBerryBush || thisMove.from.inWeb || thisMove.from.inPowderSnow)
                && mData.insideMediumCount > 1) { // mcFallDistance > 0.0 is checked above.
            tags.add("fakefall");
            return true;
        }
        return false;
    }

    private boolean handleCriticalViolation(final Player player, final Location loc, final FightData data,
            final FightConfig cc, final IPlayerData pData, final IPenaltyList penaltyList, final List<String> tags,
            final MovingData mData, final MovingConfig mCC, final PlayerMoveData thisMove) {

        final PlayerMoveInfo moveInfo = auxMoving.usePlayerMoveInfo();
        moveInfo.set(player, loc, null, mCC.yOnGround);

        if (MovingUtil.shouldCheckSurvivalFly(player, moveInfo.from, null, mData, mCC, pData)
                && !moveInfo.from.isOnGroundDueToStandingOnAnEntity()) {

            moveInfo.from.collectBlockFlags(0.4);
            if ((moveInfo.from.getBlockFlags() & BlockFlags.F_BOUNCE25) == 0 || thisMove.from.onGround
                    || thisMove.to.onGround) {

                data.criticalVL += 1.0;
                final ViolationData vd = new ViolationData(this, player, data.criticalVL, 1.0, cc.criticalActions);
                if (vd.needsParameters()) {
                    vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
                }
                final boolean cancel = executeActions(vd).willCancel();
                auxMoving.returnPlayerMoveInfo(moveInfo);
                return cancel;
            }
        }
        auxMoving.returnPlayerMoveInfo(moveInfo);
        return false;
    }

    private void rewardLegitCritical(final FightData data) {
        data.criticalVL *= 0.96D;
    }
}
