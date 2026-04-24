package com.detector.esp;

import android.content.Context;
import android.util.Log;

import com.detector.esp.detector.YoloDetector;
import com.detector.esp.preprocess.CpuPreprocessor;
import com.detector.esp.utils.DetectResultPool;
import com.detector.esp.utils.DetectionStabilizer;

public class AppPreloader {

    private static final String TAG = "Preloader";

    public static volatile YoloDetector detector;
    public static volatile CpuPreprocessor preprocessor;
    public static volatile DetectResultPool resultPool;
    public static volatile DetectionStabilizer stabilizer;
    public static volatile boolean ready = false;
    public static volatile String currentStep = "";
    public static volatile float progress = 0f;

    public static void preload(Context context) {
        try {
            currentStep = "Loading AI model...";
            progress = 0.1f;
            Log.i(TAG, currentStep);
            detector = new YoloDetector(context);

            currentStep = "Initializing preprocessor...";
            progress = 0.5f;
            Log.i(TAG, currentStep);
            preprocessor = new CpuPreprocessor(detector.getInputSize());

            currentStep = "Allocating buffers...";
            progress = 0.7f;
            Log.i(TAG, currentStep);
            resultPool = new DetectResultPool();
            stabilizer = new DetectionStabilizer();

            currentStep = "System ready";
            progress = 1.0f;
            ready = true;
            Log.i(TAG, "预加载完成");
        } catch (Exception e) {
            Log.e(TAG, "预加载失败", e);
            currentStep = "LOAD FAILED: " + e.getMessage();
        }
    }

    public static void consume() {

    }
}
