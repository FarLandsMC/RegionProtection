package com.kicas.rp.data.flagdata;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.util.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Represents the metadata for a command. This meta is used for the enter-command and exit-command flags, and specifies
 * the sender of the command as well as the command to execute.
 */
public class CommandMeta extends FlagMeta {
    private boolean runFromConsole;
    private String command;

    public static final CommandMeta EMPTY_META = new CommandMeta();

    public CommandMeta(boolean runFromConsole, String command) {
        this.runFromConsole = runFromConsole;
        this.command = command;
    }

    public CommandMeta() {
        this(false, "");
    }

    /**
     * @return true if this command is meant to be run by the console, false otherwise.
     */
    public boolean runFromConsole() {
        return runFromConsole;
    }

    /**
     * @return the text command contained in this class.
     */
    public String getCommand() {
        return command;
    }

    /**
     * Updates this metadata based on the given input string. The valid format for a command meta string is:
     * &lt;console|player&gt;:&lt;command&gt; where console and player specify the command sender to use.
     *
     * @param metaString the metadata in string form.
     */
    @Override
    public void readMetaString(String metaString) {
        int index = metaString.indexOf(':');
        if (index < 0)
            throw new IllegalArgumentException("Invalid command format. Format: <console|player>:<command>");

        String sender = metaString.substring(0, index);

        if ("console".equalsIgnoreCase(sender))
            runFromConsole = true;
        else if ("player".equalsIgnoreCase(sender))
            runFromConsole = false;
        else
            throw new IllegalArgumentException("Invalid sender: " + sender);

        command = metaString.substring(index + 1);
    }

    @Override
    public String toMetaString() {
        return (runFromConsole ? "[console] " : "[player] ") + command;
    }

    /**
     * Executes the command stored in this metadata synchronously. If this command is not run by the console, then the
     * given player is used as the sender of the command. There should always be an associated player when executing a
     * command meta. Certain values will be substituted into the command according to the following map
     * (marker, replacement):
     * <ul>
     * <li>%player%: the associated player's username</li>
     * <li>%world%: the world the player is in</li>
     * <li>%x%: the player's x position</li>
     * <li>%y%: the player's y position</li>
     * <li>%z%: the player's z position</li>
     * </ul>
     *
     * @param player the player associated with this execution of the command.
     */
    public void execute(Player player) {
        // Perform substitutions
        String cmd = command.replaceAll("%player%", player.getName())
                .replaceAll("%world%", player.getWorld().getName())
                .replaceAll("%x%", Utils.doubleToString(player.getLocation().getX(), 3))
                .replaceAll("%y%", Utils.doubleToString(player.getLocation().getY(), 3))
                .replaceAll("%z%", Utils.doubleToString(player.getLocation().getZ(), 3));

        // Perform the execution
        Bukkit.getScheduler().runTask(RegionProtection.getInstance(),
                () -> Bukkit.dispatchCommand(runFromConsole ? Bukkit.getConsoleSender() : player, cmd));
    }
}
