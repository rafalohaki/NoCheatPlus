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

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.compat.AlmostBoolean;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgePotionEffect;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.time.monotonic.Monotonic;
import fr.neatmonster.nocheatplus.utilities.PotionUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;

/**
 * A check used to verify if the player isn't breaking blocks faster than possible.
 */
public class FastBreak extends Check {

    /**
     * Instantiates a new fast break check.
     */
    public FastBreak() {
        super(CheckType.BLOCKBREAK_FASTBREAK);
    }

    /**
     * Checks a player for fastbreak. This is NOT for creative mode.
     * 
     * @param ctx
     *            encapsulates all data needed to evaluate the check
     * @return true, if successful
     */
    public boolean check(final FastBreakContext ctx) {
        boolean cancel = false;
        if (isValidContext(ctx)) {
            cancel = performCheck(ctx);
        }
        return cancel;
    }

    private boolean performCheck(final FastBreakContext ctx) {
        final Player player = ctx.player();
        final Block block = ctx.block();
        final BlockBreakConfig cc = ctx.config();
        final BlockBreakData data = ctx.breakData();
        final IPlayerData pData = ctx.playerData();
        final AlmostBoolean isInstaBreak = ctx.isInstaBreak();

        final long now = Monotonic.millis();
        boolean cancel = false;

        if (player != null && block != null && pData != null) {

            final Material blockType = block.getType();
            final long expectedBreakingTime = calculateExpectedBreakingTime(blockType, player, cc);
            final long elapsedTime = calculateElapsedTime(cc, data, now);

            final long adjustedElapsed = FastBreakDecision.adjustedElapsed(elapsedTime, isInstaBreak, cc.fastBreakDelay);

            if (!FastBreakDecision.shouldSkip(isInstaBreak) && adjustedElapsed >= 0) {
                cancel = processElapsedTime(ctx, blockType, now, expectedBreakingTime, adjustedElapsed);
            }

            if (!cancel && expectedBreakingTime > cc.fastBreakDelay) {
                data.fastBreakVL *= 0.9D;
            }

            if (pData.isDebugActive(type)) {
                detailDebugStats(ctx, elapsedTime, expectedBreakingTime);
            } else {
                data.stats = null;
            }
        }
        return cancel;
    }

    private long calculateExpectedBreakingTime(final Material blockType, final Player player,
                                               final BlockBreakConfig cc) {
        return Math.max(0, Math.round((double) BlockProperties.getBreakingDuration(blockType, player)
                * (double) cc.fastBreakModSurvival / 100D));
    }

    private long calculateElapsedTime(final BlockBreakConfig cc, final BlockBreakData data, final long now) {
        if (cc.fastBreakStrict) {
            return (data.fastBreakBreakTime > data.fastBreakfirstDamage) ? 0 : now - data.fastBreakfirstDamage;
        }
        return (data.fastBreakBreakTime > now) ? 0 : now - data.fastBreakBreakTime;
    }

    private boolean processElapsedTime(final FastBreakContext ctx, final Material blockType, final long now,
                                       final long expectedBreakingTime, final long adjustedElapsed) {
        final BlockBreakData data = ctx.breakData();
        final BlockBreakConfig cc = ctx.config();
        final IPlayerData pData = ctx.playerData();
        final Player player = ctx.player();

        boolean cancel = false;

        if (adjustedElapsed + cc.fastBreakDelay < expectedBreakingTime) {
            final float lag = pData.getCurrentWorldDataSafe().shouldAdjustToLag(type)
                    ? TickTask.getLag(expectedBreakingTime, true) : 1f;

            final long missingTime = expectedBreakingTime - (long) (lag * adjustedElapsed);

            if (missingTime > 0) {
                data.fastBreakPenalties.add(now, (float) missingTime);

                if (data.fastBreakPenalties.score(cc.fastBreakBucketFactor) > cc.fastBreakGrace) {
                    final double vlAdded = (double) missingTime / 1000.0;
                    data.fastBreakVL += vlAdded;
                    final ViolationData vd = new ViolationData(this, player, data.fastBreakVL,
                            vlAdded, cc.fastBreakActions);
                    if (vd.needsParameters()) {
                        vd.setParameter(ParameterName.BLOCK_TYPE, blockType.toString());
                    }
                    cancel = executeActions(vd).willCancel();
                }
            }
        }
        return cancel;
    }

    private void detailDebugStats(final FastBreakContext ctx, final long elapsedTime,
            final long expectedBreakingTime) {
        final Player player = ctx.player();
        final Block block = ctx.block();
        final BlockBreakData data = ctx.breakData();
        final IPlayerData pData = ctx.playerData();
        final AlmostBoolean isInstaBreak = ctx.isInstaBreak();
        final Material blockType = block.getType();
        if (pData.hasPermission(Permissions.ADMINISTRATION_DEBUG, player)) {
            // General stats:
            // Replace stats by new system (BlockBreakKey once complete), commands to inspect / auto-config.
            data.setStats();
            data.stats.addStats(data.stats.getId(blockType+ "/u", true), elapsedTime);
            data.stats.addStats(data.stats.getId(blockType + "/r", true), expectedBreakingTime);
            player.sendMessage(data.stats.getStatsStr(true));
            // Send info about current break:
            final ItemStack stack = Bridge1_9.getItemInMainHand(player);
            final boolean isValidTool = BlockProperties.isValidTool(blockType, stack);
            final double haste = PotionUtil.getPotionEffectAmplifier(player, BridgePotionEffect.HASTE);
            String msg = (isInstaBreak.decideOptimistically() ? ("[Insta=" + isInstaBreak + "]") : "[Normal]") + "[" + blockType + "] "+ elapsedTime + "u / " + expectedBreakingTime +"r (" + (isValidTool?"tool":"no-tool") + ")" + (Double.isInfinite(haste) ? "" : " haste=" + ((int) haste + 1));
            player.sendMessage(msg);
            //          net.minecraft.server.Item mcItem = net.minecraft.server.Item.byId[stack.getTypeId()];
            //          if (mcItem != null) {
            //              double x = mcItem.getDestroySpeed(((CraftItemStack) stack).getHandle(), net.minecraft.server.Block.byId[blockId]);
            //              player.sendMessage("mc speed: " + x);
            //          }
        }
    }

    private boolean isValidContext(final FastBreakContext ctx) {
        return ctx != null && ctx.player() != null && ctx.block() != null && ctx.playerData() != null;
    }
}
