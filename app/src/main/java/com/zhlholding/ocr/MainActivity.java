package com.zhlholding.ocr;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_VIN_SCAN = 1001;

    private TextView resultText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        resultText = findViewById(R.id.result_text);

        Button startScanButton = findViewById(R.id.start_scan_button);
        startScanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 方式1：使用 startActivityForResult 获取结果
                Intent intent = new Intent(MainActivity.this, VinScannerActivity.class);
                startActivityForResult(intent, REQUEST_VIN_SCAN);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_VIN_SCAN) {
            if (resultCode == VinScannerActivity.RESULT_VIN_SUCCESS) {
                // 获取识别结果
                String vinCode = data.getStringExtra(VinScannerActivity.RESULT_VIN_CODE);
                String rawResult = data.getStringExtra(VinScannerActivity.RESULT_RAW_RESULT);
                String extraMessage = data.getStringExtra(VinScannerActivity.RESULT_EXTRA_MESSAGE);

                Toast.makeText(this, "识别成功: " + vinCode, Toast.LENGTH_SHORT).show();

                // 显示结果到 TextView
                displayResult(vinCode, rawResult, extraMessage);
            } else {
                Toast.makeText(this, "识别取消或失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 显示识别结果到 TextView
     *
     * @param vinCode      标准化后的 VIN 码
     * @param rawResult   原始识别结果
     * @param extraMessage 附加信息
     */
    private void displayResult(String vinCode, String rawResult, String extraMessage) {
        resultText.setVisibility(View.VISIBLE);

        StringBuilder sb = new StringBuilder();
        sb.append("VIN码: ").append(vinCode).append("\n");
        if (rawResult != null && !rawResult.equals(vinCode)) {
            sb.append("原始结果: ").append(rawResult).append("\n");
        }
        sb.append("状态: ").append(extraMessage);

        resultText.setText(sb.toString());
        resultText.setTextColor(getResources().getColor(android.R.color.holo_green_light));
    }
}
