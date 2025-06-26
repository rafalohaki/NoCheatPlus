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
package fr.neatmonster.nocheatplus.checks.blockbreak;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;

public class WrongBlock extends Check {

    public WrongBlock() {
        super(CheckType.BLOCKBREAK_WRONGBLOCK);
    }

    /**
     * Check if the player destroys another block than interacted with last.<br>
     * This does occasionally trigger for players that destroy grass or snow, 
     * probably due to packet delaying issues for insta breaking.
     * @param player
     * @param block
     * @param data 
     * @param cc 
     * @param isInstaBreak 
     * @return
     */
    
    public boolean check(final Player player, final Block block,
            final BlockBreakConfig cc, final BlockBreakData data, final IPlayerData pData,
            final AlmostBoolean isInstaBreak) {

        if (player == null || block == null) {
            return false;
        }

        final long now = System.currentTimeMillis();
        final int dist = calculateWrongBlockDistance(player, block, data, isInstaBreak, now, pData);
        if (dist < 0) {
            return false;
        }

        return applyWrongBlockPenalty(player, dist, now, cc, data, pData);
    }

    private int calculateWrongBlockDistance(final Player player, final Block block,
            final BlockBreakData data, final AlmostBoolean isInstaBreak, final long now,
            final IPlayerData pData) {
        if (block == null || data == null) {
            return -1;
        }

        final boolean wrongTime = data.fastBreakfirstDamage < data.fastBreakBreakTime;
        final int dist = Math.min(4, data.clickedX == Integer.MAX_VALUE ? 100
                : TrigUtil.manhattan(data.clickedX, data.clickedY, data.clickedZ, block));
        final boolean debug = pData != null && pData.isDebugActive(type);

        if (dist == 0) {
            if (wrongTime) {
                data.fastBreakBreakTime = now;
                data.fastBreakfirstDamage = now;
            }
            return -1;
        }
        if (dist == 1 && now - data.wasInstaBreak < 60) {
            if (debug && player != null && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
                debug(player, "Skip on Manhattan 1 and wasInstaBreak within 60 ms.");
            }
            return -1;
        }
        return dist;
    }

    private boolean applyWrongBlockPenalty(final Player player, final int dist, final long now,
            final BlockBreakConfig cc, final BlockBreakData data, final IPlayerData pData) {

        boolean cancel = false;

        if (pData.isDebugActive(type) && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
            player.sendMessage("WrongBlock failure with dist: " + dist);
        }

        data.wrongBlockVL.add(now, (float) (dist + 1) / 2f);
        final float score = data.wrongBlockVL.score(0.9f);
        if (score > cc.wrongBLockLevel) {
            if (executeActions(player, score, 1D, cc.wrongBlockActions).willCancel()) {
                cancel = true;
            }
            if (cc.wrongBlockImprobableWeight > 0.0f) {
                if (cc.wrongBlockImprobableFeedOnly) {
                    Improbable.feed(player, cc.wrongBlockImprobableWeight, now);
                } else if (Improbable.check(player, cc.wrongBlockImprobableWeight, now,
                        "blockbreak.wrongblock", pData)) {
                    cancel = true;
                }
            }
        }

        return cancel;
    }

}
