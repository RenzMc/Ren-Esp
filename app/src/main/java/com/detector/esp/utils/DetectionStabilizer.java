package com.detector.esp.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DetectionStabilizer {

    private static final int MIN_HIT = 3;
    private static final int MAX_AGE = 4;
    private static final float MATCH_IOU = 0.3f;
    private static final float MOTION_THRESHOLD = 0.0f;
    private static final int MAX_TRAIL = DetectResult.MAX_TRAIL;
    private static final float PREDICT_SECONDS = 0.5f;

    private final List<TrackedObject> trackedObjects = new ArrayList<>();
    private int nextId = 0;

    public List<DetectResult> update(List<DetectResult> currentDetections, float cameraDx, float cameraDy) {

        for (TrackedObject t : trackedObjects) {
            t.matched = false;
        }

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

        for (DetectResult det : unmatched) {
            trackedObjects.add(new TrackedObject(nextId++, det));
        }

        Iterator<TrackedObject> it = trackedObjects.iterator();
        while (it.hasNext()) {
            TrackedObject t = it.next();
            if (!t.matched) {
                t.age++;
                if (t.age > MAX_AGE) it.remove();
            }
        }

        mergeOverlappingTracks();

        List<DetectResult> stable = new ArrayList<>();
        for (TrackedObject t : trackedObjects) {
            if (t.hitCount >= MIN_HIT) {
                stable.add(t.toDetectResult());
            }
        }

        return stable;
    }

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

        float velX = 0, velY = 0;
        float[] trailX = new float[MAX_TRAIL];
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

            float alpha = hitCount <= 1 ? 1.0f : 0.7f;
            left = left * (1 - alpha) + det.left * alpha;
            top = top * (1 - alpha) + det.top * alpha;
            right = right * (1 - alpha) + det.right * alpha;
            bottom = bottom * (1 - alpha) + det.bottom * alpha;

            float newCx = (left + right) / 2f;
            float newCy = (top + bottom) / 2f;

            float rawVx = (newCx - prevCx) - cameraDx;
            float rawVy = (newCy - prevCy) - cameraDy;

            velX = velX * 0.5f + rawVx * 0.5f;
            velY = velY * 0.5f + rawVy * 0.5f;

            boolean isTrackable = (classId == 0) || (classId >= 1 && classId <= 8) || (classId >= 14 && classId <= 23);
            float speed = (float) Math.sqrt(velX * velX + velY * velY);
            if (isTrackable && speed > MOTION_THRESHOLD && hitCount > MIN_HIT) {

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

            r.trailLen = trailLen;
            System.arraycopy(trailX, 0, r.trailX, 0, trailLen);
            System.arraycopy(trailY, 0, r.trailY, 0, trailLen);

            return r;
        }
    }
}
