package com.detector.esp.utils;

import java.util.ArrayList;
import java.util.List;

public class DetectResultPool {

    private static final int POOL_SIZE = 64;
    private final DetectResult[] pool;
    private int index;

    private final List<DetectResult> currentFrame = new ArrayList<>(POOL_SIZE);
    private volatile List<DetectResult> displayFrame = new ArrayList<>(0);

    public DetectResultPool() {
        pool = new DetectResult[POOL_SIZE];
        for (int i = 0; i < POOL_SIZE; i++) {
            pool[i] = new DetectResult();
        }
        index = 0;
    }

    public void beginFrame() {
        currentFrame.clear();
        index = 0;
    }

    public DetectResult obtain() {
        if (index >= POOL_SIZE) return null;
        DetectResult r = pool[index++];
        r.reset();
        return r;
    }

    public void addResult(DetectResult r) {
        if (r != null) {
            currentFrame.add(r);
        }
    }

    public void commitFrame() {

        displayFrame = new ArrayList<>(currentFrame);
    }

    public List<DetectResult> getDisplayResults() {
        return displayFrame;
    }
}
