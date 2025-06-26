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
package fr.neatmonster.nocheatplus.checks.inventory;

import java.util.LinkedList;
import java.util.List;

import org.bukkit.GameMode;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveInfo;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.collision.CollisionUtil;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.BridgeEnchant;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.InventoryUtil;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;


/**
 * InventoryMove listens for clicks in inventory happening at the same time of certain actions.
 * (No packet is sent for players opening their inventory)
 */
public class InventoryMove extends Check {


   /**
    * Instantiates a new InventoryMove check
    *
    */
    public InventoryMove() {
        super(CheckType.INVENTORY_INVENTORYMOVE);
    }
    

   /**
    * Checks a player
    * @param player
    * @param data
    * @param pData
    * @param cc
    * @param type
    * @return true if successful
    *
    */
    public boolean check(final Player player, final InventoryData data, final IPlayerData pData, final InventoryConfig cc, final SlotType type) {

        boolean cancel = false;
        boolean violation = false;
        List<String> tags = new LinkedList<String>();

        if (player != null && data != null && pData != null && cc != null) {
            // NOTES: 1) NoCheatPlus provides a base speed at which players can move without taking into account any mechanic:
            //        the idea is that if the base speed does not equal to the finally allowed speed then the player is being moved by friction or other means.
            //        2) Important: MC allows players to swim (and keep the status) when on ground, but this is not *consistently* reflected back to the server
            //        (while still allowing them to move at swimming speed) instead, isSprinting() will return. Observed in both Spigot and PaperMC around MC 1.13/14
            //        -> Seems fixed in latest versions (opening an inventory will end the swimming phase, if on ground)
            // Shortcuts:
            final MovingData mData = pData.getGenericInstance(MovingData.class);
            final PlayerMoveData thisMove = mData.playerMoves.getCurrentMove();
            final PlayerMoveData lastMove = mData.playerMoves.getFirstPastMove();
            final PlayerMoveData pastMove3 = mData.playerMoves.getThirdPastMove();
            final boolean fullLiquidMove = thisMove.from.inLiquid && thisMove.to.inLiquid;
            final long currentEvent = System.currentTimeMillis();
            final boolean isCollidingWithEntities = CollisionUtil.isCollidingWithEntities(player, true) && ServerVersion.compareMinecraftVersion("1.9") >= 0;
            final double minHDistance = thisMove.hAllowedDistanceBase / Math.max(1.1, cc.invMoveHdistDivisor); // Just in case admins input a too low value.
            final boolean creative = player.getGameMode() == GameMode.CREATIVE && ((type == SlotType.QUICKBAR) || cc.invMoveDisableCreative);
            final boolean isMerchant = player.getOpenInventory() != null
                    && player.getOpenInventory().getTopInventory() != null
                    && player.getOpenInventory().getTopInventory().getType() == InventoryType.MERCHANT;
            final boolean movingOnSurface = (thisMove.from.inLiquid && !thisMove.to.inLiquid || mData.surfaceId == 1) && mData.liftOffEnvelope.name().startsWith("LIMIT");

            // Debug first.
            if (pData.isDebugActive(CheckType.INVENTORY_INVENTORYMOVE)) {
                player.sendMessage("\nyDistance= " + StringUtil.fdec3.format(thisMove.yDistance)
                    + "\nhDistance= " + StringUtil.fdec3.format(thisMove.hDistance)
                    + "\nhDistMin(" + cc.invMoveHdistDivisor + ")=" + StringUtil.fdec3.format(minHDistance)
                    + "\nhAllowedDistance= " + StringUtil.fdec3.format(thisMove.hAllowedDistance)
                    + "\nhAllowedDistanceBase= " + StringUtil.fdec3.format(thisMove.hAllowedDistanceBase)
                    + "\ntouchedGround= " + thisMove.touchedGround + "(" + (thisMove.from.onGround ? "ground -> " : "---- -> ") + (thisMove.to.onGround ? "ground" : "----") + ")"
                    + "\nmovingOnSurface=" + movingOnSurface + " fullLiquidMove= " + fullLiquidMove
                );
            }

    
            final boolean[] cancelHolder = new boolean[1];

            if (handleUsingItem(mData, isMerchant, tags)) {
                violation = true;
            } else if (handleSwimming(player, thisMove, tags)) {
                violation = true;
            } else if (handleDeadOrSleeping(player, tags)) {
                violation = true;
            } else if (handleSprinting(player, fullLiquidMove, movingOnSurface, cc, pData, tags, cancelHolder)) {
                violation = true;
            } else if (handleSneaking(player, tags)) {
                violation = true;
            } else if (handleActiveMoving(player, data, mData, thisMove, lastMove, pastMove3,
                    minHDistance, currentEvent, isCollidingWithEntities, fullLiquidMove, movingOnSurface, tags)) {
                violation = true;
            }

            cancel = cancelHolder[0];
    
        // Handle violations 
        if (violation && !creative) {
            data.invMoveVL += 1D;
            final ViolationData vd = new ViolationData(this, player, data.invMoveVL, 1D, cc.invMoveActionList);
            if (vd.needsParameters()) vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
            cancel = executeActions(vd).willCancel();
        }
        // Cooldown
        else {
            data.invMoveVL *= 0.96D;
        }
        }
        return cancel;
    }

    private boolean handleUsingItem(final MovingData mData, final boolean isMerchant, final List<String> tags) {
        if (mData != null && mData.isUsingItem && !isMerchant) {
            tags.add("usingitem");
            return true;
        }
        return false;
    }

    private boolean handleSwimming(final Player player, final PlayerMoveData thisMove, final List<String> tags) {
        if (player != null && Bridge1_13.isSwimming(player) && thisMove != null && !thisMove.touchedGround) {
            tags.add("isSwimming");
            return true;
        }
        return false;
    }

    private boolean handleDeadOrSleeping(final Player player, final List<String> tags) {
        if (player != null && (player.isDead() || player.isSleeping())) {
            tags.add(player.isDead() ? "isDead" : "isSleeping");
            return true;
        }
        return false;
    }

    private boolean handleSprinting(final Player player, final boolean fullLiquidMove,
            final boolean movingOnSurface, final InventoryConfig cc, final IPlayerData pData,
            final List<String> tags, final boolean[] cancelHolder) {
        if (player != null && player.isSprinting() && !player.isFlying() && !(fullLiquidMove || movingOnSurface)) {
            if (cc.invMoveImprobableWeight > 0.0f) {
                if (cc.invMoveImprobableFeedOnly) {
                    Improbable.feed(player, cc.invMoveImprobableWeight, System.currentTimeMillis());
                } else if (Improbable.check(player, cc.invMoveImprobableWeight, System.currentTimeMillis(),
                        "inventory.invmove.sprinting", pData)) {
                    cancelHolder[0] = true;
                }
            }
            tags.add("isSprinting");
            return true;
        }
        return false;
    }

    private boolean handleSneaking(final Player player, final List<String> tags) {
        if (player != null && player.isSneaking() && !Bridge1_13.hasIsSwimming()) {
            tags.add("isSneaking(<1.13)");
            return true;
        }
        return false;
    }

    private boolean handleActiveMoving(final Player player, final InventoryData data, final MovingData mData,
            final PlayerMoveData thisMove, final PlayerMoveData lastMove, final PlayerMoveData pastMove3,
            final double minHDistance, final long currentEvent, final boolean isCollidingWithEntities,
            final boolean fullLiquidMove, final boolean movingOnSurface, final List<String> tags) {
        if (thisMove != null && thisMove.hDistance > minHDistance && ((currentEvent - data.lastMoveEvent) < 65)
                && !mData.isVelocityJumpPhase() && !isCollidingWithEntities && player != null
                && !player.isInsideVehicle() && !thisMove.downStream && !Bridge1_13.isRiptiding(player)
                && !Bridge1_9.isGlidingWithElytra(player) && !InventoryUtil.hasOpenedInvRecently(player, 1500)) {
            tags.add("moving");
            if (thisMove.touchedGround && !fullLiquidMove
                    && thisMove.hAllowedDistanceBase == lastMove.hAllowedDistance) {
                return true;
            } else if (movingOnSurface && !thisMove.touchedGround
                    && thisMove.hAllowedDistance == pastMove3.hAllowedDistanceBase
                    && Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))
                    && player.getNoDamageTicks() == 0) {
                return true;
            } else if (fullLiquidMove && thisMove.hAllowedDistance == pastMove3.hAllowedDistanceBase
                    && Double.isInfinite(Bridge1_13.getDolphinGraceAmplifier(player))
                    && player.getNoDamageTicks() == 0) {
                return true;
            }
        }
        return false;
    }
}
