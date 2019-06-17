package com.kicas.rp.data;

import com.kicas.rp.util.Decoder;
import com.kicas.rp.util.Encoder;
import com.kicas.rp.util.Serializable;
import com.kicas.rp.util.TextUtils;
import net.md_5.bungee.api.chat.BaseComponent;

import java.io.IOException;

/**
 * Represents metadata for flags that contain formatted in-game tesxt. The format stored follows that which is parsed by
 * the TextUtils utility.
 */
public class TextMeta implements Serializable {
    private String text;
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

    @Override
    public void serialize(Encoder encoder) throws IOException {
        encoder.writeUTF8Raw(text);
    }

    @Override
    public void deserialize(Decoder decoder) throws IOException {
        text = decoder.readUTF8Raw();
        formatted = TextUtils.format(text);
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
