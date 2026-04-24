package com.detector.esp.camera;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

public class CameraHelper {

    private static final String TAG = "CameraHelper";

    public interface PhotoCallback {
        void onPhotoTaken(Bitmap bitmap);
    }

    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private Handler mainHandler;
    private CaptureRequest.Builder previewBuilder;

    private final AtomicReference<Image> latestFrame = new AtomicReference<>(null);

    private final TextureView textureView;
    private final Context context;
    private Size previewSize;
    private Size analysisSize;
    private Size captureSize;
    private ImageReader jpegReader;

    private Rect sensorArraySize;
    private int sensorOrientation = 90;
    private float currentZoom = 1.0f;
    private float hFovDegrees = 70f;
    private float vFovDegrees = 50f;
    private float maxZoom = 1.0f;
    private static final float MAX_DIGITAL_ZOOM = 1000.0f;

    public CameraHelper(Context context, TextureView textureView) {
        this.context = context;
        this.textureView = textureView;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void start() {
        cameraThread = new HandlerThread("CameraThread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override public void onSurfaceTextureAvailable(SurfaceTexture s, int w, int h) { openCamera(); }
                @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s, int w, int h) {}
                @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { return true; }
                @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {}
            });
        }
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = findBackCamera(manager);
            if (cameraId == null) {
                Log.e(TAG, "未找到后置摄像头");
                return;
            }

            CameraCharacteristics chars = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            previewSize = chooseHighResSize(map);
            analysisSize = chooseLowResSize(map);
            captureSize = chooseMaxJpegSize(map);

            sensorArraySize = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            Integer orientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (orientation != null) sensorOrientation = orientation;
            Float maxZoomVal = chars.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            maxZoom = (maxZoomVal != null) ? maxZoomVal : 10.0f;

            float[] focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
            android.util.SizeF sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
            if (focalLengths != null && focalLengths.length > 0 && sensorSize != null) {
                float focalLen = focalLengths[0];
                hFovDegrees = (float) Math.toDegrees(2 * Math.atan(sensorSize.getWidth() / (2 * focalLen)));
                vFovDegrees = (float) Math.toDegrees(2 * Math.atan(sensorSize.getHeight() / (2 * focalLen)));
            }

            Log.i(TAG, "预览: " + previewSize + " 分析: " + analysisSize
                    + " 拍照: " + captureSize
                    + " 传感器旋转: " + sensorOrientation + "° 硬件变焦: " + maxZoom + "x"
                    + " FOV: " + String.format("%.1f°x%.1f°", hFovDegrees, vFovDegrees));

            imageReader = ImageReader.newInstance(
                    analysisSize.getWidth(), analysisSize.getHeight(),
                    ImageFormat.YUV_420_888, 4
            );
            imageReader.setOnImageAvailableListener(reader -> {
                try {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        Image old = latestFrame.getAndSet(image);
                        if (old != null) old.close();
                    }
                } catch (IllegalStateException e) {

                }
            }, cameraHandler);

            jpegReader = ImageReader.newInstance(
                    captureSize.getWidth(), captureSize.getHeight(),
                    ImageFormat.JPEG, 2);
            Log.i(TAG, "JPEG拍照 ImageReader: " + captureSize);

            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }

            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraDevice = camera;
                    createCaptureSession();
                }
                @Override public void onDisconnected(@NonNull CameraDevice camera) { camera.close(); }
                @Override public void onError(@NonNull CameraDevice camera, int error) { camera.close(); }
            }, cameraHandler);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Camera open failed", e);
        }
    }

    private void createCaptureSession() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture == null) return;
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            Surface previewSurface = new Surface(surfaceTexture);
            Surface analysisSurface = imageReader.getSurface();
            Surface jpegSurface = jpegReader.getSurface();

            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewBuilder.addTarget(previewSurface);
            previewBuilder.addTarget(analysisSurface);
            previewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
            previewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);

            previewBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CaptureRequest.CONTROL_SCENE_MODE_NIGHT);
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_USE_SCENE_MODE);
            previewBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);

            applyZoom(previewBuilder);

            cameraDevice.createCaptureSession(
                    Arrays.asList(previewSurface, analysisSurface, jpegSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                session.setRepeatingRequest(previewBuilder.build(), null, cameraHandler);
                                Log.i(TAG, "相机预览已启动: " + previewSize + " 变焦: " + currentZoom + "x");

                                mainHandler.post(() -> configureTransform());
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Repeating request failed", e);
                            }
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "Session config failed");
                        }
                    }, cameraHandler
            );
        } catch (CameraAccessException e) {
            Log.e(TAG, "Session creation failed", e);
        }
    }

    public void setZoom(float zoom) {
        zoom = Math.max(1.0f, Math.min(MAX_DIGITAL_ZOOM, zoom));
        currentZoom = zoom;

        if (previewBuilder != null && captureSession != null) {
            applyZoom(previewBuilder);
            try {
                captureSession.setRepeatingRequest(previewBuilder.build(), null, cameraHandler);
            } catch (CameraAccessException e) {
                Log.e(TAG, "Zoom update failed", e);
            }
        }
    }

    public float getZoom() { return currentZoom; }
    public float getMaxHardwareZoom() { return maxZoom; }

    public float getSoftwareZoomFactor() {
        if (currentZoom <= maxZoom) return 1.0f;
        return currentZoom / maxZoom;
    }

    private void applyZoom(CaptureRequest.Builder builder) {
        if (sensorArraySize == null) return;

        float hwZoom = Math.min(currentZoom, maxZoom);

        int cropW = Math.max(1, (int) (sensorArraySize.width() / hwZoom));
        int cropH = Math.max(1, (int) (sensorArraySize.height() / hwZoom));
        int cropX = (sensorArraySize.width() - cropW) / 2;
        int cropY = (sensorArraySize.height() - cropH) / 2;

        Rect cropRegion = new Rect(cropX, cropY, cropX + cropW, cropY + cropH);
        builder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion);
    }

    public void freezePreview() {
        if (captureSession != null) {
            try {
                captureSession.stopRepeating();
                Log.i(TAG, "预览已冻结");
            } catch (CameraAccessException e) {
                Log.e(TAG, "冻结预览失败", e);
            }
        }
    }

    public void unfreezePreview() {
        if (captureSession != null && previewBuilder != null) {
            try {
                captureSession.setRepeatingRequest(previewBuilder.build(), null, cameraHandler);
                Log.i(TAG, "预览已恢复");
            } catch (CameraAccessException e) {
                Log.e(TAG, "恢复预览失败", e);
            }
        }
    }

    public void takePhoto(PhotoCallback callback) {
        if (cameraDevice == null || captureSession == null || jpegReader == null) {

            mainHandler.post(() -> {
                Bitmap photo = textureView.getBitmap();
                if (photo != null) callback.onPhotoTaken(photo);
            });
            return;
        }

        jpegReader.setOnImageAvailableListener(reader -> {
            Image image = null;
            try {
                image = reader.acquireLatestImage();
                if (image != null) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] jpegData = new byte[buffer.remaining()];
                    buffer.get(jpegData);
                    Bitmap raw = android.graphics.BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);

                    Bitmap photo;
                    if (sensorOrientation != 0) {
                        android.graphics.Matrix rotMatrix = new android.graphics.Matrix();
                        rotMatrix.postRotate(sensorOrientation);
                        photo = Bitmap.createBitmap(raw, 0, 0, raw.getWidth(), raw.getHeight(), rotMatrix, true);
                        raw.recycle();
                    } else {
                        photo = raw;
                    }
                    Log.i(TAG, "高画质拍照: " + photo.getWidth() + "x" + photo.getHeight());
                    mainHandler.post(() -> callback.onPhotoTaken(photo));
                }
            } catch (Exception e) {
                Log.e(TAG, "JPEG 解码失败", e);
            } finally {
                if (image != null) image.close();
            }
        }, cameraHandler);

        try {
            CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(jpegReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            captureBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);

            applyZoom(captureBuilder);

            captureSession.capture(captureBuilder.build(), null, cameraHandler);
            Log.i(TAG, "JPEG 拍照请求已发送: " + captureSize);
        } catch (CameraAccessException e) {
            Log.e(TAG, "拍照请求失败", e);
        }
    }

    public Image pollLatestFrame() {
        return latestFrame.getAndSet(null);
    }

    private void configureTransform() {
        if (textureView == null || previewSize == null) return;
        int viewW = textureView.getWidth();
        int viewH = textureView.getHeight();
        if (viewW == 0 || viewH == 0) return;

        float effW = (sensorOrientation == 90 || sensorOrientation == 270)
                ? previewSize.getHeight() : previewSize.getWidth();
        float effH = (sensorOrientation == 90 || sensorOrientation == 270)
                ? previewSize.getWidth() : previewSize.getHeight();

        float scaleX = (float) viewW / effW;
        float scaleY = (float) viewH / effH;
        float scale = Math.max(scaleX, scaleY);

        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setScale(scale / scaleX, scale / scaleY, viewW / 2f, viewH / 2f);

        textureView.setTransform(matrix);
        Log.i(TAG, "TextureView transform: view=" + viewW + "x" + viewH
                + " eff=" + effW + "x" + effH + " scale=" + scale);
    }

    public Size getPreviewSize() { return previewSize; }
    public int getSensorOrientation() { return sensorOrientation; }
    public float getHFovDegrees() { return hFovDegrees; }
    public float getVFovDegrees() { return vFovDegrees; }

    public void stop() {
        try {
            if (captureSession != null) { captureSession.close(); captureSession = null; }
            if (cameraDevice != null) { cameraDevice.close(); cameraDevice = null; }
            if (imageReader != null) { imageReader.close(); imageReader = null; }
            if (jpegReader != null) { jpegReader.close(); jpegReader = null; }
            Image frame = latestFrame.getAndSet(null);
            if (frame != null) frame.close();
        } catch (Exception e) {
            Log.e(TAG, "Stop error", e);
        }
        if (cameraThread != null) {
            cameraThread.quitSafely();
            cameraThread = null;
        }
        previewBuilder = null;
    }

    private String findBackCamera(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics chars = manager.getCameraCharacteristics(id);
            Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        return null;
    }

    private Size chooseHighResSize(StreamConfigurationMap map) {
        Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
        for (Size s : sizes) {
            if (s.getWidth() == 1920 && s.getHeight() == 1080) {
                Log.i(TAG, "预览分辨率: " + s);
                return s;
            }
        }
        Size best = sizes[0];
        int target = 1920 * 1080;
        int bestDiff = Integer.MAX_VALUE;
        for (Size s : sizes) {
            int diff = Math.abs(s.getWidth() * s.getHeight() - target);
            if (diff < bestDiff) { bestDiff = diff; best = s; }
        }
        Log.i(TAG, "预览分辨率: " + best);
        return best;
    }

    private Size chooseMaxJpegSize(StreamConfigurationMap map) {
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        Size best = sizes[0];
        for (Size s : sizes) {
            if ((long) s.getWidth() * s.getHeight() > (long) best.getWidth() * best.getHeight()) {
                best = s;
            }
        }
        Log.i(TAG, "拍照分辨率: " + best);
        return best;
    }

    private Size chooseLowResSize(StreamConfigurationMap map) {
        Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);

        float previewRatio = (float) previewSize.getWidth() / previewSize.getHeight();

        Size best = null;
        float bestDiff = Float.MAX_VALUE;
        for (Size s : sizes) {
            if (s.getWidth() < 320 || s.getWidth() > 960) continue;
            float ratio = (float) s.getWidth() / s.getHeight();
            float diff = Math.abs(ratio - previewRatio);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = s;
            }
        }
        if (best != null && bestDiff < 0.1f) {
            Log.i(TAG, "分析分辨率（匹配预览）: " + best);
            return best;
        }

        for (Size s : sizes) {
            if (s.getWidth() == 640 && s.getHeight() == 480) return s;
        }
        return sizes[sizes.length - 1];
    }
}
