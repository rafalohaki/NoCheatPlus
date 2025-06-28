package fr.neatmonster.nocheatplus.checks.chat.util;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.worlds.IWorldData;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatCaptchaUtilTest {

    @Mock
    private Player player;
    @Mock
    private IPlayerData playerData;
    @Mock
    private IWorldData worldData;

    @Test
    void returnsFalse_whenPlayerIsNull() {
        assertFalse(ChatCaptchaUtil.isCaptchaEnabled(null, playerData));
    }

    @Test
    void returnsFalse_whenPlayerDataIsNull() {
        assertFalse(ChatCaptchaUtil.isCaptchaEnabled(player, null));
    }

    @Test
    void returnsFalse_whenWorldDataIsNull() {
        when(playerData.getCurrentWorldDataSafe()).thenReturn(null);
        assertFalse(ChatCaptchaUtil.isCaptchaEnabled(player, playerData));
    }

    @Test
    void returnsFalse_whenWorldDisablesCaptcha() {
        when(playerData.getCurrentWorldDataSafe()).thenReturn(worldData);
        when(worldData.isCheckActive(CheckType.CHAT_CAPTCHA)).thenReturn(false);
        assertFalse(ChatCaptchaUtil.isCaptchaEnabled(player, playerData));
    }

    @Test
    void returnsFalse_whenPlayerDisablesCaptcha() {
        when(playerData.getCurrentWorldDataSafe()).thenReturn(worldData);
        when(worldData.isCheckActive(CheckType.CHAT_CAPTCHA)).thenReturn(true);
        when(playerData.isCheckActive(CheckType.CHAT_CAPTCHA, player, worldData)).thenReturn(false);
        assertFalse(ChatCaptchaUtil.isCaptchaEnabled(player, playerData));
    }

    @Test
    void returnsTrue_whenBothEnableCaptcha() {
        when(playerData.getCurrentWorldDataSafe()).thenReturn(worldData);
        when(worldData.isCheckActive(CheckType.CHAT_CAPTCHA)).thenReturn(true);
        when(playerData.isCheckActive(CheckType.CHAT_CAPTCHA, player, worldData)).thenReturn(true);
        assertTrue(ChatCaptchaUtil.isCaptchaEnabled(player, playerData));
    }
}
