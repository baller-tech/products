package com.baller.test;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private Button start_asr;
    private Button start_ocr;
    private Button start_nmt;
    private Button start_tts;
    private TextView text_input, text_output;
    BallerBase baller_object = null;
    BallerPermission mBallerPermission;
    int checkPermission;
    int type = 1;

    @SuppressLint("HandlerLeak")
    public Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            if (1 == msg.what) {
                String result = (String) msg.obj;
                text_output.append(result);
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        text_input = findViewById(R.id.text_input);
        text_output = findViewById(R.id.text_output);

        start_asr = findViewById(R.id.start_asr);
        start_ocr = findViewById(R.id.start_ocr);
        start_nmt = findViewById(R.id.start_nmt);
        start_tts = findViewById(R.id.start_tts);

        start_ocr.setOnClickListener(this);
        start_asr.setOnClickListener(this);
        start_nmt.setOnClickListener(this);
        start_tts.setOnClickListener(this);

        mBallerPermission = new BallerPermission(this);
        checkPermission = mBallerPermission.checkPermissions();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(mBallerPermission.requestPermissionsResult(requestCode, permissions, grantResults)){
            checkPermission = 1;
        } else {
            checkPermission = 2;
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    @Override
    public void onClick(View view) {
        text_output.setText("");
        try {
            if (baller_object != null) {
                baller_object.setEnd();
                baller_object.join();
                baller_object = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        switch (view.getId()) {
            case R.id.start_asr:
                if (start_asr.getText() == "开始录音") {
                    start_asr.setText("结束录音");
                    text_input.setText("");
                    if (type == 1) {
                        baller_object = new BallerASRHTTPTest("zho");
                    } else {
                        baller_object = new BallerASRWebSocketTest("zho");
                    }

                    baller_object.setmHandler(handler);
                    baller_object.start();
                } else {
                    start_asr.setText("开始录音");
                    try {
                        if (baller_object != null) {
                            baller_object.setEnd();
                            baller_object.join();
                            baller_object = null;
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                break;
            case R.id.start_ocr:
                if (type == 1) {
                    baller_object = new BallerOCRHTTPTest("chs");
                }

                baller_object.setmHandler(handler);
                baller_object.start();
                break;
            case R.id.start_tts:
                String input_text = text_input.getText().toString();
                if (input_text.length() == 0) {
                    input_text = "今天天气不错";
                    text_input.setText(input_text);
                }

                if (type == 1) {
                    baller_object = new BallerTTSHTTPTest("zho", input_text);
                } else {
                    baller_object = new BallerTTSWebSocketTest("tib_wz", input_text);
                }

                baller_object.setmHandler(handler);
                baller_object.start();
                break;
            case R.id.start_nmt:
                String content = text_input.getText().toString();
                if (content.length() == 0) {
                    content = "今天天气不错";
                    text_input.setText(content);
                }

                if (type == 1) {
                    baller_object = new BallerMTHTTPTest("chs-eng", content);
                } else {
                    baller_object = new BallerMTWebSocketTest("tib-chs", content);
                }

                baller_object.setmHandler(handler);
                baller_object.start();
                break;
        }
    }


    @Override
    protected void onDestroy() {
        if (baller_object != null) {
            baller_object.setEnd();
            baller_object.interrupt();
            baller_object = null;
        }
        super.onDestroy();
    }
}