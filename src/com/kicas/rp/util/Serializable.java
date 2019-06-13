package com.kicas.rp.util;

import java.io.IOException;

/**
 * Represents a serializable object. These objects serialize and deserialize themselves through the use of an encoder
 * and decoder.
 *
 * @see com.kicas.rp.util.Encoder Encoder
 * @see com.kicas.rp.util.Decoder Decoder
 */
public interface Serializable {
    /**
     * Serializes any information necessary through the given encoder.
     * @param encoder the encoder.
     * @throws IOException if an I/O error occurs.
     */
    void serialize(Encoder encoder) throws IOException;

    /**
     * Deserializes any necessary information through the given decoder.
     * @param decoder the decoder.
     * @throws IOException if an I/O error occurs.
     */
    void deserialize(Decoder decoder) throws IOException;
}
