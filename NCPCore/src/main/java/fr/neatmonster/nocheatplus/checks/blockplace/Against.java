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
package fr.neatmonster.nocheatplus.checks.blockplace;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.blockinteract.BlockInteractData;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeMaterial;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.utilities.map.MaterialUtil;


/**
 * Check if the placing is legitimate in terms of surrounding materials.
 * @author asofold
 *
 */
public class Against extends Check {


   /**
    * Instanties a new Against check.
    *
    */
    public Against() {
        super(CheckType.BLOCKPLACE_AGAINST);
    }


    /**
     * Checks a player
     * @param player
     * @param block
     * @param placedMat 
     *               The material in hand that has been placed.
     * @param blockAgainst 
     * @param isInteractBlock
     * @param data
     * @param cc
     * @param pData
     *
     */
    public boolean check(final Player player, final Block block, final Material placedMat,
                         final Block blockAgainst, final boolean isInteractBlock,
                         final BlockPlaceData data, final BlockPlaceConfig cc, final IPlayerData pData) {

        final BlockInteractData bIData = pData.getGenericInstance(BlockInteractData.class); // Should eventually be passed as argument.
        // Do not use this to check for cheating: Bukkit will return the placed material if the placement is not possible.
        final Material bukkitAgainst = blockAgainst.getType();
        final Material ncpAgainst = bIData.getLastType();

        if (pData.isDebugActive(this.type)) {
            debug(player, "Placed " + placedMat + " against: " + bukkitAgainst + " (bukkit) / "
                    + (ncpAgainst == null ? "null" : ncpAgainst.toString()) + " (nc+)");
        }

        boolean violation = false;
        if (handleConsumedCheck(player, bIData, pData)) {
            violation = true;
        } else if (handleInteractBlock(player, isInteractBlock, ncpAgainst, pData)) {
            // allowed interact block case
        } else if (handleAirCase(player, ncpAgainst, bukkitAgainst, placedMat, pData)) {
            violation = true;
        } else if (handleLiquidCase(player, ncpAgainst, block, placedMat, pData)) {
            violation = true;
        }

        bIData.addConsumedCheck(this.type);
        if (violation) {
            data.againstVL += 1.0;
            final ViolationData vd = new ViolationData(this, player, data.againstVL, 1, cc.againstActions);
            vd.setParameter(ParameterName.BLOCK_TYPE, ncpAgainst == null ? "air" : ncpAgainst.toString());
            return executeActions(vd).willCancel();
        }
        data.againstVL *= 0.99; // Assume one false positive every 100 blocks.
        bIData.addPassedCheck(this.type);
        return false;
    }

    private boolean handleConsumedCheck(final Player player, final BlockInteractData bIData,
            final IPlayerData pData) {
        if (player == null || bIData == null || pData == null) {
            return false;
        }
        if (pData.hasBypass(this.type, player) || pData.isExempted(this.type)) {
            return false;
        }
        if (bIData.isConsumedCheck(this.type) && !bIData.isPassedCheck(this.type)) {
            if (pData.isDebugActive(this.type)) {
                debug(player, "Cancel due to block having been consumed by this check.");
            }
            return true;
        }
        return false;
    }

    private boolean handleInteractBlock(final Player player, final boolean isInteractBlock,
            final Material ncpAgainst, final IPlayerData pData) {
        if (player == null || pData == null) {
            return false;
        }
        if (isInteractBlock && !BlockProperties.isAir(ncpAgainst) && !BlockProperties.isLiquid(ncpAgainst)) {
            if (pData.isDebugActive(this.type)) {
                debug(player, "Block was placed against something, allow it.");
            }
            return true;
        }
        return false;
    }

    private boolean handleAirCase(final Player player, final Material ncpAgainst, final Material bukkitAgainst,
            final Material placedMat, final IPlayerData pData) {
        if (player == null || pData == null) {
            return false;
        }
        if (!BlockProperties.isAir(ncpAgainst)) {
            return false;
        }
        if (MaterialUtil.isFarmable(placedMat) && MaterialUtil.isFarmable(bukkitAgainst)) {
            if (pData.isDebugActive(this.type)) {
                debug(player,
                        "Ignore player attempting to place a seed/plant on a crop/plant (assume desync due to fast-farming).");
            }
            return false;
        }
        return !pData.hasPermission(Permissions.BLOCKPLACE_AGAINST_AIR, player)
                && placedMat != BridgeMaterial.LILY_PAD && placedMat != BridgeMaterial.FROGSPAWN
                && !pData.hasBypass(this.type, player) && !pData.isExempted(this.type);
    }

    private boolean handleLiquidCase(final Player player, final Material ncpAgainst, final Block block,
            final Material placedMat, final IPlayerData pData) {
        if (player == null || pData == null || block == null) {
            return false;
        }
        if (!BlockProperties.isLiquid(ncpAgainst)) {
            return false;
        }
        if (pData.hasPermission(Permissions.BLOCKPLACE_AGAINST_LIQUIDS, player)
                || pData.hasBypass(this.type, player) || pData.isExempted(this.type)) {
            return false;
        }
        return ((placedMat != BridgeMaterial.LILY_PAD || placedMat != BridgeMaterial.FROGSPAWN)
                || !BlockProperties.isLiquid(block.getRelative(BlockFace.DOWN).getType()))
                && !BlockProperties.isWaterPlant(ncpAgainst);
    }
}
