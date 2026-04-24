package com.detector.esp.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 检测结果稳定器 + 轨迹追踪
 *
 * 1. IOU 匹配跨帧同一物体
 * 2. 陀螺仪补偿后计算真实运动
 * 3. 记录轨迹 + 预测未来位置
 */
public class DetectionStabilizer {

    private static final int MIN_HIT = 3;
    private static final int MAX_AGE = 4;
    private static final float MATCH_IOU = 0.3f;
    private static final float MOTION_THRESHOLD = 0.0f;  // 不过滤，所有移动都记录
    private static final int MAX_TRAIL = DetectResult.MAX_TRAIL;
    private static final float PREDICT_SECONDS = 0.5f;  // 预测 0.5 秒后位置

    private final List<TrackedObject> trackedObjects = new ArrayList<>();
    private int nextId = 0;

    /**
     * 更新：输入当前帧检测 + 相机运动补偿
     * @param cameraDx 相机水平位移（归一化）
     * @param cameraDy 相机垂直位移（归一化）
     */
    public List<DetectResult> update(List<DetectResult> currentDetections, float cameraDx, float cameraDy) {
        // 1. 标记未匹配
        for (TrackedObject t : trackedObjects) {
            t.matched = false;
        }

        // 2. 保存相机运动量，用于轨迹计算（不改变检测框位置）
        // 3. 贪心匹配
        List<DetectResult> unmatched = new ArrayList<>(currentDetections);

        for (TrackedObject tracked : trackedObjects) {
            float bestIou = 0f;
            DetectResult bestMatch = null;

            for (DetectResult det : unmatched) {
                if (det.classId != tracked.classId) continue;
                float iou = calcIou(tracked, det);
                if (iou > bestIou) {
                    bestIou = iou;
                    bestMatch = det;
                }
            }

            if (bestIou >= MATCH_IOU && bestMatch != null) {
                tracked.updateWithMotion(bestMatch, cameraDx, cameraDy);
                tracked.matched = true;
                unmatched.remove(bestMatch);
            }
        }

        // 4. 新目标
        for (DetectResult det : unmatched) {
            trackedObjects.add(new TrackedObject(nextId++, det));
        }

        // 5. 老化移除
        Iterator<TrackedObject> it = trackedObjects.iterator();
        while (it.hasNext()) {
            TrackedObject t = it.next();
            if (!t.matched) {
                t.age++;
                if (t.age > MAX_AGE) it.remove();
            }
        }

        // 6. 合并重叠
        mergeOverlappingTracks();

        // 7. 输出
        List<DetectResult> stable = new ArrayList<>();
        for (TrackedObject t : trackedObjects) {
            if (t.hitCount >= MIN_HIT) {
                stable.add(t.toDetectResult());
            }
        }

        return stable;
    }

    /** 兼容旧接口（无补偿） */
    public List<DetectResult> update(List<DetectResult> currentDetections) {
        return update(currentDetections, 0, 0);
    }

    public void reset() {
        trackedObjects.clear();
    }

    private void mergeOverlappingTracks() {
        for (int i = 0; i < trackedObjects.size(); i++) {
            TrackedObject a = trackedObjects.get(i);
            for (int j = i + 1; j < trackedObjects.size(); j++) {
                TrackedObject b = trackedObjects.get(j);
                if (a.classId != b.classId) continue;
                float iou = calcIouTracks(a, b);
                if (iou > 0.3f) {
                    if (a.hitCount >= b.hitCount) {
                        trackedObjects.remove(j); j--;
                    } else {
                        trackedObjects.remove(i); i--; break;
                    }
                }
            }
        }
    }

    private float calcIouTracks(TrackedObject a, TrackedObject b) {
        return calcIouRect(a.left, a.top, a.right, a.bottom, b.left, b.top, b.right, b.bottom);
    }

    private float calcIou(TrackedObject t, DetectResult d) {
        return calcIouRect(t.left, t.top, t.right, t.bottom, d.left, d.top, d.right, d.bottom);
    }

    private float calcIouRect(float aL, float aT, float aR, float aB,
                              float bL, float bT, float bR, float bB) {
        float iL = Math.max(aL, bL), iT = Math.max(aT, bT);
        float iR = Math.min(aR, bR), iB = Math.min(aB, bB);
        float iArea = Math.max(0, iR - iL) * Math.max(0, iB - iT);
        float aArea = (aR - aL) * (aB - aT);
        float bArea = (bR - bL) * (bB - bT);
        float u = aArea + bArea - iArea;
        return u > 0 ? iArea / u : 0f;
    }

    private static class TrackedObject {
        int id;
        float left, top, right, bottom;
        int classId;
        String label;
        float confidence;

        int hitCount = 1;
        int age = 0;
        boolean matched = false;

        // 运动追踪
        float velX = 0, velY = 0;  // 平滑后的真实速度
        float[] trailX = new float[MAX_TRAIL];  // 中心点轨迹
        float[] trailY = new float[MAX_TRAIL];
        int trailLen = 0;

        TrackedObject(int id, DetectResult det) {
            this.id = id;
            this.classId = det.classId;
            this.label = det.label;
            this.confidence = det.confidence;
            this.left = det.left;
            this.top = det.top;
            this.right = det.right;
            this.bottom = det.bottom;
        }

        void updateWithMotion(DetectResult det, float cameraDx, float cameraDy) {
            float prevCx = (left + right) / 2f;
            float prevCy = (top + bottom) / 2f;

            // 平滑位置（检测框正常跟踪，不做补偿）
            float alpha = hitCount <= 1 ? 1.0f : 0.7f;
            left = left * (1 - alpha) + det.left * alpha;
            top = top * (1 - alpha) + det.top * alpha;
            right = right * (1 - alpha) + det.right * alpha;
            bottom = bottom * (1 - alpha) + det.bottom * alpha;

            float newCx = (left + right) / 2f;
            float newCy = (top + bottom) / 2f;

            // 画面上的位移 = 真实运动 + 相机运动
            // 真实运动 = 画面位移 - 相机运动
            float rawVx = (newCx - prevCx) - cameraDx;
            float rawVy = (newCy - prevCy) - cameraDy;

            // 平滑速度 (EMA)
            velX = velX * 0.5f + rawVx * 0.5f;
            velY = velY * 0.5f + rawVy * 0.5f;

            // 只有真实运动超过阈值才记录轨迹
            // 物品类（9-13, 24-79）不记录轨迹，只有人/车辆/动物记录
            boolean isTrackable = (classId == 0) || (classId >= 1 && classId <= 8) || (classId >= 14 && classId <= 23);
            float speed = (float) Math.sqrt(velX * velX + velY * velY);
            if (isTrackable && speed > MOTION_THRESHOLD && hitCount > MIN_HIT) {
                // 移位，腾出位置给新点
                if (trailLen >= MAX_TRAIL) {
                    System.arraycopy(trailX, 1, trailX, 0, MAX_TRAIL - 1);
                    System.arraycopy(trailY, 1, trailY, 0, MAX_TRAIL - 1);
                    trailLen = MAX_TRAIL - 1;
                }
                trailX[trailLen] = newCx;
                trailY[trailLen] = newCy;
                trailLen++;
            }

            this.classId = det.classId;
            this.label = det.label;
            this.confidence = det.confidence;
            this.hitCount++;
            this.age = 0;
        }

        DetectResult toDetectResult() {
            DetectResult r = new DetectResult();
            r.set(left, top, right, bottom, classId, label, confidence);
            r.velX = velX;
            r.velY = velY;

            float cx = (left + right) / 2f;
            float cy = (top + bottom) / 2f;
            float fps = 15f;
            r.predX = cx + velX * PREDICT_SECONDS * fps;
            r.predY = cy + velY * PREDICT_SECONDS * fps;

            // 复制轨迹
            r.trailLen = trailLen;
            System.arraycopy(trailX, 0, r.trailX, 0, trailLen);
            System.arraycopy(trailY, 0, r.trailY, 0, trailLen);

            return r;
        }
    }
}
