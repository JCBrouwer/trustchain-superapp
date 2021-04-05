package com.example.federated_ml;

/**
 * Bridge between Essentia music library and federated ml library
 */
public final class Essentia {
    static {
        System.loadLibrary("superappessentia");
    }
    public native static int extractData(String inputPath, String outputPath);
}
