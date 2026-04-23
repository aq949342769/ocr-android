package com.zhlholding.ocr;

import android.util.Log;

import java.util.LinkedList;

/**
 * 帧处理工具类
 *
 * 提供运动检测、清晰度计算、帧选择等功能，用于处理相机预览帧
 *
 * 使用示例：
 * <pre>
 * FrameProcessor frameProcessor = new FrameProcessor();
 * frameProcessor.setStabilityThreshold(0.95f);
 * frameProcessor.setFrameHistorySize(5);
 *
 * // 在预览回调中
 * boolean stable = frameProcessor.isStable(data);
 * frameProcessor.updateFrameHistory(data, width, height);
 * byte[] sharpestFrame = frameProcessor.selectSharpestFrame();
 * </pre>
 */
public class FrameProcessor {
    private static final String TAG = "FrameProcessor";

    // 帧相似度阈值，高于此值认为稳定
    private float stabilityThreshold = 0.95f;
    // 帧历史记录大小
    private int frameHistorySize = 5;
    // 采样步长，用于减少计算量
    private int frameSampleStep = 10;

    private byte[] lastFrame;
    private LinkedList<FrameInfo> frameHistory = new LinkedList<>();

    // 帧信息内部类
    private static class FrameInfo {
        byte[] data;
        long timestamp;
        float clarity;  // 清晰度评分

        FrameInfo(byte[] data, long timestamp, float clarity) {
            this.data = data;
            this.timestamp = timestamp;
            this.clarity = clarity;
        }
    }

    public FrameProcessor() {
    }

    public FrameProcessor(float stabilityThreshold, int frameHistorySize) {
        this.stabilityThreshold = stabilityThreshold;
        this.frameHistorySize = frameHistorySize;
    }

    public void setStabilityThreshold(float stabilityThreshold) {
        this.stabilityThreshold = stabilityThreshold;
    }

    public void setFrameHistorySize(int frameHistorySize) {
        this.frameHistorySize = frameHistorySize;
    }

    public void setFrameSampleStep(int frameSampleStep) {
        this.frameSampleStep = frameSampleStep;
    }

    /**
     * 重置状态
     */
    public void reset() {
        lastFrame = null;
        frameHistory.clear();
    }

    /**
     * 计算两帧之间的相似度
     * 使用采样方式计算，减少计算量
     *
     * @param frame1 上一帧数据
     * @param frame2 当前帧数据
     * @return 相似度，范围 0-1，越高表示越相似（越稳定）
     */
    public float calculateFrameSimilarity(byte[] frame1, byte[] frame2) {
        if (frame1 == null || frame2 == null || frame1.length != frame2.length) {
            return 0f;
        }

        int length = frame1.length;
        int matchingPixels = 0;
        int sampleCount = 0;

        // 只采样 Y 分量（亮度）进行比较，因为 UV 是亚采样
        // Y 分量在 NV21 中位于 [0, width*height)
        int sampleStep = frameSampleStep;
        for (int i = 0; i < length; i += sampleStep) {
            int diff = Math.abs((frame1[i] & 0xFF) - (frame2[i] & 0xFF));
            if (diff < 10) {  // 差异小于10认为是相似
                matchingPixels++;
            }
            sampleCount++;
        }

        return sampleCount > 0 ? (float) matchingPixels / sampleCount : 0f;
    }

    /**
     * 检查相机是否稳定
     *
     * @param currentFrame 当前帧
     * @return true 表示稳定，false 表示不稳定
     */
    public boolean isStable(byte[] currentFrame) {
        if (lastFrame == null) {
            return true;
        }

        float similarity = calculateFrameSimilarity(lastFrame, currentFrame);
        boolean stable = similarity >= stabilityThreshold;

        Log.d(TAG, "Frame similarity: " + similarity + ", stable: " + stable);
        return stable;
    }

    /**
     * 计算帧的清晰度评分
     * 使用像素梯度方差计算，梯度越大越清晰
     *
     * @param nv21Data NV21 格式的帧数据
     * @param width 宽度
     * @param height 高度
     * @return 清晰度评分
     */
    public float calculateFrameClarity(byte[] nv21Data, int width, int height) {
        if (nv21Data == null) {
            return 0f;
        }

        int frameSize = width * height;
        if (nv21Data.length < frameSize) {
            return 0f;
        }

        // 使用简化版的清晰度计算：计算相邻像素差异的平方和
        // 步长加大以减少计算量
        int step = 4;
        long sumSquaredDiff = 0;
        int count = 0;

        for (int y = step; y < height - step; y += step) {
            for (int x = step; x < width - step; x += step) {
                int idx = y * width + x;
                int nextIdxY = (y + step) * width + x;
                int nextIdxX = y * width + (x + step);

                if (nextIdxY < frameSize && nextIdxX < frameSize) {
                    int diffY = Math.abs((nv21Data[idx] & 0xFF) - (nv21Data[nextIdxY] & 0xFF));
                    int diffX = Math.abs((nv21Data[idx] & 0xFF) - (nv21Data[nextIdxX] & 0xFF));
                    sumSquaredDiff += diffY * diffY + diffX * diffX;
                    count++;
                }
            }
        }

        return count > 0 ? (float) sumSquaredDiff / count : 0f;
    }

    /**
     * 从帧历史中选择最清晰的帧
     *
     * @return 最清晰的帧数据，如果没有则返回 null
     */
    public byte[] selectSharpestFrame() {
        if (frameHistory.isEmpty()) {
            return null;
        }

        byte[] sharpestFrame = null;
        float maxClarity = -1;

        for (FrameInfo frameInfo : frameHistory) {
            if (frameInfo.clarity > maxClarity) {
                maxClarity = frameInfo.clarity;
                sharpestFrame = frameInfo.data;
            }
        }

        Log.d(TAG, "Selected sharpest frame with clarity: " + maxClarity);
        return sharpestFrame;
    }

    /**
     * 更新帧历史记录
     *
     * @param frameData 帧数据
     * @param width 宽度
     * @param height 高度
     */
    public void updateFrameHistory(byte[] frameData, int width, int height) {
        float clarity = calculateFrameClarity(frameData, width, height);
        Log.d(TAG, "updateFrameHistory: " + clarity);
        frameHistory.add(new FrameInfo(frameData.clone(), System.currentTimeMillis(), clarity));

        // 保持历史记录在指定大小内
        while (frameHistory.size() > frameHistorySize) {
            frameHistory.removeFirst();
        }
    }

    /**
     * 处理当前帧
     * 自动检测相机稳定性，不稳定时累积帧到历史，稳定时清除历史
     *
     * @param frameData 当前帧数据
     * @param width 宽度
     * @param height 高度
     * @return true 表示相机稳定，false 表示不稳定
     */
    public boolean processFrame(byte[] frameData, int width, int height) {
        boolean stable = isStable(frameData);

        if (!stable) {
            // 相机不稳定，累积帧到历史记录
            updateFrameHistory(frameData, width, height);
            Log.d(TAG, "Camera unstable, accumulating frames. History size: " + frameHistory.size());
        } else {
            // 相机稳定，清除历史记录，使用当前帧
            clearFrameHistory();
            updateLastFrame(frameData);
            Log.d(TAG, "Camera stable");
        }

        return stable;
    }

    /**
     * 获取最佳帧
     * 如果帧历史不为空，选择最清晰的帧；否则返回当前帧
     *
     * @param currentFrame 当前帧
     * @return 最佳帧数据
     */
    public byte[] getBestFrame(byte[] currentFrame) {
        if (frameHistory.isEmpty()) {
            return currentFrame;
        }

        byte[] sharpestFrame = selectSharpestFrame();
        if (sharpestFrame != null) {
            Log.d(TAG, "Using sharpest frame from history");
            return sharpestFrame;
        }

        return currentFrame;
    }

    /**
     * 清除帧历史
     */
    public void clearFrameHistory() {
        frameHistory.clear();
    }

    /**
     * 获取帧历史大小
     */
    public int getFrameHistorySize() {
        return frameHistory.size();
    }

    /**
     * 检查帧历史是否为空
     */
    public boolean isFrameHistoryEmpty() {
        return frameHistory.isEmpty();
    }

    /**
     * 更新上一帧数据
     */
    public void updateLastFrame(byte[] frameData) {
        lastFrame = frameData;
    }
}
