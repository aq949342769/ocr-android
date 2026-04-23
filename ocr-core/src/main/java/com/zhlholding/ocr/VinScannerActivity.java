package com.zhlholding.ocr;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * VIN码扫描识别Activity
 *
 * 可以通过以下方式获取识别结果：
 * 1. 启动Activity并通过 {@link #RESULT_VIN_CODE} 获取结果
 *
 * 原生插件调用示例：
 * Intent intent = new Intent(context, VinScannerActivity.class);
 * startActivityForResult(intent, REQUEST_CODE);
 *
 * @see #RESULT_VIN_CODE
 * @see #RESULT_EXTRA_MESSAGE
 */
public class VinScannerActivity extends AppCompatActivity {

    /**
     * 返回码：识别成功
     */
    public static final int RESULT_VIN_SUCCESS = 1;

    /**
     * Intent中存储VIN码的key
     */
    public static final String RESULT_VIN_CODE = "vin_code";

    /**
     * Intent中存储附加信息的key
     */
    public static final String RESULT_EXTRA_MESSAGE = "extra_message";

    /**
     * Intent中存储原始识别结果的key
     */
    public static final String RESULT_RAW_RESULT = "raw_result";

    /**
     * VIN识别结果回调接口
     */
    public interface OnVinResultListener {
        /**
         * 当识别完成时回调
         *
         * @param result 识别结果
         */
        void onVinResult(VinResult result);
    }

    private static final String TAG = "VinScannerActivity";
    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private static final long DETECTION_INTERVAL_MS = 200;

    private CameraPreview cameraPreview;
    private ScannerOverlayView scannerOverlay;
    private TextView statusText;

    private OCRPredictor ocrPredictor;
    private OCRModelManager modelManager;
    private FrameProcessor frameProcessor;
    private ExecutorService executorService;
    private boolean isProcessing = false;
    private byte[] currentFrame;
    private Camera.Size previewSize;

    private Thread detectionThread;
    private AtomicBoolean isDetecting = new AtomicBoolean(false);
    private AtomicBoolean stopDetection = new AtomicBoolean(false);

    private OnVinResultListener vinResultListener;

    // Intent 参数 Keys
    public static final String EXTRA_POWER_MODE = "extra_power_mode";
    public static final String EXTRA_THREADS = "extra_threads";
    public static final String EXTRA_ENABLE_DEBUG = "extra_enable_debug";
    public static final String EXTRA_DEBUG_PATH = "extra_debug_path";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 检查调用者包名
        if (!OcrSecurity.checkCallingPackage(this)) {
            Log.e(TAG, "Calling package not allowed");
            finish();
            return;
        }

        setContentView(R.layout.activity_vin_scanner);

        // 从 Intent 读取配置
        OCRConfig config = parseConfigFromIntent(getIntent());

        initViews();
        checkCameraPermission();

        executorService = Executors.newSingleThreadExecutor();

        // 使用配置初始化 OCR 模型
        initOCRModels(config);
    }

    /**
     * 从 Intent 解析配置
     */
    private OCRConfig parseConfigFromIntent(Intent intent) {
        if (intent == null) {
            return new OCRConfig();
        }

        OCRConfig.Builder builder = new OCRConfig.Builder();

        // 设置功率模式
        int powerModeOrdinal = intent.getIntExtra(EXTRA_POWER_MODE, -1);
        if (powerModeOrdinal >= 0 && powerModeOrdinal < com.baidu.paddle.lite.PowerMode.values().length) {
            builder.setPowerMode(com.baidu.paddle.lite.PowerMode.values()[powerModeOrdinal]);
        }

        // 设置线程数
        int threads = intent.getIntExtra(EXTRA_THREADS, -1);
        if (threads > 0) {
            builder.setThreads(threads);
        }

        // 设置调试图片
        boolean enableDebug = intent.getBooleanExtra(EXTRA_ENABLE_DEBUG, false);
        builder.setEnableDebugImageSave(enableDebug);

        String debugPath = intent.getStringExtra(EXTRA_DEBUG_PATH);
        if (debugPath != null && !debugPath.isEmpty()) {
            builder.setDebugImageSavePath(debugPath);
        }

        return builder.build();
    }

    private void initViews() {
        cameraPreview = findViewById(R.id.camera_preview);
        scannerOverlay = findViewById(R.id.scanner_overlay);
        statusText = findViewById(R.id.status_text);

        cameraPreview.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if (!isProcessing && isDetecting.compareAndSet(false, true)) {
                    previewSize = cameraPreview.getPreviewSize();

                    // 使用 FrameProcessor 处理帧
                    frameProcessor.processFrame(data, previewSize.width, previewSize.height);

                    currentFrame = data;
                }
            }
        });
    }

    private void checkCameraPermission() {
        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };

        boolean needRequest = false;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }

        if (needRequest) {
            ActivityCompat.requestPermissions(this, permissions, CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            boolean cameraGranted = false;
            boolean storageGranted = false;

            for (int i = 0; i < permissions.length && i < grantResults.length; i++) {
                if (Manifest.permission.CAMERA.equals(permissions[i])) {
                    cameraGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
                if (Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permissions[i])) {
                    storageGranted = grantResults[i] == PackageManager.PERMISSION_GRANTED;
                }
            }

            if (cameraGranted) {
                if (!storageGranted) {
                    Toast.makeText(this, "建议授予存储权限以便保存调试图片", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(this, "需要相机权限才能使用此功能", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initOCRModels(OCRConfig config) {
        // 使用OCRModelManager进行模型初始化（延迟初始化，模型常驻）
        modelManager = OCRModelManager.getInstance(this);

        // 初始化帧处理器
        frameProcessor = new FrameProcessor();

        // 如果模型已经就绪，直接使用
        if (modelManager.isModelReady()) {
            ocrPredictor = modelManager.getPredictor();
            if (ocrPredictor != null) {
                statusText.setText("OCR模型已加载");
                statusText.setVisibility(View.VISIBLE);
                startRealtimeDetection();
                return;
            }
        }

        // 显示加载中状态
        statusText.setText("正在加载OCR模型...");
        statusText.setVisibility(View.VISIBLE);

        // 使用传入的配置初始化模型
        modelManager.initModels(this, config, new OCRModelManager.InitCallback() {
            @Override
            public void onSuccess() {
                ocrPredictor = modelManager.getPredictor();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText("OCR模型加载成功");
                        statusText.setVisibility(View.VISIBLE);
                        startRealtimeDetection();
                    }
                });
            }

            @Override
            public void onError(final String error) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusText.setText("OCR模型加载失败: " + error + "\n请运行 ./convert_models.sh");
                        statusText.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void startRealtimeDetection() {
        stopDetection.set(false);

        detectionThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopDetection.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(DETECTION_INTERVAL_MS);

                        if (isProcessing || currentFrame == null || previewSize == null) {
                            continue;
                        }

                        byte[] frameData;
                        Camera.Size size = previewSize;

                        // 使用 FrameProcessor 获取最佳帧
                        frameData = frameProcessor.getBestFrame(currentFrame);

                        if (frameData != null && size != null) {
                            performDetection(frameData, size);
                        }

                    } catch (InterruptedException e) {
                        break;
                    } catch (Exception e) {
                        Log.e(TAG, "Detection error: " + e.getMessage());
                    }
                }
            }
        });

        detectionThread.setDaemon(true);
        detectionThread.start();
    }

    private void stopRealtimeDetection() {
        stopDetection.set(true);
        if (detectionThread != null) {
            detectionThread.interrupt();
            try {
                detectionThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to stop detection thread");
            }
            detectionThread = null;
        }
    }

    private void performDetection(byte[] frameData, Camera.Size size) {
        if (isProcessing) {
            isDetecting.set(false);
            return;
        }

        try {
            Bitmap bitmap = nv21ToBitmap(frameData, size.width, size.height);

            if (isProcessing) {
                bitmap.recycle();
                isDetecting.set(false);
                return;
            }

            // 获取扫描区域
            final Rect scanRect = getScanRectOnFrame();

            // 裁剪到扫描区域进行检测
            Bitmap croppedBitmap = bitmap;
            int offsetX = 0;
            int offsetY = 0;

            if (scanRect != null && scanRect.width() > 0 && scanRect.height() > 0) {
                int x = Math.max(0, scanRect.left);
                int y = Math.max(0, scanRect.top);
                int width = Math.min(scanRect.width(), bitmap.getWidth() - x);
                int height = Math.min(scanRect.height(), bitmap.getHeight() - y);

                if (width > 0 && height > 0) {
                    croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height);
                    offsetX = x;
                    offsetY = y;
                }
            }

            final int finalOffsetX = offsetX;
            final int finalOffsetY = offsetY;

            // 在裁剪后的图片上进行检测
            List<float[]> boxes = ocrPredictor.detectTextRegionsLite(croppedBitmap);

            // 调整检测框坐标到全图坐标
            List<float[]> adjustedBoxes = new ArrayList<>();
            if (boxes != null) {
                for (float[] box : boxes) {
                    float[] adjusted = new float[8];
                    adjusted[0] = box[0] + offsetX;
                    adjusted[1] = box[1] + offsetY;
                    adjusted[2] = box[2] + offsetX;
                    adjusted[3] = box[3] + offsetY;
                    adjusted[4] = box[4] + offsetX;
                    adjusted[5] = box[5] + offsetY;
                    adjusted[6] = box[6] + offsetX;
                    adjusted[7] = box[7] + offsetY;
                    adjustedBoxes.add(adjusted);
                }
            }

            if (adjustedBoxes != null && adjustedBoxes.isEmpty()) {
                // 没有检测到框时清除显示
                final List<float[]> emptyBoxes = new ArrayList<>();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scannerOverlay.setDetectionBoxes(emptyBoxes);
                    }
                });
            }

            if (!isProcessing && adjustedBoxes != null && !adjustedBoxes.isEmpty()) {
                // 检测框坐标已调整为全图坐标
                final List<float[]> uiBoxes = convertBoxesToViewCoords(adjustedBoxes, bitmap.getWidth(), bitmap.getHeight());

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isProcessing) {
                            scannerOverlay.setDetectionBoxes(uiBoxes);
                        }
                    }
                });

                // 检测到文本框后，自动触发识别
                final Bitmap bitmapForRecognition = croppedBitmap;
                final List<float[]> detectionBoxes = adjustedBoxes;
                final Rect scanRectForRecognition = scanRect;
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        performRecognition(bitmapForRecognition, detectionBoxes, scanRectForRecognition, finalOffsetX, finalOffsetY);
                    }
                });
                // 不回收 bitmap，由 performRecognition 处理
                isDetecting.set(false);
                return;
            } else if (!isProcessing) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        scannerOverlay.clearDetectionBoxes();
                    }
                });
            }

            if (croppedBitmap != bitmap) {
                croppedBitmap.recycle();
            }
            bitmap.recycle();
            isDetecting.set(false);

        } catch (Exception e) {
            Log.e(TAG, "performDetection failed: " + e.getMessage());
            isDetecting.set(false);
        }
    }

    private List<float[]> convertBoxesToViewCoords(List<float[]> boxes, int bitmapWidth, int bitmapHeight) {
        List<float[]> uiBoxes = new ArrayList<>();

        int viewWidth = scannerOverlay.getWidth();
        int viewHeight = scannerOverlay.getHeight();

        if (viewWidth == 0 || viewHeight == 0) {
            return uiBoxes;
        }

        float scaleX = (float) viewWidth / bitmapWidth;
        float scaleY = (float) viewHeight / bitmapHeight;

        for (float[] box : boxes) {
            float[] uiBox = new float[8];
            for (int i = 0; i < 4; i++) {
                uiBox[i * 2] = box[i * 2] * scaleX;
                uiBox[i * 2 + 1] = box[i * 2 + 1] * scaleY;
            }
            uiBoxes.add(uiBox);
        }

        return uiBoxes;
    }

    private void performRecognition(final Bitmap bitmap, final List<float[]> boxes, final Rect scanRect, final int offsetX, final int offsetY) {
        if (isProcessing) {
            bitmap.recycle();
            return;
        }

        isProcessing = true;

        try {
            String recognizedVinCode = null;

            // 对每个检测到的文本区域进行识别，直到找到有效的VIN码
            if (boxes != null && !boxes.isEmpty()) {
                for (int i = 0; i < boxes.size(); i++) {
                    float[] box = boxes.get(i);

                    // 调整检测框坐标到当前bitmap的坐标系统
                    float adjustedMinX = box[0] - offsetX;
                    float adjustedMaxX = box[2] - offsetX;
                    float adjustedMinY = box[1] - offsetY;
                    float adjustedMaxY = box[5] - offsetY;

                    // 计算检测框的边界（4个点取min/max）
                    float minX = Math.min(Math.min(adjustedMinX, adjustedMaxX), Math.min(box[4] - offsetX, box[6] - offsetX));
                    float maxX = Math.max(Math.max(adjustedMinX, adjustedMaxX), Math.max(box[4] - offsetX, box[6] - offsetX));
                    float minY = Math.min(Math.min(adjustedMinY, adjustedMaxY), Math.min(box[5] - offsetY, box[7] - offsetY));
                    float maxY = Math.max(Math.max(adjustedMinY, adjustedMaxY), Math.max(box[5] - offsetY, box[7] - offsetY));

                    int x = Math.max(0, (int) minX);
                    int y = Math.max(0, (int) minY);
                    int width = Math.min((int) (maxX - minX), bitmap.getWidth() - x);
                    int height = Math.min((int) (maxY - minY), bitmap.getHeight() - y);

                    // 稍微扩大一点区域，避免边界被切
                    int padding = 10;
                    x = Math.max(0, x - padding);
                    y = Math.max(0, y - padding);
                    width = Math.min(width + padding * 2, bitmap.getWidth() - x);
                    height = Math.min(height + padding * 2, bitmap.getHeight() - y);

                    if (width > 0 && height > 0 && x + width <= bitmap.getWidth() && y + height <= bitmap.getHeight()) {
                        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, x, y, width, height);

                        // 识别这个文本区域
                        String vinCode = recognizeVin(croppedBitmap);

                        if (vinCode != null) {
                            // 找到有效的VIN码
                            recognizedVinCode = vinCode;
                            saveDebugBitmap(croppedBitmap, "vin_success");
                            break;
                        }

                        croppedBitmap.recycle();
                    }
                }
            }

            final String vinCode = recognizedVinCode;

            // 生成VinResult
            final VinResult vinResult;
            if (vinCode != null) {
                vinResult = new VinResult(vinCode, VinResult.Status.SUCCESS, "识别成功");
            } else {
                vinResult = new VinResult(null, VinResult.Status.NOT_RECOGNIZED, "未识别到VIN码");
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 回收Bitmap
                    bitmap.recycle();

                    // 返回结果
                    returnResult(vinResult);
                }
            });

        } catch (final Exception e) {
            Log.e(TAG, "Recognition failed: " + e.getMessage());
            bitmap.recycle();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    resetUI();
                }
            });
        }
    }

    private Bitmap nv21ToBitmap(byte[] nv21, int width, int height) {
        // 先转换成横向的 Bitmap
        int[] colors = new int[width * height];
        int frameSize = width * height;

        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                int y = (0xff & nv21[j * width + i]);
                int uvIndex = frameSize + (j >> 1) * width + (i & ~1); // i向下对齐到偶数
                int v = (0xff & nv21[uvIndex]);
                int u = (0xff & nv21[uvIndex + 1]);

                y = y < 16 ? 16 : y;

                int r = (int) (1.164 * (y - 16) + 1.596 * (v - 128));
                int g = (int) (1.164 * (y - 16) - 0.813 * (v - 128) - 0.391 * (u - 128));
                int b = (int) (1.164 * (y - 16) + 2.018 * (u - 128));

                r = r < 0 ? 0 : (r > 255 ? 255 : r);
                g = g < 0 ? 0 : (g > 255 ? 255 : g);
                b = b < 0 ? 0 : (b > 255 ? 255 : b);

                colors[j * width + i] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }

        Bitmap landscapeBitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);

        // 旋转 90 度变成竖屏
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap portraitBitmap = Bitmap.createBitmap(landscapeBitmap, 0, 0, width, height, matrix, true);
        landscapeBitmap.recycle();

        return portraitBitmap;
    }

    private Rect getScanRectOnFrame() {
        android.graphics.RectF scanRectF = scannerOverlay.getScanRect();
        if (scanRectF == null) {
            return null;
        }

        int viewWidth = scannerOverlay.getWidth();
        int viewHeight = scannerOverlay.getHeight();

        // 旋转后的 Bitmap 尺寸（宽高互换）
        int bitmapWidth = previewSize.height;  // 旋转 90 度后，原来的高度变成宽度
        int bitmapHeight = previewSize.width;  // 原来的宽度变成高度

        // 计算缩放比例（现在 Bitmap 是竖屏的）
        float scaleX = (float) bitmapWidth / viewWidth;
        float scaleY = (float) bitmapHeight / viewHeight;

        // 直接映射坐标（不需要旋转，因为 Bitmap 已经旋转了）
        int left = (int) (scanRectF.left * scaleX);
        int top = (int) (scanRectF.top * scaleY);
        int right = (int) (scanRectF.right * scaleX);
        int bottom = (int) (scanRectF.bottom * scaleY);

        return new Rect(left, top, right, bottom);
    }

    /**
     * 标准化VIN：去掉非字母数字字符，然后替换I→1, O→0, Q→0
     *
     * @param vin 原始VIN
     * @return 标准化后的VIN，如果无法标准化返回null
     */
    private String normalizeVin(String vin) {
        if (vin == null) {
            return null;
        }
        String VIN = vin.toUpperCase();
        // 第一步：去掉所有非字母数字字符
        StringBuilder sb = new StringBuilder();
        for (char c : VIN.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
            }
        }
        String filtered = sb.toString();

        if (filtered.length() != 17) {
            return null;
        }

        // 第二步：字符替换 I→1, O→0, Q→0
        sb = new StringBuilder();
        for (char c : filtered.toCharArray()) {
            if (c == 'I') sb.append('1');
            else if (c == 'O') sb.append('0');
            else if (c == 'Q') sb.append('0');
            else sb.append(c);
        }
        String normalizedVin = sb.toString();

        // 第三步：检查是否包含有效字符
        if (!normalizedVin.matches("[A-HJ-NPR-Z0-9]{17}")) {
            Log.w(TAG, "normalize vin fail" + vin);
            return null;
        }

        return normalizedVin;
    }

    /**
     * 识别VIN：调用OCR识别后进行标准化
     *
     * @param bitmap 待识别图片
     * @return 标准化后的VIN，如果无法识别或标准化返回null
     */
    private String recognizeVin(Bitmap bitmap) {
        String rawResult = ocrPredictor.recognizeText(bitmap);
        if (rawResult == null) {
            return null;
        }
        return normalizeVin(rawResult);
    }

    private void resetUI() {
        isProcessing = false;

        cameraPreview.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                if (!isProcessing && isDetecting.compareAndSet(false, true)) {
                    previewSize = cameraPreview.getPreviewSize();

                    // 使用 FrameProcessor 处理帧
                    frameProcessor.processFrame(data, previewSize.width, previewSize.height);

                    currentFrame = data;
                }
            }
        });
    }


    /**
     * 返回识别结果
     * 只有识别成功时才停止检测和返回结果
     * 识别失败时重置状态，继续进行下一轮检测
     * 1. 通过回调接口返回
     * 2. 通过Intent返回（如果调用者使用startActivityForResult）
     * 3. 通过广播返回（用于 uni-app 插件）
     *
     * @param result 识别结果
     */
    private void returnResult(VinResult result) {
        // 只有识别成功时才停止检测和返回结果
        if (result.isSuccess()) {
            // 停止检测线程
            stopRealtimeDetection();

            // 1. 通过回调返回
            if (vinResultListener != null) {
                vinResultListener.onVinResult(result);
            }

            // 2. 通过广播返回（用于 uni-app 插件）
            Intent broadcastIntent = new Intent("com.zhlholding.ocr.VIN_RESULT");
            broadcastIntent.putExtra(RESULT_VIN_CODE, result.getVinCode());
            broadcastIntent.putExtra(RESULT_RAW_RESULT, result.getRawResult());
            broadcastIntent.putExtra(RESULT_EXTRA_MESSAGE, result.getExtraMessage());
            sendBroadcast(broadcastIntent);

            // 3. 通过Intent返回（保留以确保兼容性）
            Intent intent = new Intent();
            intent.putExtra(RESULT_VIN_CODE, result.getVinCode());
            intent.putExtra(RESULT_RAW_RESULT, result.getRawResult());
            intent.putExtra(RESULT_EXTRA_MESSAGE, result.getExtraMessage());
            setResult(RESULT_VIN_SUCCESS, intent);

            // 关闭Activity
            finish();
        } else {
            // 识别失败，重置状态继续检测
            resetUI();
        }
    }

    private void saveDebugBitmap(Bitmap bitmap, String prefix) {
        // 从 Manager 获取配置
        OCRConfig config = modelManager != null ? modelManager.getConfig() : null;

        // 检查是否启用了调试图片保存
        if (config == null || !config.isEnableDebugImageSave()) {
            return;
        }

        try {
            String basePath;
            if (config.getDebugImageSavePath() != null && !config.getDebugImageSavePath().isEmpty()) {
                basePath = config.getDebugImageSavePath();
            } else {
                basePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + "/OCR_Debug";
            }

            File dir = new File(basePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
            String fileName = prefix + "_" + timeStamp + ".png";
            File file = new File(dir, fileName);

            FileOutputStream fos = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();

            Log.d(TAG, "Debug bitmap saved: " + file.getAbsolutePath());

        } catch (Exception e) {
            Log.e(TAG, "Failed to save debug bitmap: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRealtimeDetection();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
