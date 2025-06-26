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
package fr.neatmonster.nocheatplus.command.admin;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.checks.ViolationHistory;
import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.command.CommandUtil;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

public class RemovePlayerCommand extends BaseCommand {

    public RemovePlayerCommand(JavaPlugin plugin) {
        super(plugin, "removeplayer", Permissions.COMMAND_REMOVEPLAYER, new String[]{
                "remove",	
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        final String c1, c2, c3, c5, c6;
        if (sender instanceof Player) {
            c1 = ChatColor.GRAY.toString();
            c2 = ChatColor.BOLD.toString();
            c3 = ChatColor.RED.toString();
            c5 = ChatColor.GOLD.toString();
            c6 = ChatColor.WHITE.toString();
        } else {
            c1 = c2 = c3 = c5 = c6 = "";
        }

        if (args.length < 2) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Please specify a player's data to remove.");
            return true;
        }
        else if (args.length > 3) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Too many arguments. Command usage: /ncp removeplayer (playername) (checktype).");
            return true;
        }
        String playerName = args[1];
        final CheckType checkType = parseCheckType(args.length == 3 ? args[2] : null, sender);
        if (checkType == null) {
            return true;
        }

        if (playerName.equals("*")){
            removeAllData(sender, checkType);
            return true;
        }

        removePlayerData(sender, playerName, checkType);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // Complete Players
        if (args.length == 2) {
            List<String> players = Lists.newArrayList();
            for (Player player : Bukkit.getOnlinePlayers()) {
                players.add(player.getName());
            }
            return players;
        }
        // Complete CheckType
        if (args.length == 3) return CommandUtil.getCheckTypeTabMatches(args[2]);
        return null;
    }

    /* (non-Javadoc)
     * @see fr.neatmonster.nocheatplus.command.AbstractCommand#testPermission(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[])
     */
    @Override
    public boolean testPermission(CommandSender sender, Command command, String alias, String[] args) {
        return super.testPermission(sender, command, alias, args)
                || args.length >= 2 && args[1].trim().equalsIgnoreCase(sender.getName())
                && sender.hasPermission(Permissions.COMMAND_REMOVEPLAYER_SELF.getBukkitPermission());
    }

    /**
     * Parse the check type argument.
     *
     * @param input the argument to parse, or {@code null} for {@link CheckType#ALL}
     * @param sender the command sender
     * @return the matching {@link CheckType} or {@code null} if not found
     */
    private CheckType parseCheckType(String input, CommandSender sender) {
        if (input == null) {
            return CheckType.ALL;
        }
        try {
            return CheckType.valueOf(input.toUpperCase().replace('-', '_').replace('.', '_'));
        } catch (Exception e) {
            final boolean player = sender instanceof Player;
            final String tag = player ? TAG : CTAG;
            final String c3 = player ? ChatColor.RED.toString() : "";
            final String c6 = player ? ChatColor.WHITE.toString() : "";
            sender.sendMessage(tag + "Could not interpret: " + c3 + input);
            sender.sendMessage(tag + "Check type should be one of: " + c3
                    + StringUtil.join(Arrays.asList(CheckType.values()), c6 + ", " + c3));
            return null;
        }
    }

    /**
     * Handle removing all data for all players.
     *
     * @param sender the command sender
     * @param type the check type
     */
    private void removeAllData(CommandSender sender, CheckType type) {
        DataManager.clearData(type);
        final String c3 = sender instanceof Player ? ChatColor.RED.toString() : "";
        sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Removed all data and history: " + c3 + type);
    }

    /**
     * Remove data and history for a specific player.
     *
     * @param sender the command sender
     * @param playerName the name of the player
     * @param type the check type
     */
    private void removePlayerData(CommandSender sender, String playerName, CheckType type) {
        final String c1, c3;
        if (sender instanceof Player) {
            c1 = ChatColor.GRAY.toString();
            c3 = ChatColor.RED.toString();
        } else {
            c1 = c3 = "";
        }

        final Player player = DataManager.getPlayer(playerName);
        if (player != null) {
            playerName = player.getName();
        }

        ViolationHistory hist = ViolationHistory.getHistory(playerName, false);
        boolean somethingFound = false;
        if (hist != null) {
            somethingFound = hist.remove(type);
            if (type == CheckType.ALL) {
                somethingFound = true;
                ViolationHistory.removeHistory(playerName);
            }
        }

        if (DataManager.removeExecutionHistory(type, playerName)) {
            somethingFound = true;
        }

        if (DataManager.removeData(playerName, type)) {
            somethingFound = true;
        }

        if (somethingFound) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Issued history and data removal (" + c3 + type + c1 + "): " + c3 + playerName + c1);
        } else {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Nothing found (" + c3 + type + c1 + "): " + c3 + playerName + c1 + " (spelled correctly?)");
        }
    }

}
