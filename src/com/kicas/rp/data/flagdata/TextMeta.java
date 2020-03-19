package com.kicas.rp.data.flagdata;

import com.kicas.rp.util.TextUtils;
import org.bukkit.entity.Player;

/**
 * Represents metadata for flags that contain formatted in-game text. The format stored follows that which is parsed by
 * the TextUtils utility.
 */
public class TextMeta extends FlagMeta {
    private String text;

    public static final String EMPTY_TEXT_PLACEHOLDER = "~";
    public static final TextMeta EMPTY_TEXT = new TextMeta();

    public TextMeta(String text) {
        this.text = text;
    }

    public TextMeta() {
        this("");
    }

    /**
     * @return the raw text in this text meta.
     */
    public String getText() {
        return text;
    }

    /**
     * Sends the formatted form of the text in this metadata to the given player, substituting instances of '%player%'
     * for the given player's name.
     *
     * @param player the player to send the message to.
     */
    public void sendTo(Player player) {
        if (!text.isEmpty())
            TextUtils.sendFormatted(player, text.replaceAll("%player%", "%0"), player.getName());
    }

    /**
     * Sets this metadata's internal text to the given string after converting '\n' and '\r' to line breaks.
     *
     * @param metaString the metadata in string form.
     */
    @Override
    public void readMetaString(String metaString) {
        text = EMPTY_TEXT_PLACEHOLDER.equals(metaString) ? "" : metaString.replaceAll("\\\\n|\\\\r", "\n");
    }

    /**
     * @return this metadata's internal text after converting line breaks to '\n'
     */
    @Override
    public String toMetaString() {
        return text.isEmpty() ? EMPTY_TEXT_PLACEHOLDER : text.replaceAll("\n|\r\n", "\\\\n");
    }

    /**
     * Returns true if and only if the given object is a text meta instance and if the raw text within the given object
     * is equal to the raw text within this object.
     *
     * @param other the object to test.
     * @return true if the given object is a text meta instance and if it's equivalent to this object, false otherwise.
     */
    @Override
    public boolean equals(Object other) {
        if (other == this)
            return true;

        if (!(other instanceof TextMeta))
            return false;

        TextMeta tm = (TextMeta) other;
        return text.equals(tm.text);
    }
}
