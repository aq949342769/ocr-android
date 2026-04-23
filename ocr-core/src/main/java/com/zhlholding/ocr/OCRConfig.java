package com.zhlholding.ocr;

import com.baidu.paddle.lite.PowerMode;

/**
 * OCR 配置类
 *
 * 用于配置 OCR 初始化参数，包括模型路径、推理参数和调试选项
 *
 * 使用示例：
 * <pre>
 * OCRConfig config = new OCRConfig.Builder()
 *     .setRecognitionModelPath("/path/to/model.nb")
 *     .setPowerMode(PowerMode.LITE_POWER_HIGH)
 *     .setThreads(4)
 *     .setEnableDebugImageSave(true)
 *     .setDebugImageSavePath("/sdcard/ocr_debug")
 *     .build();
 * </pre>
 */
public class OCRConfig {
    private String detectionModelPath;
    private String recognitionModelPath;
    private PowerMode powerMode = PowerMode.LITE_POWER_HIGH;
    private int threads = 4;
    private boolean enableDebugImageSave = false;
    private String debugImageSavePath;

    public OCRConfig() {
    }

    private OCRConfig(Builder builder) {
        this.detectionModelPath = builder.detectionModelPath;
        this.recognitionModelPath = builder.recognitionModelPath;
        this.powerMode = builder.powerMode;
        this.threads = builder.threads;
        this.enableDebugImageSave = builder.enableDebugImageSave;
        this.debugImageSavePath = builder.debugImageSavePath;
    }

    public String getDetectionModelPath() {
        return detectionModelPath;
    }

    public void setDetectionModelPath(String detectionModelPath) {
        this.detectionModelPath = detectionModelPath;
    }

    public String getRecognitionModelPath() {
        return recognitionModelPath;
    }

    public void setRecognitionModelPath(String recognitionModelPath) {
        this.recognitionModelPath = recognitionModelPath;
    }

    public PowerMode getPowerMode() {
        return powerMode;
    }

    public void setPowerMode(PowerMode powerMode) {
        this.powerMode = powerMode;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public boolean isEnableDebugImageSave() {
        return enableDebugImageSave;
    }

    public void setEnableDebugImageSave(boolean enableDebugImageSave) {
        this.enableDebugImageSave = enableDebugImageSave;
    }

    public String getDebugImageSavePath() {
        return debugImageSavePath;
    }

    public void setDebugImageSavePath(String debugImageSavePath) {
        this.debugImageSavePath = debugImageSavePath;
    }

    public static class Builder {
        private String detectionModelPath;
        private String recognitionModelPath;
        private PowerMode powerMode = PowerMode.LITE_POWER_HIGH;
        private int threads = 4;
        private boolean enableDebugImageSave = false;
        private String debugImageSavePath;

        public Builder setDetectionModelPath(String detectionModelPath) {
            this.detectionModelPath = detectionModelPath;
            return this;
        }

        public Builder setRecognitionModelPath(String recognitionModelPath) {
            this.recognitionModelPath = recognitionModelPath;
            return this;
        }

        public Builder setPowerMode(PowerMode powerMode) {
            this.powerMode = powerMode;
            return this;
        }

        public Builder setThreads(int threads) {
            this.threads = threads;
            return this;
        }

        public Builder setEnableDebugImageSave(boolean enableDebugImageSave) {
            this.enableDebugImageSave = enableDebugImageSave;
            return this;
        }

        public Builder setDebugImageSavePath(String debugImageSavePath) {
            this.debugImageSavePath = debugImageSavePath;
            return this;
        }

        public OCRConfig build() {
            return new OCRConfig(this);
        }
    }
}
