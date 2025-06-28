package fr.neatmonster.nocheatplus.checks.chat.util;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.worlds.IWorldData;

/**
 * Utility methods for chat captcha related checks.
 */
public final class ChatCaptchaUtil {

    private ChatCaptchaUtil() {}

    /**
     * Determine if chat captcha should be active for the given player.
     * <p>
     * Both the world data of the player's current world and the player data
     * must indicate that {@link CheckType#CHAT_CAPTCHA} is active.
     *
     * @param player the player, may be {@code null}
     * @param pData  player data, may be {@code null}
     * @return {@code true} if captcha checks should run
     */
    public static boolean isCaptchaEnabled(final Player player, final IPlayerData pData) {
        if (player == null || pData == null) {
            return false;
        }
        final IWorldData worldData = pData.getCurrentWorldDataSafe();
        return worldData != null
                && worldData.isCheckActive(CheckType.CHAT_CAPTCHA)
                && pData.isCheckActive(CheckType.CHAT_CAPTCHA, player, worldData);
    }
}
