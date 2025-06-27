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
package fr.neatmonster.nocheatplus.checks.chat;

import org.bukkit.entity.Player;

import fr.neatmonster.nocheatplus.checks.Check;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.ColorUtil;
import fr.neatmonster.nocheatplus.utilities.TickTask;
import fr.neatmonster.nocheatplus.checks.chat.ChatCaptchaUtil;

/**
 * Check only for commands
 * @author mc_dev
 *
 */
public class Commands extends Check {
    public Commands() {
        super(CheckType.CHAT_COMMANDS);
    }

    public boolean check(final Player player, final String message,
            final ChatConfig cc, final IPlayerData pData,
            final ICaptcha captcha) {

        if (player == null || message == null) {
            return false;
        }

        final long now = System.currentTimeMillis();
        final int tick = TickTask.getTick();

        final ChatData data = pData.getGenericInstance(ChatData.class);

        final boolean captchaEnabled = !cc.captchaSkipCommands
                && ChatCaptchaUtil.isCaptchaEnabled(player, pData);

        if (handleCaptcha(player, message, captcha, cc, data, pData, captchaEnabled)) {
            return true;
        }

        // Rest of the check is done without sync, because the data is only used by this check.

        // Weight might later be read from some prefix tree (also known / unknown).
        final float weight = 1f;

        updateCommandWeights(data, cc, pData, weight, now, tick);

        final float nw = data.commandsWeights.score(1f);
        final double violation = Math.max(nw - cc.commandsLevel,
                data.commandsShortTermWeight - cc.commandsShortTermLevel);

        return processCommandViolation(player, data, cc, captcha, captchaEnabled,
                violation, nw, now);
    }

    private boolean handleCaptcha(final Player player, final String message,
            final ICaptcha captcha, final ChatConfig cc, final ChatData data,
            final IPlayerData pData, final boolean captchaEnabled) {
        if (captchaEnabled) {
            synchronized (data) {
                if (captcha.shouldCheckCaptcha(player, cc, data, pData)) {
                    captcha.checkCaptcha(player, message, cc, data, true);
                    return true;
                }
            }
        }
        return false;
    }

    private void updateCommandWeights(final ChatData data, final ChatConfig cc,
            final IPlayerData pData, final float weight, final long now,
            final int tick) {
        data.commandsWeights.add(now, weight);
        if (tick < data.commandsShortTermTick) {
            data.commandsShortTermTick = tick;
            data.commandsShortTermWeight = 1.0;
        } else if (tick - data.commandsShortTermTick < cc.commandsShortTermTicks) {
            if (!pData.getCurrentWorldData().shouldAdjustToLag(type)
                    || TickTask.getLag(50L * (tick - data.commandsShortTermTick),
                            true) < 1.3f) {
                data.commandsShortTermWeight += weight;
            } else {
                data.commandsShortTermTick = tick;
                data.commandsShortTermWeight = 1.0;
            }
        } else {
            data.commandsShortTermTick = tick;
            data.commandsShortTermWeight = 1.0;
        }
    }

    private boolean processCommandViolation(final Player player,
            final ChatData data, final ChatConfig cc, final ICaptcha captcha,
            final boolean captchaEnabled, final double violation, final float nw,
            final long now) {
        if (violation > 0.0) {
            data.commandsVL += violation;
            if (captchaEnabled) {
                synchronized (data) {
                    captcha.sendNewCaptcha(player, cc, data);
                }
                return true;
            }
            if (executeActions(player, data.commandsVL, violation,
                    cc.commandsActions).willCancel()) {
                return true;
            }
        } else if (cc.chatWarningCheck
                && now - data.chatWarningTime > cc.chatWarningTimeout
                && (100f * nw / cc.commandsLevel > cc.chatWarningLevel || 100f
                        * data.commandsShortTermWeight / cc.commandsShortTermLevel > cc.chatWarningLevel)) {
            player.sendMessage(ColorUtil.replaceColors(cc.chatWarningMessage));
            data.chatWarningTime = now;
        } else {
            data.commandsVL *= 0.99;
        }
        return false;
    }

}
