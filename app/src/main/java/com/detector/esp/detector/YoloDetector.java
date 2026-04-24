package com.detector.esp.detector;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;

import com.detector.esp.utils.DetectResult;
import com.detector.esp.utils.DetectResultPool;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * YOLOv8n FP16 检测器 — NNAPI Delegate
 *
 * 输入: [1, 320, 320, 3] float32 (归一化 0-1)
 * 输出: [1, 84, 2100] float32
 */
public class YoloDetector {

    private static final String TAG = "YoloDetector";
    private static final String MODEL_FILE = "yolov8n_int8.tflite";
    private static final int INPUT_SIZE = 320;
    private static final float INPUT_SIZE_F = 320.0f;
    private static final int NUM_CLASSES = 80;
    private static final int NUM_BOXES = 2100;
    private volatile float confidenceThreshold = 0.15f;  // 降低阈值，暗光也能识别
    private static final float IOU_THRESHOLD = 0.35f;  // 更严格，防晃动重复框

    // 标签从 Lang 获取（支持中英切换）

    // 类别过滤：person, vehicle(1-8), animal(14-23), object(24-79)
    private volatile boolean enablePerson = true;
    private volatile boolean enableVehicle = true;
    private volatile boolean enableAnimal = true;
    private volatile boolean enableObject = true;

    private final Interpreter interpreter;
    private GpuDelegate gpuDelegate;

    // 预分配 buffer — float32 输入
    private final ByteBuffer inputBuffer;
    private final float[][][] outputBuffer;
    // 预计算 byte→float 查找表，避免每帧 30 万次除法
    private static final float[] BYTE_TO_FLOAT = new float[256];
    static {
        for (int i = 0; i < 256; i++) {
            BYTE_TO_FLOAT[i] = i / 255.0f;
        }
    }

    public YoloDetector(Context context) throws IOException {
        MappedByteBuffer modelBuffer = loadModelFile(context);

        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);

        // GPU Delegate 优先（大部分手机 GPU 比 NNAPI 快）
        boolean delegateOk = false;
        try {
            gpuDelegate = new GpuDelegate();
            options.addDelegate(gpuDelegate);
            delegateOk = true;
            Log.i(TAG, "GPU Delegate 已启用");
        } catch (Exception e) {
            Log.w(TAG, "GPU Delegate 失败: " + e.getMessage());
            gpuDelegate = null;
        }

        // GPU 失败则尝试 NNAPI
        if (!delegateOk) {
            try {
                org.tensorflow.lite.nnapi.NnApiDelegate nnapi =
                        new org.tensorflow.lite.nnapi.NnApiDelegate();
                options.addDelegate(nnapi);
                delegateOk = true;
                Log.i(TAG, "NNAPI Delegate 已启用");
            } catch (Exception e) {
                Log.w(TAG, "NNAPI 也失败: " + e.getMessage());
            }
        }

        if (!delegateOk) {
            Log.i(TAG, "所有硬件加速不可用，使用 CPU 4线程 XNNPACK");
        }

        interpreter = new Interpreter(modelBuffer, options);

        // float32 输入: 1 * 320 * 320 * 3 * 4 bytes
        inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());

        outputBuffer = new float[1][84][NUM_BOXES];

        Log.i(TAG, "模型加载完成: " + MODEL_FILE);
    }

    private MappedByteBuffer loadModelFile(Context context) throws IOException {
        AssetFileDescriptor afd = context.getAssets().openFd(MODEL_FILE);
        FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
        FileChannel channel = fis.getChannel();
        return channel.map(FileChannel.MapMode.READ_ONLY, afd.getStartOffset(), afd.getDeclaredLength());
    }

    /**
     * 从 RGB byte[320*320*3] 执行检测
     * 内部将 uint8 RGB 转为 float32 归一化
     */
    public void detect(byte[] rgbBytes, DetectResultPool pool) {
        pool.beginFrame();

        // RGB uint8 → float32 归一化 [0,1]（查找表，零除法）
        inputBuffer.rewind();
        int len = INPUT_SIZE * INPUT_SIZE * 3;
        for (int i = 0; i < len; i++) {
            inputBuffer.putFloat(BYTE_TO_FLOAT[rgbBytes[i] & 0xFF]);
        }
        inputBuffer.rewind();

        // 推理
        interpreter.run(inputBuffer, outputBuffer);

        // 后处理
        postProcess(outputBuffer[0], pool);
        pool.commitFrame();
    }

    private void postProcess(float[][] output, DetectResultPool pool) {
        List<DetectResult> candidates = new ArrayList<>();

        for (int i = 0; i < NUM_BOXES; i++) {
            float cx = output[0][i];
            float cy = output[1][i];
            float w = output[2][i];
            float h = output[3][i];

            float maxScore = 0f;
            int maxClassId = -1;

            for (int c = 0; c < NUM_CLASSES; c++) {
                float score = output[4 + c][i];
                if (score > maxScore) {
                    maxScore = score;
                    maxClassId = c;
                }
            }

            if (maxScore < confidenceThreshold) continue;

            if (!isClassEnabled(maxClassId)) continue;

            String[] labels = com.detector.esp.utils.Lang.getLabels();
            String label = (maxClassId >= 0 && maxClassId < labels.length)
                    ? labels[maxClassId] : "obj";

            // onnx2tf 导出的模型坐标已经是归一化 [0,1]，不需要除以 INPUT_SIZE
            float left = cx - w / 2f;
            float top = cy - h / 2f;
            float right = cx + w / 2f;
            float bottom = cy + h / 2f;

            DetectResult r = pool.obtain();
            if (r == null) break;
            r.set(clamp(left), clamp(top), clamp(right), clamp(bottom),
                    maxClassId, label, maxScore);
            candidates.add(r);
        }

        // NMS
        Collections.sort(candidates, (a, b) -> Float.compare(b.confidence, a.confidence));

        while (!candidates.isEmpty()) {
            DetectResult best = candidates.remove(0);
            pool.addResult(best);

            Iterator<DetectResult> it = candidates.iterator();
            while (it.hasNext()) {
                DetectResult other = it.next();
                float overlap = iou(best, other);
                if ((best.classId == other.classId && overlap > IOU_THRESHOLD) || overlap > 0.7f) {
                    it.remove();
                }
            }
        }
    }

    private float iou(DetectResult a, DetectResult b) {
        float iL = Math.max(a.left, b.left);
        float iT = Math.max(a.top, b.top);
        float iR = Math.min(a.right, b.right);
        float iB = Math.min(a.bottom, b.bottom);
        float iArea = Math.max(0, iR - iL) * Math.max(0, iB - iT);
        float aArea = (a.right - a.left) * (a.bottom - a.top);
        float bArea = (b.right - b.left) * (b.bottom - b.top);
        float uArea = aArea + bArea - iArea;
        return uArea > 0 ? iArea / uArea : 0f;
    }

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    public void close() {
        interpreter.close();
        if (gpuDelegate != null) gpuDelegate.close();
    }

    public void setEnabledCategories(boolean person, boolean vehicle, boolean animal, boolean object) {
        this.enablePerson = person;
        this.enableVehicle = vehicle;
        this.enableAnimal = animal;
        this.enableObject = object;
    }

    private boolean isClassEnabled(int classId) {
        if (classId == 0) return enablePerson;
        if (classId >= 1 && classId <= 8) return enableVehicle;
        if (classId >= 14 && classId <= 23) return enableAnimal;
        return enableObject;  // 9-13 + 24-79
    }

    public int getInputSize() { return INPUT_SIZE; }
    public float getConfidenceThreshold() { return confidenceThreshold; }
    public void setConfidenceThreshold(float t) { confidenceThreshold = Math.max(0.05f, Math.min(0.9f, t)); }
}
