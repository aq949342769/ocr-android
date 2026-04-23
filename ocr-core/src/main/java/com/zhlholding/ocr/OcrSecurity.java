package com.zhlholding.ocr;

import android.content.Context;
import android.util.Log;

/**
 * OCR 安全校验类
 *
 * 用于校验调用者是否有权限启动 OCR 扫描
 */
public class OcrSecurity {
    private static final String TAG = "OcrSecurity";

    // 允许调用的包名列表
    private static final String[] ALLOWED_PACKAGES = {
            "com.zhl",
            "com.zhlholding.ocr"
    };

    /**
     * 检查调用者包名是否被允许
     *
     * @param context 上下文（必须是 Activity）
     * @return true 允许，false 不允许
     */
    public static boolean checkCallingPackage(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null");
            return false;
        }

        String packageName = null;
        if (context instanceof android.app.Activity) {
            packageName = ((android.app.Activity) context).getCallingPackage();
        } else {
            // 如果不是 Activity，尝试获取包名
            packageName = context.getPackageName();
        }
        return isPackageAllowed(packageName);
    }

    /**
     * 检查指定包名是否被允许调用
     *
     * @param packageName 要检查的包名
     * @return 是否允许
     */
    public static boolean isPackageAllowed(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            Log.w(TAG, "Package name is null or empty");
            return false;
        }

        for (String allowed : ALLOWED_PACKAGES) {
            if (allowed.equals(packageName)) {
                Log.d(TAG, "Package '" + packageName + "' is allowed");
                return true;
            }
        }

        Log.w(TAG, "Package '" + packageName + "' is NOT allowed");
        return false;
    }
}
