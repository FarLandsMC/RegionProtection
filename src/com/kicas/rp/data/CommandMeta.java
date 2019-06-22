package com.kicas.rp.data;

import com.kicas.rp.RegionProtection;
import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;

public class CommandMeta implements Serializable {
    private boolean isConsole;
    private String command;

    public static final CommandMeta EMPTY_META = new CommandMeta();

    public CommandMeta(boolean isConsole, String command) {
        this.isConsole = isConsole;
        this.command = command;
    }

    public CommandMeta() {
        this(false, "");
    }

    public boolean isConsole() {
        return isConsole;
    }

    public String getCommand() {
        return command;
    }

    public void execute(Player player) {
        String cmd = command.replaceAll("\\%0", player.getName());
        Bukkit.getScheduler().runTask(RegionProtection.getInstance(),
                () -> Bukkit.dispatchCommand(isConsole ? Bukkit.getConsoleSender() : player, cmd));
    }

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeBoolean(isConsole);
        encoder.writeUTF8Raw(command);
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        isConsole = decoder.readBoolean();
        command = decoder.readUTF8Raw();
    }
}
