package com.kicas.rp.data.flagdata;

import com.kicas.rp.util.TextUtils;
import net.md_5.bungee.api.chat.BaseComponent;

import java.util.Objects;

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
        this(text, TextUtils.format(Objects.requireNonNull(text)));
    }

    public TextMeta() {
        this(null, null);
    }

    /**
     * @return the base components parsed upon instantiation of this object.
     */
    public BaseComponent[] getFormatted() {
        return formatted;
    }

    /**
     * @return the raw text in this text meta.
     */
    public String getText() {
        return text;
    }

    /**
     * @see TextMeta#getText()
     */
    @Override
    public String toString() {
        return getText();
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
        if(other == this)
            return true;

        if(!(other instanceof TextMeta))
            return false;

        TextMeta tm = (TextMeta)other;
        return text.equals(tm.text);
    }
}
