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
     * @param player the acting player, may be {@code null}
     * @param block the targeted block, may be {@code null}
     * @param data runtime data for this check
     * @param cc configuration settings for this check
     * @param pData aggregated player data
     * @return {@code true} if the event should be cancelled
     */

    public boolean check(final Player player, final Block block,
            final BlockBreakConfig cc, final BlockBreakData data,
            final IPlayerData pData) {

        boolean cancel = false;
        if (player != null && block != null && cc != null
                && data != null && pData != null) {
            cancel = handleCheck(player, block, cc, data, pData);
        }
        return cancel;
    }

    private boolean handleCheck(final Player player, final Block block,
            final BlockBreakConfig cc, final BlockBreakData data,
            final IPlayerData pData) {
        boolean cancel = false;
        final long now = System.currentTimeMillis();
        final boolean wrongTime = data.fastBreakfirstDamage < data.fastBreakBreakTime;
        final int dist = computeDistance(data, block);
        final boolean debug = pData.isDebugActive(type);
        final boolean wrongBlock = isWrongBlock(player, data, dist, now, wrongTime, debug);

        if (wrongBlock) {
            cancel = applyViolation(player, pData, cc, data, now, dist, debug);
        }

        return cancel;
    }

    private int computeDistance(final BlockBreakData data, final Block block) {
        if (data.clickedX == Integer.MAX_VALUE) {
            return 100;
        }
        return Math.min(4, TrigUtil.manhattan(data.clickedX, data.clickedY, data.clickedZ, block));
    }

    private boolean isWrongBlock(final Player player, final BlockBreakData data,
            final int dist, final long now, final boolean wrongTime,
            final boolean debug) {
        boolean wrongBlock;
        if (dist == 0) {
            if (wrongTime) {
                data.fastBreakBreakTime = now;
                data.fastBreakfirstDamage = now;
            }
            wrongBlock = false;
        } else if (dist == 1) {
            if (now - data.wasInstaBreak < 60) {
                if (debug) {
                    debug(player, "Skip on Manhattan 1 and wasInstaBreak within 60 ms.");
                }
                wrongBlock = false;
            } else {
                wrongBlock = true;
            }
        } else {
            wrongBlock = true;
        }
        return wrongBlock;
    }

    private boolean applyViolation(final Player player, final IPlayerData pData,
            final BlockBreakConfig cc, final BlockBreakData data, final long now,
            final int dist, final boolean debug) {
        boolean cancel = false;
        if (debug && pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
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
                } else if (Improbable.check(player, cc.wrongBlockImprobableWeight,
                        now, "blockbreak.wrongblock", pData)) {
                    cancel = true;
                }
            }
        }
        return cancel;
    }

}
