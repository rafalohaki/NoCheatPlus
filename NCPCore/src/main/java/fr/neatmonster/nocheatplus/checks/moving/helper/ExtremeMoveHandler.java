package fr.neatmonster.nocheatplus.checks.moving.helper;

import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.moving.helper.MoveCheckContext;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.checks.moving.magic.Magic;
import fr.neatmonster.nocheatplus.checks.moving.model.PlayerMoveData;
import fr.neatmonster.nocheatplus.compat.Bridge1_13;
import fr.neatmonster.nocheatplus.compat.Bridge1_9;
import fr.neatmonster.nocheatplus.actions.ActionList;
import fr.neatmonster.nocheatplus.actions.ParameterName;
import fr.neatmonster.nocheatplus.checks.ViolationData;
import fr.neatmonster.nocheatplus.utilities.location.PlayerLocation;
import fr.neatmonster.nocheatplus.utilities.location.TrigUtil;

/**
 * Utility for handling extreme player moves.
 */
public final class ExtremeMoveHandler {

    private ExtremeMoveHandler() {}

    /**
     * Handle an extreme move.
     *
     * @param context shared movement context
     * @param from server side from location
     * @param to server side to location
     * @param cc moving configuration
     * @param survivalFly SurvivalFly check instance
     * @param creativeFly CreativeFly check instance
     * @return enforced location or {@code null}
     */
    public static Location handleExtremeMove(MoveCheckContext context, PlayerLocation from,
            PlayerLocation to, MovingConfig cc, Check survivalFly, Check creativeFly) {
        if (context == null || context.player() == null || context.data() == null
                || context.thisMove() == null || context.lastMove() == null
                || from == null || to == null) {
            return null;
        }
        final Player player = context.player();
        final MovingData data = context.data();
        final PlayerMoveData thisMove = context.thisMove();
        final PlayerMoveData lastMove = context.lastMove();

        final boolean riptideBounce = Bridge1_13.isRiptiding(player) && data.verticalBounce != null
                && thisMove.yDistance < 8.0 && thisMove.yDistance > Magic.EXTREME_MOVE_DIST_HORIZONTAL;
        final boolean ripglide = Bridge1_9.isGlidingWithElytra(player)
                && Bridge1_13.isRiptiding(player)
                && thisMove.yDistance > Magic.EXTREME_MOVE_DIST_VERTICAL * 1.7;
        final boolean levitationHighLevel = !Double.isInfinite(Bridge1_9.getLevitationAmplifier(player))
                && Bridge1_9.getLevitationAmplifier(player) >= 89
                && Bridge1_9.getLevitationAmplifier(player) <= 127;

        double violation = 0.0;
        violation += computeVerticalViolation(player, thisMove, lastMove, data,
                riptideBounce, ripglide, levitationHighLevel);
        violation += computeHorizontalViolation(thisMove, lastMove, data);

        if (violation <= 0.0) {
            return null;
        }

        if (!data.hasSetBack()) {
            data.setSetBack(from);
        }
        violation *= 100.0;
        final Check check;
        final ActionList actions;
        final double vL;
        if (thisMove.flyCheck == CheckType.MOVING_SURVIVALFLY) {
            check = survivalFly;
            actions = cc.survivalFlyActions;
            data.survivalFlyVL += violation;
            vL = data.survivalFlyVL;
        } else {
            check = creativeFly;
            actions = cc.creativeFlyActions;
            data.creativeFlyVL += violation;
            vL = data.creativeFlyVL;
        }
        final ViolationData vd = new ViolationData(check, player, vL, violation, actions);
        if (vd.needsParameters()) {
            vd.setParameter(ParameterName.LOCATION_FROM,
                    String.format(Locale.US, "%.2f, %.2f, %.2f", from.getX(), from.getY(), from.getZ()));
            vd.setParameter(ParameterName.LOCATION_TO,
                    String.format(Locale.US, "%.2f, %.2f, %.2f", to.getX(), to.getY(), to.getZ()));
            vd.setParameter(ParameterName.DISTANCE,
                    String.format(Locale.US, "%.2f", TrigUtil.distance(from, to)));
            vd.setParameter(ParameterName.TAGS, "EXTREME_MOVE");
        }
        if (check.executeActions(vd).willCancel()) {
            return MovingUtil.getApplicableSetBackLocation(player, to.getYaw(), to.getPitch(), from, data, cc);
        }
        return null;
    }

    private static double computeVerticalViolation(Player player, PlayerMoveData thisMove,
            PlayerMoveData lastMove, MovingData data, boolean riptideBounce,
            boolean ripglide, boolean levitationHighLevel) {
        final boolean allowVerticalVelocity = false;
        if (Math.abs(thisMove.yDistance) <= Magic.EXTREME_MOVE_DIST_VERTICAL
                * (Bridge1_13.isRiptiding(player) ? 1.7 : 1.0)) {
            return 0.0;
        }
        final boolean decrease = lastMove.toIsValid && Math.abs(thisMove.yDistance) < Math.abs(lastMove.yDistance)
                && ((thisMove.yDistance > 0.0 && lastMove.yDistance > 0.0)
                    || (thisMove.yDistance < 0.0 && lastMove.yDistance < 0.0));
        final boolean velocity = allowVerticalVelocity && data.getOrUseVerticalVelocity(thisMove.yDistance) != null;
        if (decrease || velocity || riptideBounce || ripglide || levitationHighLevel) {
            return 0.0;
        }
        return thisMove.yDistance;
    }

    private static double computeHorizontalViolation(PlayerMoveData thisMove, PlayerMoveData lastMove,
            MovingData data) {
        if (thisMove.hDistance <= Magic.EXTREME_MOVE_DIST_HORIZONTAL) {
            return 0.0;
        }
        final double amount = thisMove.hDistance - data.getHorizontalFreedom();
        final boolean decrease = lastMove.toIsValid && thisMove.hDistance - lastMove.hDistance <= 0.0;
        if (amount < 0.0 || decrease || data.useHorizontalVelocity(amount) >= amount) {
            return 0.0;
        }
        return thisMove.hDistance;
    }
}

