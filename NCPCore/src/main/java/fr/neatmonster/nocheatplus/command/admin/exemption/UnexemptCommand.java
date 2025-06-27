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
package fr.neatmonster.nocheatplus.command.admin.exemption;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor; // ✅ FIXED: Missing import added here
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.checks.CheckType;
import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.command.CommandUtil;
import fr.neatmonster.nocheatplus.hooks.NCPExemptionManager;
import fr.neatmonster.nocheatplus.permissions.Permissions;
import fr.neatmonster.nocheatplus.players.DataManager;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

public class UnexemptCommand extends BaseCommand {

    public UnexemptCommand(JavaPlugin plugin) {
        super(plugin, "unexempt", Permissions.COMMAND_UNEXEMPT);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length < 2) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Please specify a player to unexempt.");
            return true;
        } else if (args.length > 3) {
            sender.sendMessage((sender instanceof Player ? TAG : CTAG) + "Too many arguments. Command usage: /ncp unexempt (playername) (checktype).");
            return true;
        }

        final CheckType checkType = parseCheckType(args.length > 2 ? args[2] : null, sender);
        if (checkType == null) {
            return true;
        }

        final String targetName = args[1];
        if ("*".equals(targetName)) {
            unexemptAll(checkType, sender);
            return true;
        }

        final UUID id = resolveTargetId(targetName, sender);
        if (id == null) {
            final boolean player = sender instanceof Player;
            final String tag = player ? TAG : CTAG;
            final String c3 = player ? ChatColor.RED.toString() : "";
            sender.sendMessage(tag + "Not an online player nor a UUID: " + c3 + targetName);
            return true;
        }

        final Player player = DataManager.getInstance().getPlayer(targetName);
        final String name = player != null ? player.getName() : targetName;
        unexemptPlayer(id, name, checkType, sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 3) {
            return CommandUtil.getCheckTypeTabMatches(args[2]);
        }
        return null;
    }

    @Override
    public boolean testPermission(CommandSender sender, Command command, String alias, String[] args) {
        return super.testPermission(sender, command, alias, args)
                || (args.length >= 2 && args[1].trim().equalsIgnoreCase(sender.getName())
                && sender.hasPermission(Permissions.COMMAND_UNEXEMPT_SELF.getBukkitPermission()));
    }

    private CheckType parseCheckType(String input, CommandSender sender) {
        if (input == null) {
            return CheckType.ALL;
        }
        try {
            return CheckType.valueOf(input.toUpperCase().replace('-', '_').replace('.', '_'));
        } catch (Exception e) {
            final String tag = sender instanceof Player ? TAG : CTAG;
            final String[] colors = getColorCodes(sender);
            final String c3 = colors[2];
            final String c6 = colors[5];
            sender.sendMessage(tag + "Could not interpret: " + c3 + input);
            sender.sendMessage(tag + "Check type should be one of: " + c3 + StringUtil.join(Arrays.asList(CheckType.values()), c6 + ", " + c3));
            return null;
        }
    }

    private UUID resolveTargetId(String name, CommandSender sender) {
        final Player player = DataManager.getInstance().getPlayer(name);
        if (player != null) {
            return player.getUniqueId();
        }
        return DataManager.getInstance().getUUID(name);
    }

    private void unexemptAll(CheckType type, CommandSender sender) {
        NCPExemptionManager.clear();
        final String tag = sender instanceof Player ? TAG : CTAG;
        final String[] colors = getColorCodes(sender);
        final String c3 = colors[2];
        sender.sendMessage(tag + "Removed exemptions for all players for checks: " + c3 + type);
    }

    private void unexemptPlayer(UUID id, String playerName, CheckType type, CommandSender sender) {
        NCPExemptionManager.unexempt(id, type);
        final String tag = sender instanceof Player ? TAG : CTAG;
        final String[] colors = getColorCodes(sender);
        final String c1 = colors[0];
        final String c3 = colors[2];
        sender.sendMessage(tag + "Removed exemptions for " + c3 + playerName + c1 + " for checks: " + c3 + type);
    }
}
