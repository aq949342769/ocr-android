package com.zhlholding.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.baidu.paddle.lite.MobileConfig;
import com.baidu.paddle.lite.PaddlePredictor;
import com.baidu.paddle.lite.Place;
import com.baidu.paddle.lite.PowerMode;
import com.baidu.paddle.lite.Tensor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class OCRPredictor {
    private static final String TAG = "OCRPredictor";
    private PaddlePredictor detector;
    private PaddlePredictor recognizer;
    private Context context;
    private List<String> dictionary;
    private final Object lock = new Object();
    
    public OCRPredictor(Context context) {
        this.context = context;
        this.dictionary = new ArrayList<>();
        loadDictionary();
    }
    
    private void loadDictionary() {
        try {
            InputStream is = context.getAssets().open("en_dict.txt");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            String line;
            while ((line = reader.readLine()) != null) {
                dictionary.add(line);
            }
            reader.close();
            is.close();
        } catch (Exception e) {
            dictionary.add(""); // CTC blank
            for (char c = '0'; c <= '9'; c++) dictionary.add(String.valueOf(c));
            for (char c = 'A'; c <= 'Z'; c++) dictionary.add(String.valueOf(c));
        }
    }
    
    public boolean init(String detectionModelPath, String recognitionModelPath) {
        try {
            // 加载检测模型
            if (detectionModelPath != null && new java.io.File(detectionModelPath).exists()) {
                MobileConfig detConfig = new MobileConfig();
                detConfig.setModelFromFile(detectionModelPath);
                detConfig.setPowerMode(PowerMode.LITE_POWER_HIGH);
                detConfig.setThreads(4);
                detector = PaddlePredictor.createPaddlePredictor(detConfig);
                Log.d(TAG, "Detection model loaded: " + detectionModelPath);
            } else {
                Log.w(TAG, "Detection model not found, will skip detection: " + detectionModelPath);
            }
            
            // 加载识别模型
            if (recognitionModelPath == null || !new java.io.File(recognitionModelPath).exists()) {
                Log.e(TAG, "Recognition model not found: " + recognitionModelPath);
                return false;
            }
            
            MobileConfig recogConfig = new MobileConfig();
            recogConfig.setModelFromFile(recognitionModelPath);
            recogConfig.setPowerMode(PowerMode.LITE_POWER_HIGH);
            recogConfig.setThreads(4);
            recognizer = PaddlePredictor.createPaddlePredictor(recogConfig);
            String version = recognizer.getVersion();
            Log.d(TAG, "paddle lite version" + version);

            Log.d(TAG, "OCR model initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize OCR model: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public String recognizeText(Bitmap bitmap) {
        synchronized (lock) {
            try {
                Bitmap resizedBitmap = resizeBitmap(bitmap, 1920, 48);

                float[] inputData = bitmapToFloatArray(resizedBitmap);

                int width = resizedBitmap.getWidth();
                int height = resizedBitmap.getHeight();

                Tensor input = recognizer.getInput(0);
                long[] dims = {1, 3, height, width};
                input.resize(dims);
                input.setData(inputData);

                recognizer.run();

                Tensor output = recognizer.getOutput(0);
                float[] outputData = output.getFloatData();
                long[] outputShape = output.shape();

                String text = decodeText(outputData, outputShape);

                return text;

            } catch (Exception e) {
                return null;
            }
        }
    }
    
    /**
     * 将 Bitmap 转换为 float 数组，并进行归一化处理
      - 默认使用 mean=0.5, scale=0.5 进行归一化，适用于大多数 OCR 模型
      - 对于检测模型，使用 ImageNet 的 mean/std 进行归一化
     */
    private float[] bitmapToFloatArray(Bitmap bitmap) {
        return bitmapToFloatArray(bitmap, new float[]{0.5f, 0.5f, 0.5f}, new float[]{0.5f, 0.5f, 0.5f});
    }

    private float[] bitmapToFloatArray(Bitmap bitmap, float[] mean, float[] scale) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        float[] result = new float[3 * width * height];

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            float r = Color.red(pixel) / 255.0f;
            float g = Color.green(pixel) / 255.0f;
            float b = Color.blue(pixel) / 255.0f;

            result[i] = (r - mean[0]) / scale[0];
            result[width * height + i] = (g - mean[1]) / scale[1];
            result[2 * width * height + i] = (b - mean[2]) / scale[2];
        }

        return result;
    }

    /**
     * CTC 解码
     * @param data
     * @param shape
     * @return
     */
    private String decodeText(float[] data, long[] shape) {
        StringBuilder text = new StringBuilder();

        int timestep;
        int numClasses;
        int dataOffset = 0;

        if (shape.length == 3) {
            timestep = (int) shape[1];
            numClasses = (int) shape[2];
        } else if (shape.length == 2) {
            timestep = (int) shape[0];
            numClasses = (int) shape[1];
        } else {
            return "";
        }

        if (numClasses <= 1 || timestep <= 0) {
            return "";
        }
        int lastIdx = -1;
        for (int t = 0; t < timestep; t++) {
            int offset = dataOffset + t * numClasses;

            float[] probs;
            probs = new float[numClasses];
            System.arraycopy(data, offset, probs, 0, numClasses);

            int maxIdx = 0;
            float maxVal = probs[0];
            for (int c = 1; c < numClasses; c++) {
                if (probs[c] > maxVal) {
                    maxVal = probs[c];
                    maxIdx = c;
                }
            }

            if (maxIdx != 0 && maxIdx != lastIdx) {
                if (maxIdx < dictionary.size()) {
                    String ch = dictionary.get(maxIdx);
                    text.append(ch);
                }
            }
            lastIdx = maxIdx;
        }

        return text.toString().trim();
    }

    private Bitmap resizeBitmap(Bitmap bitmap, int maxWidth, int maxHeight) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        int targetHeight = 48;
        float ratio = (float) targetHeight / height;
        int newWidth = Math.round(width * ratio);
        int newHeight = targetHeight;

        if (newWidth > maxWidth) {
            newWidth = maxWidth;
        }

        int minWidthForVIN = 480;
        if (newWidth < minWidthForVIN) {
            newWidth = minWidthForVIN;
            newHeight = 48;
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
    }

    /**
     * 文本检测 - 支持多文本区域检测，返回多个边界框
      - 适用于 DB 模型输出的概率图格式，进行二值化和连通区域分析
      - 输出是一个包含多个文本区域坐标的列表，每个区域用四边形表示
      - 后处理步骤包括：根据输出格式解析坐标、应用缩放比例将坐标映射回原始图像空间
     */
    public List<float[]> detectTextRegionsLite(Bitmap bitmap) {
        synchronized (lock) {
            try {
                if (detector == null) {
                    return null;
                }
                
                int srcWidth = bitmap.getWidth();
                int srcHeight = bitmap.getHeight();
                
                int targetSize = 480;
                float scale = (float) targetSize / Math.max(srcWidth, srcHeight);
                int newWidth = Math.round(srcWidth * scale);
                int newHeight = Math.round(srcHeight * scale);
                
                newWidth = ((newWidth + 31) / 32) * 32;
                newHeight = ((newHeight + 31) / 32) * 32;

                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);

                float[] inputData = bitmapToFloatArray(resizedBitmap, new float[]{0.485f, 0.456f, 0.406f}, new float[]{0.229f, 0.224f, 0.225f});
                
                Tensor input = detector.getInput(0);
                long[] dims = {1, 3, newHeight, newWidth};
                input.resize(dims);
                input.setData(inputData);
                
                detector.run();
                
                Tensor output = detector.getOutput(0);
                float[] outputData = output.getFloatData();
                long[] outputShape = output.shape();
                
                float scaleX = (float) srcWidth / newWidth;
                float scaleY = (float) srcHeight / newHeight;
                List<float[]> boxes = postProcessDetectionMulti(outputData, outputShape, scaleX, scaleY);
                
                resizedBitmap.recycle();

                return boxes;

            } catch (Exception e) {
                return null;
            }
        }
    }

    private List<float[]> postProcessDetectionMulti(float[] data, long[] shape, float scaleX, float scaleY) {
        List<float[]> boxes = new ArrayList<>();

        // DB 模型输出格式: [1, 1, H, W] - 概率图/热力图
        if (shape.length >= 3) {
            int channels = (int) shape[1];
            int outHeight = (int) shape[2];
            int outWidth = shape.length > 3 ? (int) shape[3] : 1;

            // 如果是 DB 模型的概率图格式，进行后处理
            if (channels == 1 && outHeight > 1 && outWidth > 1) {
                return processDBDetectionOutput(data, outHeight, outWidth, scaleX, scaleY);
            }
        }

        // 原有的边界框格式处理 (旧版检测模型)
        if (shape.length < 3) {
            return boxes;
        }

        int numBoxes = (int) shape[1];
        int boxSize = (int) shape[2];

        if (numBoxes == 0 || boxSize < 8) {
            return boxes;
        }

        int maxBoxes = Math.min(numBoxes, 5);
        for (int i = 0; i < maxBoxes; i++) {
            float[] box = new float[8];
            System.arraycopy(data, i * boxSize, box, 0, 8);

            for (int j = 0; j < 4; j++) {
                box[j * 2] *= scaleX;
                box[j * 2 + 1] *= scaleY;
            }

            boxes.add(box);
        }

        return boxes;
    }

    /**
     * DB 模型后处理 - 参考 PaddleOCR ppocr/postprocess/db_postprocess.py
     * 1. 二值化 - 将热力图按阈值二值化
     * 2. 连通区域分析 - 找出文字区域 (使用 BFS 替代 cv2.findContours)
     * 3. 边界框计算 - 计算每个连通区域的最小外接矩形
     */
    private List<float[]> processDBDetectionOutput(float[] data, int height, int width,
                                                      float scaleX, float scaleY) {
        List<float[]> boxes = new ArrayList<>();

        float thresh = 0.3f;       // 二值化阈值 - 降低以检测更多区域
        float boxThresh = 0.5f;    // 框得分阈值 - 降低以通过更多框
        float unclipRatio = 1.2f;  // 扩展比例
        int minSize = 3;           // 最小尺寸

        // 1. 二值化
        boolean[][] binaryMap = new boolean[height][width];
        int totalPixels = height * width;
        int highProbCount = 0;

        for (int i = 0; i < data.length; i++) {
            if (data[i] > thresh) {
                binaryMap[(i / width) % height][i % width] = true;
                highProbCount++;
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                if (idx < data.length) {
                    binaryMap[y][x] = data[idx] > thresh;
                    if (binaryMap[y][x]) highProbCount++;
                }
            }
        }

        // 2. 使用 BFS 找连通区域
        int[][] visited = new int[height][width];
        List<int[]> regions = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (binaryMap[y][x] && visited[y][x] == 0) {
                    int[] region = bfsRegion(binaryMap, visited, x, y, width, height);
                    if (region != null) {
                        regions.add(region);
                    }
                }
            }
        }

        // 3. 对每个区域计算边界框并过滤
        for (int i = 0; i < regions.size(); i++) {
            int[] region = regions.get(i);
            int minX = region[0];
            int minY = region[1];
            int maxX = region[2];
            int maxY = region[3];
            int pixelCount = region[4];

            int boxWidth = maxX - minX + 1;
            int boxHeight = maxY - minY + 1;

            float score = calculateBoxScore(data, width, height, minX, minY, maxX, maxY);

            // 过滤太小的区域
            if (pixelCount < 50 || boxWidth < minSize || boxHeight < minSize) {
                continue;
            }

            if (score < boxThresh) {
                continue;
            }

            // Unclip 扩展边界框
            float expandedMinX = minX;
            float expandedMinY = minY;
            float expandedMaxX = maxX;
            float expandedMaxY = maxY;

            float avgDim = (boxWidth + boxHeight) / 2.0f;
            float unclipAmount = avgDim * (unclipRatio - 1.0f) / 2.0f;

            expandedMinX = Math.max(0, minX - unclipAmount);
            expandedMinY = Math.max(0, minY - unclipAmount);
            expandedMaxX = Math.min(width - 1, maxX + unclipAmount);
            expandedMaxY = Math.min(height - 1, maxY + unclipAmount);

            // 应用缩放
            float scaledMinX = expandedMinX * scaleX;
            float scaledMinY = expandedMinY * scaleY;
            float scaledMaxX = expandedMaxX * scaleX;
            float scaledMaxY = expandedMaxY * scaleY;

            // 返回四边形框 (顺时针: 左上、右上、右下、左下)
            float[] box = new float[8];
            box[0] = scaledMinX;
            box[1] = scaledMinY;
            box[2] = scaledMaxX;
            box[3] = scaledMinY;
            box[4] = scaledMaxX;
            box[5] = scaledMaxY;
            box[6] = scaledMinX;
            box[7] = scaledMaxY;

            boxes.add(box);
        }

        return boxes;
    }

    /**
     * BFS 查找连通区域，返回 [minX, minY, maxX, maxY, pixelCount]
     */
    private int[] bfsRegion(boolean[][] binaryMap, int[][] visited, int startX, int startY,
                            int width, int height) {
        int[] dx = {0, 1, 0, -1};
        int[] dy = {-1, 0, 1, 0};

        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        int pixelCount = 0;

        java.util.LinkedList<int[]> queue = new java.util.LinkedList<>();
        queue.add(new int[]{startX, startY});
        visited[startY][startX] = 1;

        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int x = pos[0];
            int y = pos[1];

            pixelCount++;
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);

            // 4-邻域遍历
            for (int d = 0; d < 4; d++) {
                int nx = x + dx[d];
                int ny = y + dy[d];

                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    if (binaryMap[ny][nx] && visited[ny][nx] == 0) {
                        visited[ny][nx] = 1;
                        queue.add(new int[]{nx, ny});
                    }
                }
            }
        }

        return new int[]{minX, minY, maxX, maxY, pixelCount};
    }

    /**
     * 计算框内平均得分 (简化版 box_score_fast)
     */
    private float calculateBoxScore(float[] data, int dataWidth, int dataHeight,
                                     int minX, int minY, int maxX, int maxY) {
        int boxWidth = maxX - minX + 1;
        int boxHeight = maxY - minY + 1;

        if (boxWidth <= 0 || boxHeight <= 0) return 0f;

        float sum = 0f;
        int count = 0;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int idx = y * dataWidth + x;
                if (idx < data.length) {
                    sum += data[idx];
                    count++;
                }
            }
        }

        return count > 0 ? sum / count : 0f;
    }
}
