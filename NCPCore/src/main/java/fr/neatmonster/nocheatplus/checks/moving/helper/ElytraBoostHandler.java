package fr.neatmonster.nocheatplus.checks.moving.helper;

import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * Utility class for handling Elytra firework boosts.
 */
public final class ElytraBoostHandler {

    private ElytraBoostHandler() {
    }

    /**
     * Process a potential Elytra boost using a firework.
     *
     * @param context shared context holding player and data
     * @param stack the used item
     * @param tick current server tick
     * @return {@code true} if a boost has been applied
     */
    public static boolean handleBoost(CheckContext context, ItemStack stack, int tick) {
        if (context == null || context.player() == null || context.data() == null) {
            return false;
        }
        if (stack == null || !BridgeMisc.maybeElytraBoost(context.player(), stack.getType())) {
            return false;
        }
        final int power = BridgeMisc.getFireworksPower(stack);
        final MovingData mData = context.data();
        final int ticks = Math.max((1 + power) * 20, 30);
        mData.fireworksBoostDuration = ticks;
        mData.fireworksBoostTickNeedCheck = ticks - 1;
        mData.fireworksBoostTickExpire = tick + ticks;
        return true;
    }
}
