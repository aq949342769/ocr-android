package com.zhlholding.ocr;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.io.IOException;
import java.util.List;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {
    public interface OnCameraReadyListener {
        void onCameraReady();
    }

    private SurfaceHolder mHolder;
    private Camera mCamera;
    private Camera.PreviewCallback previewCallback;
    private OnCameraReadyListener cameraReadyListener;
    private boolean flashEnabled = false;

    public CameraPreview(Context context) {
        super(context);
        init();
    }

    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CameraPreview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void setPreviewCallback(Camera.PreviewCallback callback) {
        this.previewCallback = callback;
        if (mCamera != null) {
            mCamera.setPreviewCallback(callback);
        }
    }

    public void setOnCameraReadyListener(OnCameraReadyListener listener) {
        this.cameraReadyListener = listener;
    }

    public void surfaceCreated(SurfaceHolder holder) {
        try {
            mCamera = Camera.open();
            mCamera.setPreviewDisplay(holder);
            
            Camera.Parameters parameters = mCamera.getParameters();
            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            Camera.Size optimalSize = getOptimalPreviewSize(sizes, 1920, 1080);
            parameters.setPreviewSize(optimalSize.width, optimalSize.height);
            parameters.setPreviewFormat(ImageFormat.NV21);
            applyFlashMode(parameters);

            // 启用自动对焦
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            mCamera.setParameters(parameters);
            
            mCamera.setDisplayOrientation(90);
            
            if (previewCallback != null) {
                mCamera.setPreviewCallback(previewCallback);
            }

            mCamera.startPreview();
            if (cameraReadyListener != null) {
                cameraReadyListener.onCameraReady();
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            flashEnabled = false;
            if (cameraReadyListener != null) {
                cameraReadyListener.onCameraReady();
            }
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        if (mHolder.getSurface() == null) {
            return;
        }

        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            // Ignore
        }

        try {
            mCamera.setPreviewDisplay(mHolder);
            if (previewCallback != null) {
                mCamera.setPreviewCallback(previewCallback);
            }
            mCamera.startPreview();
        } catch (Exception e) {
            // Ignore
        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int targetWidth, int targetHeight) {
        double targetRatio = (double) targetWidth / targetHeight;
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
        double minDiffRatio = Double.MAX_VALUE;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) < minDiffRatio) {
                minDiffRatio = Math.abs(ratio - targetRatio);
                optimalSize = size;
            }
        }

        return optimalSize;
    }

    public Camera.Size getPreviewSize() {
        if (mCamera != null) {
            return mCamera.getParameters().getPreviewSize();
        }
        return null;
    }

    public boolean isFlashSupported() {
        if (mCamera == null) {
            return false;
        }

        Camera.Parameters parameters = mCamera.getParameters();
        List<String> flashModes = parameters.getSupportedFlashModes();
        return flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH);
    }

    public boolean setFlashEnabled(boolean enabled) {
        if (mCamera == null) {
            flashEnabled = enabled;
            return false;
        }

        try {
            Camera.Parameters parameters = mCamera.getParameters();
            List<String> flashModes = parameters.getSupportedFlashModes();
            if (flashModes == null || !flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                flashEnabled = false;
                return false;
            }

            flashEnabled = enabled;
            applyFlashMode(parameters);
            mCamera.setParameters(parameters);
            return true;
        } catch (RuntimeException e) {
            flashEnabled = false;
            return false;
        }
    }

    public boolean isFlashEnabled() {
        return flashEnabled;
    }

    private void applyFlashMode(Camera.Parameters parameters) {
        List<String> flashModes = parameters.getSupportedFlashModes();
        if (flashModes == null) {
            return;
        }

        if (flashEnabled && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        } else if (flashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }
    }
}
