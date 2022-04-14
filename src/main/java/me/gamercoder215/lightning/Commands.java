package me.gamercoder215.lightning;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import revxrsal.commands.annotation.Command;
import revxrsal.commands.annotation.Description;
import revxrsal.commands.annotation.Range;
import revxrsal.commands.annotation.Switch;
import revxrsal.commands.annotation.Usage;
import revxrsal.commands.bukkit.annotation.CommandPermission;

public class Commands {

    protected Lightning plugin;

    public Commands(Lightning plugin) {
        this.plugin = plugin;
    }

    @Command({"stopserver", "stopsrv", "lserver stop"})
    @Description("Stops the server")
    @Usage("/lserver start")
    @CommandPermission("lightning.server")
    public void stopServer(Player sender) {
        if (!(Lightning.isOnline())) {
            sender.sendMessage(ChatColor.RED + "Server is offline; cancelled.");
            return;
        }
        Lightning.getHTTPServer().stop(0);
    }

    @Command({"reloadserver", "reloadsrv", "lserver reload"})
    @Description("Reloads the server, by stopping and starting")
    @Usage("/lserver reload")
    @CommandPermission("lightning.server")
    public void reloadSever(Player sender) {
        stopServer(sender);
        startServer(sender);
    }

    @Command({"lserver", "lserver info", "lserverinfo"})
    @Description("Query Lightning Server Information")
    @Usage("/lserver info")
    @CommandPermission("lightning.server.info")
    public void getInformation(Player sender) {
        sender.sendMessage("Loading...");

        FileConfiguration config = plugin.getConfig();
        List<String> info = new ArrayList<>();
        
        info.add("=== HttpServer Information ===");
        info.add("Server Address: <ip>:" + config.getInt("port"));
        info.add(" ");
        info.add("=== HttpClient Information ===");
        info.add("Version: " + Lightning.client.version().name());

        sender.sendMessage(String.join("\n", info));
    }

    @Command({"startserver", "startsrv", "lserver start"})
    @Description("Starts the server, if offline.")
    @Usage("/lserver start")
    @CommandPermission("lightning.server.start")
    public void startServer(Player sender) {
        if (Lightning.isOnline()) {
            sender.sendMessage(ChatColor.RED + "Server is online; cancelled.");
            return;
        }
        Lightning.getHTTPServer().start();
    }

    @Command({"setport", "setsrvport", "setserverport", "lserver port"})
    @Description("Change the server port, with option to reload server.")
    @Usage("/lserver port <port> [reload]")
    @CommandPermission("lightning.server")
    public void setPort(Player sender, @Range(min = 1, max = 65535) int port, @Switch boolean rl) {
        FileConfiguration config = plugin.getConfig();

        config.set("port", port);

        if (rl) {
            reloadSever(sender);
            sender.sendMessage(ChatColor.GREEN + "Set port. You need to call /reloadserver for this to take effect.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Set port & Reloaded Server.");
        }
    }
    
}
