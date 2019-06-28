package com.kicas.rp.command;

import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_14_R1.CraftServer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom command handler for the region protection plugin.
 */
public class CommandHandler implements Listener {
    private final List<Command> commands;

    public CommandHandler() {
        this.commands = new ArrayList<>();
    }

    /**
     * Called when the plugin is enabled. Injects the command instances into the NMS system and registers them to the
     * custom handler as well.
     */
    public void registerCommands() {
        // Alphabetical order
        registerCommand(new CommandAbandonClaim());
        registerCommand(new CommandAdminRegion());
        registerCommand(new CommandClaim());
        registerCommand(new CommandClaimBlocks());
        registerCommand(new CommandClaimHeight());
        registerCommand(new CommandClaimList());
        registerCommand(new CommandExpandClaim());
        registerCommand(new CommandExpel());
        registerCommand(new CommandIgnoreTrust());
        registerCommand(new CommandRegion());
        registerCommand(new CommandSteal());
        registerCommand(new CommandClaimTnt());
        registerCommand(new CommandTransferClaim());
        registerCommand(new CommandTrust());
        registerCommand(new CommandTrustList());
    }

    private void registerCommand(Command command) {
        commands.add(command);
        ((CraftServer)Bukkit.getServer()).getCommandMap().register("regionprotection", command);
    }

    /**
     * Attempts to match the given input to a command and run it with the given sender. This method will return whether
     * or not the event pertaining to the given command should be cancelled, not necessarily if the command was
     * successfully run.
     * @param sender the command sender.
     * @param fullCommand the full command, including arguments.
     * @return true if the event pertaining to this command should be cancelled, false otherwise.
     */
    public boolean handleCommand(CommandSender sender, String fullCommand) {
        String command = fullCommand.substring(fullCommand.startsWith("/") ? 1 : 0,
                Utils.indexOfDefault(fullCommand.indexOf(' '), fullCommand.length())).trim();
        String[] args = fullCommand.contains(" ") ? fullCommand.substring(fullCommand.indexOf(' ') + 1).split(" ")
                : new String[0];
        Command c = commands.stream().filter(cmd -> cmd.matches(command)).findAny().orElse(null);

        // Invalid command
        if(c == null)
            return false;

        // Run the command
        c.execute(sender, command, args);

        return true;
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        event.setCancelled(handleCommand(event.getPlayer(), event.getMessage()));
    }

    @EventHandler(ignoreCancelled=true, priority=EventPriority.LOW)
    public void onServerCommand(ServerCommandEvent event) {
        event.setCancelled(handleCommand(event.getSender(), event.getCommand()));
    }
}
