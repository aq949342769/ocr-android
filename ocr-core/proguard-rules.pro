# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep OCR classes
-keep class com.zhlholding.ocr.** { *; }

# Keep PaddlePaddle classes
-keep class com.baidu.paddle.lite.** { *; }
