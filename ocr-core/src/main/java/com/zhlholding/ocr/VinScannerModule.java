package com.zhlholding.ocr;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.alibaba.fastjson.JSONObject;
import com.baidu.paddle.lite.PowerMode;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;

/**
 * VIN扫描插件模块
 *
 * uni-app 调用示例：
 * const vinScanner = uni.requireNativePlugin('VinScanner');
 * vinScanner.startScan({
 *     powerMode: 0,        // 可选: 0=LITE_POWER_LOW, 1=LITE_POWER_HIGH, 2=LITE_POWER_FULL, 3=LITE_POWER_NO_BIND
 *     threads: 4,           // 可选: 线程数，默认4
 *     enableDebug: false,  // 可选: 是否保存调试图片，默认false
 *     debugPath: '',       // 可选: 调试图片保存路径
 *     success: (res) => {
 *         console.log('VIN:', res.vinCode);
 *         console.log('原始结果:', res.rawResult);
 *     },
 *     fail: (err) => {
 *         console.log('错误:', err);
 *     }
 * });
 */
public class VinScannerModule extends UniModule {
    private static final String TAG = "VinScannerModule";
    public static final String ACTION_VIN_RESULT = "com.zhlholding.ocr.VIN_RESULT";
    public static final String EXTRA_VIN_CODE = "vin_code";
    public static final String EXTRA_RAW_RESULT = "raw_result";
    public static final String EXTRA_EXTRA_MESSAGE = "extra_message";

    private static final int REQUEST_CODE_SCAN = 10001;
    private UniJSCallback mCallback;
    private BroadcastReceiver mResultReceiver;

    /**
     * 启动VIN扫描
     * @param options 配置参数，可选包含 powerMode, threads, enableDebug, debugPath
     */
    @UniJSMethod
    public void startScan(JSONObject options, UniJSCallback callback) {
        mCallback = callback;

        // 注册广播接收器
        mResultReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handleVinResult(intent);
            }
        };

        Context context = mUniSDKInstance.getContext();
        IntentFilter filter = new IntentFilter(ACTION_VIN_RESULT);
        // 使用 receivePatterns 避免小米手机广播超时问题
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).registerReceiver(mResultReceiver, filter);
        } else {
            context.registerReceiver(mResultReceiver, filter);
        }

        // 启动VIN扫描Activity
        Intent intent = new Intent(context, VinScannerActivity.class);

        // 传递配置参数
        if (options != null) {
            if (options.containsKey("powerMode")) {
                intent.putExtra(VinScannerActivity.EXTRA_POWER_MODE, options.getInteger("powerMode"));
            }
            if (options.containsKey("threads")) {
                intent.putExtra(VinScannerActivity.EXTRA_THREADS, options.getInteger("threads"));
            }
            if (options.containsKey("enableDebug")) {
                intent.putExtra(VinScannerActivity.EXTRA_ENABLE_DEBUG, options.getBoolean("enableDebug"));
            }
            if (options.containsKey("debugPath")) {
                intent.putExtra(VinScannerActivity.EXTRA_DEBUG_PATH, options.getString("debugPath"));
            }
        }

        if (context instanceof Activity) {
            ((Activity) context).startActivityForResult(intent, REQUEST_CODE_SCAN);
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }
    }

    /**
     * 处理VIN识别结果
     */
    private void handleVinResult(Intent intent) {
        Log.d(TAG, "handleVinResult: " + intent);
        if (mCallback != null) {
            JSONObject result = new JSONObject();
            String vinCode = intent.getStringExtra(EXTRA_VIN_CODE);
            String rawResult = intent.getStringExtra(EXTRA_RAW_RESULT);
            String extraMessage = intent.getStringExtra(EXTRA_EXTRA_MESSAGE);

            if (vinCode != null && !vinCode.isEmpty()) {
                result.put("success", true);
                result.put("vinCode", vinCode);
                result.put("rawResult", rawResult);
            } else {
                result.put("success", false);
                result.put("errMsg", extraMessage != null ? extraMessage : "扫描取消或失败");
            }
            Log.d(TAG, "handleVinResult: " + result);
            mCallback.invoke(result);
            mCallback = null;
        }

        // 注销广播接收器
        unregisterReceiver();
    }

    /**
     * 注销广播接收器
     */
    private void unregisterReceiver() {
        if (mResultReceiver != null) {
            try {
                Context context = mUniSDKInstance.getContext();
                if (context != null) {
                    context.unregisterReceiver(mResultReceiver);
                }
            } catch (Exception e) {
                Log.e(TAG, "unregisterReceiver failed: " + e.getMessage());
            }
            mResultReceiver = null;
        }
    }

    /**
     * 处理Activity返回结果（备用方案）
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "onActivityResult: " + requestCode + "," + resultCode);
        // 广播机制会处理结果，这里可以留空或调用 super
        // 如果广播没收到，使用 onActivityResult 作为备用
        if (requestCode == REQUEST_CODE_SCAN && mCallback != null && data != null) {
            JSONObject result = new JSONObject();
            if (resultCode == com.zhlholding.ocr.VinScannerActivity.RESULT_VIN_SUCCESS) {
                result.put("success", true);
                result.put("vinCode", data.getStringExtra(VinScannerActivity.RESULT_VIN_CODE));
                result.put("rawResult", data.getStringExtra(VinScannerActivity.RESULT_RAW_RESULT));
            } else {
                result.put("success", false);
                result.put("errMsg", "扫描取消或失败");
            }
            Log.d(TAG, "onActivityResult result: " + result);
            mCallback.invoke(result);
            mCallback = null;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onActivityDestroy() {
        super.onActivityDestroy();
        unregisterReceiver();
    }
}
