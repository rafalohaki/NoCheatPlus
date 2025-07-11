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
package fr.neatmonster.nocheatplus.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.permissions.RegisteredPermission;


/**
 * Just an interface for sub commands, for future use.
 * @author mc_dev
 *
 */
public abstract class BaseCommand extends AbstractCommand<JavaPlugin>{
	

    /** The prefix of every message sent by NoCheatPlus. */
    public static final String TAG = ChatColor.GRAY +""+ ChatColor.BOLD + "[" + ChatColor.RED + "NC+" + ChatColor.GRAY +""+ ChatColor.BOLD + "] " + ChatColor.GRAY;
    public static final String CTAG = "[NoCheatPlus] ";

    /** Index constants for the color code array returned by {@link #getColorCodes(CommandSender)}. */
    protected static final int COLOR_PRIMARY = 0;
    protected static final int STYLE_BOLD = 1;
    protected static final int COLOR_ERROR = 2;
    protected static final int STYLE_SECONDARY = 3;
    protected static final int COLOR_HIGHLIGHT = 4;
    protected static final int COLOR_DEFAULT = 5;
    protected static final int COLOR_VALUE = 6;
	
	public BaseCommand(JavaPlugin plugin, String label, RegisteredPermission permission){
		this(plugin, label, permission, null);
	}

        public BaseCommand(JavaPlugin access, String label, RegisteredPermission permission, String[] aliases){
                super(access, label, permission, aliases);
        }

        /**
         * Convenience method to retrieve the color codes used for player messages.
         *
         * @param sender the command sender
         * @return an array containing seven color code strings
         */
        protected String[] getColorCodes(CommandSender sender) {
                if (sender instanceof Player) {
                        return new String[] {
                                        ChatColor.GRAY.toString(),
                                        ChatColor.BOLD.toString(),
                                        ChatColor.RED.toString(),
                                        ChatColor.ITALIC.toString(),
                                        ChatColor.GOLD.toString(),
                                        ChatColor.WHITE.toString(),
                                        ChatColor.YELLOW.toString()
                        };
                }
                return new String[] {"", "", "", "", "", "", ""};
        }

}
