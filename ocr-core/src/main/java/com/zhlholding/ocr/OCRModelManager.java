package com.zhlholding.ocr;

import android.content.Context;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * OCR模型管理器 - 采用单例模式实现模型常驻内存
 *
 * 功能：
 * 1. 延迟初始化模型（首次使用时初始化）
 * 2. 模型复用（初始化后保持常驻）
 * 3. 监听应用生命周期，在宿主退出后台时销毁模型
 *
 * 使用方式：
 * OCRModelManager.getInstance(context).initModels(context, callback);
 * OCRPredictor predictor = OCRModelManager.getInstance(context).getPredictor();
 */
public class OCRModelManager {
    private static final String TAG = "OCRModelManager";

    private static final String REC_MODEL = "ocr_rec_v3.nb";
    private static final String DET_MODEL = "ocr_detection.nb";

    private static OCRModelManager sInstance;
    private OCRPredictor ocrPredictor;
    private OCRConfig ocrConfig;
    private Context appContext;
    private boolean isModelReady = false;
    private boolean isInitializing = false;

    // 回调接口
    public interface InitCallback {
        void onSuccess();
        void onError(String error);
    }

    private OCRModelManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    /**
     * 获取单例实例
     */
    public static synchronized OCRModelManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new OCRModelManager(context.getApplicationContext());
        }
        return sInstance;
    }

    /**
     * 初始化模型（使用默认配置）
     * 如果模型已经就绪，直接回调成功
     *
     * @param context 上下文
     * @param callback 初始化回调
     */
    public synchronized void initModels(final Context context, final InitCallback callback) {
        initModels(context, new OCRConfig(), callback);
    }

    /**
     * 使用配置对象初始化模型
     * 如果模型已经就绪，直接回调成功
     *
     * @param context 上下文
     * @param config  配置对象
     * @param callback 初始化回调
     */
    public synchronized void initModels(final Context context, final OCRConfig config,
                                       final InitCallback callback) {
        if (isModelReady && ocrPredictor != null) {
            Log.d(TAG, "Models already initialized, skipping...");
            if (callback != null) {
                callback.onSuccess();
            }
            return;
        }

        if (isInitializing) {
            Log.d(TAG, "Models are being initialized, waiting...");
            return;
        }

        isInitializing = true;

        // 在后台线程初始化模型
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Context ctx = context.getApplicationContext();
                    String detectionModelPath = config.getDetectionModelPath();
                    String recognitionModelPath = config.getRecognitionModelPath();

                    // 如果配置中未指定模型路径，使用默认的 asset 文件
                    if (detectionModelPath == null) {
                        detectionModelPath = copyAssetToCache(ctx, DET_MODEL);
                        config.setDetectionModelPath(detectionModelPath);
                    }
                    if (recognitionModelPath == null) {
                        recognitionModelPath = copyAssetToCache(ctx, REC_MODEL);
                        config.setRecognitionModelPath(recognitionModelPath);
                    }

                    if (recognitionModelPath == null) {
                        Log.e(TAG, "Recognition model not found");
                        isInitializing = false;
                        if (callback != null) {
                            callback.onError("未找到识别模型文件");
                        }
                        return;
                    }

                    // 使用配置初始化 predictor
                    OCRPredictor predictor = new OCRPredictor(ctx);
                    boolean success = predictor.init(ctx, config);

                    if (success) {
                        synchronized (OCRModelManager.this) {
                            ocrPredictor = predictor;
                            ocrConfig = config;
                            isModelReady = true;
                            isInitializing = false;
                        }
                        Log.d(TAG, "Models initialized successfully with config");
                        if (callback != null) {
                            callback.onSuccess();
                        }
                    } else {
                        isInitializing = false;
                        if (callback != null) {
                            callback.onError("模型初始化失败");
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to init models: " + e.getMessage());
                    e.printStackTrace();
                    isInitializing = false;
                    if (callback != null) {
                        callback.onError(e.getMessage());
                    }
                }
            }
        }).start();
    }

    /**
     * 获取预测器
     * 如果模型未初始化，返回null
     */
    public synchronized OCRPredictor getPredictor() {
        return ocrPredictor;
    }

    /**
     * 获取 OCR 配置
     * 如果模型未初始化或未通过配置初始化，返回null
     */
    public synchronized OCRConfig getConfig() {
        return ocrConfig;
    }

    /**
     * 检查模型是否就绪
     */
    public synchronized boolean isModelReady() {
        return isModelReady;
    }

    /**
     * 检查是否正在初始化
     */
    public synchronized boolean isInitializing() {
        return isInitializing;
    }

    /**
     * 释放模型资源
     * 当应用进入后台或内存不足时调用
     */
    public synchronized void releaseModels() {
        if (!isModelReady && ocrPredictor == null) {
            return;
        }

        Log.d(TAG, "Releasing OCR models...");
        if (ocrPredictor != null) {
            ocrPredictor.destroy();
            ocrPredictor = null;
        }
        isModelReady = false;
        isInitializing = false;

        // 清理缓存的模型文件
        try {
            File detCache = new File(appContext.getCacheDir(), DET_MODEL);
            File recCache = new File(appContext.getCacheDir(), REC_MODEL);
            if (detCache.exists()) detCache.delete();
            if (recCache.exists()) recCache.delete();
        } catch (Exception e) {
            Log.e(TAG, "Failed to clean cache: " + e.getMessage());
        }

        Log.d(TAG, "Models released");
    }

    /**
     * 重置实例（用于测试或特殊场景）
     */
    public static synchronized void reset() {
        if (sInstance != null) {
            sInstance.releaseModels();
        }
        sInstance = null;
    }

    /**
     * 将assets中的模型文件复制到缓存目录
     */
    private String copyAssetToCache(Context context, String assetName) {
        String cachePath = context.getCacheDir() + "/" + assetName;
        File cacheFile = new File(cachePath);

        if (cacheFile.exists()) {
            return cachePath;
        }

        try {
            InputStream is = context.getAssets().open(assetName);
            OutputStream os = new BufferedOutputStream(new FileOutputStream(cachePath));

            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }

            os.flush();
            os.close();
            is.close();

            return cachePath;
        } catch (Exception e) {
            Log.e(TAG, "Failed to copy asset: " + e.getMessage());
            return null;
        }
    }
}
