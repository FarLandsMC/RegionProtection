package com.kicas.rp.util;

import java.io.IOException;

public interface Serializable {
    void serialize(Encoder encoder) throws IOException;

    void deserialize(Decoder decoder) throws IOException;
}
