package fr.neatmonster.nocheatplus.command;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import fr.neatmonster.nocheatplus.NoCheatPlus;
import fr.neatmonster.nocheatplus.players.Permissions;

public class DelayCommand extends DelayableCommand {

	public DelayCommand(NoCheatPlus plugin){
		super(plugin, "delay", Permissions.ADMINISTRATION_DELAY);
	}
	
	@Override
	public boolean execute(CommandSender sender, Command command, String label,
			String[] alteredArgs, long delay) {
		if (alteredArgs.length < 2) return false;
		final String cmd = join(alteredArgs, 1);
		schedule(new Runnable() {
			@Override
			public void run() {
				Server server = Bukkit.getServer();
				server.dispatchCommand(server.getConsoleSender(), cmd);
			}
		}, delay);
		return true;
	}

}
