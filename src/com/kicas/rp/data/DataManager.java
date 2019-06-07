package com.kicas.rp.data;

import java.io.File;

public class DataManager {
    private final File rootDir;
    private int regionId;

    public DataManager(File rootDir) {
        this.rootDir = rootDir;
    }
}
