package com.kicas.rp.data.flagdata;

import com.kicas.rp.util.Utils;
import org.bukkit.GameMode;

/**
 * Allows for gamemode data to be stored.  Used in entry/exit gamemode flags.
 */
public class GameModeMeta extends FlagMeta {
    private Mode mode;

    public GameModeMeta(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    /**
     * Update the gamemode change (creative, survival, adventure, or spectator) of this border based off of
     * the given string("creative", "survival", "adventure", or "spectator").
     *
     * @param metaString "creative", "survival", "adventure", or "spectator".
     */
    public void readMetaString(String metaString) {
        Mode mode = Utils.valueOfFormattedName(metaString, Mode.class);
        if (mode == null)
            throw new IllegalArgumentException("Invalid gamemode: \"" + metaString + "\"");
        this.mode = mode;
    }

    /**
     * @return the formatted name of this gamemode change.
     */
    public String toMetaString() {
        return Utils.formattedName(mode);
    }

    /**
     * @return the GameMode of this gamemode change.
     */
    public GameMode toGameMode(){
        return GameMode.valueOf(Utils.formattedName(mode).toUpperCase());
    }

    public enum Mode {
        ADVENTURE, CREATIVE, SPECTATOR, SURVIVAL;

        public static final Mode[] VALUES = values();
    }
}
