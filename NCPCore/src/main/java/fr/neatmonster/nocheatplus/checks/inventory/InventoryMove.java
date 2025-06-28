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
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryType.SlotType;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.checks.combined.Improbable;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.compat.versions.ServerVersion;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.InventoryUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.collision.CollisionUtil;


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
        final List<String> tags = new LinkedList<String>();

        final MoveContext context = createContext(player, data, pData, cc, type);
        if (context != null) {
            if (pData.isDebugActive(CheckType.INVENTORY_INVENTORYMOVE)) {
                sendDebugMessage(player, cc, context);
            }

            final boolean[] cancelHolder = new boolean[1];

            violation = determineViolation(player, data, cc, pData, context.mData, context.thisMove, context.lastMove,
                    context.pastMove3, context.minHDistance, context.currentEvent, context.isCollidingWithEntities,
                    context.fullLiquidMove, context.movingOnSurface, context.isMerchant, tags, cancelHolder);

            cancel = cancelHolder[0];

            if (violation && !context.creative) {
                data.invMoveVL += 1D;
                final ViolationData vd = new ViolationData(this, player, data.invMoveVL, 1D, cc.invMoveActionList);
                if (vd.needsParameters()) {
                    vd.setParameter(ParameterName.TAGS, StringUtil.join(tags, "+"));
                }
                cancel = executeActions(vd).willCancel();
            } else {
                data.invMoveVL *= 0.96D;
            }
        }
        return cancel;
    }

    private static final class MoveContext {
        MovingData mData;
        PlayerMoveData thisMove;
        PlayerMoveData lastMove;
        PlayerMoveData pastMove3;
        boolean fullLiquidMove;
        long currentEvent;
        boolean isCollidingWithEntities;
        double minHDistance;
        boolean creative;
        boolean isMerchant;
        boolean movingOnSurface;
    }

    private MoveContext createContext(final Player player, final InventoryData data, final IPlayerData pData,
            final InventoryConfig cc, final SlotType type) {
        if (player == null || data == null || pData == null || cc == null) {
            return null;
        }

        final MoveContext context = new MoveContext();
        context.mData = pData.getGenericInstance(MovingData.class);
        context.thisMove = context.mData.playerMoves.getCurrentMove();
        context.lastMove = context.mData.playerMoves.getFirstPastMove();
        context.pastMove3 = context.mData.playerMoves.getThirdPastMove();
        context.fullLiquidMove = context.thisMove.from.inLiquid && context.thisMove.to.inLiquid;
        context.currentEvent = System.currentTimeMillis();
        context.isCollidingWithEntities = CollisionUtil.isCollidingWithEntities(player, true)
                && ServerVersion.compareMinecraftVersion("1.9") >= 0;
        context.minHDistance = context.thisMove.hAllowedDistanceBase / Math.max(1.1, cc.invMoveHdistDivisor);
        context.creative = player.getGameMode() == GameMode.CREATIVE
                && ((type == SlotType.QUICKBAR) || cc.invMoveDisableCreative);
        context.isMerchant = player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory() != null
                && player.getOpenInventory().getTopInventory().getType() == InventoryType.MERCHANT;
        context.movingOnSurface = (context.thisMove.from.inLiquid && !context.thisMove.to.inLiquid
                || context.mData.surfaceId == 1) && context.mData.liftOffEnvelope.name().startsWith("LIMIT");

        return context;
    }

    private void sendDebugMessage(final Player player, final InventoryConfig cc, final MoveContext context) {
        if (player == null || cc == null || context == null) {
            return;
        }
        player.sendMessage("\nyDistance= " + StringUtil.fdec3.format(context.thisMove.yDistance)
                + "\nhDistance= " + StringUtil.fdec3.format(context.thisMove.hDistance)
                + "\nhDistMin(" + cc.invMoveHdistDivisor + ")=" + StringUtil.fdec3.format(context.minHDistance)
                + "\nhAllowedDistance= " + StringUtil.fdec3.format(context.thisMove.hAllowedDistance)
                + "\nhAllowedDistanceBase= " + StringUtil.fdec3.format(context.thisMove.hAllowedDistanceBase)
                + "\ntouchedGround= " + context.thisMove.touchedGround + "("
                + (context.thisMove.from.onGround ? "ground -> " : "---- -> ")
                + (context.thisMove.to.onGround ? "ground" : "----") + ")"
                + "\nmovingOnSurface=" + context.movingOnSurface + " fullLiquidMove= " + context.fullLiquidMove);
    }

    private boolean determineViolation(final Player player, final InventoryData data, final InventoryConfig cc,
            final IPlayerData pData, final MovingData mData, final PlayerMoveData thisMove,
            final PlayerMoveData lastMove, final PlayerMoveData pastMove3, final double minHDistance,
            final long currentEvent, final boolean isCollidingWithEntities, final boolean fullLiquidMove,
            final boolean movingOnSurface, final boolean isMerchant, final List<String> tags,
            final boolean[] cancelHolder) {

        if (handleUsingItem(mData, isMerchant, tags)) {
            return true;
        }
        if (handleSwimming(player, thisMove, tags)) {
            return true;
        }
        if (handleDeadOrSleeping(player, tags)) {
            return true;
        }
        if (handleSprinting(player, fullLiquidMove, movingOnSurface, cc, pData, tags, cancelHolder)) {
            return true;
        }
        if (handleSneaking(player, tags)) {
            return true;
        }
        if (handleActiveMoving(player, data, mData, thisMove, lastMove, pastMove3, minHDistance,
                currentEvent, isCollidingWithEntities, fullLiquidMove, movingOnSurface, tags)) {
            return true;
        }
        return false;
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
