package com.example.federated_ml;

public final class Essentia {
    static {
        System.loadLibrary("superappessentia");
    }
    public native static int extractData(String inputPath, String outputPath);
}
