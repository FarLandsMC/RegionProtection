package com.kicas.rp.data.flagdata;

import com.kicas.rp.util.TextUtils;
import net.md_5.bungee.api.chat.BaseComponent;

/**
 * Represents metadata for flags that contain formatted in-game text. The format stored follows that which is parsed by
 * the TextUtils utility.
 */
public class TextMeta {
    private String text;
    // Computed on construction for efficiency later on
    private BaseComponent[] formatted;

    public static final TextMeta EMPTY_TEXT = new TextMeta("", new BaseComponent[0]);

    private TextMeta(String text, BaseComponent[] formatted) {
        this.text = text;
        this.formatted = formatted;
    }

    public TextMeta(String text) {
        this.text = text;
        if(text != null)
            this.formatted = TextUtils.format(text);
    }

    public TextMeta() {
        this(null);
    }

    public BaseComponent[] getFormatted() {
        return formatted;
    }

    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    public boolean equals(Object other) {
        if(other == this)
            return true;

        if(!(other instanceof TextMeta))
            return false;

        TextMeta tm = (TextMeta)other;
        return text.equals(tm.text);
    }
}
