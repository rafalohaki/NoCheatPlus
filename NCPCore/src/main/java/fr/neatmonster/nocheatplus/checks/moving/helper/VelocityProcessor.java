package fr.neatmonster.nocheatplus.checks.moving.helper;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.map.BlockProperties;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.model.LiftOffEnvelope;
import fr.neatmonster.nocheatplus.checks.moving.velocity.AccountEntry;
import fr.neatmonster.nocheatplus.checks.moving.velocity.SimpleEntry;

/**
 * Handle extra velocity processing such as explosion and bubble column launch.
 */
public final class VelocityProcessor {

    private VelocityProcessor() {}

    /**
     * Recalculate explosion velocity and apply bubble column launch effects.
     */
    public static void handleVelocity(Player player, PlayerMoveData thisMove,
            PlayerMoveData lastMove, MovingData data, MovingConfig cc, int tick,
            boolean checkSf, boolean debug, PlayerLocation pFrom) {
        if (data.shouldApplyExplosionVelocity) {
            applyExplosionVelocity(player, lastMove, data, cc, tick, debug);
        }
        if (checkSf && data.liftOffEnvelope == LiftOffEnvelope.LIMIT_LIQUID && !lastMove.headObstructed
                && !pFrom.isDraggedByBubbleStream()) {
            applyBubbleColumnLaunch(player, thisMove, lastMove, data, tick, pFrom);
        }
    }

    private static void applyExplosionVelocity(Player player, PlayerMoveData lastMove, MovingData data,
            MovingConfig cc, int tick, boolean debug) {
        data.shouldApplyExplosionVelocity = false;
        double xLastDistance = 0.0;
        double zLastDistance = 0.0;
        double yLastDistance = 0.0;
        if (lastMove.toIsValid) {
            xLastDistance = lastMove.to.getX() - lastMove.from.getX();
            zLastDistance = lastMove.to.getZ() - lastMove.from.getZ();
            yLastDistance = lastMove.to.onGround ? 0 : lastMove.yDistance;
        }
        boolean addHorizontalVelocity = true;
        double xDistance2 = data.explosionVelAxisX + xLastDistance;
        double zDistance2 = data.explosionVelAxisZ + zLastDistance;
        double hDistance = Math.sqrt(xDistance2 * xDistance2 + zDistance2 * zDistance2);
        if (data.hasActiveHorVel() && data.getHorizontalFreedom() < hDistance
                || data.hasQueuedHorVel() && data.useHorizontalVelocity(hDistance) < hDistance
                || !data.hasAnyHorVel()) {
            data.getHorizontalVelocityTracker().clear();
            if (debug) {
                // Debug method may be null when disabled.
            }
        } else {
            addHorizontalVelocity = false;
        }
        if (addHorizontalVelocity) {
            data.addVelocity(player, cc, xDistance2,
                    data.explosionVelAxisY + yLastDistance - Magic.GRAVITY_ODD, zDistance2);
        } else {
            data.addVerticalVelocity(new SimpleEntry(data.explosionVelAxisY + yLastDistance - Magic.GRAVITY_ODD,
                    cc.velocityActivationCounter));
        }
        data.explosionVelAxisX = 0.0;
        data.explosionVelAxisY = 0.0;
        data.explosionVelAxisZ = 0.0;
    }

    private static void applyBubbleColumnLaunch(Player player, PlayerMoveData thisMove, PlayerMoveData lastMove,
            MovingData data, int tick, PlayerLocation pFrom) {
        if (!thisMove.from.inBubbleStream && lastMove.from.inBubbleStream
                && !data.hasQueuedVerVel() && data.insideBubbleStreamCount > 1 && thisMove.yDistance > 0.0
                && lastMove.yDistance > 0.0) {
            double velocity = (0.05 * data.insideBubbleStreamCount) * data.lastFrictionVertical;
            data.addVerticalVelocity(new SimpleEntry(tick, velocity, data.insideBubbleStreamCount));
            data.setFrictionJumpPhase();
        }
        if (!data.hasQueuedHorVel() && thisMove.yDistance != 0.0 && thisMove.hDistance > 0.1
                && thisMove.hDistance < thisMove.walkSpeed && thisMove.from.inBubbleStream
                && BlockProperties.isAir(pFrom.getTypeIdAbove()) && data.insideBubbleStreamCount < 7
                && !Magic.inAir(thisMove)) {
            data.addHorizontalVelocity(new AccountEntry(0.5, 0, MovingData.getHorVelValCount(0.5)));
            data.addHorizontalVelocity(new AccountEntry(0.7, 1, MovingData.getHorVelValCount(0.7)));
        }
    }
}
