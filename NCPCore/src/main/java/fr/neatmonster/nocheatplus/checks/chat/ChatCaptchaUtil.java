package fr.neatmonster.nocheatplus.checks.chat;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.worlds.IWorldData;

/**
 * Utility methods for chat captcha related checks.
 */
public final class ChatCaptchaUtil {

    private ChatCaptchaUtil() {
    }

    /**
     * Determine if the captcha check is active for both the player's world and
     * player data.
     *
     * @param player the player
     * @param pData  the player data
     * @return {@code true} if the check is active for both world and player
     */
    public static boolean isCaptchaEnabled(Player player, IPlayerData pData) {
        if (player == null || pData == null) {
            return false;
        }
        final IWorldData worldData = pData.getCurrentWorldDataSafe();
        return worldData != null
                && worldData.isCheckActive(CheckType.CHAT_CAPTCHA)
                && pData.isCheckActive(CheckType.CHAT_CAPTCHA, player, worldData);
    }
}
