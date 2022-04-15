package me.gamercoder215.lightning;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public class Commands implements CommandExecutor {

    protected Lightning plugin;

    public Commands(Lightning plugin) {
        this.plugin = plugin;
        plugin.getCommand("lserver").setExecutor(this);
        plugin.getCommand("lserverinfo").setExecutor(this);
    }

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		String path = cmd.getName() + (args.length < 1 ? "" : " " + args[0]);
		
		switch (path) {
			case "lserver addlistener": {
				if (!(sender.hasPermission("lightning.server.listener"))) {
					sender.sendMessage(ChatColor.RED + "No Permission");
					return false;
				}
				
				if (args.length < 2) {
		    		sender.sendMessage(ChatColor.RED + "Invalid URL Found");
		    		return false;
				}
				
		    	URI uri;
		    	try {
		    		uri = URI.create(args[1]);
		    	} catch (Exception e) {
		    		sender.sendMessage(ChatColor.RED + "Invalid URL Found");
		    		return false;
		    	}
		    	
		        if (Lightning.getEventListeners().contains(uri)) {
		            sender.sendMessage(ChatColor.RED + "URL already exists");
		            return false;
		        }
		        Lightning.addEventListener(uri);
		        sender.sendMessage(ChatColor.GREEN + "Successfully added event listener to " + ChatColor.YELLOW + uri.toString());
		   
		        return true;
			}
			case "lserver":
			case "lserverinfo":
			case "lserver info": {
				if (!(sender.hasPermission("lightning.server.info"))) {
					sender.sendMessage(ChatColor.RED + "No Permission");
					return false;
				}
				
		        sender.sendMessage("Loading...");

		        FileConfiguration config = plugin.getConfig();
		        List<String> info = new ArrayList<>();
		        
		        info.add("=== HttpServer Information ===");
		        info.add("Server Address: <ip>:" + config.getInt("port"));
		        info.add(" ");
		        info.add("=== HttpClient Information ===");
		        info.add("Version: " + Lightning.client.version().name());

		        sender.sendMessage(String.join("\n", info));
				return true;
			}
			case "lserver removelistener": {
				if (!(sender.hasPermission("lightning.server.listener"))) {
					sender.sendMessage(ChatColor.RED + "No Permission");
					return false;
				}
				
				if (args.length < 2) {
		    		sender.sendMessage(ChatColor.RED + "Invalid URL Found");
		    		return false;
				}
				
		    	URI uri;
		    	try {
		    		uri = URI.create(args[1]);
		    	} catch (Exception e) {
		    		sender.sendMessage(ChatColor.RED + "Invalid URL Found");
		    		return false;
		    	}
		        if (!Lightning.getEventListeners().contains(uri)) {
		            sender.sendMessage(ChatColor.RED + "URL does not exist");
		            return false;
		        }
		    	Lightning.removeEventListener(uri);
		    	sender.sendMessage(ChatColor.GREEN + "Successfully removed event listener from " + ChatColor.YELLOW + uri.toString());
		    	return true;
			}
			default: {
				sender.sendMessage(ChatColor.RED + "Invalid Arguments");
				return false;
			}
		}
	}
}
