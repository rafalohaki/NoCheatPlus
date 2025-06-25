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
package fr.neatmonster.nocheatplus.command.admin.log.stream;

import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import fr.neatmonster.nocheatplus.NCPAPIProvider;
import fr.neatmonster.nocheatplus.command.BaseCommand;
import fr.neatmonster.nocheatplus.logging.LogManager;
import fr.neatmonster.nocheatplus.logging.StreamID;
import fr.neatmonster.nocheatplus.logging.Streams;
import fr.neatmonster.nocheatplus.utilities.ColorUtil;
import fr.neatmonster.nocheatplus.utilities.StringUtil;

/**
 * Log to any log stream (console only).
 * 
 * @author asofold
 *
 */
public class StreamCommand extends BaseCommand {

    public StreamCommand(JavaPlugin plugin) {
        super(plugin, "stream", null); // No permission: currently console-only.
        this.usage = "ncp log stream (stream_id)[@(level)][?color|?nocolor][+(stream_id2)[@(level2)][?color|?nocolor][+...]] (message...) ";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!demandConsoleCommandSender(sender)) {
            return true;
        }
        if (args.length < 4) {
            return false;
        }
        LogManager man = NCPAPIProvider.getNoCheatPlusAPI().getLogManager();
        String message = null;
        String messageColor = null;
        String messageNoColor = null;
        for (String streamDef : args[2].split("\\+")) {
            StreamFlagsResult flags = parseStreamFlags(sender, streamDef);
            if (flags == null) {
                continue;
            }
            StreamLevelResult levelResult = parseStreamLevel(sender, flags.stream());
            if (levelResult == null) {
                continue;
            }
            StreamResolution resolution = resolveStreamID(sender, man, levelResult.stream(), levelResult.level());
            if (resolution == null) {
                continue;
            }
            if (message == null) {
                message = StringUtil.join(args, 3, " ");
            }
            final String logMessage;
            if (flags.noColor()) {
                if (messageNoColor == null) {
                    messageNoColor = ChatColor.stripColor(ColorUtil.removeColors(message));
                }
                logMessage = messageNoColor;
            }
            else if (flags.color()) {
                if (messageColor == null) {
                    messageColor = ColorUtil.replaceColors(message);
                }
                logMessage = messageColor;
            }
            else {
                logMessage = message;
            }
            man.log(resolution.id(), resolution.level(), logMessage);
        }
        // (No success message.)
        return true;
    }

    private record StreamFlagsResult(String stream, boolean color, boolean noColor) {
    }

    private StreamFlagsResult parseStreamFlags(CommandSender sender, String streamDef) {
        boolean color = false;
        boolean noColor = false;
        String result = streamDef;
        if (streamDef.indexOf('?') != -1) {
            String[] split = streamDef.split("\\?");
            if (split.length != 2) {
                sender.sendMessage("Bad flag (color|nocolor): " + streamDef);
                return null;
            }
            result = split[0];
            String temp = split[1].toLowerCase();
            if (temp.matches("^(nc|noc|nocol|nocolor)$")) {
                noColor = true;
            }
            else if (temp.matches("^(c|col|color)$")) {
                color = true;
            }
            else {
                sender.sendMessage("Bad flag (color|nocolor): " + temp);
                return null;
            }
        }
        return new StreamFlagsResult(result, color, noColor);
    }

    private record StreamLevelResult(String stream, Level level) {
    }

    private StreamLevelResult parseStreamLevel(CommandSender sender, String streamDef) {
        Level level = null;
        String result = streamDef;
        if (streamDef.indexOf('@') != -1) {
            String[] split = streamDef.split("@");
            if (split.length != 2) {
                sender.sendMessage("Bad level definition: " + streamDef);
                return null;
            }
            result = split[0];
            try {
                level = Level.parse(split[1].toUpperCase());
            }
            catch (IllegalArgumentException e) {
                sender.sendMessage("Bad level: " + split[1]);
                return null;
            }
        }
        return new StreamLevelResult(result, level);
    }

    private record StreamResolution(StreamID id, Level level) {
    }

    private StreamResolution resolveStreamID(CommandSender sender, LogManager man, String streamDef, Level level) {
        StreamID streamId = man.getStreamID(streamDef);
        Level resultLevel = level;
        if (streamId == null) {
            String altStreamDef = streamDef.toLowerCase();
            if (altStreamDef.equals("notify")) {
                streamId = Streams.NOTIFY_INGAME;
            }
            else if (altStreamDef.equals("debug")) {
                streamId = Streams.TRACE_FILE;
                if (resultLevel == null) {
                    resultLevel = Level.FINE;
                }
            }
            else if (altStreamDef.equals("status")) {
                streamId = Streams.STATUS;
            }
            else if (altStreamDef.equals("init")) {
                streamId = Streams.INIT;
            }
            else if (altStreamDef.equals("console")) {
                streamId = Streams.PLUGIN_LOGGER;
            }
            else if (altStreamDef.equals("file")) {
                streamId = Streams.DEFAULT_FILE;
            }
            else {
                sender.sendMessage("Bad stream id: " + streamDef);
                return null;
            }
        }
        if (resultLevel == null) {
            resultLevel = Level.INFO;
        }
        return new StreamResolution(streamId, resultLevel);
    }

}
