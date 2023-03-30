package com.baller.test;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Base64;
import android.util.Log;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.json.JSONObject;


public class BallerTTSHTTPTest extends BallerBase {
    private static String mLogTag = "BallerTTSHTTPTest";
    private static String mUrl = "http://" + mHost + "/v1/service/v1/tts";

    private String mLanguage;
    private String mTxt;
    private AudioTrack mAudioTrack = null;

    private int iAudioBuff = AudioTrack.getMinBufferSize(16000,
            AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

    BallerTTSHTTPTest(String strLanguage, String strTxt) {
        this.mLanguage = strLanguage;
        this.mTxt = strTxt;
    }

    private boolean postData(String requestId, byte[] testData) {
        JSONObject businessParams = new JSONObject();
        try {
            businessParams.put("request_id", requestId);
            businessParams.put("language", mLanguage);
            businessParams.put("sample_format", "audio/L16;rate=16000");
            businessParams.put("audio_encode", "raw");
            businessParams.put("speed", 1.0f);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        String businessParamsBase64 =
                Base64.encodeToString(businessParams.toString().getBytes(), Base64.NO_WRAP);

        String requestTime = getGmtTime();
        String checkSum = mAppkey + requestTime + businessParamsBase64;
        String md5 = MD5(checkSum);

        try {

            URL url = new URL(mUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            // 设置连接超时为5秒
            connection.setConnectTimeout(5000);
            // 设置请求类型为Get类型
            connection.setRequestMethod("POST");
            connection.setRequestProperty("B-AppId", String.valueOf(mAppId));
            connection.setRequestProperty("B-CurTime", requestTime);
            connection.setRequestProperty("B-Param", businessParamsBase64);
            connection.setRequestProperty("B-CheckSum", md5);
            connection.setRequestProperty("Content-Type", "application/octet-stream");

            OutputStream out = connection.getOutputStream();
            out.write(testData);
            out.flush();
            out.close();

            int  status_code = connection.getResponseCode();

            if (status_code == 200) {
                InputStream inStream = connection.getInputStream();
                byte[] bt = readStream(inStream);

                String content = new String(bt, StandardCharsets.UTF_8);
                com.alibaba.fastjson.JSONObject responseContent = com.alibaba.fastjson.JSONObject.parseObject(content);
                int error_code = responseContent.getIntValue("code");
                String message = responseContent.getString("message");
                if (error_code != 0)
                {
                    Log.i(mLogTag, "tts failed: " + error_code + " " +message);
                }
                inStream.close();
            } else {
                Log.i(mLogTag, "tts post failed");
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private  boolean getResult(String requestId) {
        JSONObject paramMap = new JSONObject();
        try {
            paramMap.put("request_id", requestId);
        }catch (Exception e) {
            e.printStackTrace();
            return true;
        }

        String businessParamBase64 = Base64.encodeToString(paramMap.toString().getBytes(), Base64.NO_WRAP);

        String requestTime = getGmtTime();
        String checkSum = mAppkey + requestTime + businessParamBase64;
        String md5 = MD5(checkSum);


        try {
            URL url = new URL(mUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            // 设置连接超时为5秒
            connection.setConnectTimeout(5000);
            // 设置请求类型为Get类型
            connection.setRequestMethod("GET");
            connection.setRequestProperty("B-AppId", String.valueOf(mAppId));
            connection.setRequestProperty("B-CurTime", requestTime);
            connection.setRequestProperty("B-Param", businessParamBase64);
            connection.setRequestProperty("B-CheckSum", md5);

            boolean isEnd;
            int  status_code = connection.getResponseCode();

            if (status_code == 200) {
                InputStream inStream = connection.getInputStream();
                byte[] audio = readStream(inStream);
                if (audio.length > 0) {
                    mAudioTrack.write(audio, 0, audio.length);
                }

                isEnd = 1 == Integer.parseInt(connection.getHeaderField("B-Is-End"));
                int error_code = Integer.parseInt(connection.getHeaderField("B-Code"));
                String message = connection.getHeaderField("B-Message");
                if (error_code != 0)
                {
                    Log.i(mLogTag, "tts failed: " + error_code + " " +message);
                }
                return isEnd;
            } else {
                Log.i(mLogTag, "tts get failed");
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    @Override
    public void run() {
        String requestId = UUID.randomUUID().toString();
        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 16000,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, iAudioBuff * 4,
                AudioTrack.MODE_STREAM);
        mAudioTrack.play();

        try {
            boolean putSuccess = postData(requestId, mTxt.getBytes(StandardCharsets.UTF_8));
            if (!putSuccess) {
                Log.e(mLogTag, "post data failed");
                return;
            }

            // 获取结果
            while (!isInterrupted()) {
                if(getResult(requestId)) {
                    break;
                }
                try {
                    Thread.sleep(40);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
