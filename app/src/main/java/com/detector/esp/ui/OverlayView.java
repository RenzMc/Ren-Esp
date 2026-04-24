package com.detector.esp.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.View;

import com.detector.esp.utils.DetectResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OverlayView extends View implements Choreographer.FrameCallback {

    private static final int COLOR_PERSON = 0xFFFF1744;
    private static final int COLOR_CAR = 0xFF00E676;
    private static final int COLOR_ESP_GREEN = 0xFF00E664;
    private static final int COLOR_ANIMAL = 0xFFFFD600;
    private static final int COLOR_OBJECT = 0xFF00BCD4;

    private static int getColorForClass(int classId) {
        if (classId == 0) return COLOR_PERSON;
        if (classId >= 1 && classId <= 8) return COLOR_CAR;
        if (classId >= 14 && classId <= 23) return COLOR_ANIMAL;
        return COLOR_OBJECT;
    }

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint();
    private final Paint fillPaint = new Paint();
    private final Paint labelBgPaint = new Paint();
    private final Paint labelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudBgPaint = new Paint();
    private final Paint hudBorderPaint = new Paint();
    private final Paint hudTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudInfoPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hudDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint snaplinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint distPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint healthBarBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint healthBarFg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint radarBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint radarGridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint radarDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint radarSweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint predPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint predArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long startTime = System.currentTimeMillis();

    private volatile double gpsLat = 0, gpsLon = 0;
    private volatile float gpsSpeed = 0;
    private volatile int gpsSatellites = 0;
    private volatile boolean gpsAvailable = false;

    private List<DetectResult> prevResults = Collections.emptyList();
    private List<DetectResult> currentResults = Collections.emptyList();
    private long prevTime = 0;
    private long currentTime = 0;

    private final List<float[]> interpBoxes = new ArrayList<>();
    private final List<String> interpLabels = new ArrayList<>();
    private final List<DetectResult> interpResults = new ArrayList<>();

    private volatile int detectFps;
    private volatile float latencyMs;
    private volatile float currentZoom = 1.0f;
    private volatile float vFovTanHalf = 0.839f;
    private boolean rendering = false;

    private int renderFrameCount = 0;
    private long lastRenderFpsTime = 0;
    private int renderFps = 0;

    public OverlayView(Context context) { this(context, null); }
    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        initPaints();
    }

    private void initPaints() {
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(2.5f);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(6f);
        glowPaint.setMaskFilter(new BlurMaskFilter(12f, BlurMaskFilter.Blur.OUTER));

        fillPaint.setStyle(Paint.Style.FILL);

        labelBgPaint.setColor(Color.argb(200, 0, 0, 0));
        labelBgPaint.setStyle(Paint.Style.FILL);

        labelTextPaint.setTextSize(32f);
        labelTextPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        crossPaint.setStyle(Paint.Style.STROKE);
        crossPaint.setStrokeWidth(1.5f);

        hudBgPaint.setColor(Color.argb(160, 0, 0, 0));
        hudBgPaint.setStyle(Paint.Style.FILL);

        hudBorderPaint.setColor(Color.argb(100, 0, 255, 0));
        hudBorderPaint.setStyle(Paint.Style.STROKE);
        hudBorderPaint.setStrokeWidth(1.5f);

        hudTitlePaint.setColor(COLOR_ESP_GREEN);
        hudTitlePaint.setTextSize(26f);
        hudTitlePaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

        hudInfoPaint.setColor(Color.WHITE);
        hudInfoPaint.setTextSize(22f);
        hudInfoPaint.setTypeface(Typeface.MONOSPACE);

        hudDotPaint.setStyle(Paint.Style.FILL);

        snaplinePaint.setStyle(Paint.Style.STROKE);
        snaplinePaint.setStrokeWidth(1.5f);

        headDotPaint.setStyle(Paint.Style.FILL);

        distPaint.setTextSize(22f);
        distPaint.setTypeface(Typeface.MONOSPACE);
        distPaint.setTextAlign(Paint.Align.CENTER);

        healthBarBg.setColor(0x66000000);
        healthBarBg.setStyle(Paint.Style.FILL);
        healthBarFg.setStyle(Paint.Style.FILL);

        radarBgPaint.setColor(0x88000000);
        radarBgPaint.setStyle(Paint.Style.FILL);

        radarGridPaint.setColor(0x3300FF00);
        radarGridPaint.setStyle(Paint.Style.STROKE);
        radarGridPaint.setStrokeWidth(1f);

        radarDotPaint.setStyle(Paint.Style.FILL);

        radarSweepPaint.setColor(0x4400FF00);
        radarSweepPaint.setStyle(Paint.Style.FILL);

        lockPaint.setColor(0xFFFF1744);
        lockPaint.setStyle(Paint.Style.STROKE);
        lockPaint.setStrokeWidth(2f);
        lockPaint.setTextSize(20f);
        lockPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        lockPaint.setTextAlign(Paint.Align.CENTER);

        trailPaint.setStyle(Paint.Style.STROKE);
        trailPaint.setStrokeCap(Paint.Cap.ROUND);
        trailPaint.setStrokeJoin(Paint.Join.ROUND);

        predPaint.setStyle(Paint.Style.STROKE);
        predPaint.setStrokeWidth(2f);
        predPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{8, 6}, 0));

        predArrowPaint.setStyle(Paint.Style.FILL);
    }

    public void startRendering() {
        if (!rendering) {
            rendering = true;
            lastRenderFpsTime = System.currentTimeMillis();
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    public void stopRendering() {
        rendering = false;
    }

    private long lastFrameNanos = 0;
    private static final long FRAME_INTERVAL_NANOS = 33_333_333L;

    @Override
    public void doFrame(long frameTimeNanos) {
        if (!rendering) return;

        if (frameTimeNanos - lastFrameNanos >= FRAME_INTERVAL_NANOS) {
            lastFrameNanos = frameTimeNanos;

            renderFrameCount++;
            long now = System.currentTimeMillis();
            if (now - lastRenderFpsTime >= 1000) {
                renderFps = renderFrameCount;
                renderFrameCount = 0;
                lastRenderFpsTime = now;
            }

            invalidate();
        }

        Choreographer.getInstance().postFrameCallback(this);
    }

    public void setCurrentZoom(float zoom) { this.currentZoom = zoom; }
    public void setFov(float vFovDegrees) { this.vFovTanHalf = (float) Math.tan(Math.toRadians(vFovDegrees / 2)); }

    public void setGpsData(double lat, double lon, float speed, int satellites) {
        this.gpsLat = lat;
        this.gpsLon = lon;
        this.gpsSpeed = speed;
        this.gpsSatellites = satellites;
        this.gpsAvailable = true;
    }

    public void setGpsSpeed(float speed) {
        this.gpsSpeed = speed;
    }

    public void setGpsCoord(double lat, double lon) {
        this.gpsLat = lat;
        this.gpsLon = lon;
        this.gpsAvailable = true;
    }

    public void setGpsSatellites(int satellites) {
        this.gpsSatellites = satellites;
    }

    public void setResults(List<DetectResult> results, int fps, float latencyMs) {
        synchronized (this) {
            prevResults = currentResults;
            prevTime = currentTime;
            currentResults = new ArrayList<>(results);
            currentTime = System.currentTimeMillis();
        }
        this.detectFps = fps;
        this.latencyMs = latencyMs;

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int cw = getWidth();
        int ch = getHeight();
        if (cw == 0 || ch == 0) return;

        computeInterpolation();

        float pulse = (float)(0.6 + 0.4 * Math.sin((System.currentTimeMillis() - startTime) * 0.005));

        for (float[] box : interpBoxes) {
            drawSnapline(canvas, box, cw, ch, pulse);
        }

        for (int i = 0; i < interpBoxes.size(); i++) {
            float[] box = interpBoxes.get(i);
            drawESPBox(canvas, box, interpLabels.get(i), cw, ch, pulse);
        }

        for (int i = 0; i < interpResults.size() && i < interpBoxes.size(); i++) {
            drawTrailAndPrediction(canvas, interpResults.get(i), interpBoxes.get(i), cw, ch);
        }

        drawLockIndicator(canvas, cw, ch, pulse);

        drawRadar(canvas, cw, ch);

        drawHUD(canvas, interpBoxes.size());
    }

    private void computeInterpolation() {
        interpBoxes.clear();
        interpLabels.clear();
        interpResults.clear();

        List<DetectResult> cur;
        List<DetectResult> prev;
        long ct, pt;

        synchronized (this) {
            cur = currentResults;
            prev = prevResults;
            ct = currentTime;
            pt = prevTime;
        }

        if (cur.isEmpty()) return;

        long now = System.currentTimeMillis();
        long interval = ct - pt;

        float t;
        if (interval <= 0 || prev.isEmpty()) {
            t = 1.0f;
        } else {
            t = (float)(now - pt) / interval;
            t = Math.max(0f, Math.min(1.5f, t));
        }

        for (DetectResult c : cur) {
            float bl = c.left, bt = c.top, br = c.right, bb = c.bottom;

            if (t < 1.0f && !prev.isEmpty()) {

                DetectResult bestPrev = findBestMatch(c, prev);
                if (bestPrev != null) {

                    bl = bestPrev.left + (c.left - bestPrev.left) * t;
                    bt = bestPrev.top + (c.top - bestPrev.top) * t;
                    br = bestPrev.right + (c.right - bestPrev.right) * t;
                    bb = bestPrev.bottom + (c.bottom - bestPrev.bottom) * t;
                }
            } else if (t > 1.0f && !prev.isEmpty()) {

                DetectResult bestPrev = findBestMatch(c, prev);
                if (bestPrev != null) {
                    float extraT = t - 1.0f;
                    bl = c.left + (c.left - bestPrev.left) * extraT;
                    bt = c.top + (c.top - bestPrev.top) * extraT;
                    br = c.right + (c.right - bestPrev.right) * extraT;
                    bb = c.bottom + (c.bottom - bestPrev.bottom) * extraT;
                }
            }

            bl = Math.max(0, Math.min(1, bl));
            bt = Math.max(0, Math.min(1, bt));
            br = Math.max(0, Math.min(1, br));
            bb = Math.max(0, Math.min(1, bb));

            interpBoxes.add(new float[]{bl, bt, br, bb, c.classId, c.confidence});
            interpLabels.add(c.label + " " + (int)(c.confidence * 100) + "%");
            interpResults.add(c);
        }
    }

    private DetectResult findBestMatch(DetectResult det, List<DetectResult> prev) {
        float bestIou = 0.2f;
        DetectResult best = null;
        for (DetectResult p : prev) {
            if (p.classId != det.classId) continue;
            float iou = calcIou(det, p);
            if (iou > bestIou) {
                bestIou = iou;
                best = p;
            }
        }
        return best;
    }

    private float calcIou(DetectResult a, DetectResult b) {
        float iL = Math.max(a.left, b.left);
        float iT = Math.max(a.top, b.top);
        float iR = Math.min(a.right, b.right);
        float iB = Math.min(a.bottom, b.bottom);
        float iArea = Math.max(0, iR - iL) * Math.max(0, iB - iT);
        float aArea = (a.right - a.left) * (a.bottom - a.top);
        float bArea = (b.right - b.left) * (b.bottom - b.top);
        float u = aArea + bArea - iArea;
        return u > 0 ? iArea / u : 0f;
    }

    private void drawTrailAndPrediction(Canvas canvas, DetectResult result, float[] box, int cw, int ch) {
        int color = getColorForClass((int) box[4]);

        if (result.trailLen >= 2) {
            for (int i = 1; i < result.trailLen; i++) {
                float alpha = (float) i / result.trailLen;
                trailPaint.setColor(color);
                trailPaint.setAlpha((int)(200 * alpha));
                trailPaint.setStrokeWidth(2f + alpha * 3f);
                canvas.drawLine(
                    result.trailX[i-1] * cw, result.trailY[i-1] * ch,
                    result.trailX[i] * cw, result.trailY[i] * ch,
                    trailPaint);
            }
        }

        float speed = (float) Math.sqrt(result.velX * result.velX + result.velY * result.velY);
        if (speed > 0.003f) {
            float cx = (box[0] + box[2]) / 2f * cw;
            float cy = (box[1] + box[3]) / 2f * ch;
            float px = result.predX * cw;
            float py = result.predY * ch;

            int predColor;
            if (color == COLOR_PERSON) predColor = 0xFF00FFFF;
            else if (color == COLOR_CAR) predColor = 0xFFFF00FF;
            else if (color == COLOR_ANIMAL) predColor = 0xFF8000FF;
            else predColor = 0xFFFF4444;

            predPaint.setColor(predColor);
            predPaint.setAlpha(220);
            predPaint.setStrokeWidth(3f);
            canvas.drawLine(cx, cy, px, py, predPaint);

            float angle = (float) Math.atan2(py - cy, px - cx);
            float arrowLen = 15;
            predArrowPaint.setColor(predColor);
            predArrowPaint.setAlpha(240);
            android.graphics.Path arrow = new android.graphics.Path();
            arrow.moveTo(px, py);
            arrow.lineTo(px - arrowLen * (float) Math.cos(angle - 0.4f),
                         py - arrowLen * (float) Math.sin(angle - 0.4f));
            arrow.lineTo(px - arrowLen * (float) Math.cos(angle + 0.4f),
                         py - arrowLen * (float) Math.sin(angle + 0.4f));
            arrow.close();
            canvas.drawPath(arrow, predArrowPaint);
        }
    }

    private void drawSnapline(Canvas canvas, float[] box, int cw, int ch, float pulse) {
        float targetX = (box[0] + box[2]) / 2f * cw;
        float targetY = box[1] * ch;
        snaplinePaint.setColor(getColorForClass((int) box[4]));
        snaplinePaint.setAlpha(160);
        snaplinePaint.setStrokeWidth(5f);
        canvas.drawLine(cw / 2f, 0, targetX, targetY, snaplinePaint);
    }

    private void drawLockIndicator(Canvas canvas, int cw, int ch, float pulse) {
        if (interpBoxes.isEmpty()) return;

        float centerX = 0.5f, centerY = 0.5f;
        float minDist = Float.MAX_VALUE;
        float[] closest = null;

        for (float[] box : interpBoxes) {
            float bx = (box[0] + box[2]) / 2f;
            float by = (box[1] + box[3]) / 2f;
            float dist = (bx - centerX) * (bx - centerX) + (by - centerY) * (by - centerY);
            if (dist < minDist) { minDist = dist; closest = box; }
        }

        if (closest != null && minDist < 0.06f) {
            float cx = (closest[0] + closest[2]) / 2f * cw;
            float topY = closest[1] * ch;

            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                lockPaint.setStyle(Paint.Style.FILL);
                lockPaint.setAlpha((int)(255 * pulse));
                String lockText = com.detector.esp.utils.Lang.isEnglish() ? "◆ LOCKED ◆" : "◆ 锁定 ◆";
                canvas.drawText(lockText, cx, topY - 50, lockPaint);
                lockPaint.setStyle(Paint.Style.STROKE);
            }
        }
    }

    private void drawRadar(Canvas canvas, int cw, int ch) {
        float radarR = 110;
        float radarX = cw - 130;
        float radarY = radarR + 20;

        android.graphics.RectF oval = new android.graphics.RectF(
                radarX - radarR, radarY - radarR, radarX + radarR, radarY + radarR);
        canvas.drawArc(oval, 180, 180, true, radarBgPaint);

        for (float f : new float[]{0.33f, 0.66f, 1.0f}) {
            android.graphics.RectF gridOval = new android.graphics.RectF(
                    radarX - radarR * f, radarY - radarR * f,
                    radarX + radarR * f, radarY + radarR * f);
            canvas.drawArc(gridOval, 180, 180, false, radarGridPaint);
        }

        canvas.drawLine(radarX - radarR, radarY, radarX + radarR, radarY, radarGridPaint);

        canvas.drawLine(radarX, radarY, radarX, radarY - radarR, radarGridPaint);

        float d45 = radarR * 0.707f;
        canvas.drawLine(radarX, radarY, radarX - d45, radarY - d45, radarGridPaint);
        canvas.drawLine(radarX, radarY, radarX + d45, radarY - d45, radarGridPaint);

        float sweepAngle = 180f + ((System.currentTimeMillis() - startTime) * 0.06f) % 180f;
        float sweepEndX = radarX + radarR * (float) Math.cos(Math.toRadians(sweepAngle));
        float sweepEndY = radarY + radarR * (float) Math.sin(Math.toRadians(sweepAngle));
        radarSweepPaint.setStrokeWidth(2f);
        radarSweepPaint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(radarX, radarY, sweepEndX, sweepEndY, radarSweepPaint);

        radarSweepPaint.setStyle(Paint.Style.FILL);
        canvas.drawArc(oval, sweepAngle - 20, 20, true, radarSweepPaint);

        radarDotPaint.setColor(0xFF00FF00);
        canvas.drawCircle(radarX, radarY, 4, radarDotPaint);

        int idx = 0;
        for (float[] box : interpBoxes) {
            float bx = (box[0] + box[2]) / 2f - 0.5f;

            float radarBx = bx * 3.5f;
            radarBx = Math.max(-0.95f, Math.min(0.95f, radarBx));

            int classId = (int) box[4];
            float realH = classId == 0 ? 1.7f : 1.5f;
            float boxPixelH = (box[3] - box[1]) * ch;
            float estDist = realH / (2f * vFovTanHalf * (boxPixelH / ch)) * currentZoom;
            estDist = Math.max(1f, Math.min(100f, estDist));

            float distNorm = (float)(Math.log10(estDist) / Math.log10(100));

            float dotX = radarX + radarBx * radarR * 0.9f;
            float dotY = radarY - distNorm * radarR * 0.9f;

            float dx = dotX - radarX;
            float dy = dotY - radarY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist > radarR - 4) {
                dotX = radarX + dx / dist * (radarR - 4);
                dotY = radarY + dy / dist * (radarR - 4);
            }
            if (dotY > radarY) dotY = radarY;

            int dotColor = getColorForClass((int) box[4]);
            radarDotPaint.setColor(dotColor);
            canvas.drawCircle(dotX, dotY, 5, radarDotPaint);

            if (idx < interpResults.size()) {
                DetectResult dr = interpResults.get(idx);
                if (dr.trailLen >= 2) {
                    radarDotPaint.setColor(dotColor);
                    for (int ti = 0; ti < dr.trailLen; ti++) {
                        float tbx = dr.trailX[ti] - 0.5f;
                        float tDistNorm = distNorm;
                        float tdx = radarX + tbx * 3.5f * radarR * 0.9f;
                        float tdy = radarY - tDistNorm * radarR * 0.9f;

                        float td = (float) Math.sqrt((tdx-radarX)*(tdx-radarX)+(tdy-radarY)*(tdy-radarY));
                        if (td > radarR - 4) { tdx = radarX+(tdx-radarX)/td*(radarR-4); tdy = radarY+(tdy-radarY)/td*(radarR-4); }
                        if (tdy > radarY) tdy = radarY;
                        radarDotPaint.setAlpha((int)(80 + 120f * ti / dr.trailLen));
                        canvas.drawCircle(tdx, tdy, 2, radarDotPaint);
                    }
                    radarDotPaint.setAlpha(255);
                }
            }
            idx++;
        }
    }

    private void drawESPBox(Canvas canvas, float[] box, String label, int cw, int ch, float pulse) {
        float left = box[0] * cw;
        float top = box[1] * ch;
        float right = box[2] * cw;
        float bottom = box[3] * ch;
        int classId = (int) box[4];
        float w = right - left;
        float h = bottom - top;

        if (w < 10 || h < 10) return;

        int color = getColorForClass(classId);

        fillPaint.setColor(Color.argb(20, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawRect(left, top, right, bottom, fillPaint);

        glowPaint.setColor(color);
        glowPaint.setAlpha((int)(60 * pulse));
        glowPaint.setStrokeWidth(6f * pulse);
        canvas.drawRect(left, top, right, bottom, glowPaint);

        boxPaint.setColor(color);
        canvas.drawRect(left, top, right, bottom, boxPaint);

        float cornerLen = Math.min(w, h) * 0.15f;
        boxPaint.setStrokeWidth(4f);
        canvas.drawLine(left, top, left + cornerLen, top, boxPaint);
        canvas.drawLine(left, top, left, top + cornerLen, boxPaint);
        canvas.drawLine(right, top, right - cornerLen, top, boxPaint);
        canvas.drawLine(right, top, right, top + cornerLen, boxPaint);
        canvas.drawLine(left, bottom, left + cornerLen, bottom, boxPaint);
        canvas.drawLine(left, bottom, left, bottom - cornerLen, boxPaint);
        canvas.drawLine(right, bottom, right - cornerLen, bottom, boxPaint);
        canvas.drawLine(right, bottom, right, bottom - cornerLen, boxPaint);
        boxPaint.setStrokeWidth(2.5f);

        float cx = left + w / 2;
        float cy = top + h / 2;
        float crossLen = Math.min(w, h) * 0.08f;
        crossPaint.setColor(Color.argb(180, Color.red(color), Color.green(color), Color.blue(color)));
        canvas.drawLine(cx - crossLen, cy, cx + crossLen, cy, crossPaint);
        canvas.drawLine(cx, cy - crossLen, cx, cy + crossLen, crossPaint);

        crossPaint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(cx, cy, crossLen * 1.5f, crossPaint);
        crossPaint.setStyle(Paint.Style.STROKE);

        float realHeight = classId == 0 ? 1.7f : 1.5f;
        float boxRatio = h / (float) ch;
        float estDist = realHeight / (2f * vFovTanHalf * boxRatio) * currentZoom;
        estDist = Math.max(0.5f, Math.min(999, estDist));
        String distStr = estDist < 10 ? String.format("%.1fm", estDist) : String.format("%.0fm", estDist);

        String fullLabel = label + " " + distStr;
        float textWidth = labelTextPaint.measureText(fullLabel);
        float pad = 6f;
        float labelTop2, labelBottom2, textY;
        if (top > 44) {
            labelTop2 = top - 40;
            labelBottom2 = top;
            textY = top - 8;
        } else {
            labelTop2 = top;
            labelBottom2 = top + 40;
            textY = top + 32;
        }
        canvas.drawRect(left, labelTop2, Math.min(left + textWidth + pad * 2, cw), labelBottom2, labelBgPaint);
        labelTextPaint.setColor(color);
        labelTextPaint.setFakeBoldText(true);
        canvas.drawText(fullLabel, left + pad, textY, labelTextPaint);
        labelTextPaint.setFakeBoldText(false);
    }

    private void drawHUD(Canvas canvas, int targetCount) {

        float hudBottom = gpsAvailable ? 256 : 168;
        canvas.drawRoundRect(16, 16, 340, hudBottom, 8, 8, hudBgPaint);
        canvas.drawRoundRect(16, 16, 340, hudBottom, 8, 8, hudBorderPaint);

        canvas.drawText("[ESP V2]", 28, 48, hudTitlePaint);
        canvas.drawText("Render: " + renderFps + " fps", 28, 80, hudInfoPaint);
        canvas.drawText(String.format("Detect: %d fps %.0fms", detectFps, latencyMs), 28, 108, hudInfoPaint);
        canvas.drawText((com.detector.esp.utils.Lang.isEnglish() ? "Targets: " : "目标: ") + targetCount, 28, 136, hudInfoPaint);

        if (gpsAvailable) {

            hudBorderPaint.setAlpha(60);
            canvas.drawLine(28, 152, 328, 152, hudBorderPaint);
            hudBorderPaint.setAlpha(100);

            hudInfoPaint.setTextSize(18f);
            canvas.drawText(String.format("%.5f, %.5f", gpsLat, gpsLon), 28, 174, hudInfoPaint);

            float speedKmh = gpsSpeed * 3.6f;
            canvas.drawText(String.format("%.1f km/h  SAT: %d", speedKmh, gpsSatellites), 28, 198, hudInfoPaint);

            hudDotPaint.setColor(gpsSatellites >= 6 ? Color.GREEN : gpsSatellites >= 3 ? Color.YELLOW : Color.RED);
            canvas.drawCircle(318, 190, 5, hudDotPaint);

            hudInfoPaint.setTextSize(22f);
        }

        hudDotPaint.setColor(renderFps > 45 ? Color.GREEN : renderFps > 20 ? Color.YELLOW : Color.RED);
        canvas.drawCircle(318, 42, 6, hudDotPaint);
    }
}
