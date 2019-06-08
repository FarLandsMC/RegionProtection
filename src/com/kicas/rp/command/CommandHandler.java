package com.kicas.rp.command;

import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_14_R1.CraftServer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.ArrayList;
import java.util.List;

public class CommandHandler implements Listener {
    private final List<Command> commands;

    public CommandHandler() {
        this.commands = new ArrayList<>();
    }

    public void registerCommands() {

    }

    private void registerCommand(Command command) {
        commands.add(command);
        ((CraftServer) Bukkit.getServer()).getCommandMap().register("regionprotection", command);
    }

    public boolean handleCommand(CommandSender sender, String fullCommand) {
        String command = fullCommand.substring(fullCommand.startsWith("/") ? 1 : 0, Utils.indexOfDefault(fullCommand.indexOf(' '), fullCommand.length())).trim();
        String[] args = fullCommand.contains(" ") ? fullCommand.substring(fullCommand.indexOf(' ') + 1).split(" ") : new String[0];
        Command c = commands.stream().filter(cmd -> cmd.matches(command)).findAny().orElse(null);
        if(c == null)
            return false;
        if(fullCommand.startsWith("/regionprotection:"))
            return true;
        if(c.isOpOnly() && !sender.isOp()) {
            sender.sendMessage(ChatColor.RED + "You must be an administrator to use this command.");
            return true;
        }
        c.execute(sender, command, args);
        return true;
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if(event.getMessage().startsWith("/regionprotection:")) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(handleCommand(event.getPlayer(), event.getMessage()));
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onServerCommand(ServerCommandEvent event) {
        if(event.getCommand().startsWith("/regionprotection:")) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(handleCommand(event.getSender(), event.getCommand()));
    }
}
