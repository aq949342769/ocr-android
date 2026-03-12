package com.zhlholding.ocr;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class ScannerOverlayView extends View {
    private Paint paint;
    private Paint borderPaint;
    private Paint cornerPaint;
    private Paint detectionPaint;
    private Paint detectionFillPaint;
    private RectF scanRect;
    private List<float[]> detectionBoxes;
    private static final int CORNER_LENGTH = 40;
    private static final int CORNER_WIDTH = 8;
    
    private static final int DETECTION_COLOR = Color.parseColor("#FF0000");

    public ScannerOverlayView(Context context) {
        super(context);
        init();
    }

    public ScannerOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ScannerOverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        // 确保视图在最上层
        bringToFront();
    }

    private void init() {
        // 设置背景透明
        setBackgroundColor(Color.TRANSPARENT);

        paint = new Paint();
        paint.setColor(Color.TRANSPARENT);
        
        borderPaint = new Paint();
        borderPaint.setColor(Color.GREEN);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3);
        borderPaint.setAntiAlias(true);
        
        cornerPaint = new Paint();
        cornerPaint.setColor(Color.GREEN);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(CORNER_WIDTH);
        cornerPaint.setAntiAlias(true);
        
        detectionPaint = new Paint();
        detectionPaint.setStyle(Paint.Style.STROKE);
        detectionPaint.setStrokeWidth(4);
        detectionPaint.setAntiAlias(true);
        
        detectionFillPaint = new Paint();
        detectionFillPaint.setStyle(Paint.Style.FILL);
        detectionFillPaint.setAntiAlias(true);
        
        detectionBoxes = new ArrayList<>();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // 占满屏幕宽度，不留左右边框
        int scanWidth = w;
        int scanHeight = (int) (h * 0.15);
        int left = 0;
        int top = (h - scanHeight) / 2;

        scanRect = new RectF(left, top, left + scanWidth, top + scanHeight);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        if (detectionBoxes != null && !detectionBoxes.isEmpty()) {
            for (int i = 0; i < detectionBoxes.size(); i++) {
                float[] box = detectionBoxes.get(i);
                if (box != null && box.length >= 8) {
                    int color = DETECTION_COLOR;

                    detectionPaint.setColor(color);
                    detectionFillPaint.setColor(color);
                    detectionFillPaint.setAlpha(60);
                    
                    Path path = new Path();
                    path.moveTo(box[0], box[1]);
                    path.lineTo(box[2], box[3]);
                    path.lineTo(box[4], box[5]);
                    path.lineTo(box[6], box[7]);
                    path.close();
                    
                    canvas.drawPath(path, detectionFillPaint);
                    canvas.drawPath(path, detectionPaint);
                    
                    float[] corners = {box[0], box[1], box[2], box[3], box[4], box[5], box[6], box[7]};
                    for (int j = 0; j < 4; j++) {
                        float x = corners[j * 2];
                        float y = corners[j * 2 + 1];
                        canvas.drawCircle(x, y, 6, detectionPaint);
                    }
                }
            }
        }
        
        if (scanRect != null) {
            canvas.drawRect(scanRect, borderPaint);
            
            float left = scanRect.left;
            float top = scanRect.top;
            float right = scanRect.right;
            float bottom = scanRect.bottom;
            
            canvas.drawLine(left, top, left + CORNER_LENGTH, top, cornerPaint);
            canvas.drawLine(left, top, left, top + CORNER_LENGTH, cornerPaint);
            
            canvas.drawLine(right - CORNER_LENGTH, top, right, top, cornerPaint);
            canvas.drawLine(right, top, right, top + CORNER_LENGTH, cornerPaint);
            
            canvas.drawLine(left, bottom - CORNER_LENGTH, left, bottom, cornerPaint);
            canvas.drawLine(left, bottom, left + CORNER_LENGTH, bottom, cornerPaint);
            
            canvas.drawLine(right - CORNER_LENGTH, bottom, right, bottom, cornerPaint);
            canvas.drawLine(right, bottom - CORNER_LENGTH, right, bottom, cornerPaint);
        }
    }

    public RectF getScanRect() {
        return scanRect;
    }
    
    public void setDetectionBoxes(List<float[]> boxes) {
        this.detectionBoxes = boxes;
        invalidate();
    }
    
    public void clearDetectionBoxes() {
        if (detectionBoxes != null) {
            detectionBoxes.clear();
            invalidate();
        }
    }
}
