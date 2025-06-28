package fr.neatmonster.nocheatplus.checks.moving.helper;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.moving.MovingData;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.utilities.TickTask;

/**
 * Utility class for handling Elytra firework boosts.
 */
public final class ElytraBoostHandler {

    private ElytraBoostHandler() {
    }

    public static void logBoostEvent(Player player, String event, int tick, int duration) {
        if (player == null) {
            return;
        }
        if (ConfigManager.getConfigFile().getBoolean(ConfPaths.LOGGING_EXTENDED_ELYTRABOOST)) {
            NCPAPIProvider.getNoCheatPlusAPI().getLogManager().info(Streams.STATUS,
                    "Elytra boost " + event + " for " + player.getName()
                            + " at tick " + tick + " duration " + duration);
        }
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
        mData.hasFireworkBoost = true;
        logBoostEvent(context.player(), "started", tick, ticks);
        return true;
    }
}
