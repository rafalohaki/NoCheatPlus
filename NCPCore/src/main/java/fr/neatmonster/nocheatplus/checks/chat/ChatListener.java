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

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.checks.CheckListener;
import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.chat.util.ChatCaptchaUtil;
import fr.neatmonster.nocheatplus.checks.moving.MovingConfig;
import fr.neatmonster.nocheatplus.checks.moving.util.MovingUtil;
import fr.neatmonster.nocheatplus.command.CommandUtil;
import fr.neatmonster.nocheatplus.compat.BridgeMisc;
import fr.neatmonster.nocheatplus.components.NoCheatPlusAPI;
import fr.neatmonster.nocheatplus.components.data.ICheckData;
import fr.neatmonster.nocheatplus.components.data.IData;
import fr.neatmonster.nocheatplus.components.registry.feature.INotifyReload;
import fr.neatmonster.nocheatplus.components.registry.feature.JoinLeaveListener;
import fr.neatmonster.nocheatplus.config.ConfPaths;
import fr.neatmonster.nocheatplus.config.ConfigFile;
import fr.neatmonster.nocheatplus.config.ConfigManager;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.players.IPlayerData;
import fr.neatmonster.nocheatplus.utilities.StringUtil;
import fr.neatmonster.nocheatplus.utilities.ds.prefixtree.SimpleCharPrefixTree;

/**
 * Central location to listen to events that are relevant for the chat checks.
 * 
 * @see ChatEvent
 */
public class ChatListener extends CheckListener implements INotifyReload, JoinLeaveListener {

    // Checks.

    /** Captcha handler. */
    private final Captcha captcha		= addCheck(new Captcha());  

    /** Commands repetition check. */
    private final Commands commands 	= addCheck(new Commands()); 

    /** Logins check (global) */
    private final Logins logins 		= addCheck(new Logins());

    /** Chat message check. */
    private final Text text 			= addCheck(new Text());

    /** Relogging check. */
    private final Relog relog 			= addCheck(new Relog());

    // Auxiliary stuff.

    /** Commands to be ignored completely. */
    private final SimpleCharPrefixTree commandExclusions = new SimpleCharPrefixTree();

    /** Commands to be handled as chat. */
    private final SimpleCharPrefixTree chatCommands = new SimpleCharPrefixTree();

    /** Commands not to be executed in-game.  */
    private final SimpleCharPrefixTree consoleOnlyCommands = new SimpleCharPrefixTree(); 

    /** Set world to null after use, primary thread only. */
    private final Location useLoc = new Location(null, 0, 0, 0);

    @SuppressWarnings("unchecked")
    public ChatListener() {
        super(CheckType.CHAT);
        ConfigFile config = ConfigManager.getConfigFile();
        initFilters(config);
        // (text inits in constructor.)
        final NoCheatPlusAPI api = NCPAPIProvider.getNoCheatPlusAPI();
        api.register(api.newRegistrationContext()
                .registerConfigWorld(ChatConfig.class)
                .factory(arg -> new ChatConfig(arg.worldData))
                .registerConfigTypesPlayer()
                .context() //
                .registerDataPlayer(ChatData.class)
                .factory(arg -> new ChatData())
                .addToGroups(CheckType.CHAT, true, List.of(IData.class, ICheckData.class))
                .context() //
                );
    }

    private void initFilters(ConfigFile config) {
        CommandUtil.feedCommands(consoleOnlyCommands, config, ConfPaths.PROTECT_COMMANDS_CONSOLEONLY_CMDS, true);
        CommandUtil.feedCommands(chatCommands, config, ConfPaths.CHAT_COMMANDS_HANDLEASCHAT, true);
        CommandUtil.feedCommands(commandExclusions, config, ConfPaths.CHAT_COMMANDS_EXCLUSIONS, true);
    }

    /**
     * We listen to PlayerChat events for obvious reasons.
     * 
     * @param event
     *            the event
     */
    @EventHandler(ignoreCancelled = false, priority = EventPriority.LOWEST)
    public void onPlayerChat(final AsyncPlayerChatEvent event) {

        final Player player = event.getPlayer();
        final boolean alreadyCancelled = event.isCancelled();

        if (!DataManager.getInstance().getPlayerData(player).isCheckActive(CheckType.CHAT, player)) return;

        // Tell TickTask to update cached permissions.
        // (Might omit this if already cancelled.)
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final ChatConfig cc = pData.getGenericInstance(ChatConfig.class);


        // Then the no chat check.
        // Note: event.isAsync() indicates if this is run asynchronously.
        if (textChecks(player, event.getMessage(), cc, pData, false, alreadyCancelled)) {
            event.setCancelled(true);
        }

    }

    /**
     * We listen to PlayerCommandPreprocess events because commands can be used for spamming too.
     * 
     * @param event
     *            the event
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(final PlayerCommandPreprocessEvent event) {
        final Player player = event.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }

        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final ChatConfig cc = pData.getGenericInstance(ChatConfig.class);

        final ParsedCommandInfo parsed = parseCommand(event.getMessage());

        if (handleConsoleOnly(player, parsed, cc, event)) {
            return;
        }

        if (chatCommands.hasAnyPrefixWords(parsed.messageVars)) {
            if (textChecks(player, parsed.checkMessage, cc, pData, true, false)) {
                event.setCancelled(true);
            }
        } else if (!commandExclusions.hasAnyPrefixWords(parsed.messageVars)) {
            if (commands.isEnabled(player, pData)
                    && commands.check(player, parsed.checkMessage, cc, pData, captcha)) {
                event.setCancelled(true);
            } else {
                final MovingConfig mcc = pData.getGenericInstance(MovingConfig.class);
                if (mcc.passableUntrackedCommandCheck
                        && mcc.passableUntrackedCommandPrefixes.hasAnyPrefix(parsed.messageVars)) {
                    if (checkUntrackedLocation(player, event.getMessage(), mcc, pData)) {
                        event.setCancelled(true);
                    }
                }
            }
        }

    }

    private static final class ParsedCommandInfo {
        final List<String> messageVars;
        final String checkMessage;
        final Command command;

        ParsedCommandInfo(final List<String> messageVars, final String checkMessage, final Command command) {
            this.messageVars = messageVars;
            this.checkMessage = checkMessage;
            this.command = command;
        }
    }

    private ParsedCommandInfo parseCommand(final String message) {
        final String safeMessage = message != null ? message : "";
        final String lcMessage = StringUtil.leftTrim(safeMessage).toLowerCase();
        final String[] split = lcMessage.split(" ", 2);
        final String alias = split[0].isEmpty() ? "" : split[0].substring(1);
        final Command command = CommandUtil.getCommand(alias);

        final List<String> vars = new ArrayList<>();
        vars.add(lcMessage);
        String checkMessage = safeMessage;
        if (command != null) {
            vars.add("/" + command.getLabel().toLowerCase() + (split.length > 1 ? " " + split[1] : ""));
        }
        if (alias.contains(":")) {
            final int index = safeMessage.indexOf(":") + 1;
            if (index < safeMessage.length()) {
                checkMessage = safeMessage.substring(index);
                vars.add(checkMessage.toLowerCase());
            }
        }
        return new ParsedCommandInfo(vars, checkMessage, command);
    }

    private boolean handleConsoleOnly(final Player player, final ParsedCommandInfo info,
            final ChatConfig cc, final PlayerCommandPreprocessEvent event) {
        if (cc.consoleOnlyCheck && consoleOnlyCommands.hasAnyPrefixWords(info.messageVars)) {
            if (info.command == null || info.command.testPermission(player)) {
                player.sendMessage(cc.consoleOnlyMessage);
            }
            event.setCancelled(true);
            return true;
        }
        return false;
    }
    private boolean checkUntrackedLocation(final Player player, final String message, 
            final MovingConfig mcc, final IPlayerData pData) {
        final Location loc = player.getLocation(useLoc);
        boolean cancel = false;
        if (MovingUtil.shouldCheckUntrackedLocation(player, loc, pData)) {
            final Location newTo = MovingUtil.checkUntrackedLocation(loc);
            if (newTo != null) {
                if (mcc.passableUntrackedCommandTryTeleport 
                        && player.teleport(newTo, BridgeMisc.TELEPORT_CAUSE_CORRECTION_OF_POSITION)) {
                    NCPAPIProvider.getNoCheatPlusAPI().getLogManager().info(Streams.TRACE_FILE, player.getName() + " runs the command '" + message + "' at an untracked location: " + loc + " , teleport to: " + newTo);
                } else {
                    // Cancellation can be made optional via configuration.
                    // Consider informing the player about the cancellation.
                    NCPAPIProvider.getNoCheatPlusAPI().getLogManager().info(Streams.TRACE_FILE, player.getName() + " runs the command '" + message + "' at an untracked location: " + loc + " , cancel the command.");
                    cancel = true;
                }
            }
        }
        useLoc.setWorld(null); // Cleanup.
        return cancel;
    }

    private boolean textChecks(final Player player, final String message, 
            final ChatConfig cc, final IPlayerData pData,
            final boolean isMainThread, final boolean alreadyCancelled) {
        return text.isEnabled(player, pData) && text.check(player, message, cc, pData, 
                captcha, isMainThread, alreadyCancelled);
    }

    /**
     * We listen to this type of events to prevent spambots from login to the server.
     * 
     * @param event
     *            the event
     */
    @EventHandler(
            priority = EventPriority.NORMAL)
    public void onPlayerLogin(final PlayerLoginEvent event) {
        if (event.getResult() != Result.ALLOWED) return;
        final Player player = event.getPlayer();
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);

        if (!pData.isCheckActive(CheckType.CHAT, player)) return;

        final ChatConfig cc = pData.getGenericInstance(ChatConfig.class);
        final ChatData data = pData.getGenericInstance(ChatData.class);

        // (No forced permission update, because the associated permissions are treated as hints rather.)

        // Reset captcha of player if needed.
        synchronized(data) {
            if (ChatCaptchaUtil.isCaptchaEnabled(player, pData)) {
                captcha.resetCaptcha(player, cc, data, pData);
            }
        }
        // Fast relog check.
        if (relog.isEnabled(player, pData) && relog.unsafeLoginCheck(player, cc, data,pData)) {
            event.disallow(Result.KICK_OTHER, cc.relogKickMessage);
        }
        else if (logins.isEnabled(player, pData) && logins.check(player, cc, data)) {
            event.disallow(Result.KICK_OTHER, cc.loginsKickMessage);
        }
    }

    @Override
    public void onReload() {
        // Read some things from the global config file.
        ConfigFile config = ConfigManager.getConfigFile();
        initFilters(config);
        text.onReload();
        logins.onReload();
    }

    @Override
    public void playerJoins(final Player player) {
        final IPlayerData pData = DataManager.getInstance().getPlayerData(player);
        final ChatConfig cc = pData.getGenericInstance(ChatConfig.class);
        final ChatData data = pData.getGenericInstance(ChatData.class);
        /*
         * The isEnabled check must be done with IWorldData (no locking).
         * Specifically because enabling/disabling checks should be done in the
         * primary thread, regardless of the capabilities of WorldDataManager
         * implementation.
         */
        synchronized (data) {
            if (ChatCaptchaUtil.isCaptchaEnabled(player, pData)) {
                if (captcha.shouldCheckCaptcha(player, cc, data, pData)) {
                    // shouldCheckCaptcha: only if really enabled.
                    // Possible addition: check cc.captchaOnLogin before shouldCheckCaptcha.
                    // Consider scheduling this after other plugin messages.
                    captcha.sendNewCaptcha(player, cc, data);
                }
            }
        }
    }

    @Override
    public void playerLeaves(final Player player) {
    }

}
