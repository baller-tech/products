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
    private Button start_asr, start_ocr, start_mt, start_tts;
    private TextView text_input, text_output;
    BallerBase baller_object = null;
    BallerPermission mBallerPermission;
    int checkPermission;

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
        start_mt = findViewById(R.id.start_mt);
        start_tts = findViewById(R.id.start_tts);

        start_ocr.setOnClickListener(this);
        start_asr.setOnClickListener(this);
        start_mt.setOnClickListener(this);
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
                    baller_object = new BallerASRHTTPTest("zho");
//                  baller_object = new BallerASRWebSocketTest("tib_wz");
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
                baller_object = new BallerTTSHTTPTest("tib_wz", "");
//                baller_object = new BallerTTSWebSocketTest("tib_wz", "");
                baller_object.setmHandler(handler);
                baller_object.start();
                break;
            case R.id.start_tts:
                String input_text = text_input.getText().toString();
                if (input_text.length() == 0) {
                    input_text = "རྩོམ་རིག་ནི་སྐད་ཡིག་གི་སྒྱུ་རྩལ་ཞིག་ཡིན་ལ་སྤྱི་ཚོགས་རིག་གནས་ཀྱི་མཚོན་སྟངས་གལ་ཆེན་ཞིག་ཡིན།  \n" +
                            "རྩོམ་རིག་བརྩམས་ཆོས་ནི་རྩོམ་པ་པོས་ཐུན་མོང་མ་ཡིན་པའི་སྐད་ཡིག་སྒྱུ་རྩལ་ལ་བརྟེན་ནས་དེའི་ཐུན་མོང་མ་ཡིན་པའི་སེམས་ཁམས་མཚོན་པའི་བརྩམས་ཆོས་ཤིག་ཡིན་པ་དང་རང་གཤིས་ཀྱི་ཁྱད་ཆོས་ཆེན་པོ་ལྡན་པའི་ཐུན་མོང་མ་ཡིན་པའི་རང་བཞིན་དེ་གཉིས་དང་ཁ་བྲལ་ཚེ་རྩོམ་རིག་བརྩམས་ཆོས་ངོ་མ་མེད།  \n" +
                            "ཕུལ་དུ་བྱུང་བའི་རྩོམ་རིག་པ་ཞིག་ནི་མི་རིགས་ཤིག་གི་བསམ་པའི་འཇིག་རྟེན་གྱི་དཔའ་བོ་ཡིན།  \n" +
                            "རྩོམ་རིག་གིས་མི་རིགས་ཤིག་གི་སྒྱུ་རྩལ་དང་བློ་གྲོས་མཚོན་ཡོད། ";
                    text_input.setText(input_text);
                }
                baller_object = new BallerTTSHTTPTest("tib_wz", input_text);
//                baller_object = new BallerTTSWebSocketTest("tib_wz", input_text);
                baller_object.setmHandler(handler);
                baller_object.start();
                break;
            case R.id.start_mt:
                String content = text_input.getText().toString();
                if (content.length() == 0) {
                    content = "རྩོམ་རིག་ནི་སྐད་ཡིག་གི་སྒྱུ་རྩལ་ཞིག་ཡིན་ལ་སྤྱི་ཚོགས་རིག་གནས་ཀྱི་མཚོན་སྟངས་གལ་ཆེན་ཞིག་ཡིན།  \n" +
                            "རྩོམ་རིག་བརྩམས་ཆོས་ནི་རྩོམ་པ་པོས་ཐུན་མོང་མ་ཡིན་པའི་སྐད་ཡིག་སྒྱུ་རྩལ་ལ་བརྟེན་ནས་དེའི་ཐུན་མོང་མ་ཡིན་པའི་སེམས་ཁམས་མཚོན་པའི་བརྩམས་ཆོས་ཤིག་ཡིན་པ་དང་རང་གཤིས་ཀྱི་ཁྱད་ཆོས་ཆེན་པོ་ལྡན་པའི་ཐུན་མོང་མ་ཡིན་པའི་རང་བཞིན་དེ་གཉིས་དང་ཁ་བྲལ་ཚེ་རྩོམ་རིག་བརྩམས་ཆོས་ངོ་མ་མེད།  \n" +
                            "ཕུལ་དུ་བྱུང་བའི་རྩོམ་རིག་པ་ཞིག་ནི་མི་རིགས་ཤིག་གི་བསམ་པའི་འཇིག་རྟེན་གྱི་དཔའ་བོ་ཡིན།  \n" +
                            "རྩོམ་རིག་གིས་མི་རིགས་ཤིག་གི་སྒྱུ་རྩལ་དང་བློ་གྲོས་མཚོན་ཡོད། ";
                    text_input.setText(content);
                }
//                baller_object = new BallerMTWebSocketTest("tib-chs", content);
                baller_object = new BallerMTHTTPTest("tib-chs", content);
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